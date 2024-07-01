package ai.vespa.schemals.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;

public class SchemaCompletion {

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        SchemaNode node = context.document.getNodeAtPosition(context.position);

        if (node.getIdentifierString() == "ai.vespa.schemals.parser.ast.fieldElm") {
            ArrayList<SchemaNode> results = context.schemaIndex.findSymbols(context.document.getFileURI(), Token.TokenType.FIELD);

            for (SchemaNode result : results) {
                ret.add(new CompletionItem(result.getText()));
            }
        }

        return ret;
    }

}
