package ai.vespa.schemals.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.SchemaDocument;

import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new EmptyFileProvider(),
        new BodyKeywordCompletionProvider(),
        new TypeCompletionProvider(),
        new FieldsCompletionProvider(),
        new MatchCompletionProvider(),
        new InheritsCompletionProvider(),
        new InheritanceCompletionProvider()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        // TODO: comments in rank profile files
        if ((context.document instanceof SchemaDocument) && ((SchemaDocument)context.document).isInsideComment(context.position)) {
            return ret;
        }

        SchemaNode node = CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.position);

        if (node != null) {
            context.logger.println("Current node: " + node.toString());
        }

        for (CompletionProvider provider : providers) {
            try {
                ret.addAll(provider.getCompletionItems(context));
            } catch(Exception e) {
                // ignore
            }
        }

        return ret;
    }
}
