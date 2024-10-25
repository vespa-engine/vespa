package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.ExecuteCommandParams;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.common.command.CommandRegistry;

/**
 * CommandList
 * Represents a chain of commands to be executed one after another.
 * Currently their return values are ignored.
 */
public class CommandList implements SchemaCommand {

    List<SchemaCommand> commandsToExecute = new ArrayList<>();

	@Override
	public int getArity() {
        return -1;
	}

	@Override
	public boolean setArguments(List<Object> arguments) {
        commandsToExecute.clear();

        for (Object object : arguments) {
            if (!(object instanceof JsonObject))return false;

            JsonObject jsonObject = (JsonObject)object;

            if (!jsonObject.has("command") || !jsonObject.get("command").isJsonPrimitive())return false;
            if (!jsonObject.has("arguments") || !jsonObject.get("arguments").isJsonArray())return false;

            var params = new ExecuteCommandParams(jsonObject.get("command").getAsString(), new ArrayList<Object>(((JsonArray)jsonObject.get("arguments")).asList()));
            Optional<SchemaCommand> command = CommandRegistry.getCommand(params);
            if (command.isEmpty()) return false;
            commandsToExecute.add(command.get());
        }
        return true;
	}

	@Override
	public Object execute(EventExecuteCommandContext context) {
        for (SchemaCommand cmd : commandsToExecute) {
            cmd.execute(context);
        }
        return null;
	}
}
