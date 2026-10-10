"""Bundle Unicode 17.0.0 Korean readings, preserving alternative readings."""
import io, pathlib, urllib.request, zipfile
out = pathlib.Path('app/src/main/assets')
out.mkdir(parents=True, exist_ok=True)
with urllib.request.urlopen('https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip', timeout=60) as response:
    archive = zipfile.ZipFile(io.BytesIO(response.read()))
rows = {}
for line in archive.read('Unihan_Readings.txt').decode('utf-8').splitlines():
    if line.startswith('#') or not line.strip(): continue
    code, key, value = line.split('\t', 2)
    if key == 'kHangul':
        parts = sorted(value.split(), key=lambda part: ('E' not in part.split(':')[1], '0' not in part.split(':')[1]))
        readings = list(dict.fromkeys(part.split(':')[0] for part in parts))
        rows[int(code[2:], 16)] = readings
assert len(rows) > 8000 and '한' in rows[0x6F22]
(out / 'hanja-readings.tsv').write_text(''.join(f'{code:X}\t{" ".join(readings)}\n' for code, readings in sorted(rows.items())), encoding='utf-8')
with urllib.request.urlopen('https://www.unicode.org/license.txt', timeout=60) as response:
    (out / 'Unicode-LICENSE.txt').write_bytes(response.read())
print(f'Bundled {len(rows)} Korean Hanja readings')
