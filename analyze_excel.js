#!/usr/bin/env node
// analyze_excel.js — Node.js script to analyze datamigration.xlsx
// Usage: node analyze_excel.js (requires xlsx module installed)
const path = require('path');
const fs = require('fs');

let XLSX;
try {
  XLSX = require('./node_modules/xlsx');
} catch (e) {
  try {
    XLSX = require('xlsx');
  } catch (e2) {
    console.error('xlsx module not found. Run: npm install xlsx');
    process.exit(1);
  }
}

const FILE = path.join(__dirname, 'datamigration.xlsx');
const wb = XLSX.readFile(FILE, { cellFormula: false, sheetRows: 8 });

console.log(`=== TOTAL SHEETS: ${wb.SheetNames.length} ===\n`);
wb.SheetNames.forEach((name, i) => {
  console.log(`${String(i+1).padStart(2)}. [${name}]`);
});

console.log('\n' + '='.repeat(80));

wb.SheetNames.forEach(name => {
  const ws = wb.Sheets[name];
  const range = XLSX.utils.decode_range(ws['!ref'] || 'A1:A1');
  const data = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '', blankrows: false });
  
  console.log(`\n${'='.repeat(80)}`);
  console.log(`SHEET: [${name}]   rows≈${range.e.r+1}  cols≈${range.e.c+1}`);
  console.log('='.repeat(80));
  
  const MAX_ROWS = 6;
  let count = 0;
  for (const row of data) {
    if (count >= MAX_ROWS) break;
    const trimmed = row.slice(0, 25).map(v => String(v).slice(0, 25));
    if (trimmed.every(v => v.trim() === '')) continue;
    // trim trailing empty
    let last = trimmed.length - 1;
    while (last >= 0 && trimmed[last].trim() === '') last--;
    console.log(trimmed.slice(0, last+1).map(v => v.padEnd(25)).join(' | '));
    count++;
  }
});

console.log('\n=== DONE ===');
