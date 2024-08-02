package ai.vespa.schemals.lsp.completion;

import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.lsp.completion.provider.BodyKeywordCompletion;
import ai.vespa.schemals.lsp.completion.provider.CompletionProvider;
import ai.vespa.schemals.lsp.completion.provider.EmptyFileCompletion;
import ai.vespa.schemals.lsp.completion.provider.FieldsCompletion;
import ai.vespa.schemals.lsp.completion.provider.IndexingLangaugeCompletion;
import ai.vespa.schemals.lsp.completion.provider.InheritanceCompletion;
import ai.vespa.schemals.lsp.completion.provider.InheritsCompletion;
import ai.vespa.schemals.lsp.completion.provider.SimpleColonCompletion;
import ai.vespa.schemals.lsp.completion.provider.StructFieldCompletion;
import ai.vespa.schemals.lsp.completion.provider.TypeCompletion;
import ai.vespa.schemals.schemadocument.SchemaDocument;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new EmptyFileCompletion(),
        new BodyKeywordCompletion(),
        new TypeCompletion(),
        new FieldsCompletion(),
        new SimpleColonCompletion(),
        new InheritsCompletion(),
        new InheritanceCompletion(),
        new StructFieldCompletion(),
        new IndexingLangaugeCompletion()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        if (StringUtils.isInsideComment(context.document.getCurrentContent(), context.position)) {
            return ret;
        }

        for (CompletionProvider provider : providers) {
            try {
                ret.addAll(provider.getCompletionItems(context));
            } catch(Exception e) {
                e.printStackTrace(context.logger);
                //context.logger.println(e.getMessage());
            }
        }

        return ret;
    }
}
