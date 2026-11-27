/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (danielwinks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import groovy.transform.Field
import groovy.transform.CompileStatic
import java.util.Locale
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils

definition(
  name: 'Sonos Advanced Controller - Playlists',
  version: '0.19.1',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  parent: 'dwinks:Sonos Advanced Controller',
  category: 'Audio',
  description: 'Shows the latest Sonos playlists as artwork cards with their IDs',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosAdvancedPlaylistsApp.groovy',
  singleThreaded: true
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
}

// =============================================================================
// Constants
// =============================================================================
@Field static final String CATALOG_RECORDS_STATE_KEY = 'catalogRecords'
@Field static final String CATALOG_UPDATED_AT_STATE_KEY = 'catalogUpdatedAt'
@Field static final String CATALOG_SOURCE_STATE_KEY = 'catalogSourceDni'
@Field static final String ITEM_LABEL = 'Playlist'
@Field static final String PLURAL_LABEL = 'Playlists'

// =============================================================================
// Logging
// =============================================================================
void logError(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn', 'error']) { log.error("${app.label ?: app.name}: ${message}") } }
void logWarn(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn']) { log.warn("${app.label ?: app.name}: ${message}") } }
void logInfo(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info']) { log.info("${app.label ?: app.name}: ${message}") } }
void logDebug(String message) { if(getConfiguredLogLevel() in ['trace', 'debug']) { log.debug("${app.label ?: app.name}: ${message}") } }
String getConfiguredLogLevel() {
  String level = settings.logLevel?.toString()?.toLowerCase(Locale.ROOT)
  return level in ['trace', 'debug', 'info', 'warn', 'error', 'off'] ? level : 'info'
}

// =============================================================================
// Lifecycle
// =============================================================================
void installed() { initialize() }
void updated() { configure() }
void initialize() { configure() }
void configure() { /* Rendering reads stored state; nothing to schedule or subscribe. */ }

// =============================================================================
// Snapshot storage (called by the parent app from its queued drain)
// =============================================================================
/** Replace the stored snapshot with the latest normalized records: [[id:, name:, imageUrl:], ...]. */
void storeCatalogSnapshot(List<Map> records, String sourceDni = null) {
  List<Map> normalized = normalizeCatalogRecords(records)
  if(sourceDni) { state[CATALOG_SOURCE_STATE_KEY] = sourceDni }
  // Several loads can report the same catalog; storing an identical snapshot again changes nothing.
  if(state[CATALOG_RECORDS_STATE_KEY] instanceof List && state[CATALOG_RECORDS_STATE_KEY] == normalized) {
    logDebug("${PLURAL_LABEL} are unchanged; not stored again")
    return
  }
  state[CATALOG_RECORDS_STATE_KEY] = normalized
  state[CATALOG_UPDATED_AT_STATE_KEY] = now()
  logDebug("Stored ${normalized.size()} ${PLURAL_LABEL.toLowerCase(Locale.ROOT)}")
}

@CompileStatic
static List<Map> normalizeCatalogRecords(List<Map> records) {
  List<Map> normalized = []
  if(records == null) { return normalized }
  for(Map record : records) {
    String id = record?.get('id')?.toString()
    if(!id) { continue }
    normalized.add([id: id, name: record.get('name')?.toString() ?: '', imageUrl: record.get('imageUrl')?.toString() ?: ''])
  }
  normalized.sort { Map a, Map b -> (a.get('name') as String).toLowerCase(Locale.ROOT) <=> (b.get('name') as String).toLowerCase(Locale.ROOT) }
  return normalized
}

// =============================================================================
// Page
// =============================================================================
Map mainPage() {
  dynamicPage(name: 'mainPage', title: "Sonos ${PLURAL_LABEL}", install: true, uninstall: true) {
    section() {
      paragraph renderCatalogCards((state[CATALOG_RECORDS_STATE_KEY] instanceof List ? (List<Map>)state[CATALOG_RECORDS_STATE_KEY] : []) as List<Map>,
          state[CATALOG_UPDATED_AT_STATE_KEY] as Long)
    }
    section('Logging') {
      input 'logLevel', 'enum', title: 'Logging level',
        options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'],
        required: false, defaultValue: 'info'
    }
  }
}

// =============================================================================
// Rendering (pure helpers)
// =============================================================================
@CompileStatic
static String escapeCatalogHtml(Object value) {
  return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value?.toString())).replace("'", '&#39;')
}

/** Only http(s) artwork is rendered; anything else falls back to the placeholder. */
@CompileStatic
static String safeCatalogImageUrl(String url) {
  String trimmed = url?.trim()
  if(!trimmed) { return null }
  String lower = trimmed.toLowerCase(Locale.ROOT)
  return (lower.startsWith('http://') || lower.startsWith('https://')) ? trimmed : null
}

@CompileStatic
static String renderCatalogCards(List<Map> records, Long updatedAt) {
  String css = """
    <style>
      .sonos-catalog { color: var(--text-color, #30343b); }
      .sonos-catalog-summary { color: var(--text-color-secondary, #6b7280); font-size: 13px; margin: 0 0 14px; }
      .sonos-catalog-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
      .sonos-catalog-card {
        background: var(--surface-card, var(--surface-a, #fff));
        border: 1px solid var(--surface-border, #dedede);
        border-radius: 12px;
        box-shadow: 0 2px 10px rgba(25, 35, 55, .07);
        display: flex;
        flex-direction: column;
        min-width: 0;
        overflow: hidden;
      }
      .sonos-catalog-card-header {
        background: var(--surface-b, #f7f8fa);
        border-bottom: 1px solid var(--surface-border, #e5e7eb);
        font-size: 14px;
        font-weight: 650;
        line-height: 1.3;
        overflow-wrap: anywhere;
        padding: 10px 12px;
      }
      .sonos-catalog-art { aspect-ratio: 1 / 1; background: var(--surface-b, #f2f3f5); display: block; object-fit: cover; width: 100%; }
      .sonos-catalog-art-placeholder {
        align-items: center;
        color: var(--text-color-secondary, #9aa2ad);
        display: flex;
        font-size: 12px;
        font-weight: 600;
        justify-content: center;
        letter-spacing: .04em;
        text-transform: uppercase;
      }
      .sonos-catalog-id { border-top: 1px solid var(--surface-border, #e5e7eb); font-size: 12px; padding: 8px 12px; }
      .sonos-catalog-id-label { color: var(--text-color-secondary, #6b7280); margin-right: 4px; }
      .sonos-catalog-id-value { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-weight: 600; overflow-wrap: anywhere; }
      .sonos-catalog-empty {
        border: 1px dashed var(--surface-border, #c9ced6);
        border-radius: 12px;
        color: var(--text-color-secondary, #6b7280);
        padding: 22px;
        text-align: center;
      }
    </style>
  """.toString()
  StringBuilder html = new StringBuilder(css)
  html.append("<div class='sonos-catalog'>")
  if(records == null || records.isEmpty()) {
    String emptyMessage = updatedAt != null
        ? "No ${PLURAL_LABEL.toLowerCase(Locale.ROOT)} found in your Sonos library.".toString()
        : "No ${PLURAL_LABEL.toLowerCase(Locale.ROOT)} have been loaded yet. They appear here after a Sonos player finishes loading them.".toString()
    html.append("<div class='sonos-catalog-empty'>${escapeCatalogHtml(emptyMessage)}</div>")
  } else {
    String updated = updatedAt != null ? " Last updated ${escapeCatalogHtml(new Date(updatedAt.longValue()).toString())}." : ''
    html.append("<div class='sonos-catalog-summary'>${records.size()} ${escapeCatalogHtml(PLURAL_LABEL.toLowerCase(Locale.ROOT))}.${updated}</div>")
    html.append("<div class='sonos-catalog-grid'>")
    for(Map record : records) {
      String id = record.get('id')?.toString()
      String name = record.get('name')?.toString() ?: "${ITEM_LABEL} ${id}".toString()
      String image = safeCatalogImageUrl(record.get('imageUrl')?.toString())
      html.append("<div class='sonos-catalog-card'>")
      html.append("<div class='sonos-catalog-card-header'>${escapeCatalogHtml(name)}</div>")
      if(image) {
        html.append("<img class='sonos-catalog-art' loading='lazy' alt='${escapeCatalogHtml(name)}' src='${escapeCatalogHtml(image)}'>")
      } else {
        html.append("<div class='sonos-catalog-art sonos-catalog-art-placeholder'>No artwork</div>")
      }
      html.append("<div class='sonos-catalog-id'><span class='sonos-catalog-id-label'>${escapeCatalogHtml(ITEM_LABEL)} ID:</span><span class='sonos-catalog-id-value'>${escapeCatalogHtml(id)}</span></div>")
      html.append('</div>')
    }
    html.append('</div>')
  }
  html.append('</div>')
  return html.toString()
}
