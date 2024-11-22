package ai.vespa.schemals.intellij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.io.File;
import java.util.List;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.ide.plugins.PluginManagerCore;

public class LemminxVespaServer extends ProcessStreamConnectionProvider {
    public LemminxVespaServer(Project project) {
        PluginId id = PluginId.getId("ai.vespa");
        var vespaPlugin = PluginManagerCore.getPlugin(id);
        if (vespaPlugin == null) {
            throw new IllegalStateException("Plugin " + id + " not found. Cannot start the Vespa Schema Language Support plugin.");
        }
        var vespaPluginPath = vespaPlugin.getPluginPath();

        var lsp4ijPlugin = PluginManagerCore.getPlugin(PluginId.getId("com.redhat.devtools.lsp4ij"));
        if (lsp4ijPlugin == null) {
            throw new IllegalStateException("LSP4IJ could not be found. Cannot start the Vespa Schema Language Support plugin.");
        }

        var vespaServerPath = vespaPluginPath
                .resolve("lemminx-vespa-jar-with-dependencies.jar")
                .toAbsolutePath()
                .toString();

        var lemminxPath = vespaPluginPath
                .resolve("lib")
                .resolve("*")
                .toAbsolutePath()
                .toString();

        var lsp4ijPath = lsp4ijPlugin.getPluginPath()
                .resolve("lib")
                .resolve("*")
                .toAbsolutePath()
                .toString();

        List<String> commands = new JavaProcessCommandBuilder(project, "vespaLemminxLanguageServer")
                .setCp(lemminxPath + File.pathSeparator + vespaServerPath + File.pathSeparator + lsp4ijPath)
                .create();
        commands.add("org.eclipse.lemminx.XMLServerLauncher");

        super.setCommands(commands);
    }
}
