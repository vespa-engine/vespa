package ai.vespa.schemals.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();
        
        Position startOfWord = context.document.getPreviousStartOfWord(context.position);

        SchemaNode node = context.document.getNodeAtPosition(context.position);

        CSTUtils.printTreeUpToPosition(context.logger, context.document.getRootNode(), context.position);
        //CSTUtils.printTree(context.logger, context.document.getRootNode());
        context.logger.println("Current position: " + context.position.toString());
        context.logger.println("Start of word: " + startOfWord.toString());

        EventPositionContext.EnclosingType enclosingType = context.findEnclosingType();

        if (node != null) {
            if (node.getIdentifierString() == "ai.vespa.schemals.parser.ast.fieldElm") {
                ArrayList<SchemaNode> results = context.schemaIndex.findSymbols(context.document.getFileURI(), Token.TokenType.FIELD);

                for (SchemaNode result : results) {
                    ret.add(new CompletionItem(result.getText()));
                }
            }
        }

        return ret;
    }

}
