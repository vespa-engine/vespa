
/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as path from 'path';
import * as vscode from 'vscode';

// Import the language client, language client options and server options from VSCode language client.
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

// Name of the launcher class which contains the main.
const main: string = 'StdioLauncher';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
	console.log('Congratulations, your extension "helloFalks" is now active!');

	// Get the java home from the process environment.
	//const { JAVA_HOME } = process.env;
	const JAVA_HOME = '/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home'

	console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`);
	// If java home is available continue.
	if (JAVA_HOME) {
		// Java execution path.
		let excecutable: string = path.join(JAVA_HOME, 'bin', 'java');

		// path to the launcher.jar
		let classPath = path.join(__dirname, '..', '..', 'launcher', 'target', 'launcher.jar');
		console.log(classPath)

		const args: string[] = ['-cp', classPath];

		// Set the server options 
		// -- java execution path
		// -- argument to be pass when executing the java command
		let serverOptions: ServerOptions = {
			command: excecutable,
			args: [...args, main],
			options: {}
		};

		// Options to control the language client
		let clientOptions: LanguageClientOptions = {
			// Register the server for plain text documents
			documentSelector: [{ scheme: 'file', language: 'hello' }],
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

		// Create the language client and start the client.
		client = new LanguageClient('helloLS', 'HelloFalks Language Server', serverOptions, clientOptions)
		console.log(client)
		client.start();
	}
}

// this method is called when your extension is deactivated
export function deactivate() { 
	console.log('Your extension "helloFalks" is now deactivated!');

	if (!client) {
		return undefined
	}
	return client.stop();
}
