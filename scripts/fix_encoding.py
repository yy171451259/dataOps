"""
Auto-fix corrupted UTF-8 Chinese characters in TSX files.
Pattern: 3-byte UTF-8 char's 3rd byte replaced by 0x3F, closing quote lost.
Fix: Try all possible 3rd bytes, pick the one that makes a valid Chinese char,
     then add back the closing quote.
"""
import os, sys, unicodedata

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'

# Build a lookup: (B0, B1) -> list of valid B2 values that form real Chinese chars
valid_chinese = {}
for b0 in range(0xe0, 0xf0):
    for b1 in range(0x80, 0xc0):
        valid_b2s = []
        for b2 in range(0x80, 0xc0):
            try:
                ch = bytes([b0, b1, b2]).decode('utf-8')
                # Check if it's a CJK character or common punctuation
                cp = ord(ch)
                is_cjk = (
                    (0x4e00 <= cp <= 0x9fff) or   # CJK Unified
                    (0x3400 <= cp <= 0x4dbf) or   # CJK Extension A
                    (0x3000 <= cp <= 0x303f) or   # CJK Symbols
                    (0xff00 <= cp <= 0xffef) or   # Fullwidth Forms
                    (0x2000 <= cp <= 0x206f) or   # General Punctuation
                    ch in '，。、；：？！""''（）【】《》—…·'
                )
                if is_cjk:
                    valid_b2s.append(b2)
            except:
                pass
        if valid_b2s:
            valid_chinese[(b0, b1)] = valid_b2s

def fix_file(fpath):
    with open(fpath, 'rb') as f:
        raw = bytearray(f.read())
    
    fixes = []
    i = 0
    while i < len(raw) - 2:
        b0, b1, b2 = raw[i], raw[i+1], raw[i+2]
        if 0xe0 <= b0 <= 0xef and 0x80 <= b1 <= 0xbf and b2 == 0x3f:
            # Corrupted sequence found
            key = (b0, b1)
            
            # Look at what comes before to determine quote type
            # Find the nearest opening quote before this position
            # Search backwards for ' or "
            quote_type = None
            j = i - 1
            while j >= 0 and j >= i - 100:
                if raw[j] == 0x27:  # '
                    # Check if this is an opening quote (not a closing one)
                    # Count quotes between j and i
                    quotes_between = sum(1 for k in range(j+1, i) if raw[k] == 0x27)
                    if quotes_between % 2 == 0:
                        quote_type = b"'"
                    break
                elif raw[j] == 0x22:  # "
                    quotes_between = sum(1 for k in range(j+1, i) if raw[k] == 0x22)
                    if quotes_between % 2 == 0:
                        quote_type = b'"'
                    break
                j -= 1
            
            # Look at what comes after to determine quote type
            after = raw[i+3:i+4] if i+3 < len(raw) else b''
            if after == b' ' or after == b'\n' or after == b'\r':
                # Space or newline after - need closing quote
                # Determine from context what the char and quote should be
                pass
            
            # Determine what byte follows the corrupted char
            next_byte = raw[i+3] if i+3 < len(raw) else 0
            
            if key in valid_chinese:
                candidates = valid_chinese[key]
                if len(candidates) == 1:
                    correct_b2 = candidates[0]
                else:
                    # Multiple candidates - use context to pick
                    # Look at the preceding character for context
                    correct_b2 = candidates[0]  # Default to first
                
                # Determine closing quote
                if next_byte == 0x27:  # Already has ' after
                    replacement = bytes([b0, b1, correct_b2])
                elif next_byte == 0x22:  # Already has " after  
                    replacement = bytes([b0, b1, correct_b2])
                elif next_byte == 0x20 or next_byte == 0x0a or next_byte == 0x0d:
                    # Space/newline - need to add closing quote
                    # Determine which quote type from the opening quote
                    if quote_type == b"'":
                        replacement = bytes([b0, b1, correct_b2, 0x27])
                    elif quote_type == b'"':
                        replacement = bytes([b0, b1, correct_b2, 0x22])
                    else:
                        # Default: check if inside JSX (") or JS string (')
                        # Look for patterns like = or : before
                        replacement = bytes([b0, b1, correct_b2, 0x27])
                elif next_byte == 0x7d or next_byte == 0x5d:  # } or ]
                    replacement = bytes([b0, b1, correct_b2, 0x27])
                elif next_byte == 0x29:  # )
                    replacement = bytes([b0, b1, correct_b2, 0x27])
                elif next_byte == 0x2c:  # ,
                    replacement = bytes([b0, b1, correct_b2, 0x27])
                elif next_byte == 0x2f:  # /  (like />)
                    replacement = bytes([b0, b1, correct_b2, 0x22])
                else:
                    replacement = bytes([b0, b1, correct_b2])
                
                fixes.append((i, 3, replacement))
                # Show what we're fixing
                original_char = bytes([b0, b1, correct_b2]).decode('utf-8', errors='replace')
                ctx_before = raw[max(0,i-15):i].decode('utf-8', errors='replace')
                ctx_after = raw[i+3:i+18].decode('utf-8', errors='replace')
                sys.stdout.write(f"  Fix: ...{ctx_before}[->{original_char}]{ctx_after}...\n")
            else:
                sys.stdout.write(f"  WARN: No valid char for {b0:02x} {b1:02x} 3f at pos {i}\n")
            
            i += 3
        else:
            i += 1
    
    # Apply fixes in reverse order to maintain positions
    for pos, length, replacement in reversed(fixes):
        raw[pos:pos+length] = replacement
    
    if fixes:
        with open(fpath, 'wb') as f:
            f.write(bytes(raw))
        sys.stdout.write(f"  Applied {len(fixes)} fixes\n")
    
    return len(fixes)

# Process all broken files
files = [
    'AuditList.tsx', 'DashboardPage.tsx', 'DataImportPage.tsx',
    'DataMaskingPage.tsx', 'DataQualityPage.tsx', 'DatabaseList.tsx',
    'DatabaseMonitorPage.tsx', 'LoginPage.tsx', 'MetadataPage.tsx',
    'NotificationSettingsPage.tsx', 'PipelinePage.tsx', 'SchemaDesignerPage.tsx',
    'SystemSettingsPage.tsx', 'UserManagementPage.tsx',
]

total_fixes = 0
for fname in files:
    fpath = os.path.join(pages_dir, fname)
    sys.stdout.write(f"\n=== {fname} ===\n")
    n = fix_file(fpath)
    total_fixes += n

sys.stdout.write(f"\nTotal fixes applied: {total_fixes}\n")
sys.stdout.flush()
