// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import vsctmModule from 'vscode-textmate';
const vsctm = vsctmModule;
import onigurumaModule from 'vscode-oniguruma';
const oniguruma = onigurumaModule;

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const wasmBin = fs.readFileSync(path.join(__dirname, 'node_modules/vscode-oniguruma/release/onig.wasm')).buffer;

const tmgrammarRoot = path.resolve(__dirname, '..');
const vespaRoot = path.resolve(tmgrammarRoot, '..', '..');
const grammarPath = path.join(tmgrammarRoot, 'grammar/vespa-schema.tmLanguage.json');
const sdFilesDir = path.join(vespaRoot, 'integration/schema-language-server/language-server/src/test/sdfiles');

async function main() {
    await oniguruma.loadWASM(wasmBin);

    const registry = new vsctm.Registry({
        onigLib: Promise.resolve({
            createOnigScanner: (patterns) => new oniguruma.OnigScanner(patterns),
            createOnigString: (s) => new oniguruma.OnigString(s),
        }),
        loadGrammar: async (scopeName) => {
            if (scopeName === 'source.vespaSchema') {
                const grammarContent = fs.readFileSync(grammarPath, 'utf-8');
                return vsctm.parseRawGrammar(grammarContent, grammarPath);
            }
            return null;
        },
    });

    const grammar = await registry.loadGrammar('source.vespaSchema');

    // Find all .sd files recursively
    function walkDir(dir) {
        let results = [];
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const fullPath = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                results = results.concat(walkDir(fullPath));
            } else if (entry.name.endsWith('.sd')) {
                results.push(fullPath);
            }
        }
        return results;
    }

    const sdFiles = walkDir(sdFilesDir).sort();
    const files = {};

    for (const filePath of sdFiles) {
        const content = fs.readFileSync(filePath, 'utf-8');
        const lines = content.split('\n');
        const tokens = [];
        let ruleStack = vsctm.INITIAL;

        for (let lineNum = 0; lineNum < lines.length; lineNum++) {
            const line = lines[lineNum];
            const lineResult = grammar.tokenizeLine(line, ruleStack);

            for (const token of lineResult.tokens) {
                const col = token.startIndex;
                const len = token.endIndex - token.startIndex;
                const text = line.substring(token.startIndex, token.endIndex);

                // Get scopes excluding root; most specific is last
                const filteredScopes = token.scopes.filter(s => s !== 'source.vespaSchema');
                const scope = filteredScopes.length > 0 ? filteredScopes[filteredScopes.length - 1] : null;

                tokens.push({ line: lineNum, col, len, text, scope, scopes: filteredScopes });
            }

            ruleStack = lineResult.ruleStack;
        }

        const relPath = path.relative(sdFilesDir, filePath);
        files[relPath] = { tokens };
    }

    const output = { files };
    const outputPath = path.join(__dirname, 'tm_tokens.json');
    fs.writeFileSync(outputPath, JSON.stringify(output, null, 2) + '\n');
    console.log(`Wrote ${Object.keys(files).length} files to ${outputPath}`);
}

main().catch(err => { console.error(err); process.exit(1); });
