# Schema Language Server
This directory contains both a backend and some frontends for providing language support when writing schema files.

It uses the LSP protocol for communicating with different clients. LSP is a way to standardize the way language support is provided by defining a set of messages and types that the server and client will use to communicate over JSON RPC.

https://microsoft.github.io/language-server-protocol/

This means that the bulk of the functionality lies inside /language-server. The clients are merely small bootstrapping wrappers for creating an extension/plugin and launching the language-server.

## Release

To release the language server, start the github action [Vespa Schema LSP - Deploy extension](https://github.com/vespa-engine/vespa/actions/workflows/lspDeploy.yml). Note that the action must be started manually. The action will publish the extension to all the supported marketplaces, including a github release. In addition the action will bump the version and create a PR with the updated version.

To publish a new release from a branch other than master, use the following command:
`gh workflow run "Vespa Schema LSP - Deploy extension" --ref <branch> -F version=<major | minor | patch>`

## File Structure

### ./clients
Holds our client implementations. Currently IntelliJ and VSCode. They contain the code for building, running and packaging the plugins.

### ./language-server
Maven Project containing the language server implementation. 

- [./ccc](./language-server/src/main/ccc/): CongoCC parsers. These include the Schema, Indexing language and Ranking expressions parsers, ported from JavaCC to CongoCC.
The CongoCC Maven plugin generates Java classes from the parsers and places them in [./target/generated-sources/ccc](./language-server/target/generated-sources/ccc/).

- [./python](./language-server/src/main/python/): Python code from fetching documentation from the Vespa documentation GitHub repo and placing Markdown files in
[./target/generated-resources](./language-server/target/generated-resources/)

- [./java/ai/vespa/schemals](./language-server/src/main/java/ai/vespa/schemals/): The actual language server logic.
    Files in the root of this directory contains the code for setting up the language server. It is launched by SchemaLSLauncher, 
    while SchemaLanguageServer handles initialize and setting up capabilities. The other files are wrappers for handling incoming and
    outgoing requests by implementing some interfaces from LSP.

    - [/common](./language-server/src/main/java/ai/vespa/schemals/common/): Utilities for working with files, Strings, building
    diagnostics, and creating workspace edits.
    - [/context](./language-server/src/main/java/ai/vespa/schemals/context/): Context classes for different LSP events. 
    When an LSP request is handled, an object inheriting EventContext is created. The object contains references to the global 
    data structures providing the state of the application. The context object may also contain information from the LSP request parameters.
    - [/index](./language-server/src/main/java/ai/vespa/schemals/index/): Code related to storing and looking up symbols. 
    The SchemaIndex is mainly responsible for this, but outsources some implementations to FieldIndex (for field symbols only) and InheritanceGraph. 
    The SchemaIndex is a workspace-wide object, meaning that it stores symbols across different files. This directory also contains the Symbol class, 
    a model of a user defined symbol.
    - [/lsp](./language-server/src/main/java/ai/vespa/schemals/lsp/): Code providing LSP-specific functionality to be handled after a document is parsed. 
    That is, virtually all requests except textDocument/didChange and textDocument/didOpen.
    The subdirectories gets their names from the LSP specification. The class inside with the same name, but with a "Schema" prefix provides the entry
    point for handling each request in the form of a static method receiving a context object and returning a result.
    - [/parser](./language-server/src/main/java/ai/vespa/schemals/parser/): Package name matching the package of the CongoCC generated parser. 
    Currently only holds a record that is used by the Schema Parser to capture the Indexing and Ranking expression sublanguages.
    - [/schemadocument](./language-server/src/main/java/ai/vespa/schemals/schemadocument/): Code doing the heavy lifting related to parsing, identification of and resolving symbols.
    The entry points for these are in SchemaDocument (for .sd-files) and RankProfileDocument (for .profile-files). 
    When a text document is changes, these classes will create parser objects from the CongoCC parsers, parse the content, 
    and traverse the resulting CST to identify, register and resolve symbols and other situations. 
    Before each parsing, they will call SchemaIndex to clear symbols belonging to the relevant document, and during the traversal process they will re-register symbols.
    - [/tree](./language-server/src/main/java/ai/vespa/schemals/tree/): Code containing utils for working with the CST, such as finding nodes at a 
    given position and checking different properties. Also contains the SchemaNode class, which represents a node in the CST.
## Development & Testing

The language server needs a client to start it. Therefore running and testing the language server happens through an editor with an extension or a plugin. The language server is primary developed for VSCode, but it can run on other editors as well. This guide is for running the extension in a development environment. In the `clients` folder are the different extensions and plugins for the supported editors.

### Build the langauge server
- Use `mvn install -pl :schema-language-server -Pschema-language-server -amd` in the project root to build the language server

### Visual Studio Code (VSCode)
- Open the folder `./clients/vscode` in a new VSCode window.
- Make sure `npm` is downloaded and run `npm install` to install the necessary dependencies.
- Go to `Run and Debug` tab on the left of the window.
- Click on `Run Extension`, alternatively hit `F5` to run and test the extension.
- A new window will appear with the extension running.
- View logging output in "OUTPUT"-panel, select "Vespa Schema Language Server" as the source.

### IntelliJ
- Open the folder `./clients/intellij` in an IntelliJ window.
- Wait for the build to finish, then run the gradle task `Tasks/intellij platform/runIde`.
    - The Gradle tasks can be found by clicking on the Gradle icon on the right hand side of the editor.
- A new window will appear with the plugin running.

## Basic Principles
The server is launched as an executable by the client that wants to use it. It will then run as a separate process, and they will communicate using standard input/output.
Upon initialization, the server and client will exchange 'capabilities'. The server defines the subset of the LSP specification it supports, and the client does the same.

The main bookkeeping work typically happens through textDocument/didOpen and textDocument/didChange requests. 
The client gives the server the entire contents of the current text document. The server then does the following:

- Parse the entire file using a parser generated with CongoCC parser generator. The parsing step constructs a syntax tree, CST, of the entire schema file.
- Traverse the CST to identify syntax errors, semantic errors, symbol definitions, symbol references and some type information.
- Resolve stuff like inheritance, type information, indexing language and ranking expressions
- Try to resolve all symbol references using the current set of known definitions. 
- The above steps may generate a bunch of warnings or errors, known as 'diagnostics'. In the end the server publishes the diagnostics and they are shown in the UI of the client.

During the above process, symbols and their relationships are registered in a global index. A symbol is anything with a user-defined identifier, for instance a field, a document, a struct etc.
The remaining LSP requests simply use the index and CST generated in the parsing step.

## Parser
We needed a fault tolerant parser for parsing the schema language. A fault tolerant parser can continue parsing even if there exists some syntax errors 
in the document - this is crucial for providing good language support, as most of the time a document is not finished.

The original Schema parser, written in JavaCC, is not fault tolerant. To avoid duplicating the entire language definition we ideally wanted a fault tolerant version
of the exact same parser that is used for deploying Vespa applications. That is of course impossible, 
however CongoCC is the continuation of a project called "JavaCC 21", which in turn is a continuation of JavaCC. CongoCC supports fault tolerant parsing and
generates an AST/CST out of the box.

The syntax in CongoCC is very similar to JavaCC, the most notable differences being SCAN instead of LOOKAHEAD, and slightly different syntax for defining a rule.

We therefore ported the JavaCC parsers for the schema language, indexing language and ranking expressions to CongoCC and added some small modifications.
The modifications we made are mainly to catch some exceptions early and add some bookkeeping information to some nodes in the AST.

## Model
Many errors that can occur when creating a Vespa application are not caught in the parsing phase but much later. In order to make the language server as
simple, fault tolerant and flexible as possible, we sacrificed some correctness. This means that we don't go through all the steps you usually do when deploying
an applications, but try to catch as much as possible by inspecting the CST. 

This means that the language server will not catch all possible mistakes you can do when writing a schema, but a correct schema should never show any errors.

We have split the "parsing" process into three steps, "parse", "identify" and "resolve". This involves a few traversals of the CST (maybe more than strictly necessary).
The errors generated by "identify" and "resolve" involves some very specific inspections of the syntax tree. 

This means that we don't model the 'Vespa application', but rather assign symbol types to different nodes in the syntax tree and use information about where they are 
to determine semantic correctness of the schema. It allows all constructs to be traced to their exact location in a text document, and symbols can reference other symbols across files.

## LSP
A brief description of the types of requests we support:
- initialize: Capabilities are exchanged. If we want to support a new type of LSP request we must register it as a capability in this phase.
- textDocument/didOpen: First time the client opens a specific document (or reopens after a textDocument/didClose). If it is the first document the server has seen during
the execution, the server will also look for a /schemas directory among the files ancestors. If found, the server scans the contents of /schemas recursively and processes all .sd- and .profile-files it can find.
- textDocument/didChange: Reparses the content of a file. Also traverses document inheritance and document reference graphs to reparse descendants of the current document.
- textDocument/completion: Uses the most recently updated file content and supplied cursor position to generate a relevant completion list.
- textDocument/codeAction: Using the supplied position and known diagnostics, get some actions the user can select from. This is mostly used for supplying "Quick fix" for certain error messages.
- textDocument/references: Find a list of symbols referencing the symbol at the current position. As references are already resolved during the parsing traversal, this is quite simple.
- textDocument/documentSymbol: Gives a hierarchical list of symbols in the current document. Since symbols are registered during parsing traversal, this is also quite simple.
- textDocument/rename: If the cursor is at a symbol, rename it to a new identifier. It will find all references to the symbol and create TextEdit objects describing how to change them. May involve renaming symbols across files and renaming files.
- textDocument/semanticTokens/full: Gives a list of all tokens in the current document, translated to some standard types. This enables syntax highlighting. As constructs in the schema language
are not common in other languages (for instance "rank-profile"), we have made a somewhat arbitrary conversion to the standard types that makes the colors look good.
- textDocument/hover: If the cursor is at a symbol, give information about type and doc comments. If at a keyword, show a snippet from the Vespa schema documentation.
- textDocument/definition: If the cursor is a symbol, give the location where it was defined. For instance a field reference in a fieldset will refer to the original place where field was defined.
- workspace/didDeleteFiles: Unregister files and symbols.
- workspace/didRenameFiles: Effectively delete -> add.

## Testing
We scraped /config-model for *.sd files to use for testing. The testing mainly tests that a file or directory parses and generates the appropriate number of errors.

The appropriate number of errors is defined as 0 if the schema in question is supposed to be "deployable" as-is. Otherwise it is defined as the number of errors we expect given our current implementation of the language server.

# Future work
There are some things we wanted to implement but we didn't have the time for:

- Support for services.xml file. This could involve automatically adding documents to the application, giving warnings and errors if some configuration is obviously wrong etc.
- Support for generating a schema from a json file or similar. This doesn't necessarily involve the language server, but it could be implemented as a custom command.
- Better analysis of indexing language and ranking expressions. The current implementation is very simple and does not verify that indexing expressions make sense. 
- More refactoring options other than just renaming. 
- Support for managing multiple workspaces at once. We currently only support one workspace (schemas directory).
- Catch more errors. Several settings are incompatible with each other, and should show as diagnostics. For instance, stemming settings for fields in a fieldset.
- Support for document formatting requests.
