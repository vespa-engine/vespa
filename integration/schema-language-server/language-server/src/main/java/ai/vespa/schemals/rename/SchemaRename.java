package ai.vespa.schemals.rename;

import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.workspaceEdit.SchemaTextDocumentEdit;
import ai.vespa.schemals.workspaceEdit.SchemaWorkspaceEdit;

public class SchemaRename {

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SymbolNode node = context.document.getSymbolAtPosition(context.position);
        if (node == null) {
            return null;
        }

        SchemaWorkspaceEdit workspaceEdit = new SchemaWorkspaceEdit();
        SchemaTextDocumentEdit documentEdits = new SchemaTextDocumentEdit(context.document.getVersionedTextDocumentIdentifier());

        // TODO: Fix this optimalization
        // boolean onlyDownwardsInGraph = false;
        // if (node instanceof SymbolDefinitionNode) {
        //     onlyDownwardsInGraph = true;
        // }

        // CASES: rename schema / document, field, struct, funciton

        if (node.getSymbolType() == TokenType.FUNCTION) {
            documentEdits.add(new TextEdit(node.getRange(), newName));
        }


        context.logger.println("Renaming...");

        workspaceEdit.addTextDocumentEdit(documentEdits);
        return workspaceEdit.exportEdits();
    }
}
