"""Write an empty structure template (gzipped NBT) for game tests that build their own setup.

    python -I tools/make_empty_structure.py <out.nbt> <x> <y> <z> [data_version]
"""
import gzip
import struct
import sys


def tag_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def named(tag_type, name, payload):
    return bytes([tag_type]) + tag_string(name) + payload


def int_list(values):
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def empty_list():
    return bytes([0]) + struct.pack('>i', 0)


out, x, y, z = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
data_version = int(sys.argv[5]) if len(sys.argv) > 5 else 5023  # 26.3
air = named(8, 'Name', tag_string('minecraft:air')) + b'\x00'
root = (named(9, 'size', int_list([x, y, z]))
        + named(9, 'entities', empty_list())
        + named(9, 'blocks', empty_list())
        + named(9, 'palette', bytes([10]) + struct.pack('>i', 1) + air)
        + named(3, 'DataVersion', struct.pack('>i', data_version))
        + b'\x00')
with gzip.open(out, 'wb') as f:
    f.write(named(10, '', root))
print('wrote', out)
