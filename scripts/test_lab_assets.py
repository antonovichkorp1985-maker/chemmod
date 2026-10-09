"""Static resource contracts, not a substitute for client rendering acceptance."""
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1] / 'mod/src/main/resources'
ASSETS = ROOT / 'assets/chemmod'

class LaboratoryAssetsTest(unittest.TestCase):
    def test_local_model_texture_references_exist(self):
        names = ['block/test_tube_rack', 'block/laboratory_tray', 'block/chemical_reactor',
                 'block/chemical_reactor_active', 'item/substance_vial',
                 'item/substance_vial_pure', 'item/substance_vial_mixture']
        for name in names:
            model = json.loads((ASSETS / f'models/{name}.json').read_text())
            self.assertTrue(model['elements'])
            for texture in model['textures'].values():
                self.assertTrue(texture.startswith('chemmod:'))
                path = ASSETS / ('textures/' + texture.split(':')[1] + '.png')
                data = path.read_bytes()
                self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
                self.assertEqual(struct.unpack('!II', data[16:24]), (32, 32))
            for element in model['elements']:
                for low, high in zip(element['from'], element['to']):
                    self.assertLess(low, high)
                    self.assertGreaterEqual(low, 0)
                    self.assertLessEqual(high, 16)
                for face in element['faces'].values():
                    self.assertIn(face['texture'][1:], model['textures'])

    def test_independent_holder_models_and_no_duplicate_loot(self):
        state = json.loads((ASSETS / 'blockstates/laboratory_holder.json').read_text())
        self.assertEqual([part['when'] for part in state['multipart']], [{'rack':'true'}, {'tray':'true'}])
        loot = json.loads((ROOT / 'data/chemmod/loot_table/blocks/laboratory_holder.json').read_text())
        self.assertEqual(loot['pools'], [])  # server holder drain owns all drops

    def test_languages_and_full_catalyst_name(self):
        ru = json.loads((ASSETS / 'lang/ru_ru.json').read_text())
        en = json.loads((ASSETS / 'lang/en_us.json').read_text())
        self.assertEqual(ru.keys(), en.keys())
        self.assertEqual(ru['gui.chemmod.reactor.catalyst'], 'Катализатор')
        self.assertEqual(en['gui.chemmod.reactor.catalyst'], 'Catalyst')
        for key in ('item.chemmod.test_tube_rack', 'item.chemmod.laboratory_tray', 'tooltip.chemmod.holder.use'):
            self.assertTrue(ru[key] and en[key])

    def test_content_type_labels_are_distinct(self):
        labels = [(ASSETS / f'textures/block/lab/{name}_label.png').read_bytes() for name in ('empty','pure','mixture')]
        self.assertEqual(len(set(labels)), 3)
        model = json.loads((ASSETS / 'models/item/substance_vial.json').read_text())
        self.assertEqual([entry['predicate']['chemmod:contents'] for entry in model['overrides']], [1, 2])

if __name__ == '__main__':
    unittest.main()
