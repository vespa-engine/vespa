package com.yahoo.search.query;


import com.google.common.base.Preconditions;
import com.yahoo.collections.LazyMap;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.ONearItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.QueryException;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;

import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.vespa.config.SlimeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.slime.Type.ARRAY;
import static com.yahoo.slime.Type.DOUBLE;
import static com.yahoo.slime.Type.LONG;
import static com.yahoo.slime.Type.OBJECT;
import static com.yahoo.slime.Type.STRING;

public class SelectParser implements Parser {

    Parsable query;
    private final IndexFacts indexFacts;
    private final Map<Integer, TaggableItem> identifiedItems = LazyMap.newHashMap();
    private final Normalizer normalizer;
    private final Segmenter segmenter;
    private final Detector detector;
    private final String localSegmenterBackend;
    private final Version localSegmenterVersion;
    private final ParserEnvironment environment;
    private IndexFacts.Session indexFactsSession;



    /** YQL parameters and functions */

    private static final String DESCENDING_HITS_ORDER = "descending";
    private static final String ASCENDING_HITS_ORDER = "ascending";

    private static final Integer DEFAULT_HITS = 10;
    private static final Integer DEFAULT_OFFSET = 0;
    private static final Integer DEFAULT_TARGET_NUM_HITS = 10;
    static final String ORIGIN_LENGTH = "length";
    static final String ORIGIN_OFFSET = "offset";
    static final String ORIGIN = "origin";
    static final String ORIGIN_ORIGINAL = "original";

    private static final String ANNOTATIONS = "annotations";
    private static final String NFKC = "nfkc";
    private static final String USER_INPUT_LANGUAGE = "language";
    private static final String ACCENT_DROP = "accentDrop";
    private static final String ALTERNATIVES = "alternatives";
    private static final String AND_SEGMENTING = "andSegmenting";
    private static final String DISTANCE = "distance";
    private static final String DOT_PRODUCT = "dotProduct";
    private static final String EQUIV = "equiv";
    private static final String FILTER = "filter";
    private static final String HIT_LIMIT = "hitLimit";
    private static final String IMPLICIT_TRANSFORMS = "implicitTransforms";
    private static final String LABEL = "label";
    private static final String NEAR = "near";
    private static final String NORMALIZE_CASE = "normalizeCase";
    private static final String ONEAR = "onear";
    private static final String PHRASE = "phrase";
    private static final String PREDICATE = "predicate";
    private static final String PREFIX = "prefix";
    private static final String RANKED = "ranked";
    private static final String RANK = "rank";
    private static final String SAME_ELEMENT = "sameElement";
    private static final String SCORE_THRESHOLD = "scoreThreshold";
    private static final String SIGNIFICANCE = "significance";
    private static final String STEM = "stem";
    private static final String SUBSTRING = "substring";
    private static final String SUFFIX = "suffix";
    private static final String TARGET_NUM_HITS = "targetNumHits";
    private static final String THRESHOLD_BOOST_FACTOR = "thresholdBoostFactor";
    private static final String UNIQUE_ID = "id";
    private static final String USE_POSITION_DATA = "usePositionData";
    private static final String WAND = "wand";
    private static final String WEAK_AND = "weakAnd";
    private static final String WEIGHTED_SET = "weightedSet";
    private static final String WEIGHT = "weight";

    /**************************************/

    public SelectParser(ParserEnvironment environment) {
        indexFacts = environment.getIndexFacts();
        normalizer = environment.getLinguistics().getNormalizer();
        segmenter = environment.getLinguistics().getSegmenter();
        detector = environment.getLinguistics().getDetector();
        this.environment = environment;

        Tuple2<String, Version> version = environment.getLinguistics().getVersion(Linguistics.Component.SEGMENTER);
        localSegmenterBackend = version.first;
        localSegmenterVersion = version.second;
    }

    @Override
    public QueryTree parse(Parsable query) {
        indexFactsSession = indexFacts.newSession(query.getSources(), query.getRestrict());
        this.query = query;

        return buildTree();

    }

