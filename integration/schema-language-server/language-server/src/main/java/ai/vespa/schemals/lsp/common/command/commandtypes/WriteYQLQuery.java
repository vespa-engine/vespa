package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.net.URI;
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

public class WriteYQLQuery implements SchemaCommand {
    private String fileName;
    private String query;

    @Override
    public int getArity() {
        return 2;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        Optional<String> fileName = CommandUtils.getStringArgument(arguments.get(0));
        Optional<String> query = CommandUtils.getStringArgument(arguments.get(1));
        if (fileName.isEmpty() || query.isEmpty()) return false;

        this.fileName = fileName.get();
        this.query = query.get();
        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (context.scheduler.getWorkspaceURI() == null) return null;

        URI fileURI = URI.create(context.scheduler.getWorkspaceURI()).resolve(fileName);
        Range insertRange = new Range(new Position(0, 0), new Position(0, 0));
        WorkspaceEdit edit = new WorkspaceEditBuilder()
            .addResourceOperation(new CreateFile(fileURI.toString(), new CreateFileOptions(true, false)))
            .addTextEdit(fileURI.toString(), new TextEdit(insertRange, query))
            .build();

        context.messageHandler.applyEdit(new ApplyWorkspaceEditParams(edit)).join();
        return null;
    }
}
