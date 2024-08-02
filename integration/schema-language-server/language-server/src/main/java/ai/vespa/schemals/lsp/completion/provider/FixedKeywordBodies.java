package ai.vespa.schemals.lsp.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ai.vespa.schemals.common.LocaleList;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.attributeElm;
import ai.vespa.schemals.parser.ast.attributeSetting;
import ai.vespa.schemals.parser.ast.dictionarySetting;
import ai.vespa.schemals.parser.ast.fieldStemming;
import ai.vespa.schemals.parser.ast.hnswIndex;
import ai.vespa.schemals.parser.ast.indexInsideField;
import ai.vespa.schemals.parser.ast.matchSettingsElm;
import ai.vespa.schemals.parser.ast.normalizingElm;
import ai.vespa.schemals.parser.ast.rankElm;
import ai.vespa.schemals.parser.ast.rankTypeElm;
import ai.vespa.schemals.parser.ast.sortingElm;
import ai.vespa.schemals.parser.ast.sortingSetting;
import ai.vespa.schemals.parser.ast.summaryInField;
import ai.vespa.schemals.parser.ast.weightedsetElm;

/**
 * FixedKeywordBodies
 * A lot of constructs in the schema language consist of some keyword, optionally followed by an identifier and then either : or {}, where the options 
 * inside are limited and static. For-example if you write match: ..., there is a fixed set of possible words to write after the colon.
 * This class contains the common elements among such constructs, making it possible to generate many completion items with the same code.
 */
public class FixedKeywordBodies {
    public static record FixedKeywordBody(String name, TokenType tokenType, Class<? extends Node> parentASTClass, List<CompletionItem> completionItems) {
        public String getChoiceTemplate(final boolean excludeSnippets) {
            return String.join(",", completionItems.stream()
                .filter(item -> !excludeSnippets || item.getKind() != CompletionItemKind.Snippet)
                .map(item -> item.getLabel())
                .toList());
        }

        public boolean hasSnippets() {
            return completionItems.stream().filter(item -> item.getKind() == CompletionItemKind.Snippet).count() > 0;
        }

        public String getChoiceTemplate() {
            return getChoiceTemplate(false);
        }

        public CompletionItem getColonSnippet(final boolean hasAdditionalSpec) {
            String snippetContent = name + (hasAdditionalSpec ? " $1: " : ": ");
            if (!hasSnippets()) {
                snippetContent += "${" + (hasAdditionalSpec ? "2|" : "1|") + getChoiceTemplate() + "|}";
            }
            return CompletionUtils.constructSnippet(name, snippetContent, name + ":");
        }

        public CompletionItem getColonSnippet() { return getColonSnippet(false); }

        public CompletionItem getBodySnippet(final boolean hasAdditionalSpec) {
            String snippetContent = name + (hasAdditionalSpec ? " $1 {\n\t" : " {\n\t");
            if (!hasSnippets()) {
                snippetContent += "${" + (hasAdditionalSpec ? "2|" : "1|") + getChoiceTemplate() + "|}";
            }
            snippetContent += "$0\n";
            snippetContent += "}";
            return CompletionUtils.constructSnippet(name, snippetContent, name + " {}");
        }

        public CompletionItem getBodySnippet() {
            return getBodySnippet(false);
        }
    }

    public static FixedKeywordBody MATCH = new FixedKeywordBody("match", TokenType.MATCH, matchSettingsElm.class, List.of(
        CompletionUtils.constructBasic("text"), 
        CompletionUtils.constructBasic("word"), 
        CompletionUtils.constructBasic("exact"), 
        CompletionUtils.constructBasic("gram"), 
        CompletionUtils.constructBasic("cased"), 
        CompletionUtils.constructBasic("uncased"), 
        CompletionUtils.constructBasic("prefix"), 
        CompletionUtils.constructBasic("substring"), 
        CompletionUtils.constructBasic("suffix")
    ));

    public static FixedKeywordBody RANK = new FixedKeywordBody("rank", TokenType.RANK, rankElm.class, List.of(
        CompletionUtils.constructBasic("filter"),
        CompletionUtils.constructBasic("literal"),
        CompletionUtils.constructBasic("normal")
    ));

    public static FixedKeywordBody RANK_TYPE = new FixedKeywordBody("rank-type", TokenType.RANK_TYPE, rankTypeElm.class, List.of(
        CompletionUtils.constructBasic("identity"),
        CompletionUtils.constructBasic("about"),
        CompletionUtils.constructBasic("tags"),
        CompletionUtils.constructBasic("empty")
    ));

    public static FixedKeywordBody SUMMARY = new FixedKeywordBody("summary", TokenType.SUMMARY, summaryInField.class, List.of(
        CompletionUtils.constructBasic("full"),
        CompletionUtils.constructSnippet("bolding", "bolding: ${1|on,off|}"),
        CompletionUtils.constructBasic("dynamic"),
        CompletionUtils.constructSnippet("source", "source: "),
        CompletionUtils.constructSnippet("to", "to: "),
        CompletionUtils.constructBasic("matched-elements-only"),
        CompletionUtils.constructBasic("tokens")
    ));

