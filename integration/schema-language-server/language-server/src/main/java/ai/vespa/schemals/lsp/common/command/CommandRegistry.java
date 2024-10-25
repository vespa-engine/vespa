package ai.vespa.schemals.lsp.common.command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;

import ai.vespa.schemals.lsp.common.command.commandtypes.CommandList;
import ai.vespa.schemals.lsp.common.command.commandtypes.CreateSchemaFile;
import ai.vespa.schemals.lsp.common.command.commandtypes.DocumentOpen;
import ai.vespa.schemals.lsp.common.command.commandtypes.DocumentParse;
import ai.vespa.schemals.lsp.common.command.commandtypes.RunVespaQuery;
import ai.vespa.schemals.lsp.common.command.commandtypes.FindDocument;
import ai.vespa.schemals.lsp.common.command.commandtypes.SchemaCommand;
import ai.vespa.schemals.lsp.common.command.commandtypes.SetupWorkspace;
import ai.vespa.schemals.lsp.common.command.commandtypes.HasSetupWorkspace;

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
            /*
             * Sends a window/showDocument request to the client.
             *
             * Parameters:
             * fileURI: String -- path to document 
             *
             * Return value:
             * null
             */
            public String title() { return "Open document"; } 
            public SchemaCommand construct() { return new DocumentOpen(); }
        },
        DOCUMENT_PARSE {
            /*
             * Reparse a given document.
             * If the language server does not know about the document (it is not opened), nothing will happen.
             *
             * Parameters:
             * fileURI: String -- path to document 
             *
             * Return value:
             * null
             */
            public String title() { return "Parse document"; }
            public SchemaCommand construct() { return new DocumentParse(); }
        },
        COMMAND_LIST {
            /*
             * Execute a list of commands in the given order.
             *
             * Parameters:
             * commands: List<SchemaCommand> -- commands to execute
             *
             * Return value:
             * null
             */
            public String title() { return "Command list"; }
            public SchemaCommand construct() { return new CommandList(); }
        },
        RUN_VESPA_QUERY {
            /*
             * Runs a Vespa query.
             *
             * Parameters:
             *
             * Return value:
             * null
             */
            public String title() { return "Run Vespa query"; }
            public SchemaCommand construct() { return new RunVespaQuery(); }
        },
        FIND_SCHEMA_DEFINITION {
            /*
             * Locates a schema definition. 
             *
             * Parameters:
             * schemaName: String -- Schema to locate.
             *
             * Return value:
             * List<Location> -- definitions found.
             */
            public String title() { return "Find schema document"; }
            public SchemaCommand construct() { return new FindDocument(); }
        },
        HAS_SETUP_WORKSPACE {
            /*
             * Ask if the language server has setup a workspace directory yet.
             *
             * Parameters:
             *
             * Return value:
             * boolean
             */
            public String title() { return "Has setup workspace"; }
            public SchemaCommand construct() { return new HasSetupWorkspace(); }
        },
        SETUP_WORKSPACE {
            /*
             * Set the workspace directory and parse *sd files within. If it is already set up, nothing will happen.
             *
             * Parameters:
             * baseURI: String -- Directory to set as workspace.
             *
             * Return value:
             * null
             */
            public String title() { return "Setup workspace"; }
            public SchemaCommand construct() { return new SetupWorkspace(); }
        },
        CREATE_SCHEMA_FILE {
            /*
             * Create a schema file with a schema definition.
             *
             * Parameters:
             * schemaName: String -- Name of the schema to be created.
             *
             * Return value:
             * boolean -- indicating success.
             *
             */
            public String title() { return "Create Schema File"; }
            public SchemaCommand construct() { return new CreateSchemaFile(); }
        }
    }

    public static List<String> getSupportedCommandList() {
        return Arrays.stream(CommandType.values()).map(commandType -> commandType.name()).toList();
    }

    public static Optional<SchemaCommand> getCommand(ExecuteCommandParams params) {
        try {
            CommandType commandType = CommandType.valueOf(params.getCommand());
            SchemaCommand command = commandType.construct();

            if (command.getArity() != -1 && command.getArity() != params.getArguments().size()) return Optional.empty();
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
