package ai.vespa.schemals.lsp.documentsymbols;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

/**
 * SchemaDocumentSymbols
 * Simple implementation due to our index structure.
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

        List<Either<SymbolInformation, DocumentSymbol>> ret = new ArrayList<>();
        for (Symbol symbol : allSymbols) {
            if (!symbol.getFileURI().equals(context.document.getFileURI())) continue;

            // this case can happen if the schema is not correct, so some identifiers become empty
            // It will not cause a server side error, but the client crashes (at least vscode)
            if (symbol.getShortIdentifier() == null || symbol.getShortIdentifier().isBlank()) continue;

            ret.add(Either.forRight(new DocumentSymbol(
                symbol.getShortIdentifier(), 
                schemaSymbolTypeToLSPSymbolKind(symbol.getType()), 
                symbol.getNode().getRange(), 
                symbol.getNode().getRange(),
                symbol.getPrettyIdentifier()
            )));
        }
        return ret;
    }
}
