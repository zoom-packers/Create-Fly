"""Self-test for tools/data_26_3.py (run with python -I; touches nothing outside a temp dir).

    python -I tools/test_data_26_3.py [OLD.jar NEW.jar]

Checks the asset text editors against a parse-and-rewrite reference on every model/atlas under
src/client/resources/assets, checks idempotency of the data converter on src/main/resources/data, and, when the two
vanilla jars are given, runs the vanilla round trip (--check).
"""
import copy
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('data_26_3', os.path.join(HERE, 'data_26_3.py'))
conv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(conv)


def expected_model(obj):
    """Reference implementation of fix_model_text on parsed JSON."""
    def walk(x, parent_key=None):
        if isinstance(x, dict):
            return {k: walk(v, k) for k, v in x.items()}
        if isinstance(x, list):
            out = []
            for v in x:
                if parent_key == 'elements' and isinstance(v, dict) and v.get('shade') is False:
                    v = {('shade_direction_override' if k == 'shade' else k): ('up' if k == 'shade' else val)
                         for k, val in v.items()}
                out.append(walk(v))
            return out
        return x
    return walk(obj)


def test_assets(root):
    bad = 0
    total = 0
    for dirpath, _, files in os.walk(root):
        for f in files:
            if not f.endswith('.json'):
                continue
            p = os.path.join(dirpath, f)
            parts = os.path.relpath(p, root).replace(os.sep, '/').split('/')
            if len(parts) < 3 or parts[1] not in ('models', 'atlases'):
                continue
            text = open(p, encoding='utf-8-sig').read()
            try:
                obj = json.loads(text)
            except ValueError:
                continue
            if parts[1] == 'models':
                new, count = conv.fix_model_text(text)
                want = expected_model(obj)
            else:
                new, count = conv.fix_atlas_text(text)
                want = copy.deepcopy(obj)
                for src in want.get('sources', []):
                    if conv.ns(src.get('type')) == 'minecraft:paletted_permutations':
                        src['palette_key'] = conv.palette_id(src['palette_key'])
                        src['permutations'] = {k: conv.palette_id(v) for k, v in src['permutations'].items()}
            total += count
            got = json.loads(new)
            if got != want:
                bad += 1
                print('asset mismatch: ' + p)
            again, count2 = (conv.fix_model_text if parts[1] == 'models' else conv.fix_atlas_text)(new)
            if count2 or again != new:
                bad += 1
                print('asset edit not idempotent: ' + p)
    print('assets: %d edits checked, %d problems' % (total, bad))
    return bad


def test_data(root):
    bad = 0
    n = 0
    for dirpath, _, files in os.walk(root):
        for f in files:
            if not f.endswith('.json'):
                continue
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, root).replace(os.sep, '/')
            typ = conv.data_type(rel)
            if rel.split('/')[0] in conv.SKIP_NAMESPACES or typ not in conv.CONVERTERS:
                continue
            obj = json.load(open(p, encoding='utf-8'))
            once = conv.convert(typ, obj, conv.Ctx(rel))
            twice = conv.convert(typ, once, conv.Ctx(rel))
            n += 1
            if once != twice:
                bad += 1
                print('data conversion not idempotent: ' + rel)
    print('data: %d files, %d not idempotent' % (n, bad))
    return bad


