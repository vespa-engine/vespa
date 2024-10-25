package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import ai.vespa.schemals.context.EventExecuteCommandContext;

public class HasSetupWorkspace implements SchemaCommand {

    @Override
    public int getArity() {
        return 0;
    }

    @Override
    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();
        return true;
    }

    @Override
    public Object execute(EventExecuteCommandContext context) {
        return Boolean.valueOf(context.scheduler.getWorkspaceURI() != null);
    }
}
