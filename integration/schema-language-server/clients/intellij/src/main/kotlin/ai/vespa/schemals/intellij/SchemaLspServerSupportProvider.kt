package ai.vespa.schemals.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

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
        val id = PluginId.getId("ai.vespa")
        val plugin = PluginManagerCore.getPlugin(id)!!
        val pluginPath = plugin.getPluginPath()

        val serverPath = pluginPath
            .resolve("schema-language-server-jar-with-dependencies.jar")
            .toAbsolutePath()
            .toString()

        return GeneralCommandLine("java", "-jar", serverPath)
    }
}
