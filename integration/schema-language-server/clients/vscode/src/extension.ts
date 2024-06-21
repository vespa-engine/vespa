

import * as path from 'path';
import * as vscode from 'vscode';

import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient

export function activate(context: vscode.ExtensionContext) {
	console.log('Vespa Language Support is not active.')

	const { JAVA_HOME } = process.env
	if (!JAVA_HOME) {
		throw new Error("JAVA_HOME env is not set!")
	}

	console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`)
	const excecutable = path.join(JAVA_HOME, 'bin', 'java')

	const classPath = path.join(__dirname, '..', '..', '..', 'launcher', 'target', 'laucnehr-jar-with-dependencies')
	console.log(classPath)

	const serverOptions: ServerOptions = {
		command: excecutable,
		args: ['-cp', 'classPath'],
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
				console.log(token)
				const r = await next(document, token)
				return r
			}
		}
	};

	client = new LanguageClient('helloLS', 'HelloFalks Language Server', serverOptions, clientOptions)
	console.log(client)
	client.start();

}


export function deactivate() { 
	console.log('Vespa Language Support is now deactivated!');

	if (!client) {
		return undefined
	}
	return client.stop();
}
