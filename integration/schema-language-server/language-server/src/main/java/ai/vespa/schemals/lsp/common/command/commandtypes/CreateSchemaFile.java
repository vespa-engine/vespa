package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.common.editbuilder.WorkspaceEditBuilder;
import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.lsp.common.command.CommandUtils;

public class CreateSchemaFile implements SchemaCommand {
    private String schemaName;

    @Override
    public int getArity() {
        return 1;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        Optional<String> argument = CommandUtils.getStringArgument(arguments.get(0));
        if (argument.isEmpty()) return false;
        schemaName = argument.get();

        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (context.scheduler.getWorkspaceURI() == null) {
            // Cannot create schema if we don't know where to put it
            return false;
        }
        if (context.schemaIndex.getSchemaDefinition(schemaName).isPresent()) {
            // Schema already exists
            context.logger.warning("Cannot create schema " + schemaName + " because it already exists.");
            return false;
        }

        Path writePath = Paths.get(URI.create(context.scheduler.getWorkspaceURI())).resolve(schemaName + ".sd");
        String writeUri = writePath.toUri().toString();

        String schemaText = new StringBuilder()
            .append("schema " + schemaName + " {\n")
            .append("    document " + schemaName + " {\n")
            .append("        \n")
            .append("    }\n")
            .append("}")
            .toString();

        Range insertRange = new Range(new Position(0, 0), new Position(0, 0));
        WorkspaceEdit edit = new WorkspaceEditBuilder()
            .addResourceOperation(new CreateFile(writeUri, new CreateFileOptions(false, true)))
            .addTextEdit(writeUri, new TextEdit(insertRange, schemaText))
            .build();

        Object result = context.messageHandler.applyEdit(new ApplyWorkspaceEditParams(edit)).join();

        context.logger.info(result);

        return true;
    }
}


