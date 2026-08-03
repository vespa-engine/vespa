package ai.vespa.schemals.intellij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.util.List;

public class SchemaLanguageServer4IJ extends ProcessStreamConnectionProvider {

    public SchemaLanguageServer4IJ(Project project) {
        var serverPath = PluginPaths.pluginDirectoryOf(SchemaLanguageServer4IJ.class)
                .resolve("schema-language-server-jar-with-dependencies.jar")
                .toAbsolutePath()
                .toString();

        List<String> commands = new JavaProcessCommandBuilder(project, "vespaSchemaLanguageServer")
                .setJar(serverPath)
                .create();
        super.setCommands(commands);
    }

}
