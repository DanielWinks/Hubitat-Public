#!/usr/bin/env python3
"""Generate the chunked Markdown Hubitat reference from hubitat.json."""
import json
from collections import defaultdict
from pathlib import Path

DOCS = Path(__file__).resolve().parent
SOURCE = DOCS / 'hubitat.json'
OUTPUT = DOCS / 'hubitat-reference'
data = json.loads(SOURCE.read_text())
sections = {item['id']: item['label'] for item in data['sections']}
pages = {page['id']: page for page in data['pages']}
by_section = defaultdict(list)


def cell(value):
    return str(value or '').replace('|', '\\|').replace('\n', ' ')


def member_table(items, heading, detail_label):
    if not items:
        return []
    lines = [f'## {heading}', '', f'| Name | {detail_label} | Description |', '|---|---|---|']
    for item in sorted(items, key=lambda row: row.get('name', '').lower()):
        detail = item.get('signature') or item.get('type') or '—'
        lines.append(f"| `{cell(item.get('name'))}` | `{cell(detail)}` | {cell(item.get('summary')) or '—'} |")
    return lines


OUTPUT.mkdir(exist_ok=True)
for page in data['pages']:
    section = page.get('section', 'other')
    by_section[section].append(page)
    folder = OUTPUT / section
    folder.mkdir(parents=True, exist_ok=True)
    title = page.get('label') or page.get('topic') or page['id']
    lines = [f'# {title}', '', f"- **ID:** `{page['id']}`", f"- **Section:** {sections.get(section, section)}", f"- **Class:** `{page.get('className', '—')}`"]
    if page.get('kind'):
        lines.append(f"- **Kind:** {page['kind']}")
    lines += ['', f"> {page.get('usage') or page.get('topic') or title}"]
    if page.get('reference'):
        lines += ['', f"Source reference: {page['reference']}"]
    parent_ids = ([page['extendsPageId']] if page.get('extendsPageId') else []) + (page.get('extendsPageIds') or [])
    if page.get('extendsClass') or parent_ids:
        lines += ['', '## Inheritance', '']
        if page.get('extendsClass'):
            lines.append(f"- Extends `{page['extendsClass']}`")
        for parent_id in parent_ids:
            parent = pages.get(parent_id)
            if parent:
                lines.append(f"- [{parent.get('label') or parent_id}](../{parent.get('section', 'other')}/{parent_id}.md)")
            else:
                lines.append(f'- `{parent_id}` (page not included in this snapshot)')
    lines += ['', *member_table(page.get('methods', []), 'Methods', 'Signature'), '', *member_table(page.get('properties', []), 'Properties', 'Type')]
    (folder / f"{page['id']}.md").write_text('\n'.join(lines).strip() + '\n')

index = [
    '# Hubitat Elevation Developer Reference', '',
    f"- Firmware snapshot: `{data['firmwareVersion']}`",
    f"- Schema version: `{data['schemaVersion']}`",
    f"- Content revision: `{data['contentRevision']}`", '',
    'This reference is split into one Markdown file per API page. Search by class, method, property, or API page ID, or browse the section indexes.', '',
    '## Start with these guides', ''
]
for guide in data['guides']:
    index.append(f"- [{guide['title']}]({guide['url']}): {guide['summary']}")
index += ['', '## API pages', '']
for section, group in sorted(by_section.items(), key=lambda pair: sections.get(pair[0], pair[0]).lower()):
    label = sections.get(section, section)
    index += [f'### {label}', '', f'[{label} page index]({section}/README.md) · {len(group)} pages', '']
    for page in sorted(group, key=lambda row: (row.get('label') or row.get('topic') or row['id']).lower()):
        title = page.get('label') or page.get('topic') or page['id']
        index.append(f"- [{title}]({section}/{page['id']}.md) (`{page.get('className', '')}`)")
    index.append('')
(OUTPUT / 'README.md').write_text('\n'.join(index).rstrip() + '\n')

for section, group in by_section.items():
    label = sections.get(section, section)
    lines = [f'# {label} API pages', '', f'{len(group)} pages in this section.', '', '| Page | Class | Kind |', '|---|---|---|']
    for page in sorted(group, key=lambda row: (row.get('label') or row.get('topic') or row['id']).lower()):
        title = page.get('label') or page.get('topic') or page['id']
        lines.append(f"| [{cell(title)}]({page['id']}.md) | `{cell(page.get('className'))}` | {cell(page.get('kind', 'API'))} |")
    (OUTPUT / section / 'README.md').write_text('\n'.join(lines) + '\n')

(DOCS / 'llms.txt').write_text('\n'.join([
    '# Hubitat Elevation developer reference', '',
    'Agent-readable Hubitat API documentation generated from `docs/hubitat.json`.', '',
    '## Start here', '',
    '- [Reference index](hubitat-reference/README.md)',
    '- [Apps](hubitat-reference/apps/README.md)',
    '- [Drivers](hubitat-reference/drivers/README.md)',
    '- [Shared APIs](hubitat-reference/shared/README.md)',
    '- [Protocols](hubitat-reference/protocols/README.md)',
    '- [Structured source](hubitat.json)', '',
    'Search section indexes and page files for API names. Each page includes its ID, class, usage, inheritance, methods, and properties.', '',
    f"Firmware snapshot: `{data['firmwareVersion']}`."
]) + '\n')
print(f'Generated {len(pages)} API pages in {OUTPUT}')
