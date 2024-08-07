package ai.vespa.schemals.lsp.rename;

import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Hanldes a prepare rename request, and verifyes that the text at the cursor can be renamed
 */
public class SchemaPrepareRename {

    public static Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRename(EventPositionContext context) {
        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);

        if (node == null) {
            return null;
        }
        
        return Either3.forFirst(node.getRange());
    }
}
