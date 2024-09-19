package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import ai.vespa.schemals.context.EventExecuteCommandContext;

/**
 * Command
 */
public interface SchemaCommand {

    public int getArity();

    public boolean setArguments(List<Object> arguments);

    public void execute(EventExecuteCommandContext context);
}
