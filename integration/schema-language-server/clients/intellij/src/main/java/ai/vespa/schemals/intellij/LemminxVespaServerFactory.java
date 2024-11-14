package ai.vespa.schemals.intellij;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.intellij.openapi.project.Project;

public class LemminxVespaServerFactory implements LanguageServerFactory {
    @Override
    public StreamConnectionProvider createConnectionProvider(Project project) {
        return new LemminxVespaServer(project);
    }
}
