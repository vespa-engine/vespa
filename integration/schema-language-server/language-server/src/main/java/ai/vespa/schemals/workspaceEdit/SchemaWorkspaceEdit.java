package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SchemaWorkspaceEdit {

    ArrayList<Either<TextDocumentEdit, ResourceOperation>> edits;

    public SchemaWorkspaceEdit() {
        edits = new ArrayList<>();
    }

    public void addTextDocumentEdit(SchemaTextDocumentEdit textDocumentEdit) {
        edits.add(Either.forLeft(textDocumentEdit.exportTextDocumentEdit()));
    }

    public void addTextDocumentEdits(List<SchemaTextDocumentEdit> textDocumentEdits) {
        for (SchemaTextDocumentEdit documentEdit : textDocumentEdits) {
            addTextDocumentEdit(documentEdit);
        }
    }

    public void addResourceOperation(ResourceOperation resourceOperation) {
        edits.add(Either.forRight(resourceOperation));
    }

    public WorkspaceEdit exportEdits() {
        if (edits.size() == 0) {
            return null;
        }

        return new WorkspaceEdit(edits);
    }
}