    public QueryTree buildTree() {
        Inspector inspector = SlimeUtils.jsonToSlime(this.query.getSelect().getBytes()).get();
        if (inspector.field("error_message").valid()){
            throw new QueryException("Illegal query: "+inspector.field("error_message").asString() + ", at: "+ new String(inspector.field("offending_input").asData(), StandardCharsets.UTF_8));
        }

        Item root = walkJson(inspector);
        QueryTree newTree = new QueryTree(root);

        return newTree;
    }
    private static final String AND = "and";
    private static final String AND_NOT = "and_not";
    private static final String OR = "or";
    private static final String EQ = "equals";
    private static final String RANGE = "range";
    private static final String CONTAINS = "contains";
    private static final String MATCHES = "matches";
    private static final String CALL = "call";
    private static final List<String> FUNCTION_CALLS = Arrays.asList(WAND, WEIGHTED_SET, DOT_PRODUCT, PREDICATE, RANK, WEAK_AND);


    public Item walkJson(Inspector inspector){
        final Item[] item = {null};
        inspector.traverse((ObjectTraverser) (key, value) -> {


            String type = (FUNCTION_CALLS.contains(key)) ? CALL : key;

            switch (type) {
                case AND:
                    item[0] = buildAnd(key, value);
                    break;
                case AND_NOT:
                    item[0] = buildNotAnd(key, value);
                    break;
                case OR:
                    item[0] = buildOr(key, value);
                    break;
                case EQ:
                    item[0] = buildEquals(key, value);
                    break;
                case RANGE:
                    item[0] = buildRange(key, value);
                    break;
                case CONTAINS:
                    item[0] = buildTermSearch(key, value);
                    break;
                case MATCHES:
                    item[0] = buildRegExpSearch(key, value);
                    break;
                case CALL:
                    item[0] = buildFunctionCall(key, value);
                    break;
                default:
                    throw newUnexpectedArgumentException(key, AND, CALL, CONTAINS, EQ, OR, RANGE, AND_NOT);
            }


        });
        return item[0];
    }


    @NonNull
    private Item buildFunctionCall(String key, Inspector value) {
        switch (key) {
            case WAND:
                return buildWand(key, value);
            case WEIGHTED_SET:
                return buildWeightedSet(key, value);
            case DOT_PRODUCT:
                return buildDotProduct(key, value);
            case PREDICATE:
                return buildPredicate(key, value);
            case RANK:
                return buildRank(key, value);
            case WEAK_AND:
                return buildWeakAnd(key, value);
            default:
                throw newUnexpectedArgumentException(key, DOT_PRODUCT, RANK, WAND, WEAK_AND, WEIGHTED_SET, PREDICATE);
        }
    }



    private void addItemsFromInspector(CompositeItem item, Inspector inspector){
        if (inspector.type() == ARRAY){
            inspector.traverse((ArrayTraverser) (index, new_value) -> {
                item.addItem(walkJson(new_value));
            });

        } else if (inspector.type() == OBJECT){
            if (inspector.field("children").valid()){
                inspector.field("children").traverse((ArrayTraverser) (index, new_value) -> {
                    item.addItem(walkJson(new_value));
                });
            }

        }
    }

    private Inspector getChildren(Inspector inspector){
        HashMap<Integer, Inspector> children = new HashMap<>();
        if (inspector.type() == ARRAY){
            return inspector;

        } else if (inspector.type() == OBJECT){
            if (inspector.field("children").valid()){
                return inspector.field("children");
            }
        }
        return null;
    }


    private HashMap<Integer, Inspector> getChildrenMap(Inspector inspector){
        HashMap<Integer, Inspector> children = new HashMap<>();
            if (inspector.type() == ARRAY){
                inspector.traverse((ArrayTraverser) (index, new_value) -> {
                    children.put(index, new_value);
                });

            } else if (inspector.type() == OBJECT){
                if (inspector.field("children").valid()){
                    inspector.field("children").traverse((ArrayTraverser) (index, new_value) -> {
                        children.put(index, new_value);
                    });
                }
            }
        return children;
    }

    private Inspector getAnnotations(Inspector inspector){
        if (inspector.type() == OBJECT && inspector.field("attributes").valid()){
            return inspector.field("attributes");
        }
        return null;
    }

