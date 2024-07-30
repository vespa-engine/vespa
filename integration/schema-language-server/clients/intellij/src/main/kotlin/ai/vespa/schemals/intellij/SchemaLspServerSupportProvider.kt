package ai.vespa.schemals.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.*
import com.intellij.util.concurrency.runAsCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.jetbrains.concurrency.runAsync
import kotlinx.coroutines.async

internal class SchemaLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (file.extension.equals("sd")) {
            serverStarter.ensureServerStarted(SchemaLspServerDescriptor(project));
        }
    }
}

class SchemaLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Schema LSP Server") {
    override fun isSupportedFile(file: VirtualFile) = file.extension == "sd"
    // TODO: bundle path
    override fun createCommandLine() = GeneralCommandLine("java", "-jar", "/Users/magnus/repos/vespa/integration/schema-language-server/language-server/target/schema-language-server-jar-with-dependencies.jar", "-t", "/Users/magnus/repos/integrationtest6/log.txt")

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient {
        println("CREATING A CLIENT")
        //return Lsp4jClient(handler);
        var client = super.createLsp4jClient(handler);
        return client;
    }
}

