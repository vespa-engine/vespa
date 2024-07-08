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
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {
    }
}