    private HashMap<String, Inspector> getAnnotationMapFromAnnotationInspector(Inspector annotation){
        HashMap<String, Inspector> attributes = new HashMap<>();
        if (annotation.type() == OBJECT){
            annotation.traverse((ObjectTraverser) (index, new_value) -> {
                attributes.put(index, new_value);
            });
        }
        return attributes;
    }

    private HashMap<String, Inspector> getAnnotationMap(Inspector inspector){
        HashMap<String, Inspector> attributes = new HashMap<>();
        if (inspector.type() == OBJECT && inspector.field("attributes").valid()){
            inspector.field("attributes").traverse((ObjectTraverser) (index, new_value) -> {
                attributes.put(index, new_value);
            });
        }
        return attributes;
    }

    private <T> T getAnnotation(String annotationName, HashMap<String, Inspector> annotations, Class<T> expectedClass, T defaultValue) {
        return (annotations.get(annotationName) == null) ? defaultValue : expectedClass.cast(annotations.get(annotationName).asString());
    }

    private Inspector getAnnotationAsInspectorOrNull(String annotationName, HashMap<String, Inspector> annotations) {
        return annotations.get(annotationName);
    }

    @NonNull
    private CompositeItem buildAnd(String key, Inspector value) {
        AndItem andItem = new AndItem();
        addItemsFromInspector(andItem, value);

        return andItem;
    }

    @NonNull
    private CompositeItem buildNotAnd(String key, Inspector value) {
        NotItem notItem = new NotItem();
        addItemsFromInspector(notItem, value);

        return notItem;
    }



    @NonNull
    private CompositeItem buildOr(String key, Inspector value) {
        OrItem orItem = new OrItem();
        addItemsFromInspector(orItem, value);
        return orItem;
    }

