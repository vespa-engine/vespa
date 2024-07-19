package ai.vespa.schemals.completion.provider;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.completion.utils.CompletionUtils;

public class BodyKeywordCompletionProvider implements CompletionProvider {
    // Currently key is the classLeafIdentifierString of a node with a body
    private static HashMap<String, CompletionItem[]> bodyKeywordSnippets = new HashMap<>() {{
        put("rootSchema", new CompletionItem[]{
            CompletionUtils.constructSnippet("document", "document ${1:name} {\n\t$0\n}"), // TODO: figure out client tab format
            CompletionUtils.constructSnippet("index", "index ${1:index-name}: ${2:property}", "index:"),
            CompletionUtils.constructSnippet("index", "index ${1:index-name} {\n\t$0\n}", "index {}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $0", "field"),
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

        put("documentElm", new CompletionItem[]{
            CompletionUtils.constructSnippet("struct", "struct ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $0", "field"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}", "field {}"),

        });

        put("fieldElm", new CompletionItem[]{
            CompletionUtils.constructSnippet("alias", "alias: $1"),
            // attribute is deprecated
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
            CompletionUtils.constructSnippet("rank", "rank: $0", "rank:"),
            CompletionUtils.constructSnippet("rank", "rank {\n\t$0\n}", "rank {}"),
            CompletionUtils.constructSnippet("rank-type", "rank-type: "),
            CompletionUtils.constructSnippet("sorting", "sorting: ", "sorting:"),
            CompletionUtils.constructSnippet("sorting", "sorting {\n\t$0\n}", "sorting {}"),
            CompletionUtils.constructSnippet("stemming", "stemming: "),
            CompletionUtils.constructSnippet("struct-field", "struct-field ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("summary", "summary: ", "summary:"),
            CompletionUtils.constructSnippet("summary", "summary {\n\t$0\n}", "summary {}"),
            // summary-to is deprecated
            CompletionUtils.constructSnippet("weight", "weight: "),
            CompletionUtils.constructSnippet("weightedset", "weightedset: ", "weightedset:"),
            CompletionUtils.constructSnippet("weightedset", "weightedset {\n\t$0\n}", "weightedset {}"),
        });

        put("structDefinitionElm", new CompletionItem[]{
            CompletionUtils.constructSnippet("field", "field ${1:name} type $0", "field"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {\n\t$0\n}", "field {}"),
        });

        put("rankProfile", new CompletionItem[]{
            CompletionUtils.constructSnippet("diversity", "diversity {\n\tattribute: $1\n\tmin-groups: $0\n}"),
            CompletionUtils.constructSnippet("match-phase", "match-phase {\n\tattribute: $1\n\torder: $2\n\tmax-hits: $3\n}"),
            CompletionUtils.constructSnippet("first-phase", "first-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("second-phase", "second-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("global-phase", "global-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("function", "function $1(){\n\t\n}"),
            CompletionUtils.constructSnippet("inputs", "inputs {\n\t$0\n}"),
            CompletionUtils.constructSnippet("constants", "constants {\n\t$0\n}"),
            CompletionUtils.constructSnippet("onnx-model", "onnx-model $1 {\n\t$0\n}"),
            CompletionUtils.constructSnippet("rank-properties", "rank-properties {\n\t$0\n}"),
            CompletionUtils.constructSnippet("match-features", "match-features: $0", "match-features:"),
            CompletionUtils.constructSnippet("match-features", "match-features {\n\t$0\n}", "match-features {}"),
            CompletionUtils.constructSnippet("mutate", "mutate {\n\t$0\n}"),
            CompletionUtils.constructSnippet("summary-features", "summary-features: $0", "summary-features:"),
            CompletionUtils.constructSnippet("summary-features", "summary-features {\n\t$0\n}", "summary-features {}"),
            CompletionUtils.constructSnippet("rank-features", "rank-features: $0", "rank-features:"),
            CompletionUtils.constructSnippet("rank-features", "rank-features {\n\t$0\n}", "rank-features {}"),
            CompletionUtils.constructBasic("ignore-default-rank-features"),
            CompletionUtils.constructBasic("num-threads-per-search"),
            CompletionUtils.constructBasic("num-search-partitions"),
            CompletionUtils.constructBasic("min-hits-per-thread"),
            CompletionUtils.constructBasic("termwise-limit"),
            CompletionUtils.constructBasic("post-filter-threshold"),
            CompletionUtils.constructBasic("approximate-threshold"),
            CompletionUtils.constructBasic("target-hits-max-adjustment-factor"),
            CompletionUtils.constructSnippet("rank", "rank ${1:field-name}: $0", "rank:"),
            CompletionUtils.constructSnippet("rank", "rank {\n\t$0\n}", "rank {}"),
            CompletionUtils.constructSnippet("rank-type", "rank-type ${1:field-name}: $0"),
        });

        put("firstPhase", new CompletionItem[]{
            CompletionUtils.constructSnippet("expression", "expression: $0", "expression:"),
            CompletionUtils.constructSnippet("expression", "expression {\n\t$0\n}", "expression {}"),
            CompletionUtils.constructSnippet("keep-rank-count", "keep-rank-count: $0"),
            CompletionUtils.constructSnippet("rank-score-drop-limit", "rank-score-drop-limit: $0"),
        });

        put("fieldSetElm", new CompletionItem[]{
            CompletionUtils.constructSnippet("match", "match: ", "match:"),
            CompletionUtils.constructSnippet("match", "match {\n\t$0\n}", "match {}"),
            CompletionUtils.constructSnippet("query-command", "query-command: ")
        });

        put("rankElm", new CompletionItem[]{
            CompletionUtils.constructBasic("filter"),
            CompletionUtils.constructBasic("literal"),
            CompletionUtils.constructBasic("normal"),
        });

    }};

    private String getEnclosingBodyKey(EventPositionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;

        SchemaNode last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);

        if (last == null) {
            return null;
        }

        SchemaNode searchNode = last;

        if (searchNode.getSchemaType() == TokenType.RBRACE) {
            if (searchNode.getParent() == null || searchNode.getParent().getParent() == null)return null;
            searchNode = searchNode.getParent().getParent();
        }

        if (searchNode.getSchemaType() == TokenType.NL) {
            if (searchNode.getParent() == null)return null;
            searchNode = searchNode.getParent();
        }

        if (searchNode.getSchemaType() == TokenType.LBRACE) {
            if (searchNode.getParent() == null || searchNode.getParent().getParent() == null)return null;
            searchNode = searchNode.getParent().getParent();
        }

        if (searchNode.getClassLeafIdentifierString().equals("openLbrace")) {
            if (searchNode.getParent() == null)return null;
            searchNode = searchNode.getParent();
        }

        String identifier = searchNode.getClassLeafIdentifierString();

        if (bodyKeywordSnippets.containsKey(identifier)) {
            return identifier;
        }

        return null;
    }

    @Override
    public boolean match(EventPositionContext context) {
        return getEnclosingBodyKey(context) != null;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        String key = getEnclosingBodyKey(context);
        if (key == null)return new ArrayList<>();
        return List.of(bodyKeywordSnippets.get(key));
    }
}
