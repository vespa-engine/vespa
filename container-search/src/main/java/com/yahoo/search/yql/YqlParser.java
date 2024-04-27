// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Supplier;

import com.yahoo.api.annotations.Beta;
import com.google.common.base.Preconditions;
import com.yahoo.collections.LazyMap;
import com.yahoo.collections.LazySet;
import com.yahoo.geo.DistanceParser;
import com.yahoo.geo.ParsedDegree;
import com.yahoo.language.Language;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.Segmenter;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.Location;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.BoolItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.FalseItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.GeoLocationItem;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.NumericInItem;
import com.yahoo.prelude.query.ONearItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.SegmentItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.StringInItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.prelude.query.ToolBox.QueryVisitor;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.prelude.query.UriItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.Sorting.AttributeSorter;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.query.Sorting.LowerCaseSorter;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.Sorting.RawSorter;
import com.yahoo.search.query.Sorting.UcaSorter;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;

/**
 * The YQL query language.
 *
 * This class <em>must</em> be kept in lockstep with {@link VespaSerializer}.
 * Adding anything here will usually require a corresponding addition in
 * VespaSerializer.
 *
 * @author Steinar Knutsen
 * @author Stian Kristoffersen
 * @author Simon Thoresen Hult
 */
public class YqlParser implements Parser {

    public static final String DESCENDING_HITS_ORDER = "descending";
    public static final String ASCENDING_HITS_ORDER = "ascending";

    private enum SegmentWhen {
        NEVER, POSSIBLY, ALWAYS
    }

    private static class IndexNameExpander {
        public String expand(String leaf) { return leaf; }
    }

    private static final Integer DEFAULT_HITS = 10;
    private static final Integer DEFAULT_OFFSET = 0;
    public static final Integer DEFAULT_TARGET_NUM_HITS = 10;
    private static final String ACCENT_DROP_DESCRIPTION = "setting for whether to remove accents if field implies it";
    public static final String ANNOTATIONS = "annotations";
    private static final String FILTER_DESCRIPTION = "term filter setting";
    private static final String IMPLICIT_TRANSFORMS_DESCRIPTION = "setting for whether built-in query transformers should touch the term";
    public static final String NFKC = "nfkc";
    private static final String NORMALIZE_CASE_DESCRIPTION = "setting for whether to do case normalization if field implies it";
    private static final String ORIGIN_DESCRIPTION = "string origin for a term";
    private static final String RANKED_DESCRIPTION = "setting for whether to use term for ranking";
    private static final String STEM_DESCRIPTION = "setting for whether to use stem if field implies it";
    private static final String USE_POSITION_DATA_DESCRIPTION = "setting for whether to use position data for ranking this item";
    private static final String MAX_EDIT_DISTANCE_DESCRIPTION = "setting for an inclusive upper bound for a fuzzy edit-distance search";
    private static final String PREFIX_LENGTH_DESCRIPTION = "setting for a prefix length that is considered frozen for a fuzzy search";
    private static final String USER_INPUT_ALLOW_EMPTY = "allowEmpty";
    private static final String USER_INPUT_DEFAULT_INDEX = "defaultIndex";
    private static final String USER_INPUT_GRAMMAR = "grammar";
    public static final String USER_INPUT_LANGUAGE = "language";
    private static final String USER_INPUT_GRAMMAR_RAW = "raw";
    private static final String USER_INPUT_GRAMMAR_SEGMENT = "segment";
    private static final Set<String> WEAKAND_GRAMMARS = Set.of("weakAnd", "tokenize");
    private static final String USER_INPUT = "userInput";
    private static final String USER_QUERY = "userQuery";
    private static final String NON_EMPTY = "nonEmpty";
    public static final String START_ANCHOR = "startAnchor";
    public static final String END_ANCHOR = "endAnchor";

    public static final String SORTING_FUNCTION = "function";
    public static final String SORTING_LOCALE = "locale";
    public static final String SORTING_STRENGTH = "strength";

    public static final String ACCENT_DROP = "accentDrop";
    public static final String ALTERNATIVES = "alternatives";
    public static final String AND_SEGMENTING = "andSegmenting";
    public static final String APPROXIMATE = "approximate";
    public static final String BOUNDS = "bounds";
    public static final String BOUNDS_LEFT_OPEN = "leftOpen";
    public static final String BOUNDS_OPEN = "open";
    public static final String BOUNDS_RIGHT_OPEN = "rightOpen";
    public static final String CONNECTION_ID = "id";
    public static final String CONNECTION_WEIGHT = "weight";
    public static final String CONNECTIVITY = "connectivity";
    public static final String DISTANCE = "distance";
    public static final String DISTANCE_THRESHOLD = "distanceThreshold";
    public static final String DOT_PRODUCT = "dotProduct";
    public static final String EQUIV = "equiv";
    public static final String FILTER = "filter";
    public static final String GEO_LOCATION = "geoLocation";
    public static final String HIT_LIMIT = "hitLimit";
    public static final String HNSW_EXPLORE_ADDITIONAL_HITS = "hnsw.exploreAdditionalHits";
    public static final String IMPLICIT_TRANSFORMS = "implicitTransforms";
    public static final String LABEL = "label";
    public static final String NEAR = "near";
    public static final String NEAREST_NEIGHBOR = "nearestNeighbor";
    public static final String NORMALIZE_CASE = "normalizeCase";
    public static final String ONEAR = "onear";
    public static final String ORIGIN = "origin";
    public static final String ORIGIN_LENGTH = "length";
    public static final String ORIGIN_OFFSET = "offset";
    public static final String ORIGIN_ORIGINAL = "original";
    public static final String PHRASE = "phrase";
    public static final String PREDICATE = "predicate";
    public static final String PREFIX = "prefix";
    public static final String RANGE = "range";
    public static final String RANK = "rank";
    public static final String RANKED = "ranked";
    public static final String SAME_ELEMENT = "sameElement";
    public static final String SCORE_THRESHOLD = "scoreThreshold";
    public static final String SIGNIFICANCE = "significance";
    public static final String STEM = "stem";
    public static final String SUBSTRING = "substring";
    public static final String SUFFIX = "suffix";
    public static final String TARGET_HITS = "targetHits";
    public static final String TARGET_NUM_HITS = "targetNumHits";
    public static final String THRESHOLD_BOOST_FACTOR = "thresholdBoostFactor";
    public static final String UNIQUE_ID = "id";
    public static final String URI = "uri";
    public static final String USE_POSITION_DATA = "usePositionData";
    public static final String WAND = "wand";
    public static final String WEAK_AND = "weakAnd";
    public static final String WEIGHT = "weight";
    public static final String WEIGHTED_SET = "weightedSet";
    public static final String FUZZY = "fuzzy";
    public static final String MAX_EDIT_DISTANCE = "maxEditDistance";
    public static final String PREFIX_LENGTH = "prefixLength";


    private final IndexFacts indexFacts;
    private final List<ConnectedItem> connectedItems = new ArrayList<>();
    private final List<VespaGroupingStep> groupingSteps = new ArrayList<>();
    private final Map<Integer, TaggableItem> identifiedItems = LazyMap.newHashMap();
    private final Normalizer normalizer;
    private final Segmenter segmenter;
    private final Detector detector;
    private final Set<String> yqlSources = LazySet.newHashSet();
    private final Set<String> yqlSummaryFields = LazySet.newHashSet();
    private Integer hits;
    private Integer offset;
    private Integer timeout;
    private Query userQuery;
    private Parsable currentlyParsing;
    private IndexFacts.Session indexFactsSession;
    private IndexNameExpander indexNameExpander = new IndexNameExpander();
    private Set<String> docTypes;
    private Sorting sorting;
    private boolean queryParser = true;
    private final Deque<OperatorNode<?>> annotationStack = new ArrayDeque<>();
    private final ParserEnvironment environment;

    private static final QueryVisitor noEmptyTerms = new QueryVisitor() {

        @Override
        public boolean visit(Item item) {
            if (item instanceof NullItem) {
                throw new IllegalArgumentException("Got NullItem inside nonEmpty().");
            } else if (item instanceof WordItem) {
                if (((WordItem) item).getIndexedString().isEmpty()) {
                    throw new IllegalArgumentException("Searching for empty string inside nonEmpty()");
                }
            } else if (item instanceof CompositeItem) {
                if (((CompositeItem) item).getItemCount() == 0) {
                    throw new IllegalArgumentException("Empty composite operator (" + item.getName() + ") inside nonEmpty()");
                }
            }
            return true;
        }

        @Override
        public void onExit() {
            // NOP
        }
    };

    public YqlParser(ParserEnvironment environment) {
        indexFacts = environment.getIndexFacts();
        normalizer = environment.getLinguistics().getNormalizer();
        segmenter = environment.getLinguistics().getSegmenter();
        detector = environment.getLinguistics().getDetector();
        this.environment = environment;
    }

    @Override
    public QueryTree parse(Parsable query) {
        indexFactsSession = indexFacts.newSession(query.getSources(), query.getRestrict());
        connectedItems.clear();
        groupingSteps.clear();
        identifiedItems.clear();
        yqlSources.clear();
        yqlSummaryFields.clear();
        annotationStack.clear();
        hits = null;
        offset = null;
        timeout = null;
        // userQuery set prior to calling this
        currentlyParsing = query;
        docTypes = null;
        sorting = null;
        // queryParser set prior to calling this
        return buildTree(parseYqlProgram());
    }

    private void joinDocTypesFromUserQueryAndYql() {
        List<String> allSourceNames = new ArrayList<>(currentlyParsing.getSources().size() + yqlSources.size());
        if ( ! yqlSources.isEmpty()) {
            allSourceNames.addAll(currentlyParsing.getSources());
            allSourceNames.addAll(yqlSources);
        } else {
            // no sources == all sources in Vespa
        }
        indexFactsSession = indexFacts.newSession(allSourceNames, currentlyParsing.getRestrict());
        docTypes = new HashSet<>(indexFactsSession.documentTypes());
    }

    private QueryTree buildTree(OperatorNode<?> filterPart) {
        Preconditions.checkArgument(filterPart.getArguments().length == 2,
                                    "Expected 2 arguments to filter, got %s.",
                                    filterPart.getArguments().length);
        populateYqlSources(filterPart.getArgument(0));
        OperatorNode<ExpressionOperator> filterExpression = filterPart.getArgument(1);
        Item root = convertExpression(filterExpression);
        connectItems();
        userQuery = null;
        return new QueryTree(root);
    }

