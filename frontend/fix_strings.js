const fs = require('fs');
const file = 'src/components/SqlEditor.tsx';
let content = fs.readFileSync(file, 'utf8');
const lines = content.split('\n');
let fixCount = 0;

for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  
  // Fix unterminated strings: pattern like 'mojibake_text? should be 'correct_text'
  // where ? replaced the closing '
  
  // Fix line with '无匹配结果'
  if (line.includes('searchText ?') && line.includes('emptyText') && line.includes('SQL')) {
    // Replace everything between searchText ? ' and ' : '暂无保存的SQL'
    const startIdx = line.indexOf("searchText ? '");
    const endIdx = line.indexOf(" : '暂无保存的SQL'");
    if (startIdx >= 0 && endIdx > startIdx) {
      const oldPart = line.substring(startIdx, endIdx);
      lines[i] = line.substring(0, startIdx) + "searchText ? '无匹配结果'" + line.substring(endIdx);
      console.log('Fixed L' + (i + 1) + ': 无匹配结果');
      fixCount++;
    }
  }
  
  // Fix line with '无匹配记录'
  if (line.includes('searchText ?') && line.includes('emptyText') && line.includes('执行')) {
    const startIdx = line.indexOf("searchText ? '");
    const endIdx = line.indexOf(" : '暂无执行记录'");
    if (startIdx >= 0 && endIdx > startIdx) {
      lines[i] = line.substring(0, startIdx) + "searchText ? '无匹配记录'" + line.substring(endIdx);
      console.log('Fixed L' + (i + 1) + ': 无匹配记录');
      fixCount++;
    }
  }
  
  // Fix other lines with remaining mojibake followed by ? (corrupted closing quote)
  // Pattern: 'mojibake_chars? where ? should be '
  // Look for sequences of chars in U+0080-U+00FF range followed by ? and then ) or ; or space
  const mojibakeEndPattern = /'([\u0080-\u00ff]+)\?([\s);,\/>])/g;
  let match;
  while ((match = mojibakeEndPattern.exec(lines[i])) !== null) {
    const mojibakeStr = match[1];
    // Try to decode mojibake bytes
    const bytes = [];
    for (let j = 0; j < mojibakeStr.length; j++) {
      bytes.push(mojibakeStr.charCodeAt(j));
    }
    const buf = Buffer.from(bytes);
    let decoded = '';
    let valid = true;
    let k = 0;
    while (k < buf.length) {
      const b = buf[k];
      if (b < 0x80) { decoded += String.fromCharCode(b); k++; }
      else if ((b & 0xE0) === 0xC0 && k + 1 < buf.length) {
        decoded += String.fromCodePoint(((b & 0x1F) << 6) | (buf[k+1] & 0x3F));
        k += 2;
      } else if ((b & 0xF0) === 0xE0 && k + 2 < buf.length) {
        decoded += String.fromCodePoint(((b & 0x0F) << 12) | ((buf[k+1] & 0x3F) << 6) | (buf[k+2] & 0x3F));
        k += 3;
      } else { valid = false; break; }
    }
    if (valid && decoded.length > 0) {
      const replacement = "'" + decoded + "'" + match[2];
      lines[i] = lines[i].substring(0, match.index) + replacement + lines[i].substring(match.index + match[0].length);
      console.log('Fixed L' + (i + 1) + ': ' + decoded);
      fixCount++;
      mojibakeEndPattern.lastIndex = 0; // Reset regex
    }
  }
}

fs.writeFileSync(file, lines.join('\n'), 'utf8');
console.log('Total fixes:', fixCount);