    @NonNull
    private CompositeItem buildWeakAnd(String key, Inspector value) {
        WeakAndItem weakAnd = new WeakAndItem();
        addItemsFromInspector(weakAnd, value);
        Inspector annotations = getAnnotations(value);

        if (annotations != null){
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (TARGET_NUM_HITS.equals(annotation_name)){
                    weakAnd.setN((Integer.class.cast(annotation_value.asDouble())));
                }
                if (SCORE_THRESHOLD.equals(annotation_name)){
                    weakAnd.setScoreThreshold((Integer.class.cast(annotation_value.asDouble())));
                }
            });
        }

        return weakAnd;
    }

    @NonNull
    private <T extends TaggableItem> T leafStyleSettings(Inspector annotations, @NonNull T out) {
        {
            if (annotations != null) {
                annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                    if (SIGNIFICANCE.equals(annotation_name)) {
                        if (annotation_value != null) {
                            out.setSignificance(annotation_value.asDouble());
                        }
                    }
                    if (UNIQUE_ID.equals(annotation_name)) {
                        if (annotation_value != null) {
                            out.setUniqueID(Integer.class.cast(annotation_name));
                            identifiedItems.put(Integer.class.cast(annotation_name), out);
                        }
                    }
                });
            }
        }
        {
            Item leaf = (Item) out;
            if (annotations != null) {
                Inspector itemAnnotations = getAnnotationAsInspectorOrNull(ANNOTATIONS, getAnnotationMapFromAnnotationInspector(annotations));
                if (itemAnnotations != null) {
                    itemAnnotations.traverse((ObjectTraverser) (key, value) -> {
                        leaf.addAnnotation(key, value.asString());
                    });
                }

                annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                    if (FILTER.equals(annotation_name)) {
                        if (annotation_value != null) {
                            leaf.setFilter(annotation_value.asBool());
                        }
                    }
                    if (RANKED.equals(annotation_name)) {
                        if (annotation_value != null) {
                            leaf.setRanked(annotation_value.asBool());
                        }
                    }
                    if (LABEL.equals(annotation_name)) {
                        if (annotation_value != null) {
                            leaf.setLabel(annotation_value.asString());
                        }
                    }
                    if (WEIGHT.equals(annotation_name)) {
                        if (annotation_value != null) {
                            leaf.setWeight((int)annotation_value.asDouble());
                        }
                    }
                });
            }
            if (out instanceof IntItem && annotations != null) {
                IntItem number = (IntItem) out;
                Integer hitLimit = getCappedRangeSearchParameter(annotations);
                if (hitLimit != null) {
                    number.setHitLimit(hitLimit);
                }

            }
        }

        return out;
    }


    private Integer getCappedRangeSearchParameter(Inspector annotations) {
        final Integer[] hitLimit = {null};
        annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
            if (HIT_LIMIT.equals(annotation_name)) {
                if (annotation_value != null) {
                    hitLimit[0] = Integer.class.cast(annotation_value);
                }
            }
        });
        final Boolean[] ascending = {null};
        final Boolean[] descending = {null};

        if (hitLimit[0] != null) {
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (ASCENDING_HITS_ORDER.equals(annotation_name)) {
                    ascending[0] = Boolean.class.cast(annotation_value);
                }
                if (DESCENDING_HITS_ORDER.equals(annotation_name)) {
                    descending[0] = Boolean.class.cast(annotation_value);
                }

            });
            Preconditions.checkArgument(ascending[0] == null || descending[0] == null,
                    "Settings for both ascending and descending ordering set, only one of these expected.");

            if (Boolean.TRUE.equals(descending[0]) || Boolean.FALSE.equals(ascending[0])) {
                hitLimit[0] = hitLimit[0] * -1;
            }
        }
        return hitLimit[0];
    }



    @NonNull
    private Item buildRange(String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        Inspector annotations = getAnnotations(value);

        final boolean[] equals = {false};

        String field;
        Inspector boundInspector;
        if (children.get(0).type() == STRING){
            field = children.get(0).asString();
            boundInspector = children.get(1);
        } else {
            field = children.get(1).asString();
            boundInspector = children.get(0);
        }

        final Number[] bounds = {null, null};
        final String[] operators = {null, null};
        boundInspector.traverse((ObjectTraverser) (operator, bound) -> {
            if (operator.equals("=")) {
                bounds[0] = Number.class.cast(bound.asLong());
                operators[0] = operator;
                equals[0] = true;
            }
            if (operator.equals(">=") || operator.equals(">")){
                bounds[0] = Number.class.cast(bound.asLong());
                operators[0] = operator;
            } else if (operator.equals("<=") || operator.equals("<")){
                bounds[1] = Number.class.cast(bound.asLong());
                operators[1] = operator;
            }

        });
        IntItem range = null;
        if (equals[0]){
            range = new IntItem(bounds[0].toString(), field);
        } else if (operators[0]==null || operators[1]==null){
            Integer index = (operators[0] == null) ? 1 : 0;
            switch (operators[index]){
                case ">=":
                    range = buildGreaterThanOrEquals(field, bounds[index].toString());
                    break;
                case ">":
                    range = buildGreaterThan(field, bounds[index].toString());
                    break;
                case "<":
                    range = buildLessThan(field, bounds[index].toString());
                    break;
                case "<=":
                    range = buildLessThanOrEquals(field, bounds[index].toString());
                    break;
            }
        }
        else {
            range = instantiateRangeItem(bounds[0], bounds[1], field, operators[0].equals(">"), operators[1].equals("<"));
        }

        return leafStyleSettings(annotations, range);
    }

    @NonNull
    private IntItem buildGreaterThanOrEquals(String field, String bound) {
        return new IntItem("[" + bound + ";]", field);

    }

    @NonNull
    private IntItem buildLessThanOrEquals(String field, String bound) {
        return new IntItem("[;" + bound + "]", field);
    }

    @NonNull
    private IntItem buildGreaterThan(String field, String bound) {
        return new IntItem(">" + bound, field);

    }

    @NonNull
    private IntItem buildLessThan(String field, String bound) {
        return new IntItem("<" + bound, field);
    }

    /*
    @NonNull
    private IntItem buildEquals(OperatorNode<ExpressionOperator> ast) {
        IntItem number = new IntItem(fetchConditionWord(ast), fetchConditionIndex(ast));
        if (isIndexOnLeftHandSide(ast)) {
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), number);
        } else {
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), number);
        }
    }*/

    @NonNull
    private IntItem instantiateRangeItem(Number lowerBound, Number upperBound, String field, boolean bounds_left_open, boolean bounds_right_open) {
        Preconditions.checkArgument(lowerBound != null && upperBound != null && field != null,
                "Expected 3 NonNull-arguments");

        if (!bounds_left_open && !bounds_right_open) {
            return new RangeItem(lowerBound, upperBound, field);
        } else {
            Limit from;
            Limit to;
            if (bounds_left_open && bounds_right_open) {
                from = new Limit(lowerBound, false);
                to = new Limit(upperBound, false);
            } else if (bounds_left_open) {
                from = new Limit(lowerBound, false);
                to = new Limit(upperBound, true);
            } else {
                from = new Limit(lowerBound, true);
                to = new Limit(upperBound, false);
            }
            return new IntItem(from, to, field);
        }
    }


    @NonNull
    private Item buildEquals(String key, Inspector value) {
         return buildRange(key, value);
    }


    @NonNull
    private Item buildWand(String key, Inspector value) {
        HashMap<String, Inspector> annotations = getAnnotationMap(value);
        HashMap<Integer, Inspector> children = getChildrenMap(value);

        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        Integer target_num_hits= getAnnotation(TARGET_NUM_HITS, annotations, Integer.class, DEFAULT_TARGET_NUM_HITS);

        WandItem out = new WandItem(children.get(0).asString(), target_num_hits);

        Double scoreThreshold = getAnnotation(SCORE_THRESHOLD, annotations, Double.class, null);
        if (scoreThreshold != null) {
            out.setScoreThreshold(scoreThreshold);
        }

        Double thresholdBoostFactor = getAnnotation(THRESHOLD_BOOST_FACTOR, annotations, Double.class, null);
        if (thresholdBoostFactor != null) {
            out.setThresholdBoostFactor(thresholdBoostFactor);
        }
        return fillWeightedSet(value, children, out);
    }

    @NonNull
    private WeightedSetItem fillWeightedSet(Inspector value, HashMap<Integer, Inspector> children, @NonNull WeightedSetItem out) {
        addItems(children, out);

        return leafStyleSettings(getAnnotations(value), out);
    }


    private static void addItems(HashMap<Integer, Inspector> children, WeightedSetItem out) {
        switch (children.get(1).type()) {
            case OBJECT:
                addStringItems(children, out);
                break;
            case ARRAY:
                addLongItems(children, out);
                break;
            default:
                throw newUnexpectedArgumentException(children.get(1).type(), ARRAY, OBJECT);
        }
    }

    private static void addStringItems(HashMap<Integer, Inspector> children, WeightedSetItem out) {
        //{"a":1, "b":2}
        children.get(1).traverse((ObjectTraverser) (key, value) -> out.addToken(key, Integer.class.cast(value.asLong())));
    }

    private static void addLongItems(HashMap<Integer, Inspector> children, WeightedSetItem out) {
        //[[11,1], [37,2]]
        children.get(1).traverse((ArrayTraverser) (index, pair) -> {
            List<Integer> pairValues = new ArrayList<>();
            pair.traverse((ArrayTraverser) (pairIndex, pairValue) -> {
                pairValues.add(Integer.class.cast(pairValue.asLong()));
            });
            Preconditions.checkArgument(pairValues.size() == 2,
                    "Expected item and weight, got %s.", pairValues);
            out.addToken(pairValues.get(0).longValue(), pairValues.get(1));
        });
    }


    @NonNull
    private Item buildRegExpSearch(String key, Inspector value) {
        assertHasOperator(key, MATCHES);
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        String field = children.get(0).asString();
        String wordData = children.get(1).asString();
        RegExpItem regExp = new RegExpItem(field, true, wordData);
        return leafStyleSettings(getAnnotations(value), regExp);
    }

    @NonNull
    private Item buildWeightedSet(String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        String field = children.get(0).asString();
        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        return fillWeightedSet(value, children, new WeightedSetItem(field));
    }

    @NonNull
    private Item buildDotProduct(String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        String field = children.get(0).asString();
        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        return fillWeightedSet(value, children, new DotProductItem(field));
    }

    @NonNull
    private Item buildPredicate(String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        String field = children.get(0).asString();
        Inspector args = children.get(1);

        Preconditions.checkArgument(children.size() == 3, "Expected 3 arguments, got %s.", children.size());

        PredicateQueryItem item = new PredicateQueryItem();
        item.setIndexName(field);

        List<Inspector> argumentList = valueListFromInspector(getChildren(value));

        // Adding attributes
        argumentList.get(1).traverse((ObjectTraverser) (attrKey, attrValue) -> item.addFeature(attrKey, attrValue.asString()));

        // Adding range attributes
        argumentList.get(2).traverse((ObjectTraverser) (attrKey, attrValue) -> item.addRangeFeature(attrKey, Integer.class.cast(attrValue.asDouble())));

        return leafStyleSettings(getAnnotations(value), item);
    }


    @NonNull
    private CompositeItem buildRank(String key, Inspector value) {
        RankItem rankItem = new RankItem();
        addItemsFromInspector(rankItem, value);
        return rankItem;
    }

    @NonNull
    private Item buildTermSearch(String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        String field = children.get(0).asString();

        return instantiateLeafItem(field, key, value);
    }
    @NonNull
    private Item instantiateLeafItem(String field, String key, Inspector value) {
        if (CALL.contains(key)) {
            return instantiateCompositeLeaf(field, key, value);
        } else {
            return instantiateWordItem(field, key, value);
        }
    }

    @NonNull
    private Item instantiateCompositeLeaf(String field, String key, Inspector value) {
        switch (key) {
            case SAME_ELEMENT:
                return instantiateSameElementItem(field, key, value);
            case PHRASE:
                return instantiatePhraseItem(field, key, value);
            case NEAR:
                return instantiateNearItem(field, key, value);
            case ONEAR:
                return instantiateONearItem(field, key, value);
            case EQUIV:
                return instantiateEquivItem(field, key, value);
            case ALTERNATIVES:
                return instantiateWordAlternativesItem(field, key, value);
            default:
                throw newUnexpectedArgumentException(key, EQUIV, NEAR, ONEAR, PHRASE, SAME_ELEMENT);
        }
    }


    @NonNull
    private Item instantiateWordItem(String field, String key, Inspector value) {
        String wordData = getChildrenMap(value).get(1).asString();
        return instantiateWordItem(field, wordData, key, value, false, decideParsingLanguage(value, wordData));
    }

    @NonNull
    private Item instantiateWordItem(String field, String rawWord, String key, Inspector value, boolean exactMatch, Language language) {
        String wordData = rawWord;
        HashMap<String, Inspector> annotations = getAnnotationMap(value);

        if (getAnnotation(NFKC, annotations, Boolean.class, Boolean.FALSE)) {
            // NOTE: If this is set to FALSE (default), we will still NFKC normalize text data
            // during tokenization/segmentation, as that is always turned on also on the indexing side.
            wordData = normalizer.normalize(wordData);
        }
        boolean fromQuery = getAnnotation(IMPLICIT_TRANSFORMS, annotations, Boolean.class, Boolean.TRUE);
        boolean prefixMatch = getAnnotation(PREFIX, annotations, Boolean.class, Boolean.FALSE);
        boolean suffixMatch = getAnnotation(SUFFIX, annotations, Boolean.class, Boolean.FALSE);
        boolean substrMatch = getAnnotation(SUBSTRING,annotations, Boolean.class, Boolean.FALSE);

        Preconditions.checkArgument((prefixMatch ? 1 : 0)
                        + (substrMatch ? 1 : 0) + (suffixMatch ? 1 : 0) < 2,
                "Only one of prefix, substring and suffix can be set.");
        @NonNull
        final TaggableItem wordItem;

        if (exactMatch) {
            wordItem = new ExactStringItem(wordData, fromQuery);
        } else if (prefixMatch) {
            wordItem = new PrefixItem(wordData, fromQuery);
        } else if (suffixMatch) {
            wordItem = new SuffixItem(wordData, fromQuery);
        } else if (substrMatch) {
            wordItem = new SubstringItem(wordData, fromQuery);
        } else {
            wordItem = new WordItem(wordData, fromQuery);
        }

        if (wordItem instanceof WordItem) {
            prepareWord(field, value, (WordItem) wordItem);
        }
        if (language != Language.ENGLISH) // mark the language used, unless it's the default
            ((Item)wordItem).setLanguage(language);

        return (Item) leafStyleSettings(getAnnotations(value), wordItem);
    }


    private Language decideParsingLanguage(Inspector value, String wordData) {
        String languageTag = getAnnotation(USER_INPUT_LANGUAGE, getAnnotationMap(value), String.class, null);

        Language language = Language.fromLanguageTag(languageTag);
        if (language != Language.UNKNOWN) return language;

        Optional<Language> explicitLanguage = query.getExplicitLanguage();
        if (explicitLanguage.isPresent()) return explicitLanguage.get();

        return Language.ENGLISH;
    }


    private void prepareWord(String field, Inspector value, WordItem wordItem) {
        wordItem.setIndexName(field);
        wordStyleSettings(value, wordItem);
    }

    private void wordStyleSettings(Inspector value, WordItem out) {
        HashMap<String, Inspector> annotations = getAnnotationMap(value);

        Substring origin = getOrigin(getAnnotations(value));
        if (origin != null) {
            out.setOrigin(origin);
        }

        Boolean usePositionData = Boolean.getBoolean(getAnnotation(USE_POSITION_DATA, annotations, String.class, null));
        if (usePositionData != null) {
            out.setPositionData(usePositionData);
        }
        Boolean stem = Boolean.getBoolean(getAnnotation(STEM, annotations, String.class, null));
        if (stem != null) {
            out.setStemmed(!stem);
        }
        Boolean normalizeCase = Boolean.getBoolean(getAnnotation(NORMALIZE_CASE, annotations, String.class, null));
        if (normalizeCase != null) {
            out.setLowercased(!normalizeCase);
        }
        Boolean accentDrop = Boolean.getBoolean(getAnnotation(ACCENT_DROP, annotations, String.class, null));
        if (accentDrop != null) {
            out.setNormalizable(accentDrop);
        }
        Boolean andSegmenting = Boolean.getBoolean(getAnnotation(AND_SEGMENTING, annotations, String.class, null));
        if (andSegmenting != null) {
            if (andSegmenting) {
                out.setSegmentingRule(SegmentingRule.BOOLEAN_AND);
            } else {
                out.setSegmentingRule(SegmentingRule.PHRASE);
            }
        }
    }

    private Substring getOrigin(Inspector annotations) {
        if (annotations != null) {
            Inspector origin = getAnnotationAsInspectorOrNull(ORIGIN, getAnnotationMapFromAnnotationInspector(annotations));
            if (origin == null) {
                return null;
            }
            final String[] original = {null};
            final Integer[] offset = {null};
            final Integer[] length = {null};

            origin.traverse((ObjectTraverser) (key, value) -> {
                switch (key) {
                    case (ORIGIN_ORIGINAL):
                        original[0] = value.asString();
                        break;
                    case (ORIGIN_OFFSET):
                        offset[0] = (int) value.asDouble();
                        break;
                    case (ORIGIN_LENGTH):
                        length[0] = (int) value.asDouble();
                        break;
                }


            });
            return new Substring(offset[0], length[0] + offset[0], original[0]);
        }
        return null;
    }

    @NonNull
    private Item instantiateSameElementItem(String field, String key, Inspector value) {
        assertHasOperator(key, SAME_ELEMENT);

        SameElementItem sameElement = new SameElementItem(field);
        // All terms below sameElement are relative to this.
        getChildren(value).traverse((ArrayTraverser) (index, term) -> {
            sameElement.addItem(walkJson(term));
        });

        return sameElement;
    }

    @NonNull
    private Item instantiatePhraseItem(String field, String key, Inspector value) {
        assertHasOperator(key, PHRASE);
        HashMap<String, Inspector> annotations = getAnnotationMap(value);

        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName(field);
        HashMap<Integer, Inspector> children = getChildrenMap(value);

        for (Inspector word :  children.values()){
            phrase.addItem(instantiateWordItem(word.asString(), key, value));

        }
        return leafStyleSettings(getAnnotations(value), phrase);
    }



    @NonNull
    private Item instantiateNearItem(String field, String key, Inspector value) {
        assertHasOperator(key, NEAR);

        NearItem near = new NearItem();
        near.setIndexName(field);

        HashMap<Integer, Inspector> children = getChildrenMap(value);

        for (Inspector word :  children.values()){
            near.addItem(instantiateWordItem(word.asString(), key, value));
        }

        Integer distance = getAnnotation(DISTANCE, getAnnotationMap(value), Integer.class, null);

        if (distance != null) {
            near.setDistance(distance);
        }
        return near;
    }

    @NonNull
    private Item instantiateONearItem(String field, String key, Inspector value) {
        assertHasOperator(key, ONEAR);

        NearItem onear = new ONearItem();
        onear.setIndexName(field);
        HashMap<Integer, Inspector> children = getChildrenMap(value);

        for (Inspector word :  children.values()){
            onear.addItem(instantiateWordItem(word.asString(), key, value));
        }

        Integer distance = getAnnotation(DISTANCE, getAnnotationMap(value), Integer.class, null);
        if (distance != null) {
            onear.setDistance(distance);
        }
        return onear;
    }


    @NonNull
    private Item instantiateEquivItem(String field, String key, Inspector value) {

        HashMap<Integer, Inspector> children = getChildrenMap(value);
        Preconditions.checkArgument(children.size() >= 2, "Expected 2 or more arguments, got %s.", children.size());

        EquivItem equiv = new EquivItem();
        equiv.setIndexName(field);

        for (Inspector word :  children.values()){
            if (word.type() == STRING || word.type() == LONG || word.type() == DOUBLE){
                equiv.addItem(instantiateWordItem(word.asString(), key, value));
            }
            if (word.type() == OBJECT){
                word.traverse((ObjectTraverser) (key2, value2) -> {
                    assertHasOperator(key2, PHRASE);
                    equiv.addItem(instantiatePhraseItem(word.asString(), key, value2));
                });
            }
        }

        return leafStyleSettings(getAnnotations(value), equiv);
    }

    private Item instantiateWordAlternativesItem(String field, String key, Inspector value) {
        HashMap<Integer, Inspector> children = getChildrenMap(value);
        Preconditions.checkArgument(children.size() >= 1, "Expected 1 or more arguments, got %s.", children.size());
        Preconditions.checkArgument(children.get(0).type() == OBJECT, "Expected OBJECT, got %s.", children.get(0).type());

        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();

        children.get(0).traverse((ObjectTraverser) (keys, values) -> {
            terms.add(new WordAlternativesItem.Alternative(keys, values.asDouble()));
        });
        return leafStyleSettings(getAnnotations(value), new WordAlternativesItem(field, Boolean.TRUE, null, terms));
    }





    @NonNull
    private String getIndex(String field) {
        Preconditions.checkArgument(indexFactsSession.isIndex(field), "Field '%s' does not exist.", field);
        //return indexFactsSession.getCanonicName(field);
        return field;
    }



    private static void assertHasOperator(String key, String expectedKey) {
        Preconditions.checkArgument(key.equals(expectedKey), "Expected operator class %s, got %s.", expectedKey, key);
    }


    private static IllegalArgumentException newUnexpectedArgumentException(Object actual, Object... expected) {
        StringBuilder out = new StringBuilder("Expected ");
        for (int i = 0, len = expected.length; i < len; ++i) {
            out.append(expected[i]);
            if (i < len - 2) {
                out.append(", ");
            } else if (i < len - 1) {
                out.append(" or ");
            }
        }
        out.append(", got ").append(actual).append(".");
        return new IllegalArgumentException(out.toString());
    }

    private List<Inspector> valueListFromInspector(Inspector inspector){
        List<Inspector> inspectorList = new ArrayList<>();
        inspector.traverse((ArrayTraverser) (key, value) -> inspectorList.add(value));
        return inspectorList;
    }


}
