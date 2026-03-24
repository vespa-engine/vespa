# tmgrammar

TextMate grammar for the [Vespa](https://vespa.ai) schema language (`.sd` files), plus tooling to generate, test, and compare it against the Java LSP's semantic tokens.

## Usage

The grammar file is [`grammar/vespa-schema.tmLanguage.json`](grammar/vespa-schema.tmLanguage.json). You can use it directly in any tool that supports TextMate grammars:

### Shiki (docs, blogs, static sites)

```js
import { createHighlighter } from 'shiki';

const vespaGrammar = JSON.parse(fs.readFileSync('vespa-schema.tmLanguage.json', 'utf-8'));

const highlighter = await createHighlighter({
  langs: [vespaGrammar],
  themes: ['github-dark'],
});

const html = highlighter.codeToHtml(sdCode, { lang: 'vespa-schema', theme: 'github-dark' });
```

Works with [Shiki](https://shiki.style/guide/load-custom-langs)-powered frameworks: VitePress, Astro, Nuxt Content, etc.

### VS Code

Ships with the [Vespa extension](https://marketplace.visualstudio.com/items?itemName=vespa-engine.vespa) for static highlighting when the Java LSP is not running.

### GitHub

Native `.sd` highlighting in code blocks, diffs, and file views. Requires acceptance into [github-linguist](https://github.com/github-linguist/linguist/blob/main/CONTRIBUTING.md#adding-a-language) (contribution in progress).

### Other editors

Sublime Text, BBEdit, and any editor that consumes `.tmLanguage.json` grammars.

## How it works

The grammar is auto-generated from the same source-of-truth as the Java LSP (CongoCC grammars + `SchemaSemanticTokenConfig.java` + `BuiltInFunctions.java`), ensuring scope assignments match the LSP's semantic token colors in VS Code's default Dark+ theme.

The generator (`tools/generate_tmgrammar.py`) reads three CongoCC grammar files and two Java config files directly from the vespa repo, then:

1. **Extracts** all literal keyword tokens from the `.ccc` grammars (schema, indexing, ranking)
2. **Classifies** each token using `SchemaSemanticTokenConfig.java` -- which tokens are keywords, types, operators, functions, etc.
3. **Maps** LSP SemanticTokenType to TextMate scope (e.g., `Keyword` -> `keyword.control.vespa`)
4. **Adds** built-in rank features from `BuiltInFunctions.java` (bm25, fieldMatch, onnx, etc.)
5. **Generates** context-aware begin/end patterns for indexing blocks, ranking expressions, feature lists, match blocks, summary blocks, rank-properties blocks, container types, and tensor types
6. **Handles** hyphenated keywords correctly (e.g., `rank-profile` vs `rank`)

Source files are read directly from the vespa repo tree:
- `integration/schema-language-server/language-server/src/main/ccc/` -- CongoCC grammars
- `integration/schema-language-server/language-server/src/main/java/ai/vespa/schemals/` -- Java config files
- `integration/schema-language-server/language-server/src/test/sdfiles/` -- test `.sd` files

## Development

All commands below assume you are in the `integration/tmgrammar/` directory.

### Prerequisites

- Python 3.10+ with [uv](https://docs.astral.sh/uv/)
- Node.js 18+

### Regenerate the grammar

```bash
uv run tools/generate_tmgrammar.py
```

### Run grammar tests

```bash
uv run tools/test_tmgrammar.py
```

### Compare with Java LSP tokens

The comparison needs `tools/java_tokens.json` produced by the Java LSP's `SemanticTokenDumper`. To generate it, from the **vespa repo root**:

```bash
cd integration/schema-language-server/language-server
mvn test -Dtest=SemanticTokenDumper -pl .
```

This writes `java_tokens.json` directly to `integration/tmgrammar/tools/`.

Then, back in this directory:

```bash
uv run tools/compare_tokens.py                    # summary
uv run tools/compare_tokens.py --fixable-only     # only actionable items
uv run tools/compare_tokens.py --file embed.sd    # per-file detail
```

Or use the convenience wrapper (tokenizes .sd files with vscode-textmate, then compares):

```bash
./tools/run_comparison.sh                          # tokenize + compare
./tools/run_comparison.sh --regenerate             # regenerate grammar first
```

### Playground

A browser-based viewer for inspecting `.sd` file colorization across VS Code themes (Shiki v3 + Vite):

```bash
cd playground && npm install && npm run dev
# Open http://localhost:5173
```

Audit uncolored tokens (finds tokens that fall through to the theme's default foreground):

```bash
cd playground && node audit-colors.mjs                        # all files, github-dark
node audit-colors.mjs --theme github-light                    # specific theme
node audit-colors.mjs --file spotcheck.sd                     # specific file
```

## Directory structure

```
integration/tmgrammar/
  grammar/
    vespa-schema.tmLanguage.json   # THE OUTPUT -- generated TextMate grammar
  tools/                           # Build and validation pipeline
    generate_tmgrammar.py          #   Generator: reads .ccc grammars + Java config, writes grammar/
    test_tmgrammar.py              #   Validates keyword completeness, structure, scopes, .sd parsing
    compare_tokens.py              #   Structural diff: Java LSP token types vs TM scope assignments
    tm_tokenize.mjs                #   Tokenizes .sd files with the real vscode-textmate engine
    run_comparison.sh              #   Convenience wrapper: tokenize + compare in one step
    package.json                   #   Node.js deps (vscode-textmate, vscode-oniguruma)
  playground/                      # Visual inspection during development
    main.js + index.html + css     #   Shiki-powered browser viewer, 10 VS Code themes
    vite.config.js                 #   Dev server serving grammar + .sd files
    audit-colors.mjs               #   CLI: find tokens that are visually uncolored in a given theme
    package.json                   #   Node.js deps (shiki, vite)

integration/schema-language-server/language-server/src/test/java/ai/vespa/schemals/
  SemanticTokenDumper.java         # Java test class: dumps LSP semantic tokens to JSON
```

**tools/** is the build pipeline -- generate, validate, and structurally compare the grammar against the Java LSP. **playground/** is for visual inspection -- see how `.sd` files actually render across themes and find tokens that aren't getting colored.

## Contributing

To update the grammar after Vespa parser changes:

1. `uv run tools/generate_tmgrammar.py` to regenerate
2. `uv run tools/test_tmgrammar.py` to validate
3. Review changes in the playground

## License

Apache 2.0 -- see the top-level [LICENSE](../../LICENSE) file.
