package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.SchemaDocumentParser;

public class SchemaWorkspaceEdit {

    ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> edits;
    EventContext context;

    public SchemaWorkspaceEdit(EventContext context) {
        this.context = context;
        edits = new ArrayList<>();
    }

    public void addTextDocumentEdit(SchemaTextDocumentEdit textDocumentEdit) {
        edits.add(Either.forLeft(textDocumentEdit));
    }

    public void addTextDocumentEdits(List<SchemaTextDocumentEdit> textDocumentEdits) {
        for (SchemaTextDocumentEdit documentEdit : textDocumentEdits) {
            addTextDocumentEdit(documentEdit);
        }
    }

    public void addResourceOperation(ResourceOperation resourceOperation) {
        edits.add(Either.forRight(resourceOperation));
    }

    private record EditResponsibilities(
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> server,
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> client
    ) {}
    
    private EditResponsibilities findEditResponsibilities() {
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> server = new ArrayList<>();
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> client = new ArrayList<>();

        HashSet<String> extraClientResponsibilites = new HashSet<>();

        // TODO: This implementation seems buggy. Should refactor at a point when this is used more
        for (var edit : edits) {
            if (edit.isRight()) {
                ResourceOperation operation = edit.getRight();
                
                if (operation.getKind() == ResourceOperationKind.Rename) {
                    RenameFile renameOp = (RenameFile) operation;
                    extraClientResponsibilites.add(renameOp.getOldUri());
                }
            }
        }

        for (var edit : edits) {
            if (edit.isRight()) {
                client.add(edit);
            } else {

                String fileURI = edit.getLeft().getFileURI();
                SchemaDocumentParser document = context.scheduler.getDocument(fileURI);
                if (document.getIsOpen() || extraClientResponsibilites.contains(fileURI)) {
                    client.add(edit);
                } else {
                    server.add(edit);
                }

            }
        }

        return new EditResponsibilities(server, client);
    }

    public WorkspaceEdit exportEdits() {
        EditResponsibilities responsibilities = findEditResponsibilities();
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> client = responsibilities.client;

        if (client.size() == 0) {
            return null;
        }

        ArrayList<Either<TextDocumentEdit, ResourceOperation>> ret = new ArrayList<>();

        for (var edit : client) {
            if (edit.isRight()) {
                ret.add(Either.forRight(edit.getRight()));
            } else {
                ret.add(Either.forLeft(edit.getLeft().exportTextDocumentEdit()));
            }
        }

        return new WorkspaceEdit(ret);
    }

    public void commitDiskEdits() {
        EditResponsibilities responsibilities = findEditResponsibilities();
        ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> server = responsibilities.server;

        if (server.size() == 0) return;

        context.logger.println("TODO: write to disk :)))");
    }

}
