package ai.vespa.schemals.rename;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.workspaceEdit.SchemaTextDocumentEdit;
import ai.vespa.schemals.workspaceEdit.SchemaWorkspaceEdit;

public class SchemaRename {

    private static WorkspaceEdit renameFunction(EventPositionContext context, String newName, SymbolNode node) {

        // TODO: find references

        SchemaTextDocumentEdit documentEdits = new SchemaTextDocumentEdit(context.document.getVersionedTextDocumentIdentifier());
        documentEdits.add(new TextEdit(node.getRange(), newName));
        return documentEdits.exportWorkspaceEdit();

    }

    // private static void renameFile(EventContext context, String newFileURI) {
    //     context.scheduler.closeDocument(context.document.getFileURI());
    //     context.scheduler.openDocument(newFileURI);
    // }

    private static WorkspaceEdit renameSchema(EventPositionContext context, String newName, SymbolNode node) {
        String newFileURI = context.document.getFilePath() + newName + ".sd";

        // TODO: send a message tot he user explaining the error
        SchemaDocumentParser nameCollistionDocument = context.schemaIndex.findSchemaDocumentWithFileURI(newFileURI);
        if (nameCollistionDocument != null) {
            context.messageHandler.sendMessage(MessageType.Error, "Cannot rename schema to '" + newName + "' since the name is already taken.");

            return null;
        }

        SchemaWorkspaceEdit workspaceEdits = new SchemaWorkspaceEdit();
        SchemaTextDocumentEdit documentEdits = new SchemaTextDocumentEdit(context.document.getVersionedTextDocumentIdentifier());

        documentEdits.add(new TextEdit(node.getRange(), newName));

        workspaceEdits.addTextDocumentEdit(documentEdits);


        workspaceEdits.addResourceOperation(new RenameFile(context.document.getFileURI(), newFileURI));

        return workspaceEdits.exportEdits();
    }

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SymbolNode node = context.document.getSymbolAtPosition(context.position);
        if (node == null) {
            return null;
        }

        // TODO: Fix this optimalization
        // boolean onlyDownwardsInGraph = false;
        // if (node instanceof SymbolDefinitionNode) {
        //     onlyDownwardsInGraph = true;
        // }

        // CASES: rename schema / document, field, struct, funciton
        TokenType type = node.getSymbolType();

        if (type == TokenType.FUNCTION) {
            return renameFunction(context, newName, node);
        }

        if (type == TokenType.SCHEMA) {
            return renameSchema(context, newName, node);
        }
        
        return null;
    }
}
