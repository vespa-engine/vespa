import * as path from 'path';
import * as vscode from 'vscode';
import fs from 'fs';
import hasbin from 'hasbin';

import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

let client: LanguageClient | null = null;

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

function findJavaHomeExecutable(): maybeString {
	// Try workspace setting first
    const config = vscode.workspace.getConfiguration();
    const javaHome = config.get(JAVA_HOME_SETTING) as maybeString;
    if (javaExecutableExists(javaHome)) {
        return path.join(javaHome as string, 'bin', 'java');
    }

    if (javaExecutableExists(process.env.JDK_HOME)) {
        return path.join(process.env.JDK_HOME as string, 'bin', 'java');
    }

    if (javaExecutableExists(process.env.JAVA_HOME)) {
        return path.join(process.env.JAVA_HOME as string, 'bin', 'java');
    }

    return null;
}

function findJavaExecutable(): string|null {
    const javaPath = findJavaHomeExecutable();
    if (javaPath !== null && javaPath !== undefined) {
        return javaPath as string;
    }

    if (hasbin.sync('java')) {
        return 'java';
    }

    return null;
}

function createAndStartClient(serverPath: string): LanguageClient | null {

    const javaExecutable = findJavaExecutable();

    if (javaExecutable === null) {
        showJavaErrorMessage();
        return null;
    }

    console.log("Using java executable: " + javaExecutable);

	const config = vscode.workspace.getConfiguration();
	const logFile = config.get('vespaSchemaLS.logFile');

	const logArgs = typeof(logFile) === "string" ? ['-t', logFile] : [];

	const serverOptions: ServerOptions = {
		command: javaExecutable,
		args: ['-jar', serverPath, ...logArgs],
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
    const client = new LanguageClient('vespaSchemaLS', 'Vespa Schema Language Server', serverOptions, clientOptions);
    client.start();
    return client;
}

function showJavaErrorMessage() {
    const openSettingsButton = {
        title: "Settings"
    };
    const openOracleDownloadsButton = {
        title: "Go to Java download"
    };
    vscode.window.showErrorMessage(
        "A Java executable could not be found on the system. Java is required to run the Schema Language Server."
        + " If you have Java installed, you can specify the path to Java Home in settings, or set the environment variable JAVA_HOME.",
        openSettingsButton,
        openOracleDownloadsButton
    ).then(result => {
        if (result === openSettingsButton) {
            vscode.commands.executeCommand('workbench.action.openSettings', JAVA_HOME_SETTING);
        } else if (result === openOracleDownloadsButton) {
            vscode.env.openExternal(vscode.Uri.parse(JAVA_DOWNLOAD_URL));
        } 
    });
}

export function activate(context: vscode.ExtensionContext) {
	console.log('Vespa Language Support is active.');

	const jarPath = path.join(__dirname, '..', 'server', 'schema-language-server-jar-with-dependencies.jar');

    client = createAndStartClient(jarPath);

    context.subscriptions.push(vscode.commands.registerCommand("vespaSchemaLS.restart", (() => {
        if (client === null) {
            client = createAndStartClient(jarPath);
        } else if (client.isRunning()) {
            client.restart();
        } else {
            client.start();
        }
    })));
}


export function deactivate() { 
	console.log('Vespa Language Support is deactivated!');

	if (!client) {
		return undefined;
	}
	return client.stop();
}
