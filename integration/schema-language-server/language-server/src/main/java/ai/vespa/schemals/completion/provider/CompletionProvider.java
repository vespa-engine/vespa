package ai.vespa.schemals.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;


import ai.vespa.schemals.context.EventPositionContext;

public interface CompletionProvider {
    /**
     * @param context
     * @return true if the completion provider should provide completion items given the current context
     */
    public boolean match(EventPositionContext context);

    public List<CompletionItem> getCompletionItems(EventPositionContext context);
}
