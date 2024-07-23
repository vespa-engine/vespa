package ai.vespa.schemals.rename;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.workspaceEdit.SchemaTextDocumentEdit;
import ai.vespa.schemals.workspaceEdit.SchemaWorkspaceEdit;

public class SchemaRename {

    private static ArrayList<SchemaTextDocumentEdit> createTextDocumentEditsFromSymbols(EventContext context, List<Symbol> symbols, String newName) {
        HashMap<String, SchemaTextDocumentEdit> documentEdits = new HashMap<>();

        int count = 0;
        for (Symbol symbol : symbols) {

            if (!documentEdits.containsKey(symbol.getFileURI())) {
                SchemaDocument document = context.scheduler.getSchemaDocument(symbol.getFileURI());
                if (document == null) return new ArrayList<>();
                SchemaTextDocumentEdit documentEdit = new SchemaTextDocumentEdit(document.getVersionedTextDocumentIdentifier());
                documentEdits.put(documentEdit.getFileURI(), documentEdit);
            }

            SchemaTextDocumentEdit documentEdit = documentEdits.get(symbol.getFileURI());
            documentEdit.add(new TextEdit(symbol.getNode().getRange(), newName));
            count += 1;
        }

        return new ArrayList<SchemaTextDocumentEdit>(documentEdits.values());
    }

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (node == null || !node.hasSymbol()) {
            context.logger.println("Could not find symbol");
            return null;
        }

        // CASES: rename schema / document, field, struct, funciton
        Optional<Symbol> symbol = Optional.empty();

        SymbolType type = node.getSymbol().getType();
        if (type == SymbolType.DOCUMENT) {
            type = SymbolType.SCHEMA;

            symbol = context.schemaIndex.getSchemaDefinition(node.getText());
        } else {
            symbol = context.schemaIndex.getSymbolDefinition(node.getSymbol());
        }


        if (symbol.isEmpty()) {
            context.messageHandler.sendMessage(MessageType.Error, "Could not find the symbol definition for: " + symbol.get().getShortIdentifier());
            return null;
        }

        SchemaDocument document = context.scheduler.getSchemaDocument(symbol.get().getFileURI());
        if (document == null) {
            context.logger.println("Symbol has a fileURI to a file not in index!");
            return null;
        }
        
        List<Symbol> symbolOccurances = new ArrayList<>();
        symbolOccurances.addAll(context.schemaIndex.getSymbolReferences(symbol.get()));
        symbolOccurances.add(symbol.get());
        
        if (type == SymbolType.SCHEMA) {
            return renameSchema(context, document, symbol.get(), symbolOccurances, node.getText(), newName);
        }

        SchemaWorkspaceEdit workspaceEdits = new SchemaWorkspaceEdit();

        ArrayList<SchemaTextDocumentEdit> documentEdits = createTextDocumentEditsFromSymbols(context, symbolOccurances, newName);

        workspaceEdits.addTextDocumentEdits(documentEdits);
        
        return workspaceEdits.exportEdits();
    }

    private static WorkspaceEdit renameSchema(EventContext context, SchemaDocument document, Symbol symbol, List<Symbol> symbolOccurances, String oldName, String newName) {
        SchemaWorkspaceEdit workspaceEdits = new SchemaWorkspaceEdit();

        Optional<Symbol> documentSymbol = context.schemaIndex.findSymbol(symbol, SymbolType.DOCUMENT, oldName);

        if (documentSymbol.isPresent() && !documentSymbol.get().equals(symbol)) {
            symbolOccurances.add(documentSymbol.get());
            List<Symbol> documentReferences = context.schemaIndex.getSymbolReferences(documentSymbol.get());
            symbolOccurances.addAll(documentReferences);
        }
        // TODO: Fix this
        // else {
        //     List<Symbol> documentReferences = context.schemaIndex.findDocumentSymbolReferences(symbol);
        //     symbolOccurances.addAll(documentReferences);
        // }


        String newFileURI = document.getFilePath() + newName + ".sd";

        // Check for name collision
        if (context.scheduler.fileURIExists(newFileURI) && !newFileURI.equals(document.getFileURI())) {
            context.messageHandler.sendMessage(MessageType.Error, "Cannot rename schema to '" + newName + "' since the name is already taken.");

            return null;
        }

        ArrayList<SchemaTextDocumentEdit> documentEdits = createTextDocumentEditsFromSymbols(context, symbolOccurances, newName);

        for (int i = 0; i < documentEdits.size(); i++) {
            if (documentEdits.get(i).getFileURI().equals(document.getFileURI())) {
                workspaceEdits.addTextDocumentEdit(documentEdits.get(i));

                documentEdits.remove(i);

                break;
            }
        }

        if (!document.getFileURI().equals(newFileURI)) {
            workspaceEdits.addResourceOperation(new RenameFile(document.getFileURI(), newFileURI));
        }

        workspaceEdits.addTextDocumentEdits(documentEdits);

        return workspaceEdits.exportEdits();
    }

}
