

import * as path from 'path';
import * as vscode from 'vscode';
import fs from 'fs';

import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient;

const JAVA_HOME_SETTING = 'vespaSchemaLS.javaHome';
// update if something changes
const JAVA_DOWNLOAD_URL = 'https://www.oracle.com/java/technologies/downloads/#java17';

type maybeString = string|null|undefined;

function javaExecutableExists(javaHome: maybeString) {
    if (javaHome === null || javaHome === undefined) {
        return false;
    }
    return fs.existsSync(path.join(javaHome, 'bin', 'java'));
}

function findJavaPath(): maybeString {
	// Try workspace setting first
    const config = vscode.workspace.getConfiguration();
    const javaHome = config.get(JAVA_HOME_SETTING) as maybeString;
    if (javaExecutableExists(javaHome)) {
        return javaHome;
    }

    if (javaExecutableExists(process.env.JDK_HOME)) {
        return process.env.JDK_HOME;
    }

    if (javaExecutableExists(process.env.JAVA_HOME)) {
        return process.env.JAVA_HOME;
    }

}

export function activate(context: vscode.ExtensionContext) {
	console.log('Vespa Language Support is active.');

    const javaPath = findJavaPath();

    if (javaPath === null || javaPath === undefined) {
        const openSettingsButton = {
            title: "Settings"
        };
        const openOracleDownloadsButton = {
            title: "Go to Java download"
        };
        vscode.window.showWarningMessage(
            "Java Home could not be found on your system. " +
            "Vespa Language Support requires Java 17 or later to be installed. "+
            "Make sure Java is installed, and set the path to Java Home in settings.",
            openSettingsButton,
            openOracleDownloadsButton
        ).then(result => {
            if (result === openSettingsButton) {
                vscode.commands.executeCommand('workbench.action.openSettings', JAVA_HOME_SETTING);
            } else if (result === openOracleDownloadsButton) {
                vscode.env.openExternal(vscode.Uri.parse(JAVA_DOWNLOAD_URL));
            }
        });
        return;
    }

	console.log(`Using java from JAVA_HOME: ${javaPath}`);
	const excecutable = path.join(javaPath, 'bin', 'java');

    console.log(__dirname);
	const jarPath = path.join(__dirname, '..', 'server', 'schema-language-server-jar-with-dependencies.jar');

	const config = vscode.workspace.getConfiguration();
	const logFile = config.get('vespaSchemaLS.logFile');

	const logArgs = typeof(logFile) === "string" ? ['-t', logFile] : [];

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
				const r = await next(document, position, context, token);
				return r;
			},
			provideDocumentHighlights: async (document, position, token, next) => {
				const r = await next(document, position, token);
				return r;
			},
			provideDocumentSemanticTokens: async (document, token, next) => {
				const r = await next(document, token);
				return r;
			},
		},
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher("**/*{.sd,.profile}")
        }
	};

	client = new LanguageClient('vespaSchemaLS', 'Vespa Schema Language Server', serverOptions, clientOptions);
	client.start();

    const command = "vespaSchemaLS.restart";

    const commandHandler = () => {
        if (client.isRunning()) {
            client.restart();
        } else {
	        client.start();
        }
    };

    context.subscriptions.push(vscode.commands.registerCommand(command, commandHandler));
}


export function deactivate() { 
	console.log('Vespa Language Support is deactivated!');

	if (!client) {
		return undefined;
	}
	return client.stop();
}
