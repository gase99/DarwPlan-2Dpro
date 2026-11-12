from pathlib import Path
import os
import xml.etree.ElementTree as ET


def annotate(level, title, text):
    escaped = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{level} title={title}::{escaped}')


totals = dict(tests=0, failures=0, errors=0, skipped=0)
for file in Path('app/build/test-results').rglob('TEST-*.xml'):
    root = ET.parse(file).getroot()
    for key in totals:
        totals[key] += int(root.attrib.get(key, 0))
    for case in root.findall('testcase'):
        for tag in ('failure', 'error'):
            failure = case.find(tag)
            if failure is not None:
                annotate('error', case.attrib.get('name', 'Test failure'), (failure.text or '')[:5000])
summary = ' · '.join(f'{key}: {value}' for key, value in totals.items())
annotate('notice', 'Test summary', summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as stream:
        stream.write(f'## Android quality checks\n\n{summary}\n')
for file in Path('app/build/reports').glob('lint-results-*.xml'):
    for issue in ET.parse(file).getroot().findall('issue'):
        if issue.attrib.get('severity') in ('Fatal', 'Error'):
            annotate('error', issue.attrib.get('id', 'Lint'), issue.attrib.get('message', ''))
# Export the generated schema through Checks once, so it can be committed for future migrations.
for file in Path('app/schemas').rglob('*.json'):
    annotate('notice', 'Room schema', file.read_text())
