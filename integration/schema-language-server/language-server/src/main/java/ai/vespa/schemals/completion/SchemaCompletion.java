package ai.vespa.schemals.completion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new BodyKeywordCompletionProvider(),
        new TypeCompletionProvider()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        for (CompletionProvider provider : providers) {
            if (provider.match(context)) {
                context.logger.println("Match with: " + provider.getClass().toString());
                ret.addAll(provider.getCompletionItems(context));
            }
        }

        return ret;
    }
}
