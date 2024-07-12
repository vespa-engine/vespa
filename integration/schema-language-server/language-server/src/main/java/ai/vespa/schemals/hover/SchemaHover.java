package ai.vespa.schemals.hover;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaHover {
    private static final String markdownPathRoot = "hover/";

    private static Hover getStructHover(SchemaNode node, EventPositionContext context) {
        Symbol structDefinitionSymbol = context.schemaIndex.findSymbol(node.getSymbol().getFileURI(), SymbolType.STRUCT, node.getSymbol().getLongIdentifier());
        if (structDefinitionSymbol == null) {
            Optional<Symbol> res = context.schemaIndex.findDefinitionOfReference(node.getSymbol());
            if (res.isEmpty()) return null;
            structDefinitionSymbol = res.get();
        }
        List<Symbol> fieldDefs = context.schemaIndex.getAllStructFieldSymbols(structDefinitionSymbol);

        String result = "### struct " + structDefinitionSymbol.getLongIdentifier() + "\n";
        for (Symbol fieldDef : fieldDefs) {
            result += "    field " + fieldDef.getShortIdentifier() + " type " +  
                fieldDef.getNode().getNextSibling().getNextSibling().getText();

            if (!fieldDef.isInScope(structDefinitionSymbol)) {
                result += " (inherited from " + fieldDef.getScopeIdentifier() + ")";
            }

            result += "\n";
        }

        if (result.isEmpty())result = "no fields found";

        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, result));
    }

    private static Hover getSymbolHover(SchemaNode node, EventPositionContext context) {
        switch(node.getSymbol().getType()) {
            case STRUCT:
                return getStructHover(node, context);
            default:
                break;
        }

        if (node.getSymbol().getStatus() == SymbolStatus.BUILTIN_REFERENCE) {
            return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, "builtin"));
        }
        return null;
    }

    public static Hover getHover(EventPositionContext context) {
        SchemaNode node = context.document.getSymbolAtPosition(context.position);
        if (node != null) {
            return getSymbolHover(node, context);
        }

        node = context.document.getLeafNodeAtPosition(context.position);
        if (node == null) return null;



        String fileName = markdownPathRoot + node.getClassLeafIdentifierString() + ".md";

        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);

        if (inputStream == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        
        String markdown = reader.lines().collect(Collectors.joining(System.lineSeparator()));

        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markdown), node.getRange());
    }
}
