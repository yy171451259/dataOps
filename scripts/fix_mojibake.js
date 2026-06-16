const fs = require('fs');
const path = require('path');

// Fix double-encoded UTF-8 (mojibake): each char's code point is actually a byte value
function fixMojibake(text) {
  const result = [];
  const chars = [...text];
  let i = 0;
  
  while (i < chars.length) {
    const code = chars[i].codePointAt(0);
    
    // Check if this looks like a Latin-1 byte that was part of UTF-8
    if (code >= 0x80 && code <= 0xFF) {
      // Try to collect consecutive Latin-1 bytes and decode as UTF-8
      const bytes = [];
      let j = i;
      while (j < chars.length) {
        const c = chars[j].codePointAt(0);
        if (c >= 0x80 && c <= 0xFF) {
          bytes.push(c);
          j++;
        } else if (c === 0x3F) { // '?' - might be a corrupted byte
          bytes.push(0x3F);
          j++;
        } else {
          break;
        }
      }
      
      // Try to decode these bytes as UTF-8
      const buf = Buffer.from(bytes);
      let decoded = '';
      let valid = true;
      let k = 0;
      while (k < buf.length) {
        const b = buf[k];
        if (b < 0x80) {
          decoded += String.fromCharCode(b);
          k++;
        } else if ((b & 0xE0) === 0xC0 && k + 1 < buf.length && (buf[k+1] & 0xC0) === 0x80) {
          const cp = ((b & 0x1F) << 6) | (buf[k+1] & 0x3F);
          decoded += String.fromCodePoint(cp);
          k += 2;
        } else if ((b & 0xF0) === 0xE0 && k + 2 < buf.length && (buf[k+1] & 0xC0) === 0x80 && (buf[k+2] & 0xC0) === 0x80) {
          const cp = ((b & 0x0F) << 12) | ((buf[k+1] & 0x3F) << 6) | (buf[k+2] & 0x3F);
          decoded += String.fromCodePoint(cp);
          k += 3;
        } else if (b === 0x3F) {
          // '?' - corrupted byte, skip or treat as literal
          decoded += '?';
          k++;
        } else {
          // Invalid UTF-8 sequence
          valid = false;
          decoded += String.fromCharCode(b);
          k++;
        }
      }
      
      if (valid && decoded !== chars.slice(i, j).join('')) {
        result.push(decoded);
      } else {
        result.push(chars.slice(i, j).join(''));
      }
      i = j;
    } else {
      result.push(chars[i]);
      i++;
    }
  }
  
  return result.join('');
}

// Also fix unterminated strings where ? appears instead of closing quote
function fixUnterminatedStrings(text) {
  // Pattern: 'text? : ' should be 'text' : '
  // And 'text?); should be 'text');
  // But be careful not to break intentional ? characters
  return text;
}

const file = path.join(__dirname, '..', 'frontend', 'src', 'components', 'SqlEditor.tsx');
let content = fs.readFileSync(file, 'utf8');

// Check if file has mojibake (chars in U+0080-U+00FF range that form UTF-8 byte sequences)
const mojibakePattern = /[\u0080-\u00ff]{3,}/g;
const matches = content.match(mojibakePattern);
console.log(`Found ${matches ? matches.length : 0} potential mojibake sequences`);

// Apply fix
const fixed = fixMojibake(content);

// Fix known unterminated strings
let finalContent = fixed;
// The ? at end of corrupted Chinese strings that should be closing quotes
// Pattern: Chinese_string? followed by space/semicolon/closing
// This needs careful handling

fs.writeFileSync(file + '.fixed', fixed, 'utf8');
console.log('Fixed file written to', file + '.fixed');

// Show first few differences
const origLines = content.split('\n');
const fixedLines = fixed.split('\n');
let diffs = 0;
for (let i = 0; i < Math.min(origLines.length, fixedLines.length); i++) {
  if (origLines[i] !== fixedLines[i]) {
    diffs++;
    if (diffs <= 10) {
      console.log(`\nLine ${i+1}:`);
      console.log('  OLD:', origLines[i].substring(0, 100));
      console.log('  NEW:', fixedLines[i].substring(0, 100));
    }
  }
}
console.log(`\nTotal lines changed: ${diffs}`);
