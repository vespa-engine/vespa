package ai.vespa.schemals.lsp.schema.completion.provider;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.SchemaLanguageServer;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.parser.ast.LBRACE;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.RBRACE;
import ai.vespa.schemals.parser.ast.RootRankProfile;
import ai.vespa.schemals.parser.ast.annotationBody;
import ai.vespa.schemals.parser.ast.attributeElm;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.dictionaryElm;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.firstPhase;
import ai.vespa.schemals.parser.ast.globalPhase;
import ai.vespa.schemals.parser.ast.hnswIndex;
import ai.vespa.schemals.parser.ast.indexInsideField;
import ai.vespa.schemals.parser.ast.indexOutsideDoc;
import ai.vespa.schemals.parser.ast.INDEX;
import ai.vespa.schemals.parser.ast.linguisticsElm;
import ai.vespa.schemals.parser.ast.mapElm;
import ai.vespa.schemals.parser.ast.onnxModel;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.PROFILE;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.SEARCH;
import ai.vespa.schemals.parser.ast.secondPhase;
import ai.vespa.schemals.parser.ast.significanceElm;
import ai.vespa.schemals.parser.ast.sortingElm;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.parser.ast.summaryInFieldLong;
import ai.vespa.schemals.parser.ast.weightedsetElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;

