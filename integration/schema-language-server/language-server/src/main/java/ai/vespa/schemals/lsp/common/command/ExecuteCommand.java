package ai.vespa.schemals.lsp.common.command;

import java.util.Optional;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.common.command.commandtypes.SchemaCommand;

/**
 * Responsible for LSP workspace/executeCommand requests.
 */
public class ExecuteCommand {
    public static Object executeCommand(EventExecuteCommandContext context) {
        Optional<SchemaCommand> command = CommandRegistry.getCommand(context.params);

        context.logger.info("Received command: " + context.params.getCommand());

        if (command.isEmpty()) {
            context.logger.error("Unknown command " + context.params.getCommand());
            context.logger.error("Arguments:");
            for (Object obj : context.params.getArguments()) {
                context.logger.info(obj.getClass().toString() + ": " + obj.toString());
            }
            return null;
        }

        Object resultOrNull = command.get().execute(context);
        return resultOrNull;
    }
}
