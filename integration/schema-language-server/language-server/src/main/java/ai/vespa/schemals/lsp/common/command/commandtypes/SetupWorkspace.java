package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;

public class SetupWorkspace implements SchemaCommand {

    URI baseURI;

    @Override
    public int getArity() {
        return 1;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        String suppliedURI = arg.getAsString();
        try {
            Path schemasPath = Paths.get(new URI(suppliedURI)).getParent().resolve("schemas");
            if (schemasPath.toFile().exists()) {
                baseURI = schemasPath.toUri();
            }
        } catch (URISyntaxException exception) {
            return false;
        }
        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        if (context.scheduler.getWorkspaceURI() == null && baseURI != null) {
            context.scheduler.setupWorkspace(baseURI);
        }
        return null;
    }
}
