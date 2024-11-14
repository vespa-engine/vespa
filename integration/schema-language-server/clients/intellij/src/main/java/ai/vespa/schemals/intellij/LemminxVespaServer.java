package ai.vespa.schemals.intellij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ide.plugins.PluginManagerCore;

public class LemminxVespaServer extends ProcessStreamConnectionProvider {
    public LemminxVespaServer(Project project) {
        PluginId id = PluginId.getId("ai.vespa");
        var plugin = PluginManagerCore.getPlugin(id);
        if (plugin == null) {
            throw new IllegalStateException("Plugin " + id + " not found. Cannot start the Vespa Schema Language Support plugin.");
        }
        var pluginPath = plugin.getPluginPath();

        var vespaServerPath = pluginPath
                .resolve("lemminx-vespa-jar-with-dependencies.jar")
                .toAbsolutePath()
                .toString();

        var lemminxPath = pluginPath
                .resolve("lib")
                .resolve("org.eclipse.lemminx-0.28.0-uber.jar")
                .toAbsolutePath()
                .toString();

        List<String> commands = new JavaProcessCommandBuilder(project, "vespaSchemaLanguageServer")
                .setCp(lemminxPath + File.pathSeparator + vespaServerPath)
                .create();
        commands.add("org.eclipse.lemminx.XMLServerLauncher");

        super.setCommands(commands);
    }
}
