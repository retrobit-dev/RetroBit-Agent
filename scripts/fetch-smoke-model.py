#!/usr/bin/env python3
"""Fetch an actual small Instruct model for CI, never include weights in Git/APK.

Resolve official publisher metadata to an immutable revision; verify advertised
LFS SHA-256 and length, then use the production Android SAF/import/JNI UI path.
"""
import hashlib
import json
import pathlib
import re
import urllib.parse
import urllib.request

REPO = 'Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF'
LIMIT = 512 * 1024 * 1024
root = pathlib.Path('artifacts-local')
root.mkdir(exist_ok=True)
request = urllib.request.Request('https://huggingface.co/api/models/' + REPO + '?blobs=true', headers={'User-Agent': 'RetroBit-CI'})
with urllib.request.urlopen(request, timeout=60) as response:
    metadata = json.load(response)
revision = metadata['sha']
assert re.fullmatch('[a-f0-9]{40}', revision)
candidates = [f for f in metadata['siblings'] if f['rfilename'].lower().endswith('-q4_k_m.gguf')]
assert len(candidates) == 1, 'Expected a single-file Q4_K_M model'
entry = candidates[0]
expected = entry['lfs']['sha256']
size = entry['lfs']['size']
assert re.fullmatch('[a-f0-9]{64}', expected)
assert 0 < size <= LIMIT
url = 'https://huggingface.co/' + REPO + '/resolve/' + revision + '/' + urllib.parse.quote(entry['rfilename'])
partial = root / 'ci-model.partial'
final = root / 'ci-model.gguf'
try:
    digest = hashlib.sha256()
    copied = 0
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'RetroBit-CI'}), timeout=120) as source, partial.open('wb') as out:
        assert source.geturl().startswith('https://'), 'HTTPS required after redirect'
        while chunk := source.read(1024 * 1024):
            copied += len(chunk)
            assert copied <= min(size, LIMIT)
            digest.update(chunk)
            out.write(chunk)
    assert copied == size and digest.hexdigest() == expected, 'Publisher checksum/length mismatch'
    with partial.open('rb') as header:
        assert header.read(8) == b'GGUF\x03\x00\x00\x00'
    partial.replace(final)
    receipt = {'repository': REPO, 'revision': revision, 'filename': entry['rfilename'], 'sha256': expected, 'bytes': copied,
               'scope': 'Real CI model; not bundled, not GT30Pro benchmark; recommended user model remains 1.5B.'}
    (root / 'ci-model.json').write_text(json.dumps(receipt, indent=2))
    print(json.dumps(receipt))
finally:
    partial.unlink(missing_ok=True)
