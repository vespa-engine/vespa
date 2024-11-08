package ai.vespa.schemals.intellij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.util.Arrays;
import java.util.List;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ide.plugins.PluginManagerCore;

public class SchemaLanguageServer4IJ extends ProcessStreamConnectionProvider {
    
    public SchemaLanguageServer4IJ(Project project) {
        PluginId id = PluginId.getId("ai.vespa");
        var plugin = PluginManagerCore.getPlugin(id);
        if (plugin == null) {
            throw new IllegalStateException("Plugin " + id + " not found. Cannot start the Vespa Schema Language Support plugin.");
        }
        var pluginPath = plugin.getPluginPath();

        var serverPath = pluginPath
                .resolve("schema-language-server-jar-with-dependencies.jar")
                .toAbsolutePath()
                .toString();

        List<String> commands = new JavaProcessCommandBuilder(project, "vespaSchemaLanguageServer")
                .setJar(serverPath)
                .create();
        super.setCommands(commands);
    }

}
