package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;

import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SchemaWorkspaceEdit {
    private ArrayList<SchemaTextDocumentEdit> textDocumentEdits = new ArrayList<>();
        private ArrayList<ResourceOperation> resourceOperations = new ArrayList<>();

        public SchemaWorkspaceEdit() {
            
        }

        public void addTextDocumentEdit(SchemaTextDocumentEdit textDocumentEdit) {
            textDocumentEdits.add(textDocumentEdit);
        }

        public void addResourceOperation(ResourceOperation resourceOperation) {
            resourceOperations.add(resourceOperation);
        }

        public WorkspaceEdit exportEdits() {
            ArrayList<Either<TextDocumentEdit, ResourceOperation>> ret = new ArrayList<>();

            for (int i = 0; i < textDocumentEdits.size(); i++) {
                ret.add(Either.forLeft(textDocumentEdits.get(i).exportTextDocumentEdit()));
            }

            for (int i = 0; i < resourceOperations.size(); i++) {
                ret.add(Either.forRight(resourceOperations.get(i)));
            }

            if (ret.size() == 0) {
                return null;
            }

            return new WorkspaceEdit(ret);
        }
}
