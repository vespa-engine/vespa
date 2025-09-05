package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;
import java.util.Optional;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.common.command.CommandUtils;
import ai.vespa.schemals.tree.Node;

public class GetSchemaFields implements SchemaCommand {

    private String schemaName;

    @Override
    public int getArity() {
        return 1;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        Optional<String> argument = CommandUtils.getStringArgument(arguments.get(0));
        if (argument.isEmpty()) return false;

        schemaName = argument.get();
        return true;
    }

    public String fieldSymbolTypeText(EventExecuteCommandContext context, Symbol fieldSymbol) {
        Optional<Node> typeNode = context.schemaIndex.fieldIndex().getFieldDataTypeNode(fieldSymbol);
        if (typeNode.isPresent()) {
            return typeNode.get().getText();
        }

        return "";
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (schemaName == null) return List.of();

        Optional<Symbol> schemaSymbol = context.schemaIndex.findSymbol(null, SymbolType.SCHEMA, schemaName);

        if (schemaSymbol.isEmpty()) {
            return List.of();
        }

        return context.schemaIndex
                      .listSymbolsInScope(schemaSymbol.get(), SymbolType.FIELD)
                      .stream()
                      .map(symbol -> 
                          List.of(symbol.getShortIdentifier(), fieldSymbolTypeText(context, symbol)))
                      .toList();
    }
}
