import tempfile
from pathlib import Path
import unittest
import zipfile

from release_metadata import metadata, verify_jar


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'build.gradle.kts').write_text('allprojects {\n    version = "0.9.0-test.5"\n}\n')
        for kind in ('releases', 'testing'):
            path = self.root / f'docs/{kind}/v0.9.0-test.5.md'
            path.parent.mkdir(parents=True)
            path.write_text('test document\n')

    def test_exact_tag_and_derived_tag_agree(self):
        expected = metadata(self.root, 'v0.9.0-test.5')
        self.assertEqual(metadata(self.root), expected)
        self.assertEqual(expected['artifact'], 'mod/build/libs/chemmod-0.9.0-test.5.jar')

    def test_mismatch_or_untrusted_tag_rejected(self):
        for tag in ('v0.9.0-test.4', '../../wrong', '$(false)', 'v0.9.0-test.5\nother=value'):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                metadata(self.root, tag)

    def test_missing_notes_rejected(self):
        (self.root / 'docs/releases/v0.9.0-test.5.md').unlink()
        with self.assertRaises(ValueError):
            metadata(self.root)

    def test_missing_checklist_rejected(self):
        (self.root / 'docs/testing/v0.9.0-test.5.md').unlink()
        with self.assertRaises(ValueError):
            metadata(self.root)

    def test_ambiguous_version_rejected(self):
        (self.root / 'build.gradle.kts').write_text('version = "0.9.0-test.5"\nversion = "0.9.0-test.6"\n')
        with self.assertRaises(ValueError):
            metadata(self.root)

    def test_non_prerelease_version_rejected(self):
        (self.root / 'build.gradle.kts').write_text('version = "0.9.0"\n')
        with self.assertRaises(ValueError):
            metadata(self.root)

    def write_jar(self, version, mod_id='chemmod'):
        values = metadata(self.root)
        jar = self.root / values['artifact']
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, 'w') as output:
            output.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
        return values

    def test_matching_internal_jar_version(self):
        verify_jar(self.root, self.write_jar('0.9.0-test.5'))

    def test_wrong_internal_version_or_mod_rejected(self):
        for version, mod_id in [('0.9.0-test.4', 'chemmod'), ('0.9.0-test.5', 'other')]:
            with self.subTest(version=version, mod_id=mod_id), self.assertRaises(ValueError):
                verify_jar(self.root, self.write_jar(version, mod_id))


if __name__ == '__main__':
    unittest.main()
