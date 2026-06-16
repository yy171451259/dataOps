"""Fix merged-line issues caused by wrong quote insertion after comments."""
import os, sys, re

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'

files = [
    'AuditList.tsx', 'DashboardPage.tsx', 'DataImportPage.tsx',
    'DataMaskingPage.tsx', 'DataQualityPage.tsx', 'DatabaseList.tsx',
    'DatabaseMonitorPage.tsx', 'MetadataPage.tsx',
    'NotificationSettingsPage.tsx', 'PipelinePage.tsx', 'SchemaDesignerPage.tsx',
    'SystemSettingsPage.tsx', 'UserManagementPage.tsx',
]

total = 0
for fname in files:
    fpath = os.path.join(pages_dir, fname)
    with open(fpath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    new_lines = []
    fixes = 0
    for line in lines:
        # Pattern: // Chinese comment' followed by code on same line
        # The ' was wrongly inserted by the fix script
        match = re.match(r"^(.*//\s*[\u4e00-\u9fff].*?)'(\s*(?:const|useEffect|useCallback|if|else|return|let|var|for|while|switch|try|catch|function|await|import|export|class|interface|type)\b.*)$", line.rstrip('\n'))
        if match:
            comment_part = match.group(1)
            code_part = match.group(2)
            indent = len(code_part) - len(code_part.lstrip())
            new_lines.append(comment_part + '\n')
            new_lines.append(code_part + '\n')
            fixes += 1
            sys.stdout.write(f"  {fname}: split merged line: {comment_part.strip()[:40]}... | {code_part.strip()[:40]}...\n")
        else:
            new_lines.append(line)
    
    if fixes > 0:
        with open(fpath, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        total += fixes

sys.stdout.write(f"\nTotal merged-line fixes: {total}\n")
sys.stdout.flush()
