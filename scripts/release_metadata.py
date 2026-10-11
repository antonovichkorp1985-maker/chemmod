#!/usr/bin/env python3
"""Validate the immutable release contract before building/publishing anything."""
import argparse
from pathlib import Path
import re
import tomllib
import zipfile

# A full release is plain semver; a test candidate carries the explicit -test.N suffix.
# Both are immutable and both refuse to overwrite, but only the test form publishes as a
# GitHub prerelease.
FULL_RELEASE = re.compile(r'v[0-9]+\.[0-9]+\.[0-9]+')
TEST_CANDIDATE = re.compile(r'v[0-9]+\.[0-9]+\.[0-9]+-test\.[0-9]+')


def metadata(root: Path, tag: str | None = None) -> dict[str, str]:
    versions = re.findall(r'^\s*version\s*=\s*"([^"\n]+)"\s*$',
                          (root / 'build.gradle.kts').read_text(), re.MULTILINE)
    if len(versions) != 1:
        raise ValueError('Expected one explicit project version in build.gradle.kts')
    version = versions[0]
    expected = f'v{version}'
    if not TEST_CANDIDATE.fullmatch(expected) and not FULL_RELEASE.fullmatch(expected):
        raise ValueError('Only a full semver release or an explicitly numbered test '
                         'candidate may use this publisher')
    if tag is not None and tag != expected:
        raise ValueError(f'Requested tag {tag!r} differs from project version {expected!r}')
    notes = f'docs/releases/{expected}.md'
    checklist = f'docs/testing/{expected}.md'
    for document in (notes, checklist):
        if not (root / document).is_file():
            raise ValueError(f'Missing release document: {document}')
    return {'tag': expected, 'version': version,
            'prerelease': 'false' if FULL_RELEASE.fullmatch(expected) else 'true',
            'artifact': f'mod/build/libs/chemmod-{version}.jar',
            'notes': notes, 'checklist': checklist}


def verify_jar(root: Path, values: dict[str, str]) -> None:
    with zipfile.ZipFile(root / values['artifact']) as jar:
        descriptor = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
    mods = [mod for mod in descriptor.get('mods', []) if mod.get('modId') == 'chemmod']
    if len(mods) != 1 or mods[0].get('version') != values['version']:
        raise ValueError('JAR metadata does not match the ChemMod release version')


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
