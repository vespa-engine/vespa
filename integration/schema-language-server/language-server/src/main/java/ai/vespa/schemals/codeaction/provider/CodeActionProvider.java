package ai.vespa.schemals.codeaction.provider;

import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.context.EventCodeActionContext;

/**
 * CodeActionProvider
 */
public interface CodeActionProvider {
    public List<Either<Command, CodeAction>> getActions(EventCodeActionContext context);
}
