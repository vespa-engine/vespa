package ai.vespa.schemals.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.jsonrpc.CompletableFutures
import java.util.concurrent.CompletableFuture

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
    override fun createCommandLine() = GeneralCommandLine("java", "-jar", "/Users/magnus/repos/vespa/integration/schema-language-server/language-server/target/schema-language-server-jar-with-dependencies.jar", "-t", "log.txt")

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient {
        println("CREATING A CLIENT")
        //return Lsp4jClient(handler);
        return super.createLsp4jClient(handler);
    }
}

private class SchemaLspClient(serverNotificationsHandler: LspServerNotificationsHandler) : Lsp4jClient(
    serverNotificationsHandler
) {
    override fun telemetryEvent(`object`: Any) {
        println("Telemetry event")
        super.telemetryEvent(`object`)
    }
}
