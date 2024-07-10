package ai.vespa.schemals.rename;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.workspaceEdit.SchemaTextDocumentEdit;
import ai.vespa.schemals.workspaceEdit.SchemaWorkspaceEdit;

public class SchemaRename {

    public static ArrayList<SchemaTextDocumentEdit> createTextDocumentEditsFromSymbols(EventContext context, List<Symbol> symbols, String newName) {
        HashMap<String, SchemaTextDocumentEdit> documentEdits = new HashMap<>();

        for (Symbol symbol : symbols) {

            if (!documentEdits.containsKey(symbol.getFileURI())) {
                SchemaDocumentParser document = context.scheduler.getDocument(symbol.getFileURI());
                SchemaTextDocumentEdit documentEdit = new SchemaTextDocumentEdit(document.getVersionedTextDocumentIdentifier());
                documentEdits.put(symbol.getFileURI(), documentEdit);
            }

            SchemaTextDocumentEdit documentEdit = documentEdits.get(symbol.getFileURI());
            documentEdit.add(new TextEdit(symbol.getNode().getRange(), newName));
        }

        return new ArrayList<SchemaTextDocumentEdit>(documentEdits.values());
    }

    private static WorkspaceEdit renameSchema(EventPositionContext context, SchemaDocumentParser document, SymbolNode node, String newName) {
        String newFileURI = document.getFilePath() + newName + ".sd";

        // TODO: send a message tot he user explaining the error
        SchemaDocumentParser nameCollistionDocument = context.schemaIndex.findSchemaDocumentWithFileURI(newFileURI);
        if (nameCollistionDocument != null) {
            context.messageHandler.sendMessage(MessageType.Error, "Cannot rename schema to '" + newName + "' since the name is already taken.");

            return null;
        }

        SchemaWorkspaceEdit workspaceEdits = new SchemaWorkspaceEdit();
        SchemaTextDocumentEdit documentEdits = new SchemaTextDocumentEdit(document.getVersionedTextDocumentIdentifier());

        documentEdits.add(new TextEdit(node.getRange(), newName));

        workspaceEdits.addTextDocumentEdit(documentEdits);

        workspaceEdits.addResourceOperation(new RenameFile(document.getFileURI(), newFileURI));

        context.schemaIndex.dumpIndex(context.logger);

        return workspaceEdits.exportEdits();
    }

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SymbolNode node = context.document.getSymbolAtPosition(context.position);
        if (node == null) {
            return null;
        }

        // CASES: rename schema / document, field, struct, funciton

        TokenType type = node.getSymbolType();
        if (type == TokenType.DOCUMENT) {
            type = TokenType.SCHEMA;
        }

        Symbol symbol = context.schemaIndex.findSymbol(context.document.getFileURI(), type, node.getText());
        if (symbol == null) {
            context.logger.println("Could not find the symbol definition");
            return null;
        }

        context.logger.println("Symbol: " + symbol);

        SchemaDocumentParser document = context.scheduler.getDocument(symbol.getFileURI());
        if (document == null) {
            context.logger.println("Symbol has a fileURI to a file not in index!");
            return null;
        }

        context.logger.println("fileURI: " + document.getFileURI());

        ArrayList<Symbol> references = context.schemaIndex.findSymbolReferences(symbol);
        references.add(symbol);

        context.logger.println("SymbolsToReplace: " + references.size());

        context.schemaIndex.dumpIndex(context.logger);

        SchemaWorkspaceEdit workspaceEdits = new SchemaWorkspaceEdit();
        ArrayList<SchemaTextDocumentEdit> documentEdits = createTextDocumentEditsFromSymbols(context, references, newName);

        workspaceEdits.addTextDocumentEdits(documentEdits);

        // if (type == TokenType.SCHEMA) {
        //     return renameSchema(context, document, symbol.getNode(), newName);
        // }
        
        return workspaceEdits.exportEdits();
    }
}