    private void populateYqlSources(OperatorNode<?> filterArgs) {
        yqlSources.clear();
        if (filterArgs.getOperator() == SequenceOperator.SCAN) {
            yqlSources.addAll(filterArgs.<List<String>>getArgument(0));
        }
        else if (filterArgs.getOperator() == SequenceOperator.ALL) {
            // yqlSources has already been cleared
        }
        else if (filterArgs.getOperator() == SequenceOperator.MULTISOURCE) {
            for (List<String> source : filterArgs.<List<List<String>>> getArgument(0)) {
                yqlSources.add(source.get(0));
            }
        }
        else {
            throw newUnexpectedArgumentException(filterArgs.getOperator(),
                    SequenceOperator.SCAN, SequenceOperator.ALL,
                    SequenceOperator.MULTISOURCE);
        }
        joinDocTypesFromUserQueryAndYql();
    }

    private void populateYqlSummaryFields(List<OperatorNode<ProjectOperator>> fields) {
        yqlSummaryFields.clear();
        for (OperatorNode<ProjectOperator> field : fields) {
            assertHasOperator(field, ProjectOperator.FIELD);
            yqlSummaryFields.add(field.getArgument(1, String.class));
        }
    }

    private void connectItems() {
        for (ConnectedItem entry : connectedItems) {
            TaggableItem to = identifiedItems.get(entry.toId);
            if (to == null)
                throw new IllegalArgumentException("Item '" + entry.fromItem +
                                                   "' was specified to connect to item with ID " + entry.toId +
                                                   ", which does not exist in the query.");
            entry.fromItem.setConnectivity((Item) to, entry.weight);
        }
    }

    private Item convertExpression(OperatorNode<ExpressionOperator> ast) {
        try {
            annotationStack.addFirst(ast);
            return switch (ast.getOperator()) {
                case AND -> buildAnd(ast);
                case OR -> buildOr(ast);
                case EQ -> buildEquals(ast);
                case LT -> buildLessThan(ast);
                case GT -> buildGreaterThan(ast);
                case LTEQ -> buildLessThanOrEquals(ast);
                case GTEQ -> buildGreaterThanOrEquals(ast);
                case CONTAINS -> buildTermSearch(ast);
                case MATCHES -> buildRegExpSearch(ast);
                case CALL -> buildFunctionCall(ast);
                case LITERAL -> buildLiteral(ast);
                case NOT -> buildNot(ast);
                case IN -> buildIn(ast);
                default -> throw newUnexpectedArgumentException(ast.getOperator(),
                                                                ExpressionOperator.AND, ExpressionOperator.CALL,
                                                                ExpressionOperator.CONTAINS, ExpressionOperator.EQ,
                                                                ExpressionOperator.GT, ExpressionOperator.GTEQ,
                                                                ExpressionOperator.IN,
                                                                ExpressionOperator.LT, ExpressionOperator.LTEQ,
                                                                ExpressionOperator.OR);
            };
        } finally {
            annotationStack.removeFirst();
        }
    }

    private Item buildFunctionCall(OperatorNode<ExpressionOperator> ast) {
        List<String> names = ast.getArgument(0);
        Preconditions.checkArgument(names.size() == 1, "Expected 1 name, got %s.", names.size());
        return switch (names.get(0)) {
            case USER_QUERY -> fetchUserQuery();
            case RANGE -> buildRange(ast);
            case WAND -> buildWand(ast);
            case WEIGHTED_SET -> buildWeightedSet(ast);
            case DOT_PRODUCT -> buildDotProduct(ast);
            case GEO_LOCATION -> buildGeoLocation(ast);
            case NEAREST_NEIGHBOR -> buildNearestNeighbor(ast);
            case PREDICATE -> buildPredicate(ast);
            case RANK -> buildRank(ast);
            case WEAK_AND -> buildWeakAnd(ast);
            case USER_INPUT -> buildUserInput(ast);
            case NON_EMPTY -> ensureNonEmpty(ast);
            default -> throw newUnexpectedArgumentException(names.get(0), DOT_PRODUCT, GEO_LOCATION, NEAREST_NEIGHBOR,
                                                            RANGE, RANK, USER_QUERY, WAND, WEAK_AND, WEIGHTED_SET,
                                                            PREDICATE, USER_INPUT, NON_EMPTY);
        };
    }

