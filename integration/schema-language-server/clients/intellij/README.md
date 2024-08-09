# IntelliJ plugin for Schema Language Server
This plugin provides language support for the Vespa Schema language using LSP.

The plugin acts as a client for the Schema Language Server, and requires IntelliJ Ultimate edition.
It uses IntelliJ's built in LSP integration to interface the server, which means that the features of this plugin is 
limited to the currently supported set of LSP features in IntelliJ. As of version 2024.1.4, the supported features we use are:

- Error and warning highlighting (textDocument/publishDiagnostics)
- Code actions, including Quick fixes (textDocument/codeAction)
- Code completion (textDocument/completion)
- Go-to-definition (textDocument/definition)
- Hover (textDocument/hover)

In addition, we have implemented a basic client-side syntax highlighter. This highlighter is based only on the lexer, meaning that you don't get the same semantic highlighting as you get through LSP.

The following features will become available as soon as JetBrains implements it for IntelliJ:

- Semantic token highlighting (textDocument/semanticTokens/full
- Renaming/refactoring (textDocument/rename)
- Find references (textDocument/references)
- List of document symbols (textDocument/documentSymbol)
