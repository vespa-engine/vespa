package ai.vespa.schemals.completion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    private static CompletionItem constructSnippet(String label, String snippet, String detail) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setKind(CompletionItemKind.Snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setInsertText(snippet);
        item.setDetail(detail);
        return item;
    }

    private static CompletionItem constructSnippet(String label, String snippet) {
        return constructSnippet(label, snippet, "");
    }

    // TODO: make compatible with parser
    private static HashMap<EventPositionContext.EnclosingBody, CompletionItem[]> bodyKeywordSnippets = new HashMap<>() {{
        put(EventPositionContext.EnclosingBody.SCHEMA, new CompletionItem[]{
            constructSnippet("document", "document ${1:name} {\n\t$0\n}"), // TODO: figure out client tab format
            constructSnippet("index", "index ${1:index-name}: ${2:property}", "index:"),
            constructSnippet("index", "index ${1:index-name} {\n\t$0\n}", "index {}"),
            constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}"),
            constructSnippet("fieldset", "fieldset ${1:default} {\n\tfields: $0\n}"),
            constructSnippet("rank-profile", "rank-profile ${1:name} {\n\t$0\n}"),
            constructSnippet("constant", "constant ${1:name} {\n\t$0\n}"),
            constructSnippet("onnx-model", "onnx-model ${1:name} {\n\t$0\n}"),
            constructSnippet("stemming", "stemming: $1"),
            constructSnippet("document-summary", "document-summary ${1:name} {\n\t$0\n}"),
            constructSnippet("annotation", "annotation ${1:name} {\n\t$0\n}"),
            constructSnippet("import field", "import field ${1:name} as $2 {}"),
            constructSnippet("raw-as-base64-in-summary", "raw-as-base64-in-summary")
        });

        put(EventPositionContext.EnclosingBody.DOCUMENT, new CompletionItem[]{
            constructSnippet("struct", "struct ${1:name} {\n\t$0\n}"),
            constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}"),
            constructSnippet("compression", "compression {\n\t$0\n}"),

        });

        put(EventPositionContext.EnclosingBody.FIELD, new CompletionItem[]{
            constructSnippet("alias", "alias: $1"),
            constructSnippet("attribute", "attribute ${1:name}: ${2:property}", "attribute:"),
            constructSnippet("attribute", "attribute ${1:name} {\n\t$0\n}", "attribute {}"),
            constructSnippet("bolding", "bolding: on"),
            constructSnippet("dictionary", "dictionary {\n\t$0\n}"),
            constructSnippet("id", "id: "),
            constructSnippet("index", "index ${1:index-name}: ${2:property}", "index:"),
            constructSnippet("index", "index ${1:index-name} {\n\t$0\n}", "index {}"),
            constructSnippet("indexing", "indexing: ", "indexing:"),
            constructSnippet("indexing", "indexing {\n\t$0\n}", "indexing {}"),
            constructSnippet("match", "match: ", "match:"),
            constructSnippet("match", "match {\n\t$0\n}", "match {}"),
            constructSnippet("normalizing", "normalizing: "),
            constructSnippet("query-command", "query-command: "),
            constructSnippet("rank", "rank {\n\t$0\n}"),
            constructSnippet("rank-type", "rank-type: "),
            constructSnippet("sorting", "sorting: ", "sorting:"),
            constructSnippet("sorting", "sorting {\n\t$0\n}", "sorting {}"),
            constructSnippet("stemming", "stemming: "),
            constructSnippet("struct-field", "struct-field ${1:name} {\n\t$0\n}"),
            constructSnippet("summary", "summary: ", "summary:"),
            constructSnippet("summary", "summary {\n\t$0\n}", "summary {}"),
            // summary-to is deprecated
            constructSnippet("weight", "weight: ")
        });
    }};

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {
        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();
        
        EventPositionContext.EnclosingBody enclosingBody = context.findEnclosingBody();

        if (bodyKeywordSnippets.containsKey(enclosingBody)) {
            ret.addAll(Arrays.asList(bodyKeywordSnippets.get(enclosingBody)));
        }

        return ret;
    }
}
