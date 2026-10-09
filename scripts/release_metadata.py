#!/usr/bin/env python3
"""Validate the immutable prerelease contract before building/publishing anything."""
import argparse
from pathlib import Path
import re
import tomllib
import zipfile


def metadata(root: Path, tag: str | None = None) -> dict[str, str]:
    versions = re.findall(r'^\s*version\s*=\s*"([^"\n]+)"\s*$',
                          (root / 'build.gradle.kts').read_text(), re.MULTILINE)
    if len(versions) != 1:
        raise ValueError('Expected one explicit project version in build.gradle.kts')
    version = versions[0]
    expected = f'v{version}'
    if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+-test\.[0-9]+', expected):
        raise ValueError('Only explicitly numbered test prereleases may use this publisher')
    if tag is not None and tag != expected:
        raise ValueError(f'Requested tag {tag!r} differs from project version {expected!r}')
    notes = f'docs/releases/{expected}.md'
    checklist = f'docs/testing/{expected}.md'
    for document in (notes, checklist):
        if not (root / document).is_file():
            raise ValueError(f'Missing release document: {document}')
    return {'tag': expected, 'version': version,
            'artifact': f'mod/build/libs/chemmod-{version}.jar',
            'notes': notes, 'checklist': checklist}


def verify_jar(root: Path, values: dict[str, str]) -> None:
    with zipfile.ZipFile(root / values['artifact']) as jar:
        descriptor = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
    mods = [mod for mod in descriptor.get('mods', []) if mod.get('modId') == 'chemmod']
    if len(mods) != 1 or mods[0].get('version') != values['version']:
        raise ValueError('JAR metadata does not match the ChemMod prerelease version')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tag', nargs='?')
    parser.add_argument('--verify-jar', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    values = metadata(root, args.tag)
    if args.verify_jar:
        verify_jar(root, values)
    for key, value in values.items():
        print(f'{key}={value}')


if __name__ == '__main__':
    main()