    public static FixedKeywordBody WEIGHTEDSET = new FixedKeywordBody("weightedset", TokenType.WEIGHTEDSET, weightedsetElm.class, List.of(
        CompletionUtils.constructBasic("create-if-nonexistent"),
        CompletionUtils.constructBasic("remove-if-zero")
    ));

    public static FixedKeywordBody HNSW = new FixedKeywordBody("hnsw", TokenType.HNSW, hnswIndex.class, List.of(
        CompletionUtils.constructSnippet("max-links-per-node", "max-links-per-node: "),
        CompletionUtils.constructSnippet("neighbors-to-explore-at-insert", "neighbors-to-explore-at-insert: ")
    ));

    public static FixedKeywordBody STEMMING = new FixedKeywordBody("stemming", TokenType.STEMMING, fieldStemming.class, List.of(
        CompletionUtils.constructBasic("none"),
        CompletionUtils.constructBasic("best"),
        CompletionUtils.constructBasic("shortest"),
        CompletionUtils.constructBasic("multiple")
    ));

    public static FixedKeywordBody NORMALIZING = new FixedKeywordBody("normalizing", TokenType.NORMALIZING, normalizingElm.class, List.of(
        CompletionUtils.constructBasic("none")
    ));

    public static FixedKeywordBody DICTIONARY = new FixedKeywordBody("dictionary", TokenType.DICTIONARY, dictionarySetting.class, List.of(
        CompletionUtils.constructBasic("btree"),
        CompletionUtils.constructBasic("hash"),
        CompletionUtils.constructBasic("cased"),
        CompletionUtils.constructBasic("uncased")
    ));

    public static FixedKeywordBody SORT_FUNCTION = new FixedKeywordBody("function", TokenType.FUNCTION, sortingSetting.class, List.of(
        CompletionUtils.constructBasic("uca"),
        CompletionUtils.constructBasic("lowercase"),
        CompletionUtils.constructBasic("raw")
    ));

    public static FixedKeywordBody SORT_STRENGTH = new FixedKeywordBody("strength", TokenType.STRENGTH, sortingSetting.class, List.of(
        CompletionUtils.constructBasic("primary"),    
        CompletionUtils.constructBasic("secondary"),
        CompletionUtils.constructBasic("tertiary"),
        CompletionUtils.constructBasic("quaternary"),
        CompletionUtils.constructBasic("identical")
    ));

    public static FixedKeywordBody SORT_LOCALE = new FixedKeywordBody("locale", TokenType.LOCALE, sortingSetting.class, 
        LocaleList.locales.stream().map(locale -> CompletionUtils.constructBasic(locale)).toList()
    );

    public static FixedKeywordBody SORTING = new FixedKeywordBody("sorting", TokenType.SORTING, sortingElm.class, List.of(
        CompletionUtils.constructBasic("ascending"),
        CompletionUtils.constructBasic("descending"),
        SORT_FUNCTION.getColonSnippet(),
        SORT_STRENGTH.getColonSnippet(),
        SORT_LOCALE.getColonSnippet()
    ));

    public static FixedKeywordBody DISTANCE_METRIC = new FixedKeywordBody("distance-metric", TokenType.DISTANCE_METRIC, attributeSetting.class, List.of(
        CompletionUtils.constructBasic("euclidean"),
        CompletionUtils.constructBasic("angular"),
        CompletionUtils.constructBasic("dotproduct"),
        CompletionUtils.constructBasic("prenormalized-angular"),
        CompletionUtils.constructBasic("geodegrees"),
        CompletionUtils.constructBasic("hamming")
    ));

    public static FixedKeywordBody ATTRIBUTE = new FixedKeywordBody("attribute", TokenType.ATTRIBUTE, attributeElm.class, List.of(
        CompletionUtils.constructBasic("fast-search"),
        CompletionUtils.constructBasic("fast-access"),
        CompletionUtils.constructBasic("fast-rank"),
        CompletionUtils.constructBasic("paged"),
        SORTING.getColonSnippet(),
        SORTING.getBodySnippet(),
        DISTANCE_METRIC.getColonSnippet(),
        CompletionUtils.constructBasic("mutable")
    ));

    public static FixedKeywordBody INDEX = new FixedKeywordBody("index", TokenType.INDEX, indexInsideField.class, List.of(
        STEMMING.getColonSnippet(),
        CompletionUtils.constructSnippet("arity", "arity: "),
        CompletionUtils.constructSnippet("lower-bound", "lower-bound: "),
        CompletionUtils.constructSnippet("upper-bound", "upper-bound: "),
        CompletionUtils.constructSnippet("dense-posting-list-threshold", "dense-posting-list-threshold: "),
        CompletionUtils.constructBasic("enable-bm25"),
        HNSW.getBodySnippet()
    ));
}
