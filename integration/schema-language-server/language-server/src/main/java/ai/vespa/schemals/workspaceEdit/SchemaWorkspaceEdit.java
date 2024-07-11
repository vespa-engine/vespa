package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SchemaWorkspaceEdit {

    ArrayList<Either<SchemaTextDocumentEdit, ResourceOperation>> edits;

    public SchemaWorkspaceEdit() {
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

    public WorkspaceEdit exportEdits() {

        if (edits.size() == 0) {
            return null;
        }

        ArrayList<Either<TextDocumentEdit, ResourceOperation>> ret = new ArrayList<>();

        for (var edit : edits) {
            if (edit.isRight()) {
                ret.add(Either.forRight(edit.getRight()));
            } else {
                ret.add(Either.forLeft(edit.getLeft().exportTextDocumentEdit()));
            }
        }

        return new WorkspaceEdit(ret);
    }

}
