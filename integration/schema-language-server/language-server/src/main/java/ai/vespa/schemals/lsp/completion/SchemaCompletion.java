package ai.vespa.schemals.lsp.completion;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.completion.provider.BodyKeywordCompletion;
import ai.vespa.schemals.lsp.completion.provider.CompletionProvider;
import ai.vespa.schemals.lsp.completion.provider.EmptyFileCompletion;
import ai.vespa.schemals.lsp.completion.provider.FieldsCompletion;
import ai.vespa.schemals.lsp.completion.provider.IndexingLangaugeCompletion;
import ai.vespa.schemals.lsp.completion.provider.InheritanceCompletion;
import ai.vespa.schemals.lsp.completion.provider.InheritsCompletion;
import ai.vespa.schemals.lsp.completion.provider.RankingExpressionCompletion;
import ai.vespa.schemals.lsp.completion.provider.SimpleColonCompletion;
import ai.vespa.schemals.lsp.completion.provider.StructFieldCompletion;
import ai.vespa.schemals.lsp.completion.provider.TypeCompletion;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;

/**
 * Responsible for LSP textDocument/completion requests.
 */
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
        new IndexingLangaugeCompletion(),
        new RankingExpressionCompletion()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventCompletionContext context, PrintStream errorLogger) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        if (StringUtils.isInsideComment(context.document.getCurrentContent(), context.position)) {
            return ret;
        }

        if (context.document.getDocumentType() != DocumentType.SCHEMA && context.document.getDocumentType() != DocumentType.PROFILE) {
            return ret;
        }

        for (CompletionProvider provider : providers) {
            try {
                ret.addAll(provider.getCompletionItems(context));
            } catch(Exception e) {
                e.printStackTrace(errorLogger);
            }
        }

        return ret;
    }
}
