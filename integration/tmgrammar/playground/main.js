// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import { createHighlighter } from 'shiki';

const THEMES = [
  { id: 'github-dark', label: 'GitHub Dark' },
  { id: 'one-dark-pro', label: 'One Dark Pro' },
  { id: 'dracula', label: 'Dracula' },
  { id: 'tokyo-night', label: 'Tokyo Night' },
  { id: 'catppuccin-mocha', label: 'Catppuccin Mocha' },
  { id: 'github-light', label: 'GitHub Light' },
  { id: 'one-light', label: 'One Light' },
  { id: 'catppuccin-latte', label: 'Catppuccin Latte' },
  { id: 'min-dark', label: 'Min Dark' },
  { id: 'min-light', label: 'Min Light' },
];

const SD_FILES = [
  'single/spotcheck.sd',
  'single/attributeposition.sd',
  'single/defaultdefault.sd',
  'single/definition.sd',
  'single/elementwise.sd',
  'single/embed.sd',
  'single/featuresinheritance.sd',
  'single/foreach.sd',
  'single/foreachbad.sd',
  'single/hover.sd',
  'single/onnxmodel.sd',
  'single/onnxmodelinput.sd',
  'single/rankprofilebuiltin.sd',
  'single/rankprofilefuncs.sd',
  'single/rankproperties.sd',
  'single/slack_message.sd',
  'single/structinfieldset.sd',
  'single/subqueries.sd',
  'single/tensorGenerate.sd',
  'multi/bookandmusic/book.sd',
  'multi/bookandmusic/music.sd',
  'multi/types/other_doc.sd',
  'multi/types/type_with_doc_field.sd',
  'multi/types/types.sd',
];

const DEFAULT_THEME = 'github-dark';
const DEFAULT_FILE = 'single/spotcheck.sd';

const themeSelect = document.getElementById('theme-select');
const fileSelect = document.getElementById('file-select');
const output = document.getElementById('code-output');

let highlighter;
const fileCache = new Map();

function populateSelect(select, items, valueKey, labelKey) {
  for (const item of items) {
    const opt = document.createElement('option');
    opt.value = item[valueKey];
    opt.textContent = item[labelKey];
    select.appendChild(opt);
  }
}

function populateFileSelect() {
  for (const path of SD_FILES) {
    const opt = document.createElement('option');
    opt.value = path;
    opt.textContent = path;
    fileSelect.appendChild(opt);
  }
}

function loadPrefs() {
  return {
    theme: localStorage.getItem('vespa-pg-theme') || DEFAULT_THEME,
    file: localStorage.getItem('vespa-pg-file') || DEFAULT_FILE,
  };
}

function savePrefs(theme, file) {
  localStorage.setItem('vespa-pg-theme', theme);
  localStorage.setItem('vespa-pg-file', file);
}

function applyThemeColors(themeId) {
  const theme = highlighter.getTheme(themeId);
  const bg = theme.bg || '#1e1e2e';
  const fg = theme.fg || '#cdd6f4';
  document.documentElement.style.setProperty('--bg', bg);
  document.documentElement.style.setProperty('--fg', fg);
}

async function fetchFile(path) {
  if (fileCache.has(path)) return fileCache.get(path);
  const res = await fetch(`/sdfiles/${path}`);
  const text = await res.text();
  fileCache.set(path, text);
  return text;
}

async function render() {
  const themeId = themeSelect.value;
  const filePath = fileSelect.value;
  savePrefs(themeId, filePath);

  const code = await fetchFile(filePath);
  const html = highlighter.codeToHtml(code, {
    lang: 'vespa-schema',
    theme: themeId,
  });

  output.innerHTML = html;
  applyThemeColors(themeId);
}

async function init() {
  output.textContent = 'Loading…';

  const grammar = await fetch('/repo/grammar/vespa-schema.tmLanguage.json').then(r => r.json());

  highlighter = await createHighlighter({
    themes: THEMES.map(t => t.id),
    langs: [
      {
        ...grammar,
        name: 'vespa-schema',
      },
    ],
  });

  populateSelect(themeSelect, THEMES, 'id', 'label');
  populateFileSelect();

  const prefs = loadPrefs();
  themeSelect.value = prefs.theme;
  fileSelect.value = prefs.file;

  themeSelect.addEventListener('change', render);
  fileSelect.addEventListener('change', render);

  await render();
}

init().catch(err => {
  output.textContent = `Error: ${err.message}\n\n${err.stack}`;
  output.style.cssText = 'padding:2rem;white-space:pre-wrap;color:red;font-family:monospace';
});
