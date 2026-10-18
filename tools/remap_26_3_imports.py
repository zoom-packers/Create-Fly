"""Rewrite references to classes that 26.3 moved to another package (com.mojang.blaze3d -> com.mojang.renderpearl
and a few net.minecraft.client moves). Builds the map from the 26.2 and 26.3 jars: a class missing from 26.3 whose
simple name exists exactly once in 26.3 is assumed to have moved there.

    python tools/remap_26_3_imports.py <26.2 merged jar> <26.3 patched merged jar> <source dir>...
"""
import collections
import pathlib
import re
import sys
import zipfile


def classes(jar):
    return {n[:-6] for n in zipfile.ZipFile(jar).namelist() if n.endswith('.class') and '$' not in n}


old, new = classes(sys.argv[1]), classes(sys.argv[2])
by_simple = collections.defaultdict(list)
for c in new:
    by_simple[c.rsplit('/', 1)[-1]].append(c)
mapping = {}
for c in old - new:
    if not c.startswith(('com/mojang/blaze3d/', 'net/minecraft/client/')) or c.endswith('package-info'):
        continue
    cands = by_simple.get(c.rsplit('/', 1)[-1], [])
    if len(cands) == 1:
        mapping[c.replace('/', '.')] = cands[0].replace('/', '.')
# Saved for tools/classtweaker2at.py, which applies it to upstream's access widener
moves = pathlib.Path(__file__).resolve().parent / 'class-moves-26.3.txt'
moves.write_text(''.join(f'{k} {v}\n' for k, v in sorted(mapping.items())), encoding='utf-8')
# Longest first, so a.b.C is not rewritten by a shorter a.b match
pattern = re.compile(r'\b(' + '|'.join(re.escape(k) for k in sorted(mapping, key=len, reverse=True)) + r')\b(?![\w])')
# Wildcard imports of a package whose classes all moved to one package
packages = {}
for k, v in mapping.items():
    packages.setdefault(k.rsplit('.', 1)[0], set()).add(v.rsplit('.', 1)[0])
slash_pattern = re.compile(r'L(' + '|'.join(re.escape(k.replace('.', '/')) for k in sorted(mapping, key=len, reverse=True)) + r')([;$])')
changed = 0
for root in sys.argv[3:]:
    for f in pathlib.Path(root).rglob('*.java'):
        raw = f.read_bytes().decode('utf-8')
        out = pattern.sub(lambda m: mapping[m.group(1)], raw)
        # Descriptors in mixin targets use the slash form
        out = slash_pattern.sub(lambda m: 'L' + mapping[m.group(1).replace('/', '.')].replace('.', '/') + m.group(2), out)
        for pkg, targets in packages.items():
            # Only if nothing is left in the old package; otherwise keep both wildcard imports
            if any(c.rsplit('/', 1)[0].replace('/', '.') == pkg for c in new):
                for target in targets:
                    if f'import {pkg}.*;' in out and f'import {target}.*;' not in out:
                        out = out.replace(f'import {pkg}.*;', f'import {pkg}.*;\nimport {target}.*;')
            elif len(targets) == 1:
                out = out.replace(f'import {pkg}.*;', f'import {next(iter(targets))}.*;')
        if out != raw:
            f.write_bytes(out.encode('utf-8'))
            changed += 1
print(f'{len(mapping)} moved classes, {changed} files rewritten')
