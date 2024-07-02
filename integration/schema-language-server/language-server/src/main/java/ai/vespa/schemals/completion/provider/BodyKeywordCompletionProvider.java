package ai.vespa.schemals.completion.provider;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.completion.provider.CompletionProvider;
import ai.vespa.schemals.completion.utils.CompletionUtils;

public class BodyKeywordCompletionProvider implements CompletionProvider {
    // TODO: make compatible with parser
    private static HashMap<EventPositionContext.EnclosingBody, CompletionItem[]> bodyKeywordSnippets = new HashMap<>() {{
        put(EventPositionContext.EnclosingBody.SCHEMA, new CompletionItem[]{
            CompletionUtils.constructSnippet("document", "document ${1:name} {\n\t$0\n}"), // TODO: figure out client tab format
            CompletionUtils.constructSnippet("index", "index ${1:index-name}: ${2:property}", "index:"),
            CompletionUtils.constructSnippet("index", "index ${1:index-name} {\n\t$0\n}", "index {}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}"),
            CompletionUtils.constructSnippet("fieldset", "fieldset ${1:default} {\n\tfields: $0\n}"),
            CompletionUtils.constructSnippet("rank-profile", "rank-profile ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("constant", "constant ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("onnx-model", "onnx-model ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("stemming", "stemming: $1"),
            CompletionUtils.constructSnippet("document-summary", "document-summary ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("annotation", "annotation ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("import field", "import field ${1:name} as $2 {}"),
            CompletionUtils.constructSnippet("raw-as-base64-in-summary", "raw-as-base64-in-summary")
        });

        put(EventPositionContext.EnclosingBody.DOCUMENT, new CompletionItem[]{
            CompletionUtils.constructSnippet("struct", "struct ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}"),
            CompletionUtils.constructSnippet("compression", "compression {\n\t$0\n}"),

        });

        put(EventPositionContext.EnclosingBody.FIELD, new CompletionItem[]{
            CompletionUtils.constructSnippet("alias", "alias: $1"),
            CompletionUtils.constructSnippet("attribute", "attribute ${1:name}: ${2:property}", "attribute:"),
            CompletionUtils.constructSnippet("attribute", "attribute ${1:name} {\n\t$0\n}", "attribute {}"),
            CompletionUtils.constructSnippet("bolding", "bolding: on"),
            CompletionUtils.constructSnippet("dictionary", "dictionary {\n\t$0\n}"),
            CompletionUtils.constructSnippet("id", "id: "),
            CompletionUtils.constructSnippet("index", "index ${1:index-name}: ${2:property}", "index:"),
            CompletionUtils.constructSnippet("index", "index ${1:index-name} {\n\t$0\n}", "index {}"),
            CompletionUtils.constructSnippet("indexing", "indexing: ", "indexing:"),
            CompletionUtils.constructSnippet("indexing", "indexing {\n\t$0\n}", "indexing {}"),
            CompletionUtils.constructSnippet("match", "match: ", "match:"),
            CompletionUtils.constructSnippet("match", "match {\n\t$0\n}", "match {}"),
            CompletionUtils.constructSnippet("normalizing", "normalizing: "),
            CompletionUtils.constructSnippet("query-command", "query-command: "),
            CompletionUtils.constructSnippet("rank", "rank {\n\t$0\n}"),
            CompletionUtils.constructSnippet("rank-type", "rank-type: "),
            CompletionUtils.constructSnippet("sorting", "sorting: ", "sorting:"),
            CompletionUtils.constructSnippet("sorting", "sorting {\n\t$0\n}", "sorting {}"),
            CompletionUtils.constructSnippet("stemming", "stemming: "),
            CompletionUtils.constructSnippet("struct-field", "struct-field ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("summary", "summary: ", "summary:"),
            CompletionUtils.constructSnippet("summary", "summary {\n\t$0\n}", "summary {}"),
            // summary-to is deprecated
            CompletionUtils.constructSnippet("weight", "weight: ")
        });
    }};

    @Override
    public boolean match(EventPositionContext context) {
        EventPositionContext.EnclosingBody enclosingBody = context.findEnclosingBody();
        return bodyKeywordSnippets.containsKey(enclosingBody);
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        EventPositionContext.EnclosingBody enclosingBody = context.findEnclosingBody();
        if (bodyKeywordSnippets.containsKey(enclosingBody)) {
            return Arrays.asList(bodyKeywordSnippets.get(enclosingBody));
        }
        return new ArrayList<>();
    }
}
