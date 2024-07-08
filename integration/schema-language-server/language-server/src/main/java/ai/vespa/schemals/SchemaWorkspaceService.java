package ai.vespa.schemals;

import java.io.PrintStream;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class SchemaWorkspaceService implements WorkspaceService {

    private PrintStream logger;

    public SchemaWorkspaceService(PrintStream logger) {
        this.logger = logger;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {
        this.logger.println("WORKSPACE: Configuration change!");
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {
        this.logger.println("WORKSPACE: Watched files change!");
        for (var changeEvent : didChangeWatchedFilesParams.getChanges()) {
            this.logger.println("    File changed: " + changeEvent.getUri());
        }
    }
}
