package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventContextCreator;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.command.ExecuteCommand;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class SchemaWorkspaceService implements WorkspaceService {

    private PrintStream logger;
    private SchemaDocumentScheduler scheduler;
    private SchemaMessageHandler schemaMessageHandler;
    private EventContextCreator eventContextCreator;

    public SchemaWorkspaceService(PrintStream logger, SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex, SchemaMessageHandler schemaMessageHandler) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaMessageHandler = schemaMessageHandler;
        eventContextCreator = new EventContextCreator(logger, scheduler, schemaIndex, schemaMessageHandler);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        logger.println("Client deleted a lot of files.");
        List<FileDelete> deletedFiles = params.getFiles();
        
        for (FileDelete file : deletedFiles) {
            scheduler.removeDocument(file.getUri());
        }
        
    }

    @Override
    public void didRenameFiles(RenameFilesParams params) {
        logger.println("Renamed files!");
        List<FileRename> renamedFiles = params.getFiles();

        for (FileRename file : renamedFiles) {
            scheduler.removeDocument(file.getOldUri());
            scheduler.addDocument(file.getNewUri());
        }

    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            return ExecuteCommand.executeCommand(eventContextCreator.createContext(params));
        });
    }
}
