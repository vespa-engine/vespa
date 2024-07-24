package ai.vespa.schemals.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;


import ai.vespa.schemals.context.EventPositionContext;

public interface CompletionProvider {
    public List<CompletionItem> getCompletionItems(EventPositionContext context);
}
