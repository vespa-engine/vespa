import * as path from 'path';
import * as vscode from 'vscode';
import fs from 'fs';
import hasbin from 'hasbin';

import { ExecuteCommandParams, ExecuteCommandRequest, LanguageClient, LanguageClientOptions, ProtocolRequestType, RequestType, ServerOptions, ExecuteCommandRegistrationOptions } from 'vscode-languageclient/node';

let schemaClient: LanguageClient | null = null;

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

	const serverOptions: ServerOptions = {
		command: javaExecutable,
		args: ['-jar', serverPath],
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

//const FindDocumentRequest: RequestType<ExecuteCommandParams, any, void> = new RequestType("vespaSchemaLS/findDocument");
const FindDocumentRequest: ProtocolRequestType<ExecuteCommandParams, any, never, void, ExecuteCommandRegistrationOptions>
    = new ProtocolRequestType("vespaSchemaLS/findDocument");

export function activate(context: vscode.ExtensionContext) {
	const jarPath = path.join(__dirname, '..', 'server', 'schema-language-server-jar-with-dependencies.jar');

    schemaClient = createAndStartClient(jarPath);

    const logger = vscode.window.createOutputChannel("Vespa language client", {log: true});

    context.subscriptions.push(vscode.commands.registerCommand("vespaSchemaLS.restart", (() => {
        if (schemaClient === null) {
            schemaClient = createAndStartClient(jarPath);
        } else if (schemaClient.isRunning()) {
            schemaClient.restart();
        } else {
            schemaClient.start();
        }
    })));


    context.subscriptions.push(vscode.commands.registerCommand("vespaSchemaLS.servicesxml.findDocument", async (fileName) => {
        if (schemaClient !== null) {
            const result = await schemaClient.sendRequest(ExecuteCommandRequest.type, fileName);
            logger.info("Find document returned: ");
            logger.info(result);
        }
        return null;
    }));
    logger.info("Vespa language client activated");
}


export function deactivate() { 
	if (!schemaClient) {
		return undefined;
	}
	return schemaClient.stop();
}
