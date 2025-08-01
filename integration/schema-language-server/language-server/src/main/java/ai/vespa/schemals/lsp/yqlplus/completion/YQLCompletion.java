package ai.vespa.schemals.lsp.yqlplus.completion;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.yqlplus.completion.provider.GroupOperationCompletion;
import ai.vespa.schemals.lsp.yqlplus.completion.provider.GroupingExpressionProvider;
import ai.vespa.schemals.lsp.yqlplus.completion.provider.GroupingFilterProvider;
import ai.vespa.schemals.lsp.yqlplus.completion.provider.RootCompletion;
import ai.vespa.schemals.lsp.yqlplus.completion.provider.RootGroupingCompletion;

/**
 * Responsible for LSP textDocument/completion requests for YQL language
 */
public class YQLCompletion {

    private static CompletionProvider[] providers = {
        new RootCompletion(),
        new RootGroupingCompletion(),
        new GroupOperationCompletion(),
        new GroupingExpressionProvider(),
        new GroupingFilterProvider()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventCompletionContext context, PrintStream errorLogger) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

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
