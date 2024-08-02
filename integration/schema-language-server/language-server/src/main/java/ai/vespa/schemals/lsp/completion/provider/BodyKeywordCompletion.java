package ai.vespa.schemals.lsp.completion.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.annotationBody;
import ai.vespa.schemals.parser.ast.attributeElm;
import ai.vespa.schemals.parser.ast.dictionaryElm;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.firstPhase;
import ai.vespa.schemals.parser.ast.hnswIndex;
import ai.vespa.schemals.parser.ast.indexInsideField;
import ai.vespa.schemals.parser.ast.indexOutsideDoc;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.sortingElm;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.parser.ast.summaryInField;
import ai.vespa.schemals.parser.ast.summaryInFieldLong;
import ai.vespa.schemals.parser.ast.weightedsetElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class BodyKeywordCompletion implements CompletionProvider {
    // Currently key is the classLeafIdentifierString of a node with a body
    private static Map<Class<?>, List<CompletionItem>> bodyKeywordSnippets = new HashMap<>() {{
        put(rootSchema.class, List.of(
            CompletionUtils.constructSnippet("document", "document ${1:name} {\n\t$0\n}"),
            FixedKeywordBodies.INDEX.getColonSnippet(true),
            FixedKeywordBodies.INDEX.getBodySnippet(true),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {$0}"),
            CompletionUtils.constructSnippet("fieldset", "fieldset ${1:default} {\n\tfields: $0\n}"),
            CompletionUtils.constructSnippet("rank-profile", "rank-profile ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("constant", "constant ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("onnx-model", "onnx-model ${1:name} {\n\t$0\n}"),
            FixedKeywordBodies.STEMMING.getColonSnippet(),
            CompletionUtils.constructSnippet("document-summary", "document-summary ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("annotation", "annotation ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("import field", "import field ${1:name} as $2 {}"),
            CompletionUtils.constructSnippet("raw-as-base64-in-summary", "raw-as-base64-in-summary")
        ));

        put(documentElm.class, List.of(
            CompletionUtils.constructSnippet("struct", "struct ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {$0}")

        ));

        put(fieldElm.class, List.of(
            CompletionUtils.constructSnippet("alias", "alias: $1"),
            FixedKeywordBodies.ATTRIBUTE.getColonSnippet(),
            FixedKeywordBodies.ATTRIBUTE.getBodySnippet(),
            CompletionUtils.constructSnippet("bolding", "bolding: on"),
            FixedKeywordBodies.DICTIONARY.getBodySnippet(),
            CompletionUtils.constructSnippet("id", "id: "),
            FixedKeywordBodies.INDEX.getColonSnippet(),
            FixedKeywordBodies.INDEX.getBodySnippet(),
            CompletionUtils.constructSnippet("indexing", "indexing: ", "indexing:"),
            CompletionUtils.constructSnippet("indexing", "indexing {\n\t$0\n}", "indexing {}"),
            FixedKeywordBodies.MATCH.getColonSnippet(),
            FixedKeywordBodies.MATCH.getBodySnippet(),
            CompletionUtils.constructSnippet("normalizing", "normalizing: "),
            CompletionUtils.constructSnippet("query-command", "query-command: "),
            FixedKeywordBodies.RANK.getColonSnippet(),
            FixedKeywordBodies.RANK.getBodySnippet(),
            FixedKeywordBodies.RANK_TYPE.getColonSnippet(),
            FixedKeywordBodies.SORTING.getColonSnippet(),
            FixedKeywordBodies.SORTING.getBodySnippet(),
            FixedKeywordBodies.STEMMING.getColonSnippet(),
            FixedKeywordBodies.SUMMARY.getColonSnippet(),
            FixedKeywordBodies.SUMMARY.getBodySnippet(),
            CompletionUtils.constructBasicDeprecated("summary-to: "), // summary-to is deprecated
            CompletionUtils.constructSnippet("weight", "weight: "),
            FixedKeywordBodies.WEIGHTEDSET.getColonSnippet(),
            FixedKeywordBodies.WEIGHTEDSET.getBodySnippet()
        ));

        // There is one more possible in struct-field: struct-field itself.
        // However it is provided by StructFieldCompletion, because it is context-dependent.
        put(structFieldElm.class, List.of(
            CompletionUtils.constructSnippet("indexing", "indexing: ", "indexing:"),
            CompletionUtils.constructSnippet("indexing", "indexing {\n\t$0\n}", "indexing {}"),
            FixedKeywordBodies.ATTRIBUTE.getColonSnippet(),
            FixedKeywordBodies.ATTRIBUTE.getBodySnippet(),
            FixedKeywordBodies.RANK.getColonSnippet(),
            FixedKeywordBodies.RANK.getBodySnippet(),
            FixedKeywordBodies.MATCH.getColonSnippet(),
            FixedKeywordBodies.MATCH.getBodySnippet()
        ));

        put(structDefinitionElm.class, List.of(
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {}")
        ));

        put(annotationBody.class, List.of(
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {}")
        ));

        put(rankProfile.class, List.of(
            CompletionUtils.constructSnippet("diversity", "diversity {\n\tattribute: $1\n\tmin-groups: $0\n}"),
            CompletionUtils.constructSnippet("match-phase", "match-phase {\n\tattribute: $1\n\torder: $2\n\tmax-hits: $3\n}"),
            CompletionUtils.constructSnippet("first-phase", "first-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("second-phase", "second-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("global-phase", "global-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("function", "function $1() {\n\texpression: $0\n}"),
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
            FixedKeywordBodies.RANK.getColonSnippet(true),
            FixedKeywordBodies.RANK_TYPE.getColonSnippet(true)
        ));

        put(firstPhase.class, List.of(
            CompletionUtils.constructSnippet("expression", "expression: $0", "expression:"),
            CompletionUtils.constructSnippet("expression", "expression {\n\t$0\n}", "expression {}"),
            CompletionUtils.constructSnippet("keep-rank-count", "keep-rank-count: $0"),
            CompletionUtils.constructSnippet("rank-score-drop-limit", "rank-score-drop-limit: $0")
        ));

        put(fieldSetElm.class, List.of(
            FixedKeywordBodies.MATCH.getColonSnippet(),
            FixedKeywordBodies.MATCH.getBodySnippet(),
            CompletionUtils.constructSnippet("query-command", "query-command: ")
        ));

        put(FixedKeywordBodies.MATCH.parentASTClass(), FixedKeywordBodies.MATCH.completionItems());
        put(FixedKeywordBodies.RANK.parentASTClass(), FixedKeywordBodies.RANK.completionItems());

        put(summaryInDocument.class, FixedKeywordBodies.SUMMARY.completionItems());
        put(summaryInFieldLong.class, FixedKeywordBodies.SUMMARY.completionItems());

        put(weightedsetElm.class, FixedKeywordBodies.WEIGHTEDSET.completionItems());

        put(hnswIndex.class, FixedKeywordBodies.HNSW.completionItems());

        put(dictionaryElm.class, FixedKeywordBodies.DICTIONARY.completionItems());

        put(sortingElm.class, FixedKeywordBodies.SORTING.completionItems());

        put(attributeElm.class, FixedKeywordBodies.ATTRIBUTE.completionItems());

        put(indexInsideField.class, FixedKeywordBodies.INDEX.completionItems());
        put(indexOutsideDoc.class, FixedKeywordBodies.INDEX.completionItems());


    }};

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;

        SchemaNode last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);

        if (last == null) {
            return null;
        }

        if (!last.isASTInstance(NL.class)) return null;

        SchemaNode searchNode = last.getParent();

        if (searchNode == null) return null;

        if (searchNode.isASTInstance(openLbrace.class))searchNode = searchNode.getParent();

        List<CompletionItem> result = bodyKeywordSnippets.get(searchNode.getASTClass());
        if (result == null) return List.of();
        return result;
    }
}
