package ai.vespa.schemals.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.SchemaDocument;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new EmptyFileProvider(),
        new BodyKeywordCompletionProvider(),
        new TypeCompletionProvider(),
        new FieldsCompletionProvider(),
        new MatchCompletionProvider(),
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        // TODO: comments in rank profile files
        if ((context.document instanceof SchemaDocument) && ((SchemaDocument)context.document).isInsideComment(context.position)) {
            return ret;
        }

        for (CompletionProvider provider : providers) {
            try {
                if (provider.match(context)) {
                    context.logger.println("Match with: " + provider.getClass().toString());
                        ret.addAll(provider.getCompletionItems(context));
                }
            } catch(Exception e) {
                // ignore
            }
        }

        return ret;
    }
}
