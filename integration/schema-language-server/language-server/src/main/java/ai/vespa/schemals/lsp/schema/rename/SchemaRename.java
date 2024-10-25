package ai.vespa.schemals.lsp.schema.rename;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.common.editbuilder.WorkspaceEditBuilder;
import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Handles a rename requests
 */
public class SchemaRename {

    private static WorkspaceEditBuilder addTextEditsFromSymbols(WorkspaceEditBuilder builder, EventContext context, List<Symbol> symbols, String newName) {
        for (Symbol symbol : symbols) {
            DocumentManager manager = context.scheduler.getDocument(symbol.getFileURI());

            if (manager == null) {
                builder.addTextEdit(symbol.getFileURI(), new TextEdit(symbol.getNode().getRange(), newName));
            } else {
                builder.addTextEdit(manager.getVersionedTextDocumentIdentifier(), new TextEdit(symbol.getNode().getRange(), newName));
            }
        }
        return builder;
    }

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);
        if (node == null || !node.hasSymbol()) {
            context.logger.error("Could not find symbol");
            return null;
        }

        // CASES: rename schema / document, field, struct, function
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
            context.logger.error("Symbol has a fileURI to a file not in index!");
            return null;
        }
        
        List<Symbol> symbolOccurances = new ArrayList<>();
        symbolOccurances.addAll(context.schemaIndex.getSymbolReferences(symbol.get()));
        symbolOccurances.add(symbol.get());
        
        if (type == SymbolType.SCHEMA) {
            return renameSchema(context, document, symbol.get(), symbolOccurances, node.getText(), newName);
        }

        WorkspaceEditBuilder builder = new WorkspaceEditBuilder();
        builder = addTextEditsFromSymbols(builder, context, symbolOccurances, newName);
        return builder.build();
    }

    private static WorkspaceEdit renameSchema(EventContext context, SchemaDocument document, Symbol symbol, List<Symbol> symbolOccurances, String oldName, String newName) {

        Optional<Symbol> documentSymbol = context.schemaIndex.findSymbol(symbol, SymbolType.DOCUMENT, oldName);

        if (documentSymbol.isPresent() && !documentSymbol.get().equals(symbol)) {
            symbolOccurances.add(documentSymbol.get());
            List<Symbol> documentReferences = context.schemaIndex.getSymbolReferences(documentSymbol.get());
            symbolOccurances.addAll(documentReferences);
        }

        String newFileURI = document.getFilePath() + newName + ".sd";

        // Check for name collision
        if (context.scheduler.fileURIExists(newFileURI) && !newFileURI.equals(document.getFileURI())) {
            context.messageHandler.sendMessage(MessageType.Error, "Cannot rename schema to '" + newName + "' since the name is already taken.");

            return null;
        }

        WorkspaceEditBuilder builder = new WorkspaceEditBuilder();

        // register this document so edits here happens before rename
        builder.registerVersionedDocumentIdentifier(document.getVersionedTextDocumentIdentifier());

        if (!document.getFileURI().equals(newFileURI)) {
            builder.addResourceOperation(new RenameFile(document.getFileURI(), newFileURI));
        }

        builder = addTextEditsFromSymbols(builder, context, symbolOccurances, newName);
        return builder.build();
    }
}
