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

## Requirements
The extension requires Java 17 or greater. Upon activation, the extension will look in the following locations in this order for a Java executable:

- Workspace setting: vespaSchemaLS.javaHome
- User setting: vespaSchemaLS.javaHome
- JDK_HOME environment variable
- JAVA_HOME environment variable
