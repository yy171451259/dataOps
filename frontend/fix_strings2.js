const fs = require('fs');
const file = 'src/components/SqlEditor.tsx';
let content = fs.readFileSync(file, 'utf8');

// Find and replace the problematic strings by line
const lines = content.split('\n');
for (let i = 0; i < lines.length; i++) {
  if (lines[i].includes('searchText ?') && lines[i].includes('emptyText')) {
    // Replace the entire locale line
    if (lines[i].includes('SQL')) {
      lines[i] = `              locale={{ emptyText: <Empty description={searchText ? '无匹配结果' : '暂无保存的SQL'} /> }}`;
      console.log('Replaced L' + (i+1));
    } else if (lines[i].includes('执行')) {
      lines[i] = `              locale={{ emptyText: <Empty description={searchText ? '无匹配记录' : '暂无执行记录'} /> }}`;
      console.log('Replaced L' + (i+1));
    }
  }
}

// Also fix any remaining unterminated strings with ? pattern
// Look for pattern: Chinese_char? where ? should be closing quote
// This happens when mojibake decode produces incomplete UTF-8
for (let i = 0; i < lines.length; i++) {
  // Fix 'text?' patterns that should be 'text'
  // where the ? is U+003F (literal question mark) right after a CJK char and before );  or space
  const pattern = /([\u4e00-\u9fff])\?([\s);\/>,])/g;
  let match;
  let changed = false;
  while ((match = pattern.exec(lines[i])) !== null) {
    // Check context: is this inside a string literal?
    const before = lines[i].substring(Math.max(0, match.index - 30), match.index);
    if (before.includes("'") && !before.includes('"')) {
      // Likely inside a single-quoted string
      const replacement = match[1] + "'" + match[2];
      lines[i] = lines[i].substring(0, match.index) + replacement + lines[i].substring(match.index + match[0].length);
      console.log('Fixed unterminated L' + (i+1) + ': ' + lines[i].substring(match.index - 10, match.index + 20));
      changed = true;
      pattern.lastIndex = 0;
    }
  }
}

fs.writeFileSync(file, lines.join('\n'), 'utf8');
console.log('Done');
