package ai.vespa.schemals.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor

/**
 * Entry point for giving LSP support by starting the server.
 * Used in plugin.xml
 */
internal class SchemaLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (file.extension.equals("sd") || file.extension.equals("profile")) {
            serverStarter.ensureServerStarted(SchemaLspServerDescriptor(project));
        }
    }
}

class SchemaLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Schema LSP Server") {
    override fun isSupportedFile(file: VirtualFile) = file.extension.equals("sd") || file.extension.equals("profile")

    override fun createCommandLine(): GeneralCommandLine {
        val schemaPlugin = PluginManager.getInstance().findEnabledPlugin(PluginId.getId("ai.vespa"))!!
        val serverPath = schemaPlugin.pluginPath.resolve("schema-language-server-jar-with-dependencies.jar").toAbsolutePath().toString()
        return GeneralCommandLine("java", "-jar", serverPath)
    }
}