public class BodyKeywordCompletion implements CompletionProvider {
    // Currently key is the classLeafIdentifierString of a node with a body
    private static Map<Class<?>, List<CompletionItem>> bodyKeywordSnippets = new HashMap<>() {{
        put(rootSchema.class, List.of(
            CompletionUtils.constructSnippet("annotation", "annotation ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("constant", "constant ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("document", "document ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("document-summary", "document-summary ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("field", "field ${1:name} type $2 {$0}"),
            CompletionUtils.constructSnippet("fieldset", "fieldset ${1:default} {\n\tfields: $0\n}"),
            CompletionUtils.constructSnippet("import field", "import field ${1:name} as $2 {}"),
            CompletionUtils.constructSnippet("onnx-model", "onnx-model ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("rank-profile", "rank-profile ${1:name} {\n\t$0\n}"),
            CompletionUtils.constructSnippet("raw-as-base64-in-summary", "raw-as-base64-in-summary"),
            FixedKeywordBodies.DOCUMENT_ID.getColonSnippet(),
            FixedKeywordBodies.INDEX.getBodySnippet(true),
            FixedKeywordBodies.INDEX.getColonSnippet(true),
            FixedKeywordBodies.STEMMING.getColonSnippet()
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
            CompletionUtils.constructSnippet("linguistics", "linguistics {\n\tprofile: $0\n}"),
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
            FixedKeywordBodies.STRICT.getColonSnippet(false),
            CompletionUtils.constructSnippet("approximate-threshold", "approximate-threshold: $0"),
            CompletionUtils.constructSnippet("constants", "constants {\n\t$0\n}"),
            CompletionUtils.constructSnippet("diversity", "diversity {\n\tattribute: $1\n\tmin-groups: $0\n}"),
            CompletionUtils.constructSnippet("exploration-slack", "exploration-slack: $0"),
            CompletionUtils.constructSnippet("filter-first-exploration", "filter-first-exploration: $0"),
            CompletionUtils.constructSnippet("filter-first-threshold", "filter-first-threshold: $0"),
            CompletionUtils.constructSnippet("filter-threshold", "filter-threshold: $0"),
            CompletionUtils.constructSnippet("first-phase", "first-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("function", "function $1() {\n\texpression: $0\n}"),
            CompletionUtils.constructSnippet("global-phase", "global-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("inputs", "inputs {\n\t$0\n}"),
            CompletionUtils.constructSnippet("match-features", "match-features {\n\t$0\n}", "match-features {}"),
            CompletionUtils.constructSnippet("match-features", "match-features: $0", "match-features:"),
            CompletionUtils.constructSnippet("match-phase", "match-phase {\n\tattribute: $1\n\torder: $2\n\ttotal-max-hits: $3\n}"),
            CompletionUtils.constructSnippet("min-hits-per-thread", "min-hits-per-thread: $0"),
            CompletionUtils.constructSnippet("mutate", "mutate {\n\t$0\n}"),
            CompletionUtils.constructSnippet("num-search-partitions", "num-search-partitions: $0"),
            CompletionUtils.constructSnippet("num-threads-per-search", "num-threads-per-search: $0"),
            CompletionUtils.constructSnippet("onnx-model", "onnx-model $1 {\n\t$0\n}"),
            CompletionUtils.constructSnippet("post-filter-threshold", "post-filter-threshold: $0"),
            CompletionUtils.constructSnippet("rank-features", "rank-features {\n\t$0\n}", "rank-features {}"),
            CompletionUtils.constructSnippet("rank-features", "rank-features: $0", "rank-features:"),
            CompletionUtils.constructSnippet("rank-properties", "rank-properties {\n\t$0\n}"),
            CompletionUtils.constructSnippet("second-phase", "second-phase {\n\t$0\n}"),
            CompletionUtils.constructSnippet("significance", "significance {\n\tuse-model: ${1|true,false|}\n}"),
            CompletionUtils.constructSnippet("summary-features", "summary-features {\n\t$0\n}", "summary-features {}"),
            CompletionUtils.constructSnippet("summary-features", "summary-features: $0", "summary-features:"),
            CompletionUtils.constructSnippet("target-hits-max-adjustment-factor", "target-hits-max-adjustment-factor: $0"),
            CompletionUtils.constructSnippet("termwise-limit", "termwise-limit: $0"),

            CompletionUtils.constructBasic("ignore-default-rank-features"),
            FixedKeywordBodies.RANK.getColonSnippet(true),
            FixedKeywordBodies.RANK_IN_PROFILE.getBodySnippet(true),
            FixedKeywordBodies.RANK_TYPE.getColonSnippet(true),
            FixedKeywordBodies.WEAKAND.getBodySnippet(false)
        ));

        put(RootRankProfile.class, get(rankProfile.class));


        put(firstPhase.class, List.of(
            CompletionUtils.constructSnippet("expression", "expression: $0", "expression:"),
            CompletionUtils.constructSnippet("expression", "expression {\n\t$0\n}", "expression {}"),
            CompletionUtils.constructSnippet("keep-rank-count", "keep-rank-count: $0"),
            CompletionUtils.constructSnippet("total-keep-rank-count", "total-keep-rank-count: $0"),
            CompletionUtils.constructSnippet("rank-score-drop-limit", "rank-score-drop-limit: $0")
        ));

        put(secondPhase.class, List.of(
            CompletionUtils.constructSnippet("expression", "expression: $0", "expression:"),
            CompletionUtils.constructSnippet("expression", "expression {\n\t$0\n}", "expression {}"),
            CompletionUtils.constructSnippet("rerank-count", "rerank-count: $0"),
            CompletionUtils.constructSnippet("total-rerank-count", "total-rerank-count: $0"),
            CompletionUtils.constructSnippet("rank-score-drop-limit", "rank-score-drop-limit: $0")
        ));

        put(globalPhase.class, List.of(
            CompletionUtils.constructSnippet("expression", "expression: $0", "expression:"),
            CompletionUtils.constructSnippet("expression", "expression {\n\t$0\n}", "expression {}"),
            CompletionUtils.constructSnippet("keep-rank-count", "keep-rank-count: $0"),
            CompletionUtils.constructSnippet("rank-score-drop-limit", "rank-score-drop-limit: $0")
        ));

        put(onnxModel.class, List.of(
            CompletionUtils.constructSnippet("input", "input \"$1\": $0"),
            CompletionUtils.constructSnippet("output", "output \"$1\": $0"),
            CompletionUtils.constructSnippet("gpu-device", "gpu-device: $0"),
            CompletionUtils.constructSnippet("file", "file: $0"),
            CompletionUtils.constructSnippetDeprecated("uri", "uri: $0", "Not supported yet"),
            CompletionUtils.constructSnippet("intraop-threads", "intraop-threads: $0"),
            CompletionUtils.constructSnippet("interop-threads", "interop-threads: $0"),
            FixedKeywordBodies.EXECUTION_MODE.getColonSnippet()
        ));

        put(fieldSetElm.class, List.of(
            FixedKeywordBodies.MATCH.getColonSnippet(),
            FixedKeywordBodies.MATCH.getBodySnippet(),
            CompletionUtils.constructSnippet("query-command", "query-command: ")
        ));

        put(significanceElm.class, List.of(
            CompletionUtils.constructSnippet("use-model", "use-model: ${1|true,false|}")
        ));

        put(FixedKeywordBodies.MATCH.parentASTClass(), FixedKeywordBodies.MATCH.completionItems());

        put(FixedKeywordBodies.RANK.parentASTClass(), FixedKeywordBodies.RANK.completionItems());

        put(FixedKeywordBodies.RANK_IN_PROFILE.parentASTClass(), FixedKeywordBodies.RANK_IN_PROFILE.completionItems());

        put(FixedKeywordBodies.WEAKAND.parentASTClass(), FixedKeywordBodies.WEAKAND.completionItems());

        put(summaryInDocument.class, FixedKeywordBodies.SUMMARY.completionItems());
        put(summaryInFieldLong.class, FixedKeywordBodies.SUMMARY.completionItems());

        put(weightedsetElm.class, FixedKeywordBodies.WEIGHTEDSET.completionItems());

        put(mapElm.class, FixedKeywordBodies.MAP.completionItems());

        put(hnswIndex.class, FixedKeywordBodies.HNSW.completionItems());

        put(dictionaryElm.class, FixedKeywordBodies.DICTIONARY.completionItems());

        put(sortingElm.class, FixedKeywordBodies.SORTING.completionItems());

        put(attributeElm.class, FixedKeywordBodies.ATTRIBUTE.completionItems());

        put(indexInsideField.class, FixedKeywordBodies.INDEX.completionItems());
        put(indexOutsideDoc.class, FixedKeywordBodies.INDEX.completionItems());

        for (var entry : this.entrySet()) {
            withDocumentation(entry.getValue());
        }
    }};

    /**
     * Attaches hover documentation to each completion item, looked up by the item label.
     * Returns the same list to allow use in field initializers.
     */
    private static List<CompletionItem> withDocumentation(List<CompletionItem> items) {
        Path schemaHoverPath = SchemaLanguageServer.getDefaultDocumentationPath().resolve("schema");
        for (CompletionItem item : items) {
            String markdownKey = item.getLabel().toUpperCase(Locale.ROOT).replaceAll("-", "_");
            Optional<Hover> hover = SchemaHover.getFileHoverInformation(schemaHoverPath, markdownKey, new Range());
            if (hover.isPresent() && hover.get().getContents().isRight()) {
                item.setDocumentation(hover.get().getContents().getRight());
            }
        }
        return items;
    }

    /**
     * The config model only accepts 'map: fast-search' on maps whose key and value types are among these,
     * see CreateFastMapSearch and ConvertParsedFields in config-model.
     */
    private static final Set<String> FAST_MAP_SEARCH_KEY_VALUE_TYPES = Set.of("string", "int");

    /** Snippets for the map settings block, only offered in fields where fast map search is allowed. */
    private static final List<CompletionItem> mapFieldSnippets = withDocumentation(List.of(
        FixedKeywordBodies.MAP.getColonSnippet(),
        FixedKeywordBodies.MAP.getBodySnippet()
    ));

    private static boolean isFastMapSearchKeyValueType(ParsedType type) {
        return type != null
            && type.getVariant() == Variant.BUILTIN
            && FAST_MAP_SEARCH_KEY_VALUE_TYPES.contains(type.name());
    }

    /**
     * Returns true if the given field element has a map type on which 'map: fast-search' can be set.
     */
    private static boolean supportsFastMapSearch(Node fieldNode) {
        for (Node child : fieldNode) {
            if (!child.isASTInstance(dataType.class)) {
                continue;
            }
            if (!(child.getSchemaNode().getOriginalSchemaNode() instanceof dataType typeNode)) {
                return false;
            }
            ParsedType type = typeNode.getParsedType();
            if (type == null || type.getVariant() != Variant.MAP) {
                return false;
            }
            return isFastMapSearchKeyValueType(type.mapKeyType()) && isFastMapSearchKeyValueType(type.mapValueType());
        }
        return false;
    }

    private static final String TOKENS_MODE_CHOICE = "${1|original,first-alternative,alternatives,original-and-alternatives|}";

    private static final List<CompletionItem> linguisticsBodySnippets = List.of(
        CompletionUtils.constructSnippet("profile", "profile: $0", "profile:"),
        CompletionUtils.constructSnippet("profile", "profile {\n\tindex: $1\n\tsearch: $0\n}", "profile {}"),
        CompletionUtils.constructSnippet("tokens", "tokens: " + TOKENS_MODE_CHOICE, "tokens:"),
        CompletionUtils.constructSnippet("index", "index {\n\t$0\n}", "index {}"),
        CompletionUtils.constructSnippet("search", "search {\n\t$0\n}", "search {}")
    );

    private static final List<CompletionItem> linguisticsProfileBodySnippets = List.of(
        CompletionUtils.constructSnippet("index", "index: $0"),
        CompletionUtils.constructSnippet("search", "search: $0")
    );

    private static final List<CompletionItem> linguisticsSettingsBodySnippets = List.of(
        CompletionUtils.constructSnippet("profile", "profile: $0"),
        CompletionUtils.constructSnippet("tokens", "tokens: " + TOKENS_MODE_CHOICE)
    );

    /**
     * A linguistics element holds nested bodies, e.g. <code>linguistics { profile { ... } }</code> or
     * <code>linguistics { index { ... } }</code>, which the grammar represents as a single flat node.
     * Which keywords are valid therefore depends on which keyword opened the brace enclosing the cursor
     * (tracked here as a stack), not just the brace depth.
     */
    private static List<CompletionItem> linguisticsCompletionItems(Node linguisticsNode, Position position) {
        Deque<String> blockStack = new ArrayDeque<>();
        String pendingKeyword = null;
        for (Node child : linguisticsNode) {
            if (CSTUtils.positionLT(position, child.getRange().getStart())) break;
            if (child.isASTInstance(PROFILE.class)) pendingKeyword = "profile";
            else if (child.isASTInstance(INDEX.class)) pendingKeyword = "index";
            else if (child.isASTInstance(SEARCH.class)) pendingKeyword = "search";
            else if (child.isASTInstance(openLbrace.class) || child.isASTInstance(LBRACE.class)) {
                // The very first brace is the outer 'linguistics {' body itself, not a keyword-tagged
                // sub-block, and has no preceding PROFILE/INDEX/SEARCH keyword: don't push a context for it.
                if (pendingKeyword != null) blockStack.push(pendingKeyword);
                pendingKeyword = null;
            } else if (child.isASTInstance(RBRACE.class)) {
                if (!blockStack.isEmpty()) blockStack.pop();
            }
        }

        if (blockStack.isEmpty()) return linguisticsBodySnippets;
        if (blockStack.size() == 1) {
            return "profile".equals(blockStack.peek()) ? linguisticsProfileBodySnippets : linguisticsSettingsBodySnippets;
        }
        return List.of();
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;

        Node last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);

        if (last == null) {
            return List.of();
        }

        if (!last.isASTInstance(NL.class)) return List.of();

        Node searchNode = last.getParent();

        if (searchNode == null) return List.of();

        if (searchNode.isASTInstance(openLbrace.class))searchNode = searchNode.getParent();

        if (searchNode.isASTInstance(linguisticsElm.class)) return linguisticsCompletionItems(searchNode, searchPos);

        List<CompletionItem> result = bodyKeywordSnippets.get(searchNode.getASTClass());
        if (result == null) return List.of();

        if (searchNode.isASTInstance(fieldElm.class) && supportsFastMapSearch(searchNode)) {
            List<CompletionItem> withMap = new ArrayList<>(result);
            withMap.addAll(mapFieldSnippets);
            return withMap;
        }
        return result;
    }
}
