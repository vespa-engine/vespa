package ai.vespa.schemals.rename;

import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SymbolNode;

public class SchemaRename {
    

    public static WorkspaceEdit rename(EventPositionContext context, String newName) {

        SymbolNode node = context.document.getSymbolAtPosition(context.position);
        if (node == null) {
            return null;
        }

        // TODO: Fix this optimalization
        // boolean onlyDownwardsInGraph = false;
        // if (node instanceof SymbolDefinitionNode) {
        //     onlyDownwardsInGraph = true;
        // }

        // CASES: rename schema / document, field, struct, funciton



        context.logger.println("Renaming...");

        return null;
    }
}
