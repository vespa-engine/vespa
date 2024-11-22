package ai.vespa.schemals.intellij;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import org.eclipse.lsp4j.services.LanguageServer;

public class LemminxVespaServerFactory implements LanguageServerFactory {
    @Override
    public StreamConnectionProvider createConnectionProvider(Project project) {
        return new LemminxVespaServer(project);
    }

    @Override
    public LanguageClientImpl createLanguageClient(Project project) {
        return new LemminxVespaClient(project);
    }
}
