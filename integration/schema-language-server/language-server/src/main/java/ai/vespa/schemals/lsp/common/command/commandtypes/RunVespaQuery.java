package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;

public class RunVespaQuery implements SchemaCommand {

    private String queryCommand;

    public int getArity() {
        return 1;
    }

    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        queryCommand = arg.getAsString();
        return true;
    }

    public void execute(EventExecuteCommandContext context) {
        context.logger.info("Running Vespa query...");
        context.logger.info(queryCommand);
    }
    
}
