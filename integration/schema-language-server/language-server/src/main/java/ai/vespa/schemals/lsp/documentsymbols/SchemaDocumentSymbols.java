package ai.vespa.schemals.lsp.documentsymbols;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

/**
 * SchemaDocumentSymbols
 * Yields a hierarchical list of symbols for a given file. A symbol is a "child" of another symbol if it is enclosed in its definition (child in AST).
 * In our index structure, we only store the parent of a symbol, which we refer to as "scope".
 *
 * Document Symbols request is used to display UI elements like 
 *     grandparent > parent > child based on the current cursor position,
 * as well as handling explicit "list symbols in file" command.
 */
public class SchemaDocumentSymbols {
    private static SymbolKind schemaSymbolTypeToLSPSymbolKind(SymbolType type) {
        // A somewhat arbitrary conversion
        switch (type) {
            case FIELD:
            case STRUCT_FIELD:
            case SUBFIELD:
                return SymbolKind.Field;
            case STRUCT:
                return SymbolKind.Struct;
            case SCHEMA:
                return SymbolKind.Namespace;
            case DOCUMENT:
                return SymbolKind.Class;
            case FUNCTION:
            case LAMBDA_FUNCTION:
                return SymbolKind.Function;
            case PARAMETER:
                return SymbolKind.Variable;
            case RANK_PROFILE:
                return SymbolKind.Method;
            default:
                return SymbolKind.Property;
        }
    }

    public static List<Either<SymbolInformation, DocumentSymbol>> documentSymbols(EventDocumentContext context) {
        // providing scope = null yields all symbols
        List<Symbol> allSymbols = context.schemaIndex.listSymbolsInScope(null, EnumSet.allOf(SymbolType.class));

        // three stages: first find relevant symbols and construct LSP "DocumentSymbol" objects from them.
        //               second go through and create the DocumentSymbol tree structure by adding a symbol to the children list of its scope.
        //               last, only add the roots of this forest to the result
        Map<Symbol, DocumentSymbol> symbols = new HashMap<>();
        for (Symbol symbol : allSymbols) {
            if (!FileUtils.fileURIEquals(symbol.getFileURI(), context.document.getFileURI())) continue;

            // this case can happen if the schema is not correct, so some identifiers become empty
            // It will not cause a server side error, but the client crashes (at least vscode)
            if (symbol.getShortIdentifier() == null || symbol.getShortIdentifier().isBlank()) continue;

            symbols.put(symbol, new DocumentSymbol(
                symbol.getShortIdentifier(), 
                schemaSymbolTypeToLSPSymbolKind(symbol.getType()), 
                // range should enclose the entirety of the definition. This allows UI to display the hierarchy at the cursor position.
                symbol.getNode().getParent() == null ? symbol.getNode().getRange() : symbol.getNode().getParent().getRange(), 
                // selection range is the small range at the identifier
                symbol.getNode().getRange(), 
                symbol.getPrettyIdentifier(), 
                new ArrayList<>()));
        }

        for (var entry : symbols.entrySet()) {
            Symbol child = entry.getKey();
            Symbol parent = child.getScope();
            if (parent != null && symbols.containsKey(parent)) {
                symbols.get(parent).getChildren().add(entry.getValue());
            }
        }

        // Finally we add only the roots to the result.
        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        for (var entry : symbols.entrySet()) {
            if (entry.getKey().getScope() == null || !symbols.containsKey(entry.getKey().getScope())) {
                result.add(Either.forRight(entry.getValue()));
            }
        }
        return result;
    }
}
