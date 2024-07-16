package ai.vespa.schemals.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.SchemaDocument;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new BodyKeywordCompletionProvider(),
        new TypeCompletionProvider(),
        new FieldsCompletionProvider(),
        new MatchCompletionProvider(),
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        if (!(context.document instanceof SchemaDocument)) return ret; // TODO: completion in rank profile files

        if (((SchemaDocument)context.document).isInsideComment(context.position)) {
            return ret;
        }

        for (CompletionProvider provider : providers) {
            if (provider.match(context)) {
                // context.logger.println("Match with: " + provider.getClass().toString());
                ret.addAll(provider.getCompletionItems(context));
            }
        }

        return ret;
    }
}
