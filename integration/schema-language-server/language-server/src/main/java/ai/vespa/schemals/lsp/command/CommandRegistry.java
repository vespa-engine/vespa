package ai.vespa.schemals.lsp.command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;

import ai.vespa.schemals.lsp.command.commandtypes.DocumentOpen;
import ai.vespa.schemals.lsp.command.commandtypes.SchemaCommand;

/**
 * SchemaCommand
 */
public class CommandRegistry {
    public interface GenericCommandType {
        public String title();
        public SchemaCommand construct();
    }
    public enum CommandType implements GenericCommandType {
        DOCUMENT_OPEN { 
            public String title() { return "Open document"; } 
            public SchemaCommand construct() { return new DocumentOpen(); }
        }
    }

    public static List<String> getSupportedCommandList() {
        return Arrays.stream(CommandType.values()).map(commandType -> commandType.name()).toList();
    }

    public static Optional<SchemaCommand> getCommand(ExecuteCommandParams params) {
        try {
            CommandType commandType = CommandType.valueOf(params.getCommand());
            SchemaCommand command = commandType.construct();

            if (command.getArity() != params.getArguments().size()) return Optional.empty();
            if (!command.setArguments(params.getArguments())) return Optional.empty();
            return Optional.of(command);
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    public static Command createLSPCommand(CommandType commandType, List<Object> arguments) {
        return new Command(commandType.title(), commandType.name(), arguments);
    }
}
