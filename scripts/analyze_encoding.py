import os, sys

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'

# For each file, collect all corrupted byte sequences and their context
files_to_check = [
    'LoginPage.tsx', 'DashboardPage.tsx', 'DataImportPage.tsx',
    'DataMaskingPage.tsx', 'DataQualityPage.tsx', 'DatabaseList.tsx',
    'DatabaseMonitorPage.tsx', 'MetadataPage.tsx', 'NotificationSettingsPage.tsx',
    'PipelinePage.tsx', 'SchemaDesignerPage.tsx', 'SystemSettingsPage.tsx',
    'UserManagementPage.tsx', 'AuditList.tsx',
]

# Pattern: 3-byte UTF-8 start (e0-ef), valid continuation (80-bf), then 3f (corrupted)
# The 3f replaced the original 3rd byte, and the closing quote was lost

all_corruptions = {}  # file -> list of (corrupted_bytes, context_before, context_after)

for fname in files_to_check:
    fpath = os.path.join(pages_dir, fname)
    with open(fpath, 'rb') as f:
        raw = f.read()
    
    corruptions = []
    i = 0
    while i < len(raw) - 2:
        b0, b1, b2 = raw[i], raw[i+1], raw[i+2]
        if 0xe0 <= b0 <= 0xef and 0x80 <= b1 <= 0xbf and b2 == 0x3f:
            # Corrupted 3-byte sequence
            # Get context before (up to 30 bytes)
            ctx_before = raw[max(0, i-30):i]
            # Get context after (up to 10 bytes)
            ctx_after = raw[i+3:i+13]
            corruptions.append({
                'pos': i,
                'bytes': bytes([b0, b1, b2]),
                'ctx_before': ctx_before,
                'ctx_after': ctx_after,
            })
            i += 3
        else:
            i += 1
    
    if corruptions:
        all_corruptions[fname] = corruptions
        sys.stdout.write(f"\n{fname}: {len(corruptions)} corruptions\n")
        for c in corruptions[:5]:
            before_text = c['ctx_before'].decode('utf-8', errors='replace')[-15:]
            after_text = c['ctx_after'].decode('utf-8', errors='replace')[:15:]
            sys.stdout.write(f"  pos={c['pos']} bytes={c['bytes'].hex(' ')}: ...{before_text}[CORRUPT]{after_text}...\n")
        if len(corruptions) > 5:
            sys.stdout.write(f"  ... and {len(corruptions)-5} more\n")

sys.stdout.write(f"\nTotal files with corruptions: {len(all_corruptions)}\n")
sys.stdout.flush()
