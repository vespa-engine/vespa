package ai.vespa.schemals.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import ai.vespa.schemals.intellij.settings.SchemaSettings
import java.nio.file.Paths
import kotlin.io.path.isExecutable

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

        // Check if user has supplied a custom path to java
        val settingsState = SchemaSettings.getInstance().state
        val javaPath = settingsState?.javaPath ?: ""

        val customJavaExecutable = listOf(
            Paths.get(javaPath).resolve("java"),
            Paths.get(javaPath).resolve("bin").resolve("java")
        ).firstOrNull { it.isExecutable() }

        return if (customJavaExecutable != null) {
            GeneralCommandLine(customJavaExecutable.toString(), "-jar", serverPath)
        } else {
            GeneralCommandLine("java", "-jar", serverPath)
        }
    }
}