# constructs that vanilla 26.2 data does not use, so the --check round trip cannot cover them
CASES = [
    ('predicate', {'condition': 'minecraft:reference', 'name': 'create:foo'}, 'create:foo'),
    ('predicate', [{'condition': 'killed_by_player'}, {'condition': 'minecraft:random_chance', 'chance': 0.5}],
     {'type': 'minecraft:all_of', 'terms': [{'type': 'minecraft:killed_by_player'},
                                            {'type': 'minecraft:random_chance', 'chance': 0.5}]}),
    ('predicate', {'condition': 'minecraft:value_check', 'value': {'type': 'minecraft:constant', 'value': 3.0},
                   'range': {'min': 1.0, 'max': {'type': 'minecraft:uniform', 'min': 2.0, 'max': 4.0}}},
     {'type': 'minecraft:int_value_check', 'value': 3,
      'test': {'min': 1, 'max': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}}}),
    ('predicate', {'condition': 'minecraft:entity_scores', 'entity': 'this', 'scores': {'s': {'min': 1.0}}},
     {'type': 'minecraft:entity_scores', 'entity': 'this', 'scores': {'s': {'min': 1}}}),
    ('item_modifier', [{'function': 'minecraft:set_damage',
                        'damage': {'type': 'minecraft:binomial', 'n': 3.0, 'p': 0.5}}],
     {'type': 'minecraft:set_damage',
      'damage': {'type': 'minecraft:from_int', 'input': {'type': 'minecraft:binomial', 'n': 3, 'p': 0.5}}}),
    ('item_modifier', {'function': 'minecraft:set_count', 'count': {
        'type': 'minecraft:sum', 'summands': [1.0, {'type': 'minecraft:score', 'target': 'this', 'score': 'x',
                                                    'scale': 2.0}, {'min': 0, 'max': 2}]}},
     {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:add', 'inputs': [
         1, {'type': 'minecraft:from_float', 'input': {'type': 'minecraft:mul', 'inputs': [
             {'type': 'minecraft:from_int', 'input': {'type': 'minecraft:score', 'target': 'this', 'score': 'x'}},
             2.0]}},
         {'type': 'minecraft:uniform', 'min': 0, 'max': 2}]}}),
    ('item_modifier', {'function': 'minecraft:set_count', 'count': {
        'type': 'minecraft:enchantment_level', 'amount': 2}},
     {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:from_float', 'input': {
         'type': 'minecraft:enchantment_level', 'amount': 2}}}),
    ('loot_table', {'pools': [{'rolls': 1.0, 'entries': [{'type': 'minecraft:tag', 'name': 'c:ores', 'expand': True}],
                               'functions': [{'function': 'minecraft:furnace_smelt'}]}]},
     {'pools': [{'rolls': 1, 'modifier': {'type': 'minecraft:furnace_smelt'},
                 'entries': [{'type': 'minecraft:tag', 'items': '#c:ores', 'expand': True}]}]}),
    ('advancement', {'criteria': {'a': {'trigger': 'minecraft:player_killed_entity', 'conditions': {
        'player': {'type_specific': {'type': 'minecraft:player', 'level': {'min': 5}}},
        'entity': [{'condition': 'minecraft:entity_properties', 'entity': 'this',
                    'predicate': {'type': 'minecraft:zombie'}}]}}}},
     {'criteria': {'a': {'trigger': 'minecraft:player_killed_entity', 'conditions': {
         'player': {'type': 'minecraft:entity_properties', 'entity': 'this',
                    'predicate': {'type_specific': {'type': 'minecraft:player', 'level': {'min': 5}}}},
         'entity': {'type': 'minecraft:entity_properties', 'entity': 'this',
                    'predicate': {'type': 'minecraft:zombie'}}}}}}),
    ('recipe', {'type': 'minecraft:smoking', 'ingredient': 'a', 'result': {'id': 'b'}},
     {'type': 'minecraft:smoking', 'ingredient': 'a', 'result': {'id': 'b'}, 'cookingtime': 200}),
    ('recipe', {'type': 'minecraft:blasting', 'cookingtime': 100, 'ingredient': 'a', 'result': {'id': 'b'}},
     {'type': 'minecraft:blasting', 'cookingtime': 200, 'ingredient': 'a', 'result': {'id': 'b'}}),
    ('loot_modifiers', {'type': 'neoforge:add_table', 'conditions': [
        {'condition': 'neoforge:loot_table_id', 'loot_table_id': 'minecraft:chests/x'}], 'table': 'a:b'},
     {'type': 'neoforge:add_table', 'condition': {'type': 'neoforge:loot_table_id',
                                                  'loot_table_id': 'minecraft:chests/x'}, 'table': 'a:b'}),
]


def test_cases():
    bad = 0
    for typ, old, want in CASES:
        got = conv.convert(typ, old, conv.Ctx('case'))
        if conv.norm(got) != conv.norm(want):
            bad += 1
            print('case %s failed:\n  in:   %s\n  got:  %s\n  want: %s' % (typ, json.dumps(old), json.dumps(got),
                                                                        json.dumps(want)))
        if conv.convert(typ, got, conv.Ctx('case')) != got:
            bad += 1
            print('case %s not idempotent: %s' % (typ, json.dumps(old)))
    print('cases: %d checked, %d failed' % (len(CASES), bad))
    return bad


def main(argv):
    base = os.path.join(HERE, '..', 'src')
    bad = test_cases()
    bad += test_assets(os.path.join(base, 'client', 'resources', 'assets'))
    bad += test_data(os.path.join(base, 'main', 'resources', 'data'))
    if len(argv) == 2:
        conv.check(argv[0], argv[1], 3)
    print('FAILED' if bad else 'OK')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
