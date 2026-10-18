"""Convert upstream's Fabric classtweaker (create.classtweaker) into NeoForge's accesstransformer.cfg and the
ModDevGradle interface injection file. Re-run after rebasing on upstream:

    python tools/classtweaker2at.py src/main/resources/create.classtweaker
"""
import json
import pathlib
import sys

src = pathlib.Path(sys.argv[1])
root = pathlib.Path(__file__).resolve().parent.parent
ALIASES = {"com/zurrtum/create/infrastructure/items/ContainerExtension": "com/zurrtum/create/neoforge/CreateContainerExtension"}
at_lines = ["# Generated from create.classtweaker by tools/classtweaker2at.py - do not edit by hand"]
interfaces = {}
# 26.3 moved some classes upstream's classtweaker names (see tools/remap_26_3_imports.py)
moves_file = root / "tools/class-moves-26.3.txt"
moves = dict(l.split() for l in moves_file.read_text(encoding="utf-8").splitlines()) if moves_file.exists() else {}


def remap(name):
    outer, _, inner = name.replace("/", ".").partition("$")
    outer = moves.get(outer, outer)
    return outer + ("$" + inner if inner else "")


for raw in src.read_text(encoding="utf-8").splitlines():
    line = raw.split("#", 1)[0].strip()
    if not line or line.startswith("classTweaker"):
        continue
    parts = line.split()
    kind, target = parts[0], parts[1]
    if kind.endswith("inject-interface"):
        # NeoForge's Container already implements a ContainerExtension; inject a differently named alias
        iface = ALIASES.get(parts[2], parts[2])
        interfaces.setdefault(parts[1], []).append(iface)
        continue
    modifier = {"accessible": "public", "extendable": "public-f", "mutable": "public-f"}[kind]
    owner = remap(parts[2])
    for old, new in moves.items():
        parts = [p.replace("L" + old.replace(".", "/") + ";", "L" + new.replace(".", "/") + ";") for p in parts]
    if target == "class":
        at_lines.append(f"{modifier} {owner}")
    elif target == "field":
        at_lines.append(f"{modifier} {owner} {parts[3]}")
    elif target == "method":
        at_lines.append(f"{modifier} {owner} {parts[3]}{parts[4]}")
    else:
        raise SystemExit(f"unknown classtweaker line: {raw}")
extra = root / "tools/accesstransformer-neoforge.cfg"
if extra.exists():
    at_lines += [l for l in extra.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
(root / "src/main/resources/META-INF").mkdir(parents=True, exist_ok=True)
(root / "src/main/resources/META-INF/accesstransformer.cfg").write_text("\n".join(at_lines) + "\n", encoding="utf-8")
(root / "interfaces.json").write_text(json.dumps(interfaces, indent=2) + "\n", encoding="utf-8")
print(f"{len(at_lines) - 1} AT entries, {sum(map(len, interfaces.values()))} injected interfaces")

# Fabric applies access changes in bytecode, where the JVM does not care that an override is less visible than the
# method it overrides. NeoForge recompiles Minecraft from source, so widening a method also needs every override
# widened. Pass the NeoForge-patched Minecraft jar to add those entries:
#     python tools/classtweaker2at.py src/main/resources/create.classtweaker build/moddev/artifacts/minecraft-patched-*-merged.jar
if len(sys.argv) > 2:
    import re
    import struct
    import zipfile

    def parse(data):
        """Return (name, super, interfaces, {(method, desc): access}) for a class file."""
        count = struct.unpack_from(">H", data, 8)[0]
        cp = [None] * count
        pos, i = 10, 1
        while i < count:
            tag = data[pos]
            if tag == 1:
                ln = struct.unpack_from(">H", data, pos + 1)[0]
                cp[i] = data[pos + 3:pos + 3 + ln].decode("utf-8", "replace")
                pos += 3 + ln
            elif tag in (3, 4):
                pos += 5
            elif tag in (5, 6):
                pos += 9
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = struct.unpack_from(">H", data, pos + 1)[0]
                pos += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                pos += 5
            elif tag == 15:
                pos += 4
            i += 1
        _, this, sup, nif = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        ifs = [cp[cp[struct.unpack_from(">H", data, pos + 2 * k)[0]]] for k in range(nif)]
        pos += 2 * nif
        methods = {}
        for member in range(2):
            n = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            for _ in range(n):
                acc, ni, di, na = struct.unpack_from(">HHHH", data, pos)
                pos += 8
                if member == 1:
                    methods[(cp[ni], cp[di])] = acc
                for _ in range(na):
                    pos += 6 + struct.unpack_from(">I", data, pos + 2)[0]
        return cp[cp[this]], cp[cp[sup]] if sup else None, ifs, methods

    classes = {}
    with zipfile.ZipFile(sys.argv[2]) as zf:
        for n in zf.namelist():
            if n.endswith(".class") and n.startswith(("net/minecraft/", "com/mojang/")):
                name, sup, ifs, methods = parse(zf.read(n))
                classes[name] = (sup, ifs, methods)

    def ancestors(name):
        seen, todo = set(), [name]
        while todo:
            c = todo.pop()
            if c in seen or c not in classes:
                continue
            seen.add(c)
            todo.append(classes[c][0])
            todo.extend(classes[c][1])
        return seen

    ACC_PUBLIC = 0x0001
    extra = []
    existing = set(at_lines)
    for line in list(at_lines[1:]):
        m = re.fullmatch(r"(\S+) (\S+) ([^(\s]+)(\(\S+)", line)
        if not m or m.group(3) == "<init>":
            continue
        owner, name, desc = m.group(2).replace(".", "/"), m.group(3), m.group(4)
        for cname, (_, _, methods) in classes.items():
            acc = methods.get((name, desc))
            if cname == owner or acc is None or acc & ACC_PUBLIC or acc & 0x0008:
                continue
            if owner not in ancestors(cname):
                continue
            descs = [desc]
            if acc & 0x1000:
                # Synthetic bridge of a generic override: widen the real method, which has a narrower descriptor
                descs = [d for (n, d), a in methods.items() if n == name and not a & 0x1000 and not a & ACC_PUBLIC]
            for d in descs:
                entry = f"public {cname.replace('/', '.')} {name}{d}"
                if entry not in existing:
                    existing.add(entry)
                    extra.append(entry)
    if extra:
        at_lines.append("# Overrides of widened methods (see tools/classtweaker2at.py)")
        at_lines.extend(sorted(extra))
        (root / "src/main/resources/META-INF/accesstransformer.cfg").write_text("\n".join(at_lines) + "\n", encoding="utf-8")
    print(f"{len(extra)} override entries added")