    private Item ensureNonEmpty(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 1, "Expected 1 arguments, got %s.", args.size());
        Item item = convertExpression(args.get(0));
        ToolBox.visit(noEmptyTerms, item);
        return item;
    }

    private Item buildWeightedSet(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 2, "Expected 2 arguments, got %s.", args.size());

        return fillWeightedSet(ast, args.get(1), new WeightedSetItem(getIndex(args.get(0))));
    }

    private Item buildDotProduct(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 2, "Expected 2 arguments, got %s.", args.size());

        return fillWeightedSet(ast, args.get(1), new DotProductItem(getIndex(args.get(0))));
    }

    private Item buildIn(OperatorNode<ExpressionOperator> ast) {
        String field = getIndex(ast.getArgument(0));
        var index = indexFactsSession.getIndex(field);
        boolean stringField = index.isString();
        if (!index.isInteger() && !stringField)
            throw new IllegalArgumentException("The in operator is only supported for integer and string fields. The field " +
                    field + " is not of these types");
        if (index.isInteger() && stringField) {
            throw new IllegalArgumentException("The in operator is not supported for fieldsets with a mix of integer and string fields. The fieldset " +
                        field + " has both");
        }
        Item item = null;
        if (stringField) {
            item = fillStringIn(ast, ast.getArgument(1), new StringInItem(field));
        } else {
            item = fillNumericIn(ast, ast.getArgument(1), new NumericInItem(field));
        }
        return item;
    }

    private ParsedDegree degreesFromArg(OperatorNode<ExpressionOperator> ast, boolean first) {
        Object arg = null;
        switch (ast.getOperator()) {
        case LITERAL:
            arg = ast.getArgument(0);
	    break;
        case READ_FIELD:
            arg = ast.getArgument(1);
	    break;
        default:
            throw newUnexpectedArgumentException(ast.getOperator(),
                                                 ExpressionOperator.READ_FIELD, ExpressionOperator.PROPREF);
        }
        if (arg instanceof Number n) {
            return new ParsedDegree(n.doubleValue(), first, !first);
        }
        return ParsedDegree.fromString(arg.toString(), first, !first);
    }

    private Item buildGeoLocation(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 4, "Expected 4 arguments, got %s.", args.size());
        String field = fetchFieldName(args.get(0));
        var coord_1 = degreesFromArg(args.get(1), true);
        var coord_2 = degreesFromArg(args.get(2), false);
        double radius = DistanceParser.parse(fetchLiteral(args.get(3)));
        var loc = new Location();
        if (coord_1.isLatitude && coord_2.isLongitude) {
            loc.setGeoCircle(coord_1.degrees, coord_2.degrees, radius);
        } else if (coord_2.isLatitude && coord_1.isLongitude) {
            loc.setGeoCircle(coord_2.degrees, coord_1.degrees, radius);
        } else {
            throw new IllegalArgumentException("Invalid geoLocation coordinates '"+coord_1+"' and '"+coord_2+"'");
        }
        var item = new GeoLocationItem(loc, field);
        String label = getAnnotation(ast, LABEL, String.class, null, "item label");
        if (label != null) {
            item.setLabel(label);
        }
        return item;
    }

    private Item buildLiteral(OperatorNode<ExpressionOperator> ast) {
        var literal = ast.getArgument(0);
        if (Boolean.TRUE.equals(literal)) {
            return new TrueItem();
        }
        if (Boolean.FALSE.equals(literal)) {
            return new FalseItem();
        }
        throw newUnexpectedArgumentException(literal, Boolean.FALSE, Boolean.TRUE);
    }

    private Item buildNearestNeighbor(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 2, "Expected 2 arguments, got %s.", args.size());
        String field = fetchFieldName(args.get(0));
        String property = fetchLiteral(args.get(1));
        NearestNeighborItem item = new NearestNeighborItem(indexFactsSession.getCanonicName(field), property);
        Integer targetNumHits = getAnnotation(ast, TARGET_HITS,
                Integer.class, null, "desired minimum hits to produce");
        if (targetNumHits == null) {
            targetNumHits = getAnnotation(ast, TARGET_NUM_HITS,
                Integer.class, null, "desired minimum hits to produce");
        }
        if (targetNumHits != null) {
            item.setTargetNumHits(targetNumHits);
        }
        Double distanceThreshold = getAnnotation(ast, DISTANCE_THRESHOLD,
                Double.class, null, "maximum distance allowed from query point");
        if (distanceThreshold != null) {
            item.setDistanceThreshold(distanceThreshold);
        }
        Integer hnswExploreAdditionalHits = getAnnotation(ast, HNSW_EXPLORE_ADDITIONAL_HITS,
                Integer.class, null, "number of extra hits to explore for HNSW algorithm");
        if (hnswExploreAdditionalHits != null) {
            item.setHnswExploreAdditionalHits(hnswExploreAdditionalHits);
        }
        Boolean allowApproximate = getAnnotation(ast, APPROXIMATE,
                Boolean.class, Boolean.TRUE, "allow approximate nearest neighbor search");
        item.setAllowApproximate(allowApproximate);
        String label = getAnnotation(ast, LABEL, String.class, null, "item label");
        if (label != null) {
            item.setLabel(label);
        }
        return item;
    }

    private Item buildPredicate(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 3, "Expected 3 arguments, got %s.", args.size());

        PredicateQueryItem item = new PredicateQueryItem();
        item.setIndexName(getIndex(args.get(0)));

        addFeatures(args.get(1),
                   (key, value, subqueryBitmap) -> item.addFeature(key, (String) value, subqueryBitmap), PredicateQueryItem.ALL_SUB_QUERIES);
        addFeatures(args.get(2), (key, value, subqueryBitmap) -> {
            if (value instanceof Long) {
                item.addRangeFeature(key, (Long) value, subqueryBitmap);
            } else {
                item.addRangeFeature(key, (Integer) value, subqueryBitmap);
            }
        }, PredicateQueryItem.ALL_SUB_QUERIES);
        return leafStyleSettings(ast, item);
    }

    interface AddFeature {
        void addFeature(String key, Object value, long subqueryBitmap);
    }

    private void addFeatures(OperatorNode<ExpressionOperator> map, AddFeature item, long subqueryBitmap) {
        if (map.getOperator() != ExpressionOperator.MAP) return;

        assertHasOperator(map, ExpressionOperator.MAP);
        List<String> keys = map.getArgument(0);
        List<OperatorNode<ExpressionOperator>> values = map.getArgument(1);
        for (int i = 0; i < keys.size(); ++i) {
            String key = keys.get(i);
            OperatorNode<ExpressionOperator> value = values.get(i);
            if (value.getOperator() == ExpressionOperator.ARRAY) {
                List<OperatorNode<ExpressionOperator>> multiValues = value.getArgument(0);
                for (OperatorNode<ExpressionOperator> multiValue : multiValues) {
                    assertHasOperator(multiValue, ExpressionOperator.LITERAL);
                    item.addFeature(key, multiValue.getArgument(0), subqueryBitmap);
                }
            } else if (value.getOperator() == ExpressionOperator.LITERAL) {
                item.addFeature(key, value.getArgument(0), subqueryBitmap);
            } else {
                assertHasOperator(value, ExpressionOperator.MAP); // Subquery syntax
                Preconditions.checkArgument(key.indexOf("0x") == 0 || key.indexOf("[") == 0);
                if (key.indexOf("0x") == 0) {
                    String subqueryString = key.substring(2);
                    if (subqueryString.length() > 16)
                        throw new NumberFormatException("Too long subquery string: " + key);
                    long currentSubqueryBitmap = new BigInteger(subqueryString, 16).longValue();
                    addFeatures(value, item, currentSubqueryBitmap);
                } else {
                    StringTokenizer bits = new StringTokenizer(key.substring(1, key.length() - 1), ",");
                    long currentSubqueryBitmap = 0;
                    while (bits.hasMoreTokens()) {
                        int bit = Integer.parseInt(bits.nextToken().trim());
                        currentSubqueryBitmap |= 1L << bit;
                    }
                    addFeatures(value, item, currentSubqueryBitmap);
                }
            }
        }
    }

    private Item buildWand(OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 2, "Expected 2 arguments, got %s.", args.size());

        Integer targetNumHits = getAnnotation(ast, TARGET_HITS,
                Integer.class, null, "desired number of hits to accumulate in wand");
        if (targetNumHits == null) {
            targetNumHits = getAnnotation(ast, TARGET_NUM_HITS,
                Integer.class, DEFAULT_TARGET_NUM_HITS, "desired number of hits to accumulate in wand");
        }
        WandItem out = new WandItem(getIndex(args.get(0)), targetNumHits);
        Double scoreThreshold = getAnnotation(ast, SCORE_THRESHOLD, Double.class, null,
                                              "score must be above this threshold for hit inclusion");
        if (scoreThreshold != null) {
            out.setScoreThreshold(scoreThreshold);
        }
        Double thresholdBoostFactor = getAnnotation(ast,
                THRESHOLD_BOOST_FACTOR, Double.class, null,
                "boost factor used to boost threshold before comparing against upper bound score");
        if (thresholdBoostFactor != null) {
            out.setThresholdBoostFactor(thresholdBoostFactor);
        }
        return fillWeightedSet(ast, args.get(1), out);
    }

    private WeightedSetItem fillWeightedSet(OperatorNode<ExpressionOperator> ast,
                                            OperatorNode<ExpressionOperator> arg,
                                            WeightedSetItem out) {
        addItems(arg, out);
        return leafStyleSettings(ast, out);
    }

    private StringInItem fillStringIn(OperatorNode<ExpressionOperator> ast,
                                      OperatorNode<ExpressionOperator> arg,
                                      StringInItem out) {
        assertHasOperator(arg, ExpressionOperator.ARRAY);
        List<OperatorNode<ExpressionOperator>> values = arg.getArgument(0);
        for (var value : values) {
            switch (value.getOperator()) {
                case LITERAL -> {
                    String tokenValue = value.getArgument(0, String.class);
                    out.addToken(tokenValue);
                }
                case VARREF -> {
                    Preconditions.checkState(userQuery != null, "Query properties are not available");
                    String varRef = value.getArgument(0, String.class);
                    ParameterListParser.addStringTokensFromString(userQuery.properties().getString(varRef), out);
                }
                default -> throw newUnexpectedArgumentException(value.getOperator(),
                        ExpressionOperator.LITERAL, ExpressionOperator.VARREF);
            }
        }
        return out;
    }

    private NumericInItem fillNumericIn(OperatorNode<ExpressionOperator> ast,
                                        OperatorNode<ExpressionOperator> arg,
                                        NumericInItem out) {
        assertHasOperator(arg, ExpressionOperator.ARRAY);
        List<OperatorNode<ExpressionOperator>> values = arg.getArgument(0);
        for (var value : values) {
            switch (value.getOperator()) {
                case LITERAL -> {
                    Number numberTokenValue = value.getArgument(0, Number.class);
                    Long tokenValue = (numberTokenValue instanceof Integer) ? numberTokenValue.longValue() : Long.class.cast(numberTokenValue);
                    out.addToken(tokenValue);
                }
                case VARREF -> {
                    Preconditions.checkState(userQuery != null, "Query properties are not available");
                    String varRef = value.getArgument(0, String.class);
                    ParameterListParser.addNumericTokensFromString(userQuery.properties().getString(varRef), out);
                }
                default -> throw newUnexpectedArgumentException(value.getOperator(),
                        ExpressionOperator.LITERAL, ExpressionOperator.VARREF);
            }
        }
        return out;
    }

    private static class PrefixExpander extends IndexNameExpander {
        private final String prefix;
        public PrefixExpander(String prefix) {
            this.prefix = prefix + ".";
        }

        @Override
        public String expand(String leaf) {
            return prefix + leaf;
        }
    }

    private Item instantiateSameElementItem(String field, OperatorNode<ExpressionOperator> ast) {
        assertHasFunctionName(ast, SAME_ELEMENT);

        SameElementItem sameElement = new SameElementItem(field);
        // All terms below sameElement are relative to this.
        IndexNameExpander prev = swapIndexCreator(new PrefixExpander(field));
        for (OperatorNode<ExpressionOperator> term : ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1)) {
            // TODO getIndex that is called once every term is rather expensive as it does sanity checking
            // that is not necessary. This is an issue when having many elements
            sameElement.addItem(convertExpression(term));
        }
        swapIndexCreator(prev);
        return sameElement;
    }

    private Item instantiatePhraseItem(String field, OperatorNode<ExpressionOperator> ast) {
        assertHasFunctionName(ast, PHRASE);

        if (getAnnotation(ast, ORIGIN, Map.class, null, ORIGIN_DESCRIPTION, false) != null) {
            return instantiatePhraseSegmentItem(field, ast, false);
        }

        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName(field);
        phrase.setExplicit(true);
        for (OperatorNode<ExpressionOperator> word : ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1)) {
            if (word.getOperator() == ExpressionOperator.CALL) {
                List<String> names = word.getArgument(0);
                switch (names.get(0)) {
                case PHRASE:
                    if (getAnnotation(word, ORIGIN, Map.class, null, ORIGIN_DESCRIPTION, false) == null) {
                        phrase.addItem(instantiatePhraseItem(field, word));
                    } else {
                        phrase.addItem(instantiatePhraseSegmentItem(field, word, true));
                    }
                    break;
                case ALTERNATIVES:
                    phrase.addItem(instantiateWordAlternativesItem(field, word));
                    break;
                default:
                    throw new IllegalArgumentException("Expected phrase or word alternatives, got " + names.get(0));
                }
            } else {
                phrase.addItem(instantiateWordItem(field, word, phrase.getClass()));
            }
        }
        return leafStyleSettings(ast, phrase);
    }

    private Item instantiatePhraseSegmentItem(String field, OperatorNode<ExpressionOperator> ast, boolean forcePhrase) {
        Substring origin = getOrigin(ast);
        Boolean stem = getAnnotation(ast, STEM, Boolean.class, Boolean.TRUE, STEM_DESCRIPTION);
        Boolean andSegmenting = getAnnotation(ast, AND_SEGMENTING, Boolean.class, Boolean.FALSE,
                                              "setting for whether to force using AND for segments on and off");
        SegmentItem phrase;
        List<String> words = null;

        if (forcePhrase || !andSegmenting) {
            phrase = new PhraseSegmentItem(origin.getValue(), origin.getValue(), true, !stem, origin);
        } else {
            phrase = new AndSegmentItem(origin.getValue(), true, !stem);
        }
        phrase.setIndexName(field);

        if (getAnnotation(ast, IMPLICIT_TRANSFORMS, Boolean.class, Boolean.TRUE, IMPLICIT_TRANSFORMS_DESCRIPTION)) {
            words = segmenter.segment(origin.getValue(), currentlyParsing.getLanguage());
        }

        if (words != null && words.size() > 0) {
            for (String word : words) {
                phrase.addItem(new WordItem(word, field, true));
            }
        } else {
            for (OperatorNode<ExpressionOperator> word : ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1)) {
                phrase.addItem(instantiateWordItem(field, word, phrase.getClass(), SegmentWhen.NEVER));
            }
        }
        if (phrase instanceof TaggableItem) {
            leafStyleSettings(ast, (TaggableItem) phrase);
        }
        phrase.lock();
        return phrase;
    }

    private Item instantiateNearItem(String field, OperatorNode<ExpressionOperator> ast) {
        assertHasFunctionName(ast, NEAR);

        NearItem near = new NearItem();
        near.setIndexName(field);
        for (OperatorNode<ExpressionOperator> word : ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1)) {
            near.addItem(instantiateWordItem(field, word, near.getClass()));
        }
        Integer distance = getAnnotation(ast, DISTANCE, Integer.class, null, "term distance for NEAR operator");
        if (distance != null) {
            near.setDistance(distance);
        }
        return near;
    }

    private Item instantiateONearItem(String field, OperatorNode<ExpressionOperator> ast) {
        assertHasFunctionName(ast, ONEAR);

        NearItem onear = new ONearItem();
        onear.setIndexName(field);
        for (OperatorNode<ExpressionOperator> word : ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1)) {
            onear.addItem(instantiateWordItem(field, word, onear.getClass()));
        }
        Integer distance = getAnnotation(ast, DISTANCE, Integer.class, null, "term distance for ONEAR operator");
        if (distance != null) {
            onear.setDistance(distance);
        }
        return onear;
    }

    private Item fetchUserQuery() {
        Preconditions.checkState(!queryParser, "Tried inserting user query into itself.");
        Preconditions.checkState(userQuery != null,
                                 "User query must be set before trying to build complete query "
                                 + "tree including user query.");
        return userQuery.getModel().getQueryTree().getRoot();
    }

    private Item buildUserInput(OperatorNode<ExpressionOperator> ast) {
        // TODO: Add support for default arguments if property results in nothing
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        String wordData = getStringContents(args.get(0));

        Boolean allowEmpty = getAnnotation(ast, USER_INPUT_ALLOW_EMPTY, Boolean.class,
                                           Boolean.FALSE, "flag for allowing NullItem to be returned");
        if (allowEmpty && (wordData == null || wordData.isEmpty())) return new NullItem();

        String grammar = getAnnotation(ast, USER_INPUT_GRAMMAR, String.class,
                                       Query.Type.WEAKAND.toString(), "grammar for handling user input");
        String defaultIndex = getAnnotation(ast, USER_INPUT_DEFAULT_INDEX,
                                            String.class, "default", "default index for user input terms");
        Language language = decideParsingLanguage(ast, wordData);
        Item item;
        if (USER_INPUT_GRAMMAR_RAW.equals(grammar)) {
            item = instantiateWordItem(defaultIndex, wordData, ast, null, SegmentWhen.NEVER, true, language);
        } else if (USER_INPUT_GRAMMAR_SEGMENT.equals(grammar)) {
            item = instantiateWordItem(defaultIndex, wordData, ast, null, SegmentWhen.ALWAYS, false, language);
        } else {
            item = parseUserInput(grammar, defaultIndex, wordData, language, allowEmpty);
            propagateUserInputAnnotationsRecursively(ast, item);
        }

        // Set grammar-specific annotations
        if (WEAKAND_GRAMMARS.contains(grammar) && item instanceof WeakAndItem weakAndItem) {
            weakAndItem.setN(getAnnotation(ast, TARGET_HITS, Integer.class, WeakAndItem.defaultN,
                                           "'targetHits' (N) for weak and"));
        }
        return item;
    }

    private Language decideParsingLanguage(OperatorNode<ExpressionOperator> ast, String wordData) {
        String languageTag = getAnnotation(ast, USER_INPUT_LANGUAGE, String.class, null,
                                           "language setting for segmenting query section");

        Language language = Language.fromLanguageTag(languageTag);
        if (language != Language.UNKNOWN) return language;

        Optional<Language> explicitLanguage = currentlyParsing.getExplicitLanguage();
        if (explicitLanguage.isPresent()) return explicitLanguage.get();

        language = detector.detect(wordData, null).getLanguage();
        if (language != Language.UNKNOWN) return language;

        return Language.ENGLISH;
    }

    private String getStringContents(OperatorNode<ExpressionOperator> operator) {
        switch (operator.getOperator()) {
            case LITERAL:
                return operator.getArgument(0, String.class);
            case VARREF:
                Preconditions.checkState(userQuery != null,
                                         "properties must be available when trying to fetch user input");
                return userQuery.properties().getString(operator.getArgument(0, String.class));
            default:
                throw newUnexpectedArgumentException(operator.getOperator(),
                                                     ExpressionOperator.LITERAL, ExpressionOperator.VARREF);
        }
    }

    private void propagateUserInputAnnotationsRecursively(OperatorNode<ExpressionOperator> ast, Item item) {
        ToolBox.visit(new AnnotationPropagator(ast), item);
    }

    private Item parseUserInput(String grammar, String defaultIndex, String wordData,
                                Language language, boolean allowNullItem) {
        Query.Type parseAs = Query.Type.getType(grammar);
        Parser parser = ParserFactory.newInstance(parseAs, environment);
        // perhaps not use already resolved doctypes, but respect source and restrict
        Item item = parser.parse(new Parsable().setQuery(wordData)
                                               .addSources(docTypes)
                                               .setLanguage(language)
                                               .setDefaultIndexName(defaultIndex)).getRoot();
        // the null check should be unnecessary, but is there to avoid having to suppress null warnings
        if ( ! allowNullItem && (item == null || item instanceof NullItem))
            throw new IllegalArgumentException("Parsing '" + wordData + "' only resulted in NullItem.");

        if (language != Language.ENGLISH) // mark the language used, unless it's the default
            item.setLanguage(language);
        return item;
    }

    private OperatorNode<?> parseYqlProgram() {
        OperatorNode<?> ast;
        try {
            ast = new ProgramParser().parse("query", currentlyParsing.getQuery());
        } catch (Exception e) {
            throw new IllegalInputException(e);
        }
        assertHasOperator(ast, StatementOperator.PROGRAM);
        Preconditions.checkArgument(ast.getArguments().length == 1,
                                    "Expected only a single argument to the root node, got %s.",
                                    ast.getArguments().length);
        // TODO: should we check size of first argument as well?
        ast = ast.<List<OperatorNode<?>>> getArgument(0).get(0);
        assertHasOperator(ast, StatementOperator.EXECUTE);

        ast = ast.getArgument(0);
        ast = fetchPipe(ast);
        ast = fetchTimeout(ast);
        ast = fetchSummaryFields(ast);
        ast = fetchOffsetAndHits(ast);
        ast = fetchSorting(ast);
        assertHasOperator(ast, SequenceOperator.FILTER);
        return ast;
    }

    @SuppressWarnings("unchecked")
    private OperatorNode<?> fetchPipe(OperatorNode<?> toScan) {
        OperatorNode<?> ast = toScan;
        while (ast.getOperator() == SequenceOperator.PIPE) {
            OperatorNode<ExpressionOperator> groupingAst = ast.<List<OperatorNode<ExpressionOperator>>> getArgument(2).get(0);
            GroupingOperation groupingOperation = GroupingOperation.fromString(groupingAst.getArgument(0));
            VespaGroupingStep groupingStep = new VespaGroupingStep(groupingOperation);
            List<Object> continuations = getAnnotation(groupingAst, "continuations", List.class,
                                                       List.of(), "grouping continuations");

            for (Object continuation : continuations) {
                groupingStep.continuations().add(Continuation.fromString(dereference(continuation)));
            }
            groupingSteps.add(groupingStep);
            ast = ast.getArgument(0);
        }
        Collections.reverse(groupingSteps);
        return ast;
    }

    private String dereference(Object constantOrVarref) {
        if (constantOrVarref instanceof OperatorNode<?> varref) {
            Preconditions.checkState(userQuery != null,
                                     "properties must be available when trying to fetch user input");
            return userQuery.properties().getString(varref.getArgument(0, String.class));
        }
        else {
            return constantOrVarref.toString();
        }
    }

    private OperatorNode<?> fetchSorting(OperatorNode<?> ast) {
        if (ast.getOperator() != SequenceOperator.SORT) return ast;

        List<FieldOrder> sortingInit = new ArrayList<>();
        List<OperatorNode<?>> sortArguments = ast.getArgument(1);
        for (OperatorNode<?> op : sortArguments) {
            OperatorNode<ExpressionOperator> fieldNode = op.getArgument(0);
            String field = fetchFieldName(fieldNode);
            String locale = getAnnotation(fieldNode, SORTING_LOCALE, String.class, null,
                                          "locale used by sorting function");
            String function = getAnnotation(fieldNode, SORTING_FUNCTION, String.class, null,
                                            "sorting function for the specified attribute");
            String strength = getAnnotation(fieldNode, SORTING_STRENGTH, String.class, null,
                                            "strength for sorting function");
            AttributeSorter sorter;
            if (function == null) {
                sorter = new AttributeSorter(field);
            } else if (Sorting.LOWERCASE.equals(function)) {
                sorter = new LowerCaseSorter(field);
            } else if (Sorting.RAW.equals(function)) {
                sorter = new RawSorter(field);
            } else if (Sorting.UCA.equals(function)) {
                if (locale != null) {
                    UcaSorter.Strength ucaStrength = UcaSorter.Strength.UNDEFINED;
                    if (strength != null) {
                        if (Sorting.STRENGTH_PRIMARY.equalsIgnoreCase(strength)) {
                            ucaStrength = UcaSorter.Strength.PRIMARY;
                        } else if (Sorting.STRENGTH_SECONDARY
                                .equalsIgnoreCase(strength)) {
                            ucaStrength = UcaSorter.Strength.SECONDARY;
                        } else if (Sorting.STRENGTH_TERTIARY
                                .equalsIgnoreCase(strength)) {
                            ucaStrength = UcaSorter.Strength.TERTIARY;
                        } else if (Sorting.STRENGTH_QUATERNARY
                                .equalsIgnoreCase(strength)) {
                            ucaStrength = UcaSorter.Strength.QUATERNARY;
                        } else if (Sorting.STRENGTH_IDENTICAL
                                .equalsIgnoreCase(strength)) {
                            ucaStrength = UcaSorter.Strength.IDENTICAL;
                        } else {
                            throw newUnexpectedArgumentException(function,
                                    Sorting.STRENGTH_PRIMARY,
                                    Sorting.STRENGTH_SECONDARY,
                                    Sorting.STRENGTH_TERTIARY,
                                    Sorting.STRENGTH_QUATERNARY,
                                    Sorting.STRENGTH_IDENTICAL);
                        }
                        sorter = new UcaSorter(field, locale, ucaStrength);
                    } else {
                        sorter = new UcaSorter(field, locale, ucaStrength);
                    }
                } else {
                    sorter = new UcaSorter(field);
                }
            } else {
                throw newUnexpectedArgumentException(function, "lowercase", "raw", "uca");
            }
            switch ((SortOperator) op.getOperator()) {
                case ASC -> sortingInit.add(new FieldOrder(sorter, Order.ASCENDING));
                case DESC -> sortingInit.add(new FieldOrder(sorter, Order.DESCENDING));
                default -> throw newUnexpectedArgumentException(op.getOperator(),
                                                                SortOperator.ASC, SortOperator.DESC);
            }
        }
        sorting = new Sorting(sortingInit);
        return ast.getArgument(0);
    }

    private OperatorNode<?> fetchOffsetAndHits(OperatorNode<?> ast) {
        if (ast.getOperator() == SequenceOperator.OFFSET) {
            offset = ast.<OperatorNode<?>> getArgument(1).<Integer> getArgument(0);
            hits = DEFAULT_HITS;
            return ast.getArgument(0);
        }
        if (ast.getOperator() == SequenceOperator.SLICE) {
            offset = ast.<OperatorNode<?>> getArgument(1).<Integer> getArgument(0);
            hits = ast.<OperatorNode<?>> getArgument(2).<Integer> getArgument(0) - offset;
            return ast.getArgument(0);
        }
        if (ast.getOperator() == SequenceOperator.LIMIT) {
            hits = ast.<OperatorNode<?>> getArgument(1).<Integer> getArgument(0);
            offset = DEFAULT_OFFSET;
            return ast.getArgument(0);
        }
        return ast;
    }

    private OperatorNode<?> fetchSummaryFields(OperatorNode<?> ast) {
        if (ast.getOperator() != SequenceOperator.PROJECT) return ast;

        Preconditions.checkArgument(ast.getArguments().length == 2,
                                   "Expected 2 arguments to PROJECT, got %s.",
                                   ast.getArguments().length);
        populateYqlSummaryFields(ast.getArgument(1));
        return ast.getArgument(0);
    }

    private OperatorNode<?> fetchTimeout(OperatorNode<?> ast) {
        if (ast.getOperator() != SequenceOperator.TIMEOUT) return ast;

        timeout = ast.<OperatorNode<?>> getArgument(1).<Integer> getArgument(0);
        return ast.getArgument(0);
    }

    private String fetchFieldName(OperatorNode<ExpressionOperator> ast) {
        return switch (ast.getOperator()) {
            case LITERAL -> indexFactsSession.getCanonicName(ast.getArgument(0).toString());
            case READ_FIELD -> ast.getArgument(1);
            case PROPREF -> fetchLiteral(ast.getArgument(0)) + '.' + ast.getArgument(1);
            default -> throw newUnexpectedArgumentException(ast.getOperator(),
                                                            ExpressionOperator.READ_FIELD, ExpressionOperator.PROPREF);
        };
    }

    private static String fetchLiteral(OperatorNode<ExpressionOperator> ast) {
        return switch (ast.getOperator()) {
            case LITERAL -> ast.getArgument(0).toString();
            case READ_FIELD -> ast.getArgument(1); // TODO: Should probably remove this option
            default -> throw newUnexpectedArgumentException(ast.getOperator(),
                                                            ExpressionOperator.READ_FIELD, ExpressionOperator.PROPREF);
        };
    }

    private IntItem buildGreaterThanOrEquals(OperatorNode<ExpressionOperator> ast) {
        if (isIndexOnLeftHandSide(ast)) {
            IntItem number = new IntItem("[" + fetchConditionWord(ast) + ";]", fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), number);
        } else {
            IntItem number = new IntItem("[;" + fetchConditionWord(ast) + "]", fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), number);
        }
    }

    private IntItem buildLessThanOrEquals(OperatorNode<ExpressionOperator> ast) {
        if (isIndexOnLeftHandSide(ast)) {
            IntItem number = new IntItem("[;" + fetchConditionWord(ast) + "]", fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), number);
        } else {
            IntItem number = new IntItem("[" + fetchConditionWord(ast) + ";]", fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), number);
        }
    }

    private IntItem buildGreaterThan(OperatorNode<ExpressionOperator> ast) {
        if (isIndexOnLeftHandSide(ast)) {
            IntItem number = new IntItem(">" + fetchConditionWord(ast), fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), number);
        } else {
            IntItem number = new IntItem("<" + fetchConditionWord(ast), fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), number);
        }
    }

    private IntItem buildLessThan(OperatorNode<ExpressionOperator> ast) {
        if (isIndexOnLeftHandSide(ast)) {
            IntItem number = new IntItem("<" + fetchConditionWord(ast), fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), number);
        } else {
            IntItem number = new IntItem(">" + fetchConditionWord(ast), fetchConditionIndex(ast));
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), number);
        }
    }

    private TermItem buildEquals(OperatorNode<ExpressionOperator> ast) {
        String value = fetchConditionWord(ast);

        TermItem item;
        if (value.equals("true"))
            item = new BoolItem(true, fetchConditionIndex(ast));
        else if (value.equals("false"))
            item = new BoolItem(false, fetchConditionIndex(ast));
        else
            item = new IntItem(value, fetchConditionIndex(ast));

        if (isIndexOnLeftHandSide(ast))
            return leafStyleSettings(ast.getArgument(1, OperatorNode.class), item);
        else
            return leafStyleSettings(ast.getArgument(0, OperatorNode.class), item);
    }

    private String fetchConditionIndex(OperatorNode<ExpressionOperator> ast) {
        OperatorNode<ExpressionOperator> lhs = ast.getArgument(0);
        OperatorNode<ExpressionOperator> rhs = ast.getArgument(1);
        if (isNumber(lhs))
            return getIndex(rhs);
        else if (isNumber(rhs))
            return getIndex(lhs);
        else
            throw new IllegalArgumentException("Expected LITERAL/VARREF and READ_FIELD/PROPREF, got " + lhs.getOperator() +
                                               " and " + rhs.getOperator() + ".");
    }

    private boolean isNumber(OperatorNode<ExpressionOperator> ast) {
        return ast.getOperator() == ExpressionOperator.NEGATE ||
               ast.getOperator() == ExpressionOperator.LITERAL || ast.getOperator() == ExpressionOperator.VARREF;
    }

    private String getNumberAsString(OperatorNode<ExpressionOperator> ast) {
        String negative = "";
        if (ast.getOperator() == ExpressionOperator.NEGATE) {
            negative = "-";
            ast = ast.getArgument(0);
        }
        switch (ast.getOperator()) {
            case VARREF:
                Preconditions.checkState(userQuery != null,
                                         "properties must be available when trying to fetch user input");
                return negative + userQuery.properties().getString(ast.getArgument(0, String.class));
            case LITERAL:
                return negative + ast.getArgument(0).toString();
            default:
                throw new IllegalArgumentException("Expected VARREF or LITERAL, got " + ast.getOperator());
        }
    }

    private String fetchConditionWord(OperatorNode<ExpressionOperator> ast) {
        OperatorNode<ExpressionOperator> lhs = ast.getArgument(0);
        OperatorNode<ExpressionOperator> rhs = ast.getArgument(1);
        if (isNumber(lhs)) {
            assertFieldName(rhs);
            return getNumberAsString(lhs);
        }
        else if (isNumber(rhs)) {
            assertFieldName(lhs);
            return getNumberAsString(rhs);
        }
        else
            throw new IllegalArgumentException("Expected LITERAL/NEGATE and READ_FIELD/PROPREF, got " +
                                               lhs.getOperator() + " and " + rhs.getOperator() + ".");
    }

    private static boolean isIndexOnLeftHandSide(OperatorNode<ExpressionOperator> ast) {
        OperatorNode<?> node =  ast.getArgument(0, OperatorNode.class);
        return node.getOperator() == ExpressionOperator.READ_FIELD || node.getOperator() == ExpressionOperator.PROPREF;
    }

    private CompositeItem buildAnd(OperatorNode<ExpressionOperator> ast) {
        AndItem andItem = new AndItem();
        NotItem notItem = new NotItem();
        convertVarArgsAnd(ast, 0, andItem, notItem);
        if (notItem.getItemCount() == 0) {
            return andItem;
        }
        if (andItem.getItemCount() == 1) {
            notItem.setPositiveItem(andItem.getItem(0));
        } else if (andItem.getItemCount() > 1) {
            notItem.setPositiveItem(andItem);
        } // else no positives, which is ok
        return notItem;
    }

    /** Build a "pure" not, without any positive terms. */
    private CompositeItem buildNot(OperatorNode<ExpressionOperator> ast) {
        NotItem notItem = new NotItem();
        notItem.addNegativeItem(convertExpression(ast.getArgument(0)));
        return notItem;
    }

    private CompositeItem buildOr(OperatorNode<ExpressionOperator> spec) {
        return convertVarArgs(spec, 0, new OrItem());
    }

    private CompositeItem buildWeakAnd(OperatorNode<ExpressionOperator> spec) {
        WeakAndItem weakAnd = new WeakAndItem();
        Integer targetNumHits = getAnnotation(spec, TARGET_HITS,
                Integer.class, null, "desired minimum hits to produce");
        if (targetNumHits == null) {
            targetNumHits = getAnnotation(spec, TARGET_NUM_HITS,
                Integer.class, null, "desired minimum hits to produce");
        }
        if (targetNumHits != null) {
            weakAnd.setN(targetNumHits);
        }
        return convertVarArgs(spec, 1, weakAnd);
    }

    private CompositeItem buildRank(OperatorNode<ExpressionOperator> spec) {
        return convertVarArgs(spec, 1, new RankItem());
    }

    private CompositeItem convertVarArgs(OperatorNode<ExpressionOperator> ast, int argIdx, CompositeItem out) {
        Iterable<OperatorNode<ExpressionOperator>> args = ast.getArgument(argIdx);
        for (OperatorNode<ExpressionOperator> arg : args) {
            assertHasOperator(arg, ExpressionOperator.class);
            out.addItem(convertExpression(arg));
        }
        return out;
    }

    private void convertVarArgsAnd(OperatorNode<ExpressionOperator> ast, int argIdx, AndItem outAnd, NotItem outNot) {
        Iterable<OperatorNode<ExpressionOperator>> args = ast.getArgument(argIdx);
        for (OperatorNode<ExpressionOperator> arg : args) {
            assertHasOperator(arg, ExpressionOperator.class);
            if (arg.getOperator() == ExpressionOperator.NOT) {
                OperatorNode<ExpressionOperator> exp = arg.getArgument(0);
                assertHasOperator(exp, ExpressionOperator.class);
                outNot.addNegativeItem(convertExpression(exp));
            } else {
                outAnd.addItem(convertExpression(arg));
            }
        }
    }

    private Item buildTermSearch(OperatorNode<ExpressionOperator> ast) {
        assertHasOperator(ast, ExpressionOperator.CONTAINS);
        String field = getIndex(ast.getArgument(0));
        if (userQuery != null && indexFactsSession.getIndex(field).isAttribute()) {
            userQuery.trace("Field '" + field + "' is an attribute, 'contains' will only match exactly (unless fuzzy is used)", 2);
        }
        return instantiateLeafItem(field, ast.getArgument(1));
    }

    private Item buildRegExpSearch(OperatorNode<ExpressionOperator> ast) {
        assertHasOperator(ast, ExpressionOperator.MATCHES);
        String field = getIndex(ast.getArgument(0));
        if (userQuery != null && !indexFactsSession.getIndex(field).isAttribute()) {
            userQuery.trace("Field '" + field + "' is indexed, non-literal regular expressions will not be matched", 1);
        }
        OperatorNode<ExpressionOperator> ast1 = ast.getArgument(1);
        String wordData = getStringContents(ast1);
        RegExpItem regExp = new RegExpItem(field, true, wordData);
        return leafStyleSettings(ast1, regExp);
    }

    private Item buildRange(OperatorNode<ExpressionOperator> spec) {
        assertHasOperator(spec, ExpressionOperator.CALL);
        assertHasFunctionName(spec, RANGE);

        IntItem range = instantiateRangeItem(spec.getArgument(1), spec);
        return leafStyleSettings(spec, range);
    }

    private static Number negate(Number x) {
        if (x.getClass() == Integer.class) {
            int x1 = x.intValue();
            return -x1;
        } else if (x.getClass() == Long.class) {
            long x1 = x.longValue();
            return -x1;
        } else if (x.getClass() == Float.class) {
            float x1 = x.floatValue();
            return -x1;
        } else if (x.getClass() == Double.class) {
            double x1 = x.doubleValue();
            return -x1;
        } else {
            throw newUnexpectedArgumentException(x.getClass(), Integer.class, Long.class, Float.class, Double.class);
        }
    }

    private IntItem instantiateRangeItem(List<OperatorNode<ExpressionOperator>> args,
                                         OperatorNode<ExpressionOperator> spec) {
        Preconditions.checkArgument(args.size() == 3,
                "Expected 3 arguments, got %s.", args.size());

        Number lowerArg = getRangeBound(args.get(1));
        Number upperArg = getRangeBound(args.get(2));
        String bounds = getAnnotation(spec, BOUNDS, String.class, null,
                                      "whether bounds should be open or closed");
        // TODO: add support for implicit transforms
        if (bounds == null) {
            return new RangeItem(lowerArg, upperArg, getIndex(args.get(0)));
        } else {
            Limit from;
            Limit to;
            if (BOUNDS_OPEN.equals(bounds)) {
                from = new Limit(lowerArg, false);
                to = new Limit(upperArg, false);
            } else if (BOUNDS_LEFT_OPEN.equals(bounds)) {
                from = new Limit(lowerArg, false);
                to = new Limit(upperArg, true);
            } else if (BOUNDS_RIGHT_OPEN.equals(bounds)) {
                from = new Limit(lowerArg, true);
                to = new Limit(upperArg, false);
            } else {
                throw newUnexpectedArgumentException(bounds, BOUNDS_OPEN, BOUNDS_LEFT_OPEN, BOUNDS_RIGHT_OPEN);
            }
            return new IntItem(from, to, getIndex(args.get(0)));
        }
    }

    private Number getRangeBound(OperatorNode<ExpressionOperator> bound) {
        if (bound.getOperator() == ExpressionOperator.NEGATE)
            return negate(getPositiveRangeBound(bound.getArgument(0)));
        else
            return getPositiveRangeBound(bound);
    }

    private Number getPositiveRangeBound(OperatorNode<ExpressionOperator> bound) {
        if (bound.getOperator() == ExpressionOperator.READ_FIELD) {
            // Why getArgument(1)? Because all of this is [mildly non-perfect] and we need to port it to JavaCC
            if (bound.getArgument(1).toString().equals("Infinity"))
                return Double.POSITIVE_INFINITY;
            else
                throw new IllegalArgumentException("Expected a numerical argument (or 'Infinity') to range but got '" +
                                                   bound.getArgument(1) + "'");
        }

        assertHasOperator(bound, ExpressionOperator.LITERAL,
                          () -> "Expected a numerical argument to range but got '" + bound.getArgument(0) + "'");
        return bound.getArgument(0, Number.class);
    }

    private Item instantiateLeafItem(String field, OperatorNode<ExpressionOperator> ast) {
        return switch (ast.getOperator()) {
            case LITERAL, VARREF -> instantiateWordItem(field, ast, null);
            case CALL -> instantiateCompositeLeaf(field, ast);
            default -> throw newUnexpectedArgumentException(ast.getOperator().name(),
                                                            ExpressionOperator.CALL, ExpressionOperator.LITERAL);
        };
    }

    private Item instantiateCompositeLeaf(String field, OperatorNode<ExpressionOperator> ast) {
        List<String> names = ast.getArgument(0);
        Preconditions.checkArgument(names.size() == 1, "Expected 1 name, got %s.", names.size());
        return switch (names.get(0)) {
            case SAME_ELEMENT -> instantiateSameElementItem(field, ast);
            case PHRASE -> instantiatePhraseItem(field, ast);
            case NEAR -> instantiateNearItem(field, ast);
            case ONEAR -> instantiateONearItem(field, ast);
            case EQUIV -> instantiateEquivItem(field, ast);
            case ALTERNATIVES -> instantiateWordAlternativesItem(field, ast);
            case URI -> instantiateUriItem(field, ast);
            case FUZZY -> instantiateFuzzyItem(field, ast);
            default ->
                    throw newUnexpectedArgumentException(names.get(0), EQUIV, NEAR, ONEAR, PHRASE, SAME_ELEMENT, URI, FUZZY);
        };
    }

    private Item instantiateFuzzyItem(String field, OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() == 1, "Expected 1 argument, got %s.", args.size());

        String wordData = getStringContents(args.get(0));

        Integer maxEditDistance = getAnnotation(
                ast,
                MAX_EDIT_DISTANCE,
                Integer.class,
                FuzzyItem.DEFAULT_MAX_EDIT_DISTANCE,
                MAX_EDIT_DISTANCE_DESCRIPTION);

        Integer prefixLength = getAnnotation(
                ast,
                PREFIX_LENGTH,
                Integer.class,
                FuzzyItem.DEFAULT_PREFIX_LENGTH,
                PREFIX_LENGTH_DESCRIPTION);

        boolean prefixMatch = getAnnotation(
                ast,
                PREFIX,
                Boolean.class,
                Boolean.FALSE,
                "setting for whether to use prefix match of input data");

        FuzzyItem fuzzy = new FuzzyItem(field, true, wordData, maxEditDistance, prefixLength, prefixMatch);
        return leafStyleSettings(ast, fuzzy);
    }

    private Item instantiateEquivItem(String field, OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() >= 2, "Expected 2 or more arguments, got %s.", args.size());

        EquivItem equiv = new EquivItem();
        equiv.setIndexName(field);
        for (OperatorNode<ExpressionOperator> arg : args) {
            switch (arg.getOperator()) {
                case LITERAL, VARREF -> equiv.addItem(instantiateWordItem(field, arg, equiv.getClass()));
                case CALL -> {
                    assertHasFunctionName(arg, PHRASE);
                    equiv.addItem(instantiatePhraseItem(field, arg));
                }
                default -> throw newUnexpectedArgumentException(arg.getOperator(),
                                                                ExpressionOperator.CALL, ExpressionOperator.LITERAL, ExpressionOperator.VARREF);
            }
        }
        return leafStyleSettings(ast, equiv);
    }

    private Item instantiateWordAlternativesItem(String field, OperatorNode<ExpressionOperator> ast) {
        List<OperatorNode<ExpressionOperator>> args = ast.getArgument(1);
        Preconditions.checkArgument(args.size() >= 1, "Expected 1 or more arguments, got %s.", args.size());
        Preconditions.checkArgument(args.get(0).getOperator() == ExpressionOperator.MAP, "Expected MAP, got %s.",
                                    args.get(0).getOperator());

        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();
        List<String> keys = args.get(0).getArgument(0);
        List<OperatorNode<ExpressionOperator>> values = args.get(0).getArgument(1);
        for (int i = 0; i < keys.size(); ++i) {
            OperatorNode<ExpressionOperator> value = values.get(i);
            if (value.getOperator() != ExpressionOperator.LITERAL)
                throw newUnexpectedArgumentException(value.getOperator(), ExpressionOperator.LITERAL);

            String term = keys.get(i);
            double exactness = value.getArgument(0, Double.class);
            terms.add(new WordAlternativesItem.Alternative(term, exactness));
        }
        Substring origin = getOrigin(ast);
        Boolean isFromQuery = getAnnotation(ast, IMPLICIT_TRANSFORMS, Boolean.class, Boolean.TRUE,
                                            IMPLICIT_TRANSFORMS_DESCRIPTION);
        return leafStyleSettings(ast, new WordAlternativesItem(field, isFromQuery, origin, terms));
    }

    private UriItem instantiateUriItem(String field, OperatorNode<ExpressionOperator> ast) {
        UriItem uriItem = new UriItem(field);

        boolean startAnchorDefault = false;
        boolean endAnchorDefault = indexFactsSession.getIndex(field).isHostIndex();

        if (getAnnotation(ast, START_ANCHOR, Boolean.class, startAnchorDefault,
                          "whether uri matching should be anchored to the start"))
            uriItem.addStartAnchorItem();

        String uriString = ast.<List<OperatorNode<ExpressionOperator>>> getArgument(1).get(0).getArgument(0);
        for (String token : segmenter.segment(uriString, Language.ENGLISH))
            uriItem.addItem(new WordItem(token, field, true));

        if (getAnnotation(ast, END_ANCHOR, Boolean.class, endAnchorDefault,
                          "whether uri matching should be anchored to the end"))
            uriItem.addEndAnchorItem();

        // Aux info to preserve minimal and expected canonical form
        uriItem.setStartAnchorDefault(startAnchorDefault);
        uriItem.setEndAnchorDefault(endAnchorDefault);
        uriItem.setSourceString(uriString);

        return uriItem;
    }

    private Item instantiateWordItem(String field, OperatorNode<ExpressionOperator> ast, Class<?> parent) {
        return instantiateWordItem(field, ast, parent, SegmentWhen.POSSIBLY);
    }

    private Item instantiateWordItem(String field,
                                     OperatorNode<ExpressionOperator> ast,
                                     Class<?> parent,
                                     SegmentWhen segmentPolicy) {
        String wordData = getStringContents(ast);
        return instantiateWordItem(field, wordData, ast, parent, segmentPolicy, null, decideParsingLanguage(ast, wordData));
    }

    /**
     * Converts the payload of a contains statement into an Item
     *
     * @param exactMatch true to always create an ExactStringItem, false to never do so, and null to
     *                   make the choice based on the field settings
     */
    // TODO: Clean up such that there is one way to look up an Index instance
    //       which always expands first, but not using getIndex, which performs checks that doesn't always work
    private Item instantiateWordItem(String field,
                                     String rawWord,
                                     OperatorNode<ExpressionOperator> ast,
                                     Class<?> parent,
                                     SegmentWhen segmentPolicy,
                                     Boolean exactMatch,
                                     Language language) {
        String wordData = rawWord;
        if (getAnnotation(ast, NFKC, Boolean.class, Boolean.FALSE,
                          "setting for whether to NFKC normalize input data")) {
            // NOTE: If this is set to FALSE (default), we will still NFKC normalize text data
            // during tokenization/segmentation, as that is always turned on also on the indexing side.
            wordData = normalizer.normalize(wordData);
        }
        boolean fromQuery = getAnnotation(ast, IMPLICIT_TRANSFORMS,
                                          Boolean.class, Boolean.TRUE, IMPLICIT_TRANSFORMS_DESCRIPTION);
        boolean prefixMatch = getAnnotation(ast, PREFIX, Boolean.class, Boolean.FALSE,
                                            "setting for whether to use prefix match of input data");
        boolean suffixMatch = getAnnotation(ast, SUFFIX, Boolean.class, Boolean.FALSE,
                                            "setting for whether to use suffix match of input data");
        boolean substrMatch = getAnnotation(ast, SUBSTRING, Boolean.class, Boolean.FALSE,
                                            "setting for whether to use substring match of input data");
        boolean exact = exactMatch != null ? exactMatch : indexFactsSession.getIndex(indexNameExpander.expand(field)).isExact();
        String grammar = getAnnotation(ast, USER_INPUT_GRAMMAR, String.class,
                                       Query.Type.WEAKAND.toString(), "grammar for handling word input");
        Preconditions.checkArgument((prefixMatch ? 1 : 0) +
                                    (substrMatch ? 1 : 0) + (suffixMatch ? 1 : 0) < 2,
                                    "Only one of prefix, substring and suffix can be set.");

        TaggableItem wordItem;
        if (prefixMatch) {
            wordItem = new PrefixItem(wordData, fromQuery);
        } else if (suffixMatch) {
            wordItem = new SuffixItem(wordData, fromQuery);
        } else if (substrMatch) {
            wordItem = new SubstringItem(wordData, fromQuery);
        } else if (exact) {
            wordItem = new ExactStringItem(wordData, fromQuery);
        } else {
            switch (segmentPolicy) {
                case NEVER:
                    wordItem = new WordItem(wordData, fromQuery);
                    break;
                case POSSIBLY:
                    if (shouldSegment(field, fromQuery) && ! grammar.equals(USER_INPUT_GRAMMAR_RAW)) {
                        wordItem = segment(field, ast, wordData, fromQuery, parent, language);
                    } else {
                        wordItem = new WordItem(wordData, fromQuery);
                    }
                    break;
                case ALWAYS:
                    wordItem = segment(field, ast, wordData, fromQuery, parent, language);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected segmenting rule: " + segmentPolicy);
            }
        }
        if (wordItem instanceof WordItem) {
            prepareWord(field, ast, (WordItem) wordItem);
        }
        if (language != Language.ENGLISH) // mark the language used, unless it's the default
            ((Item)wordItem).setLanguage(language);
        return (Item) leafStyleSettings(ast, wordItem);
    }

    private boolean shouldSegment(String field, boolean fromQuery) {
        return fromQuery && ! indexFactsSession.getIndex(indexNameExpander.expand(field)).isAttribute();
    }

    private TaggableItem segment(String field, OperatorNode<ExpressionOperator> ast, String wordData,
                                 boolean fromQuery, Class<?> parent, Language language) {
        String toSegment = wordData;
        Substring s = getOrigin(ast);
        Language usedLanguage = language == null ? currentlyParsing.getLanguage() : language;
        if (s != null) {
            toSegment = s.getValue();
        }
        List<String> words = segmenter.segment(toSegment, usedLanguage);

        TaggableItem wordItem;
        if (words.size() == 0) {
            wordItem = new WordItem(wordData, fromQuery);
        } else if (words.size() == 1 || !phraseArgumentSupported(parent)) {
            wordItem = new WordItem(words.get(0), fromQuery);
        } else {
            wordItem = new PhraseSegmentItem(toSegment, fromQuery, false);
            ((PhraseSegmentItem) wordItem).setIndexName(field);
            for (String w : words) {
                WordItem segment = new WordItem(w, fromQuery);
                prepareWord(field, ast, segment);
                ((PhraseSegmentItem) wordItem).addItem(segment);
            }
            ((PhraseSegmentItem) wordItem).lock();
        }
        return wordItem;
    }

    private boolean phraseArgumentSupported(Class<?> parent) {
        if (parent == null) return true;

        // not supported in backend, but the container flattens the arguments itself:
        if (parent == PhraseItem.class) return true;

        return parent == EquivItem.class;
    }

    private void prepareWord(String field, OperatorNode<ExpressionOperator> ast, WordItem wordItem) {
        wordItem.setIndexName(field);
        wordStyleSettings(ast, wordItem);
    }

    private <T extends TaggableItem> T leafStyleSettings(OperatorNode<?> ast, T out) {
        {
            Map<?, ?> connectivity = getAnnotation(ast, CONNECTIVITY, Map.class, null, "connectivity settings");
            if (connectivity != null) {
                connectedItems.add(new ConnectedItem(out,
                                                     getMapValue(CONNECTIVITY, connectivity, CONNECTION_ID,
                                                                 Integer.class), getMapValue(CONNECTIVITY,
                                                                                             connectivity,
                                                                                             CONNECTION_WEIGHT,
                                                                                             Number.class).doubleValue()));
            }
            Number significance = getAnnotation(ast, SIGNIFICANCE, Number.class, null, "term significance");
            if (significance != null) {
                out.setSignificance(significance.doubleValue());
            }
            Integer uniqueId = getAnnotation(ast, UNIQUE_ID, Integer.class, null, "term ID", false);
            if (uniqueId != null) {
                out.setUniqueID(uniqueId);
                identifiedItems.put(uniqueId, out);
            }
        }
        {
            Item leaf = (Item) out;
            Map<?, ?> itemAnnotations = getAnnotation(ast, ANNOTATIONS,
                                                      Map.class, Map.of(), "item annotation map");
            for (Map.Entry<?, ?> entry : itemAnnotations.entrySet()) {
                Preconditions.checkArgument(entry.getKey() instanceof String,
                                            "Expected String annotation key, got %s.", entry.getKey().getClass());
                Preconditions.checkArgument(entry.getValue() instanceof String,
                                            "Expected String annotation value, got %s.", entry.getValue().getClass());
                leaf.addAnnotation((String) entry.getKey(), entry.getValue());
            }
            Boolean filter = getAnnotation(ast, FILTER, Boolean.class, null, FILTER_DESCRIPTION);
            if (filter != null) {
                leaf.setFilter(filter);
            }
            Boolean isRanked = getAnnotation(ast, RANKED, Boolean.class, null, RANKED_DESCRIPTION);
            if (isRanked != null) {
                leaf.setRanked(isRanked);
            }
            String label = getAnnotation(ast, LABEL, String.class, null, "item label");
            if (label != null) {
                leaf.setLabel(label);
            }
            Integer weight = getAnnotation(ast, WEIGHT, Integer.class, null, "term weight for ranking");
            if (weight != null) {
                leaf.setWeight(weight);
            }
        }
        if (out instanceof IntItem number) {
            Integer hitLimit = getCappedRangeSearchParameter(ast);
            if (hitLimit != null) {
                number.setHitLimit(hitLimit);
            }
        }

        return out;
    }

    private Integer getCappedRangeSearchParameter(OperatorNode<?> ast) {
        Integer hitLimit = getAnnotation(ast, HIT_LIMIT, Integer.class, null, "hit limit");

        if (hitLimit != null) {
            Boolean ascending = getAnnotation(ast, ASCENDING_HITS_ORDER, Boolean.class, null,
                                              "ascending population ordering for capped range search");
            Boolean descending = getAnnotation(ast, DESCENDING_HITS_ORDER, Boolean.class, null,
                                               "descending population ordering for capped range search");
            Preconditions.checkArgument(ascending == null || descending == null,
                                        "Settings for both ascending and descending ordering set, only one of these expected.");
            if (Boolean.TRUE.equals(descending) || Boolean.FALSE.equals(ascending)) {
                hitLimit = hitLimit * -1;
            }
        }
        return hitLimit;
    }

    @Beta
    public boolean isQueryParser() { return queryParser; }

    @Beta
    public void setQueryParser(boolean queryParser) { this.queryParser = queryParser; }

    @Beta
    public void setUserQuery(Query userQuery) { this.userQuery = userQuery; }

    @Beta
    public Set<String> getYqlSummaryFields() { return yqlSummaryFields; }

    @Beta
    public List<VespaGroupingStep> getGroupingSteps() { return groupingSteps; }

    /**
     * Give the offset expected from the latest parsed query if anything is
     * explicitly specified.
     *
     * @return an Integer instance or null
     */
    public Integer getOffset() { return offset; }

    /**
     * Give the number of hits expected from the latest parsed query if anything
     * is explicitly specified.
     *
     * @return an Integer instance or null
     */
    public Integer getHits() { return hits; }

    /**
     * The timeout specified in the YQL+ query last parsed.
     *
     * @return an Integer instance or null
     */
    public Integer getTimeout() { return timeout; }

    /**
     * The sorting specified in the YQL+ query last parsed.
     *
     * @return a Sorting instance or null
     */
    public Sorting getSorting() { return sorting; }

    Set<String> getDocTypes() { return docTypes; }

    Set<String> getYqlSources() { return yqlSources; }

    private static void assertHasOperator(OperatorNode<?> ast, Class<? extends Operator> expectedOperatorClass) {
        Preconditions.checkArgument(expectedOperatorClass.isInstance(ast.getOperator()),
                                    "Expected operator class %s, got %s.",
                                    expectedOperatorClass.getName(), ast.getOperator().getClass().getName());
    }

    private static void assertHasOperator(OperatorNode<?> ast, Operator expectedOperator) {
        Preconditions.checkArgument(ast.getOperator() == expectedOperator,
                                    "Expected operator %s, got %s.",
                                    expectedOperator, ast.getOperator());
    }

    private static void assertHasOperator(OperatorNode<?> ast, Operator expectedOperator, Supplier<String> errorMessage) {
        try {
            Preconditions.checkArgument(ast.getOperator() == expectedOperator);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(errorMessage.get());
        }

    }

    private static void assertHasFunctionName(OperatorNode<?> ast, String expectedFunctionName) {
        List<String> names = ast.getArgument(0);
        Preconditions.checkArgument(expectedFunctionName.equals(names.get(0)),
                                    "Expected function '%s', got '%s'.",
                                    expectedFunctionName, names.get(0));
    }

    private static void assertFieldName(OperatorNode<?> ast) {
        Preconditions.checkArgument(ast.getOperator() == ExpressionOperator.READ_FIELD ||
                        ast.getOperator() == ExpressionOperator.PROPREF,
                "Expected operator READ_FIELD or PRPPREF, got %s.", ast.getOperator());
    }

    private void addItems(OperatorNode<ExpressionOperator> ast, WeightedSetItem out) {
        switch (ast.getOperator()) {
            case MAP -> addStringItems(ast, out);
            case ARRAY -> addLongItems(ast, out);
            case VARREF -> {
                Preconditions.checkState(userQuery != null, "Query properties are not available");
                ParameterListParser.addItemsFromString(userQuery.properties().getString(ast.getArgument(0, String.class)), out);
            }
            default -> throw newUnexpectedArgumentException(ast.getOperator(),
                                                            ExpressionOperator.ARRAY, ExpressionOperator.MAP);
        }
    }

    private static void addStringItems(OperatorNode<ExpressionOperator> ast, WeightedSetItem out) {
        List<String> keys = ast.getArgument(0);
        List<OperatorNode<ExpressionOperator>> values = ast.getArgument(1);
        for (int i = 0; i < keys.size(); ++i) {
            OperatorNode<ExpressionOperator> tokenWeight = values.get(i);
            assertHasOperator(tokenWeight, ExpressionOperator.LITERAL);
            out.addToken(keys.get(i), tokenWeight.getArgument(0, Integer.class));
        }
    }

    private static void addLongItems(OperatorNode<ExpressionOperator> ast, WeightedSetItem out) {
        List<OperatorNode<ExpressionOperator>> values = ast.getArgument(0);
        for (OperatorNode<ExpressionOperator> value : values) {
            assertHasOperator(value, ExpressionOperator.ARRAY);
            List<OperatorNode<ExpressionOperator>> args = value.getArgument(0);
            Preconditions.checkArgument(args.size() == 2,
                    "Expected item and weight, got %s.", args);

            OperatorNode<ExpressionOperator> tokenValueNode = args.get(0);
            assertHasOperator(tokenValueNode, ExpressionOperator.LITERAL);
            Number tokenValue = tokenValueNode.getArgument(0, Number.class);
            Preconditions.checkArgument(tokenValue instanceof Integer || tokenValue instanceof Long,
                    "Expected Integer or Long, got %s.", tokenValue.getClass().getName());

            OperatorNode<ExpressionOperator> tokenWeightNode = args.get(1);
            assertHasOperator(tokenWeightNode, ExpressionOperator.LITERAL);
            Integer tokenWeight = tokenWeightNode.getArgument(0, Integer.class);

            out.addToken(tokenValue.longValue(), tokenWeight);
        }
    }

    private void wordStyleSettings(OperatorNode<ExpressionOperator> ast, WordItem out) {
        Substring origin = getOrigin(ast);
        if (origin != null) {
            out.setOrigin(origin);
        }
        Boolean usePositionData = getAnnotation(ast, USE_POSITION_DATA, Boolean.class, null, USE_POSITION_DATA_DESCRIPTION);
        if (usePositionData != null) {
            out.setPositionData(usePositionData);
        }
        Boolean stem = getAnnotation(ast, STEM, Boolean.class, null, STEM_DESCRIPTION);
        if (stem != null) {
            out.setStemmed(!stem);
        }
        Boolean normalizeCase = getAnnotation(ast, NORMALIZE_CASE, Boolean.class, null, NORMALIZE_CASE_DESCRIPTION);
        if (normalizeCase != null) {
            out.setLowercased(!normalizeCase);
        }
        Boolean accentDrop = getAnnotation(ast, ACCENT_DROP, Boolean.class, null, ACCENT_DROP_DESCRIPTION);
        if (accentDrop != null) {
            out.setNormalizable(accentDrop);
        }
        Boolean andSegmenting = getAnnotation(ast, AND_SEGMENTING, Boolean.class, null,
                                              "setting for whether to force using AND for segments on and off");
        if (andSegmenting != null) {
            if (andSegmenting) {
                out.setSegmentingRule(SegmentingRule.BOOLEAN_AND);
            } else {
                out.setSegmentingRule(SegmentingRule.PHRASE);
            }
        }
    }

    private IndexNameExpander swapIndexCreator(IndexNameExpander newExpander) {
        IndexNameExpander old = indexNameExpander;
        indexNameExpander = newExpander;
        return old;
    }

    private String getIndex(OperatorNode<ExpressionOperator> operatorNode) {
        String index = fetchFieldName(operatorNode);
        String expanded = indexNameExpander.expand(index);
        Preconditions.checkArgument(indexFactsSession.isIndex(expanded), "Field '%s' does not exist.", expanded);
        return indexFactsSession.getCanonicName(index);
    }

    private Substring getOrigin(OperatorNode<ExpressionOperator> ast) {
        Map<?, ?> origin = getAnnotation(ast, ORIGIN, Map.class, null, ORIGIN_DESCRIPTION);
        if (origin == null) {
            return null;
        }
        String original = getMapValue(ORIGIN, origin, ORIGIN_ORIGINAL, String.class);
        int offset = getMapValue(ORIGIN, origin, ORIGIN_OFFSET, Integer.class);
        int length = getMapValue(ORIGIN, origin, ORIGIN_LENGTH, Integer.class);
        return new Substring(offset, length + offset, original);
    }

    private static <T> T getMapValue(String mapName, Map<?, ?> map, String key, Class<T> expectedValueClass) {
        Object value = map.get(key);
        Preconditions.checkArgument(value != null, "Map annotation '%s' must contain an entry with key '%s'.",
                                    mapName, key);
        Preconditions.checkArgument(expectedValueClass.isInstance(value),
                                    "Expected %s for entry '%s' in map annotation '%s', got %s.",
                                    expectedValueClass.getName(), key, mapName, value.getClass().getName());
        return expectedValueClass.cast(value);
    }

    private <T> T getAnnotation(OperatorNode<?> ast, String key, Class<T> expectedClass,
                                T defaultValue, String description) {
        return getAnnotation(ast, key, expectedClass, defaultValue, description, true);
    }

    private <T> T getAnnotation(OperatorNode<?> ast, String key, Class<T> expectedClass, T defaultValue,
                                String description, boolean considerParents) {
        Object value = ast.getAnnotation(key);
        for (Iterator<OperatorNode<?>> i = annotationStack.iterator(); value == null
                                                                       && considerParents && i.hasNext();) {
            OperatorNode<?> node = i.next();
            if (node.getOperator() == ExpressionOperator.VARREF) {
                Preconditions.checkState(userQuery != null,
                                         "properties must be available when trying to fetch user input");
                value = userQuery.properties().getString(ast.getArgument(0, String.class));
            }
            else {
                value = node.getAnnotation(key);
            }
        }
        if (value == null) return defaultValue;
        Preconditions.checkArgument(expectedClass.isInstance(value),
                                   "Expected %s for annotation '%s' (%s), got %s.",
                                    expectedClass.getName(), key, description, value.getClass().getName());
        return expectedClass.cast(value);
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

    private static final class ConnectedItem {

        final double weight;
        final int toId;
        final TaggableItem fromItem;

        ConnectedItem(TaggableItem fromItem, int toId, double weight) {
            this.weight = weight;
            this.toId = toId;
            this.fromItem = fromItem;
        }
    }

    private class AnnotationPropagator extends QueryVisitor {

        private final Boolean isRanked;
        private final Boolean filter;
        private final Boolean stem;
        private final Boolean normalizeCase;
        private final Boolean accentDrop;
        private final Boolean usePositionData;

        public AnnotationPropagator(OperatorNode<ExpressionOperator> ast) {
            isRanked = getAnnotation(ast, RANKED, Boolean.class, null, RANKED_DESCRIPTION);
            filter = getAnnotation(ast, FILTER, Boolean.class, null, FILTER_DESCRIPTION);
            stem = getAnnotation(ast, STEM, Boolean.class, null, STEM_DESCRIPTION);
            normalizeCase = getAnnotation(ast, NORMALIZE_CASE, Boolean.class, Boolean.TRUE, NORMALIZE_CASE_DESCRIPTION);
            accentDrop = getAnnotation(ast, ACCENT_DROP, Boolean.class, null, ACCENT_DROP_DESCRIPTION);
            usePositionData = getAnnotation(ast, USE_POSITION_DATA, Boolean.class, null, USE_POSITION_DATA_DESCRIPTION);
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof WordItem w) {
                if (usePositionData != null) {
                    w.setPositionData(usePositionData);
                }
                if (stem != null) {
                    w.setStemmed(!stem);
                }
                if (normalizeCase != null) {
                    w.setLowercased(!normalizeCase);
                }
                if (accentDrop != null) {
                    w.setNormalizable(accentDrop);
                }
            }
            if (item instanceof TaggableItem) {
                if (isRanked != null) {
                    item.setRanked(isRanked);
                }
                if (filter != null) {
                    item.setFilter(filter);
                }
            }
            return true;
        }

        @Override
        public void onExit() {
            // intentionally left blank
        }
    }

}
