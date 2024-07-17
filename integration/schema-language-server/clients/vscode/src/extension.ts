

import * as path from 'path';
import * as vscode from 'vscode';

import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient

export function activate(context: vscode.ExtensionContext) {
	console.log('Vespa Language Support is active.')

	const JAVA_HOME = process.env.JAVA_HOME ?? "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
	if (!JAVA_HOME) {
		console.error("JAVA_HOME is not set!")
		throw new Error("JAVA_HOME env is not set!")
	}

	console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`)
	const excecutable = path.join(JAVA_HOME, 'bin', 'java')

	const jarPath = path.join(__dirname, '..', '..', '..', 'language-server', 'target', 'schema-language-server-jar-with-dependencies.jar')

	const config = vscode.workspace.getConfiguration();
	const logFile = config.get('vespaSchemaLS.logFile');

	const logArgs = typeof(logFile) === "string" ? ['-t', logFile] : []

	const serverOptions: ServerOptions = {
		command: excecutable,
		args: ['-jar', jarPath, ...logArgs],
		options: {}
	};

	let clientOptions: LanguageClientOptions = {
		// Register the server for plain text documents
		documentSelector: [{
			scheme: 'file',
			language: 'vespaSchema',
		}],
		middleware: {
			provideCompletionItem: async (document, position, context, token, next) => {
				// console.log(document)
				// console.log(position)
				// console.log(context)
				// console.log(token)
				const r = await next(document, position, context, token)

				return r
			},
			provideDocumentHighlights: async (document, position, token, next) => {
				const r = await next(document, position, token)
				return r;
			},
			provideDocumentSemanticTokens: async (document, token, next) => {
				const r = await next(document, token)
				console.log(r?.data)
				return r
			}
		},
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher("**/*{.sd,.profile}")
        }
	};

	client = new LanguageClient('vespaSchemaLS', 'Vespa Schema Language Server', serverOptions, clientOptions)
	client.start();

}


export function deactivate() { 
	console.log('Vespa Language Support is deactivated!');

	if (!client) {
		return undefined
	}
	return client.stop();
}
