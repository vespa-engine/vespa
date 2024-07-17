package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;

public class SymbolArgument implements Argument {
    
    private SymbolType symbolType;

    public SymbolArgument(SymbolType symbolType) {
        this.symbolType = symbolType;
    }

    public List<Diagnostic> verifyArgument(ParseContext context, SchemaNode node) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        SchemaNode symbolNode = node;

        while (!symbolNode.hasSymbol() && symbolNode.size() > 0) {
            symbolNode = symbolNode.get(0);
        }

        if (symbolNode.hasSymbol()) {
            Symbol symbol = symbolNode.getSymbol();

            if (symbol.getStatus() == SymbolStatus.REFERENCE) {
                symbol.setStatus(SymbolStatus.UNRESOLVED);
                context.schemaIndex().deleteSymbolReference(symbol);
            }
            
            if (symbol.getStatus() == SymbolStatus.UNRESOLVED) {
                symbol.setType(symbolType);
            }
        }

        return diagnostics;
    }
}