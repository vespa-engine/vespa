# VSCode extension for Schema Language Server
Language support for the Vespa Schema language using LSP.

The extension acts as a client for the Schema Language Server, providing tools for developing Vespa Schema files.

Features:
- Error and warning highlighting 
- Code actions, including Quick fixes 
- Code completion 
- Go-to-definition
- Find references
- Documentation on hover
- Semantic token highlighting
- Renaming/refactoring
- List document symbols

YQL Features:
- Error highlighting
- Semantic token highlighting
- Running queries directly from `.yql` files

## Requirements
The extension requires Java 17 or greater. Upon activation, the extension will look in the following locations in this order for a Java executable:

- PATH environment variable
- Workspace setting: vespaSchemaLS.javaHome
- User setting: vespaSchemaLS.javaHome
- JDK_HOME environment variable
- JAVA_HOME environment variable

The extension also requires [Vespa CLI](https://docs.vespa.ai/en/vespa-cli.html) to run Vespa Queries from `.yql` files.

## XML support
This extension bundles with an extension to the [LemMinX XML Language server](https://github.com/eclipse/lemminx).
This is to provide additional support when editing the services.xml file in Vespa applications. 
For the best possible experience, install the [VSCode XML extension](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-xml) as well.
