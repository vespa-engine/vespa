package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

/**
 * FindDocument
 * Param: schema identifier
 * Return: List<Location>
 */
public class FindDocument implements SchemaCommand {
    private String schemaName;

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (schemaName == null)
            return null;
        List<Symbol> schemaDefinitions = context.schemaIndex.findSymbols(null, SymbolType.SCHEMA, schemaName);
        // LSP expects the result of "definition" to be a list of Location (rather than one Location)
        // so we adhere to this here, because that is the most natural use of this command
        return schemaDefinitions.stream()
                                .map(symbol -> symbol.getLocation())
                                .toList();
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        schemaName = arg.getAsString();
        return true;
    }

    @Override
    public int getArity() {
        return 1;
    }
}
