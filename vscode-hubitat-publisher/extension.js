const vscode = require('vscode');
const http = require('http');
const https = require('https');

function request(url, method = 'GET', form = undefined, timeoutMs = 180000, followRedirects = true) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const transport = target.protocol === 'https:' ? https : http;
    const body = form ? new URLSearchParams(form).toString() : null;
    const req = transport.request(target, {
      method,
      timeout: timeoutMs,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(body) } : {})
      }
    }, (res) => {
      if (followRedirects && [301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
        res.resume();
        request(new URL(res.headers.location, target).toString(), method, form, timeoutMs, followRedirects)
          .then(resolve, reject);
        return;
      }
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode || 0, body: Buffer.concat(chunks).toString('utf8'), headers: res.headers }));
    });
    req.on('timeout', () => req.destroy(new Error(`Hub request timed out after ${Math.round(timeoutMs / 1000)} seconds`)));
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function identify(source) {
  const definition = /^\s*definition\s*\(/m.exec(source);
  const scope = definition ? source.slice(definition.index + definition[0].length) : source;
  const name = /\bname\s*:\s*["']([^"']+)["']/.exec(scope);
  const namespace = /\bnamespace\s*:\s*["']([^"']+)["']/.exec(scope);
  return { name: name && name[1], namespace: namespace && namespace[1] };
}

function codeType(source) {
  if (/^\s*metadata\s*\{/m.test(source)) return 'driver';
  if (/^\s*definition\s*\(/m.test(source)) return 'app';
  return null;
}

function responseError(body) {
  const match = /id=["']errors["'][^>]*>(.*?)<\/div>/is.exec(body);
  if (match) return match[1].replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  if (/password.{0,100}login|login.{0,100}password/i.test(body)) return 'Hub returned a login page. Check hub security/authentication.';
  if (/\/ui2\/|<title>/i.test(body)) return 'Hub returned the editor page without error details. Check the compiler message in the Hubitat editor.';
  return body.slice(0, 1400) || 'Empty response from Hubitat.';
}

async function publish(host, type, identity, source, timeoutMs) {
  const listPath = type === 'app' ? '/hub2/userAppTypes' : '/hub2/userDeviceTypes';
  const listResponse = await request(host + listPath, 'GET', undefined, Math.min(timeoutMs, 15000));
  if (listResponse.status !== 200) throw new Error(`Could not list ${type}s on hub (HTTP ${listResponse.status}). ${responseError(listResponse.body)}`);
  let entries;
  try { entries = JSON.parse(listResponse.body); }
  catch (_) { throw new Error(`Hub returned an unreadable ${type} list. Check hub security/authentication.`); }
  if (!Array.isArray(entries)) throw new Error(`Hub returned an unexpected ${type} list.`);

  const exact = entries.find(item => item.name === identity.name && item.namespace === identity.namespace);
  const nameOnly = exact ? null : entries.find(item => item.name === identity.name);
  let matched = exact || nameOnly;
  if (nameOnly) {
    const choice = await vscode.window.showWarningMessage(
      `Found “${identity.name}” on the hub with namespace “${nameOnly.namespace || '(none)'}”, while this file uses “${identity.namespace || '(none)'}”. Update that code anyway?`,
      { modal: true }, 'Update existing code', 'Create a new copy'
    );
    if (!choice) throw new Error('Publish cancelled.');
    if (choice === 'Create a new copy') matched = null;
  }

  if (matched) {
    const versionResponse = await request(`${host}/${type}/ajax/code?id=${encodeURIComponent(matched.id)}`, 'GET', undefined, Math.min(timeoutMs, 15000));
    let version = 0;
    if (versionResponse.status === 200) {
      try {
        const current = JSON.parse(versionResponse.body);
        if (String(current.id) === String(matched.id) && Number.isFinite(Number(current.version))) version = Number(current.version);
      } catch (_) { /* Hubitat can omit version on some firmware; update endpoint reports any rejection. */ }
    }
    const saved = await request(`${host}/${type}/ajax/update`, 'POST', { id: String(matched.id), version: String(version), source }, timeoutMs);
    if (saved.status !== 200) throw new Error(`Hub rejected update (HTTP ${saved.status}). ${responseError(saved.body)}`);
    let result;
    try { result = JSON.parse(saved.body); }
    catch (_) { throw new Error(`Hub returned a non-JSON update response. ${responseError(saved.body)}`); }
    if (result.status !== 'success') throw new Error(`Hub rejected update: ${(result.errorMessage || 'no error detail').replace('java.sql.SQLException:', '').trim()}`);
    return `Updated ${type} “${identity.name}” (ID ${matched.id}, version ${result.version ?? version + 1})${exact ? '' : ' by name only'}.`;
  }

  const created = await request(`${host}/${type}/save`, 'POST', { id: '', version: '', create: '', source }, timeoutMs, false);
  if (created.status !== 302) throw new Error(`Hub rejected creation (HTTP ${created.status}). ${responseError(created.body)}`);
  const location = created.headers.location || '';
  const id = /\/(\d+)\/?(?:[?#].*)?$/.exec(location);
  if (!id) throw new Error(`Hub created the code but returned an unexpected redirect: ${location || '(no Location header)'}`);
  return `Created ${type} “${identity.name}” (ID ${id[1]}).`;
}

function normalizeHost(value) {
  const host = /^https?:\/\//i.test(value) ? value : `http://${value}`;
  return host.replace(/\/+$/, '');
}

function activate(context) {
  context.subscriptions.push(vscode.commands.registerCommand('hubitatDynamicPublisher.publishActive', async () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor) { vscode.window.showErrorMessage('Open a Hubitat app or driver before publishing.'); return; }
    const source = editor.document.getText();
    const type = codeType(source);
    if (!type) { vscode.window.showErrorMessage('Could not identify this file as a Hubitat app or driver.'); return; }
    const identity = identify(source);
    if (!identity.name || !identity.namespace) { vscode.window.showErrorMessage('Could not read name and namespace from the Hubitat definition.'); return; }
    const config = vscode.workspace.getConfiguration('hubitatDynamicPublisher');
    const hubs = config.get('hubs', []);
    if (!hubs.length) { vscode.window.showErrorMessage('Add hubs under Settings → Hubitat Dynamic Publisher → Hubs.'); return; }
    const selection = await vscode.window.showQuickPick(hubs.map(hub => ({ label: hub.name, description: hub.host, hub })), { placeHolder: 'Choose the Hubitat hub to publish to' });
    if (!selection) return;
    try {
      await editor.document.save();
      const host = normalizeHost(selection.hub.host);
      const timeoutMs = Math.max(10, config.get('requestTimeoutSeconds', 180)) * 1000;
      const message = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: `Publishing ${identity.name} to ${selection.hub.name}…`, cancellable: false }, () => publish(host, type, identity, source, timeoutMs));
      vscode.window.showInformationMessage(message);
    } catch (error) {
      vscode.window.showErrorMessage(`Hubitat publish failed: ${error.message}`);
    }
  }));
}

function deactivate() {}
module.exports = { activate, deactivate };
