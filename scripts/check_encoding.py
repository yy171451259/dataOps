import os, sys

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'
broken = []

for fname in sorted(os.listdir(pages_dir)):
    if not fname.endswith('.tsx') or fname.endswith('.bak'):
        continue
    fpath = os.path.join(pages_dir, fname)
    with open(fpath, 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()
    
    # Look for replacement character U+FFFD
    lines_with_fffd = []
    for lineno, line in enumerate(content.split('\n'), 1):
        if '\ufffd' in line:
            lines_with_fffd.append(lineno)
    
    if lines_with_fffd:
        broken.append((fname, lines_with_fffd))
        sys.stdout.write(f"BROKEN: {fname} - lines: {lines_with_fffd}\n")
        sys.stdout.flush()

sys.stdout.write(f"\nTotal: {len(broken)} broken files\n")
sys.stdout.flush()
