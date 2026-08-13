package ai.vespa.schemals.intellij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.io.File;
import java.util.List;

public class LemminxVespaServer extends ProcessStreamConnectionProvider {
    public LemminxVespaServer(Project project) {
        var vespaPluginPath = PluginPaths.pluginDirectoryOf(LemminxVespaServer.class);

        var vespaServerPath = vespaPluginPath
                .resolve("lemminx-vespa-jar-with-dependencies.jar")
                .toAbsolutePath()
                .toString();

        var lemminxPath = vespaPluginPath
                .resolve("lib")
                .resolve("*")
                .toAbsolutePath()
                .toString();

        var lsp4ijPath = PluginPaths.libDirectoryOf(ProcessStreamConnectionProvider.class)
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
