package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

public class GetDefinedSchemas implements SchemaCommand {

    @Override
    public int getArity() {
        return 0;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();
        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        List<Symbol> schemaDefinitions = context.schemaIndex.getSymbolsByType(SymbolType.DOCUMENT);
        return schemaDefinitions.stream().map(symbol -> symbol.getShortIdentifier()).toList();
    }
}
