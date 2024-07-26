package ai.vespa.schemals.lsp.command;

import java.util.Optional;

import org.eclipse.lsp4j.ShowDocumentResult;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.command.commandtypes.SchemaCommand;

/**
 * ExecuteCommand
 */
public class ExecuteCommand {
    public static Object executeCommand(EventExecuteCommandContext context) {
        Optional<SchemaCommand> command = CommandRegistry.getCommand(context.params);

        command.ifPresent(cmd -> cmd.execute(context));
        return null;
    }
}
