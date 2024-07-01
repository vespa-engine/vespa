package ai.vespa.schemals.completion;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    // TODO: make compatible with parser
    private static HashMap<EventPositionContext.EnclosingBody, String[]> validKeywordsInBody = new HashMap<>() {{
        put(EventPositionContext.EnclosingBody.SCHEMA, new String[]{
            "document",
            "index",
            "field",
            "fieldset",
            "rank-profile",
            "constant",
            "onnx-model",
            "stemming",
            "document-summary",
            "annotation",
            "import",
            "raw-as-base64-in-summary"
        });

        put(EventPositionContext.EnclosingBody.DOCUMENT, new String[]{
            "struct",
            "field",
            "compression"
        });

        put(EventPositionContext.EnclosingBody.FIELD, new String[]{
            "alias",
            "attribute",
            "bolding",
            "dictionary",
            "id",
            "index",
            "indexing",
            "match",
            "normalizing",
            "query-command",
            "rank",
            "rank-type",
            "sorting",
            "stemming",
            "struct-field",
            "summary",
            "summary-to",
            "weight"
        });
    }};

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();
        
        EventPositionContext.EnclosingBody enclosingBody = context.findEnclosingBody();

        context.logger.println(enclosingBody.toString());

        if (validKeywordsInBody.containsKey(enclosingBody)) {
            for (String str : validKeywordsInBody.get(enclosingBody)) {
                ret.add(new CompletionItem(str));
            }
        }

        return ret;
    }
}
