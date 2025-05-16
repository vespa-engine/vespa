package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import ai.vespa.schemals.context.EventExecuteCommandContext;

public class SetComponentIds implements SchemaCommand {

    private Set<String> componentIds;

    @Override
    public int getArity() {
        return 1;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonArray))
            return false;

        componentIds = new HashSet<>();

        try {
            JsonArray args = (JsonArray) arguments.get(0);

            for (int i = 0; i < args.size(); i++) {
                String argument = args.get(i).getAsString();
                componentIds.add(argument);
            }
        } catch (UnsupportedOperationException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }

        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        context.schemaIndex.setComponentIdsInServiceXML(componentIds);
        return null;
    }
}
