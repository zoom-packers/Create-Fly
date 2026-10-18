"""Convert upstream's 26.2 configured_feature JSON to 26.3's worldgen/feature format.

26.3 merged ConfiguredFeature into Feature: the feature's settings sit next to "type" instead of under "config",
the files live in worldgen/feature, and block states are written as {"id", "properties"} or a bare block id instead
of {"Name", "Properties"}. Re-run after rebasing on upstream if it changed these files:

    python tools/worldgen_26_3.py
"""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/data/create/worldgen'


def convert_state(node):
    if isinstance(node, dict):
        if 'Name' in node and set(node) <= {'Name', 'Properties'}:
            if node.get('Properties'):
                return {'id': node['Name'], 'properties': node['Properties']}
            return node['Name']
        return {k: convert_state(v) for k, v in node.items()}
    if isinstance(node, list):
        return [convert_state(v) for v in node]
    return node


src = root / 'configured_feature'
dst = root / 'feature'
dst.mkdir(exist_ok=True)
for f in sorted(src.glob('*.json')):
    data = json.loads(f.read_text(encoding='utf-8'))
    out = {'type': data['type'], **convert_state(data.get('config', {}))}
    (dst / f.name).write_text(json.dumps(out, indent=2) + '\n', encoding='utf-8')
    f.unlink()
    print(f.name)
src.rmdir()
