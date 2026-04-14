// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Audit script: find tokens that get the default (uncolored) foreground.
 * Prints lines where tokens fall back to the theme's default fg color,
 * meaning the grammar didn't classify them (or their scope maps to default).
 *
 * Usage:
 *   cd playground && npm install && node audit-colors.mjs
 *   node audit-colors.mjs                          # all files, github-dark
 *   node audit-colors.mjs --theme github-light     # specific theme
 *   node audit-colors.mjs --file spotcheck.sd      # specific file
 */
import { createHighlighter } from 'shiki';
import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';

const TMGRAMMAR = join(import.meta.dirname, '..');
const VESPA_ROOT = join(TMGRAMMAR, '..', '..');
const GRAMMAR_PATH = join(TMGRAMMAR, 'grammar/vespa-schema.tmLanguage.json');
const TEST_DIR = join(VESPA_ROOT, 'integration/schema-language-server/language-server/src/test/sdfiles');

// Parse CLI args
const args = process.argv.slice(2);
let theme = 'github-dark';
let fileFilter = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--theme' && args[i + 1]) theme = args[++i];
  if (args[i] === '--file' && args[i + 1]) fileFilter = args[++i];
}

function collectSdFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      files.push(...collectSdFiles(full));
    } else if (entry.endsWith('.sd')) {
      files.push(full);
    }
  }
  return files;
}

async function main() {
  const grammar = JSON.parse(readFileSync(GRAMMAR_PATH, 'utf-8'));

  const highlighter = await createHighlighter({
    themes: [theme],
    langs: [{ ...grammar, name: 'vespa-schema' }],
  });

  const themeObj = highlighter.getTheme(theme);
  const defaultFg = (themeObj.fg || '').toLowerCase();

  let sdFiles = collectSdFiles(TEST_DIR);
  if (fileFilter) {
    sdFiles = sdFiles.filter(f => f.includes(fileFilter));
  }

  let totalUncolored = 0;

  for (const filePath of sdFiles) {
    const code = readFileSync(filePath, 'utf-8');
    const result = highlighter.codeToTokens(code, {
      lang: 'vespa-schema',
      theme,
    });

    const rel = relative(TEST_DIR, filePath);
    const fileUncolored = [];

    for (let i = 0; i < result.tokens.length; i++) {
      const line = result.tokens[i];
      const lineNum = i + 1;
      const uncolored = [];
      for (const token of line) {
        const tokenColor = (token.color || '').toLowerCase();
        const text = token.content.trim();
        if (!text || /^\s*$/.test(text)) continue;
        // Skip punctuation-only tokens
        if (/^[{}()\[\]:;,.<>=+\-*\/|&!@#$%^~?"'\s]+$/.test(text)) continue;
        // Skip tokens that are just punctuation mixed with whitespace
        if (/^[>{}\[\](),\s]+$/.test(text)) continue;
        if (tokenColor === defaultFg) {
          uncolored.push(text);
        }
      }
      if (uncolored.length > 0) {
        const srcLine = code.split('\n')[i] || '';
        fileUncolored.push({ lineNum, srcLine: srcLine.trimEnd(), uncolored });
      }
    }

    if (fileUncolored.length > 0) {
      console.log(`\n=== ${rel} ===`);
      console.log(`Theme: ${theme}, default fg: ${defaultFg}\n`);
      for (const { lineNum, srcLine, uncolored } of fileUncolored) {
        console.log(`L${lineNum}: ${srcLine}`);
        console.log(`  uncolored: ${uncolored.join(', ')}`);
      }
      totalUncolored += fileUncolored.length;
    }
  }

  console.log(`\n--- Summary: ${totalUncolored} lines with uncolored tokens across ${sdFiles.length} files ---`);
}

main().catch(console.error);
