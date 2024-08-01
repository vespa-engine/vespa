package ai.vespa.schemals.lsp.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.lsp.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.SchemaDocument;

import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new EmptyFileCompletion(),
        new BodyKeywordCompletion(),
        new TypeCompletion(),
        new FieldsCompletion(),
        new MatchCompletion(),
        new InheritsCompletion(),
        new InheritanceCompletion(),
        new StructFieldCompletion(),
        new IndexingLangaugeCompletion()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        // TODO: comments in rank profile files
        if ((context.document instanceof SchemaDocument) && ((SchemaDocument)context.document).isInsideComment(context.position)) {
            return ret;
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
