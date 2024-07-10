package ai.vespa.schemals.rename;

import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaPrepareRename {

    public static Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRename(EventPositionContext context) {
        SchemaNode node = context.document.getSymbolAtPosition(context.position);

        if (node == null) {
            return null;
        }
        
        return Either3.forFirst(node.getRange());
    }
}
