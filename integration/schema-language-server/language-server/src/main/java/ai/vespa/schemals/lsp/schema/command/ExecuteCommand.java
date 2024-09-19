package ai.vespa.schemals.lsp.schema.command;

import java.util.Optional;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.schema.command.commandtypes.SchemaCommand;

/**
 * Responsible for LSP workspace/executeCommand requests.
 */
public class ExecuteCommand {
    public static Object executeCommand(EventExecuteCommandContext context) {
        Optional<SchemaCommand> command = CommandRegistry.getCommand(context.params);

        if (command.isEmpty()) {
            for (Object obj : context.params.getArguments()) {
                context.logger.info(obj.getClass().toString() + " ||| " + obj.toString());
            }
        }

        command.ifPresent(cmd -> cmd.execute(context));
        return null;
    }
}
