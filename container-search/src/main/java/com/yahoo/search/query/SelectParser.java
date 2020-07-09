// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.google.common.base.Preconditions;
import com.yahoo.collections.LazyMap;
import com.yahoo.geo.ParseDegree;
import com.yahoo.geo.ParseDistance;
import com.yahoo.language.Language;
import com.yahoo.language.process.Normalizer;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.Location;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.BoolItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.GeoLocationItem;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NearestNeighborItem;
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
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.yql.VespaGroupingStep;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
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

import static com.yahoo.search.yql.YqlParser.ACCENT_DROP;
import static com.yahoo.search.yql.YqlParser.ALTERNATIVES;
import static com.yahoo.search.yql.YqlParser.AND_SEGMENTING;
import static com.yahoo.search.yql.YqlParser.ANNOTATIONS;
import static com.yahoo.search.yql.YqlParser.APPROXIMATE;
import static com.yahoo.search.yql.YqlParser.ASCENDING_HITS_ORDER;
import static com.yahoo.search.yql.YqlParser.BOUNDS;
import static com.yahoo.search.yql.YqlParser.BOUNDS_LEFT_OPEN;
import static com.yahoo.search.yql.YqlParser.BOUNDS_OPEN;
import static com.yahoo.search.yql.YqlParser.BOUNDS_RIGHT_OPEN;
import static com.yahoo.search.yql.YqlParser.CONNECTION_ID;
import static com.yahoo.search.yql.YqlParser.CONNECTION_WEIGHT;
import static com.yahoo.search.yql.YqlParser.CONNECTIVITY;
import static com.yahoo.search.yql.YqlParser.DEFAULT_TARGET_NUM_HITS;
import static com.yahoo.search.yql.YqlParser.DESCENDING_HITS_ORDER;
import static com.yahoo.search.yql.YqlParser.DISTANCE;
import static com.yahoo.search.yql.YqlParser.DOT_PRODUCT;
import static com.yahoo.search.yql.YqlParser.END_ANCHOR;
import static com.yahoo.search.yql.YqlParser.EQUIV;
import static com.yahoo.search.yql.YqlParser.FILTER;
import static com.yahoo.search.yql.YqlParser.GEO_LOCATION;
import static com.yahoo.search.yql.YqlParser.HIT_LIMIT;
import static com.yahoo.search.yql.YqlParser.HNSW_EXPLORE_ADDITIONAL_HITS;
import static com.yahoo.search.yql.YqlParser.IMPLICIT_TRANSFORMS;
import static com.yahoo.search.yql.YqlParser.LABEL;
import static com.yahoo.search.yql.YqlParser.NEAR;
import static com.yahoo.search.yql.YqlParser.NEAREST_NEIGHBOR;
import static com.yahoo.search.yql.YqlParser.NFKC;
import static com.yahoo.search.yql.YqlParser.NORMALIZE_CASE;
import static com.yahoo.search.yql.YqlParser.ONEAR;
import static com.yahoo.search.yql.YqlParser.ORIGIN;
import static com.yahoo.search.yql.YqlParser.ORIGIN_LENGTH;
import static com.yahoo.search.yql.YqlParser.ORIGIN_OFFSET;
import static com.yahoo.search.yql.YqlParser.ORIGIN_ORIGINAL;
import static com.yahoo.search.yql.YqlParser.PHRASE;
import static com.yahoo.search.yql.YqlParser.PREDICATE;
import static com.yahoo.search.yql.YqlParser.PREFIX;
import static com.yahoo.search.yql.YqlParser.RANGE;
import static com.yahoo.search.yql.YqlParser.RANK;
import static com.yahoo.search.yql.YqlParser.RANKED;
import static com.yahoo.search.yql.YqlParser.SAME_ELEMENT;
import static com.yahoo.search.yql.YqlParser.SCORE_THRESHOLD;
import static com.yahoo.search.yql.YqlParser.SIGNIFICANCE;
import static com.yahoo.search.yql.YqlParser.START_ANCHOR;
import static com.yahoo.search.yql.YqlParser.STEM;
import static com.yahoo.search.yql.YqlParser.SUBSTRING;
import static com.yahoo.search.yql.YqlParser.SUFFIX;
import static com.yahoo.search.yql.YqlParser.TARGET_HITS;
import static com.yahoo.search.yql.YqlParser.TARGET_NUM_HITS;
import static com.yahoo.search.yql.YqlParser.THRESHOLD_BOOST_FACTOR;
import static com.yahoo.search.yql.YqlParser.UNIQUE_ID;
import static com.yahoo.search.yql.YqlParser.URI;
import static com.yahoo.search.yql.YqlParser.USE_POSITION_DATA;
import static com.yahoo.search.yql.YqlParser.USER_INPUT_LANGUAGE;
import static com.yahoo.search.yql.YqlParser.WAND;
import static com.yahoo.search.yql.YqlParser.WEAK_AND;
import static com.yahoo.search.yql.YqlParser.WEIGHT;
import static com.yahoo.search.yql.YqlParser.WEIGHTED_SET;

/**
 * The Select query language.
 *
 * This class will be parsing the Select parameters, and will be used when the query has the SELECT-type.
 *
 * @author henrhoi
 */
public class SelectParser implements Parser {

    private static final String AND = "and";
    private static final String AND_NOT = "and_not";
    private static final String CALL = "call";
    private static final String CONTAINS = "contains";
    private static final String EQ = "equals";
    private static final String MATCHES = "matches";
    private static final String OR = "or";

    Parsable query;
    private final IndexFacts indexFacts;
    private final Map<Integer, TaggableItem> identifiedItems = LazyMap.newHashMap();
    private final List<ConnectedItem> connectedItems = new ArrayList<>();
    private final Normalizer normalizer;
    private IndexFacts.Session indexFactsSession;

    private static final List<String> FUNCTION_CALLS = Arrays.asList(WAND, WEIGHTED_SET, DOT_PRODUCT, GEO_LOCATION, NEAREST_NEIGHBOR, PREDICATE, RANK, WEAK_AND);

    public SelectParser(ParserEnvironment environment) {
        indexFacts = environment.getIndexFacts();
        normalizer = environment.getLinguistics().getNormalizer();
    }

    @Override
    public QueryTree parse(Parsable query) {
        indexFactsSession = indexFacts.newSession(query.getSources(), query.getRestrict());
        connectedItems.clear();
        identifiedItems.clear();
        this.query = query;

        return buildTree();
    }

    private QueryTree buildTree() {
        Inspector inspector = SlimeUtils.jsonToSlime(this.query.getSelect().getWhereString()).get();
        if (inspector.field("error_message").valid()) {
            throw new QueryException("Illegal query: " + inspector.field("error_message").asString() +
                                     " at: '" + new String(inspector.field("offending_input").asData(), StandardCharsets.UTF_8) + "'");
        }

        Item root = walkJson(inspector);
        connectItems();
        return new QueryTree(root);
    }

    private Item walkJson(Inspector inspector) {
        Item[] item = {null};
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

    public List<VespaGroupingStep> getGroupingSteps(String grouping){
        List<VespaGroupingStep> groupingSteps = new ArrayList<>();
        List<String> groupingOperations = toGroupingRequests(grouping);
        for (String groupingString : groupingOperations) {
            GroupingOperation groupingOperation = GroupingOperation.fromString(groupingString);
            VespaGroupingStep groupingStep = new VespaGroupingStep(groupingOperation);
            groupingSteps.add(groupingStep);
        }
        return groupingSteps;
    }

    /** Translates a list of grouping requests on JSON form to a list in the grouping language form */
    private List<String> toGroupingRequests(String groupingJson) {
        Inspector inspector = SlimeUtils.jsonToSlime(groupingJson).get();
        if (inspector.field("error_message").valid()) {
            throw new QueryException("Illegal query: " + inspector.field("error_message").asString() +
                                     " at: '" + new String(inspector.field("offending_input").asData(), StandardCharsets.UTF_8) + "'");
        }

        List<String> operations = new ArrayList<>();
        inspector.traverse((ArrayTraverser) (__, item) -> operations.add(toGroupingRequest(item)));
        return operations;
    }

    private String toGroupingRequest(Inspector groupingJson) {
        StringBuilder b = new StringBuilder();
        toGroupingRequest(groupingJson, b);
        return b.toString();
    }

    private void toGroupingRequest(Inspector groupingJson, StringBuilder b) {
        switch (groupingJson.type()) {
            case ARRAY:
                groupingJson.traverse((ArrayTraverser) (index, item) -> {
                    toGroupingRequest(item, b);
                    if (index + 1 < groupingJson.entries())
                        b.append(",");
                });
                break;
            case OBJECT:
                groupingJson.traverse((ObjectTraverser) (name, object) -> {
                    b.append(name);
                    b.append("(");
                    toGroupingRequest(object, b);
                    b.append(") ");
                });
                break;
            case STRING:
                b.append(groupingJson.asString());
                break;
            default:
                b.append(groupingJson.toString());
                break;
        }
    }

    private Item buildFunctionCall(String key, Inspector value) {
        switch (key) {
            case WAND:
                return buildWand(key, value);
            case WEIGHTED_SET:
                return buildWeightedSet(key, value);
            case DOT_PRODUCT:
                return buildDotProduct(key, value);
            case GEO_LOCATION:
                return buildGeoLocation(key, value);
            case NEAREST_NEIGHBOR:
                return buildNearestNeighbor(key, value);
            case PREDICATE:
                return buildPredicate(key, value);
            case RANK:
                return buildRank(key, value);
            case WEAK_AND:
                return buildWeakAnd(key, value);
            default:
                throw newUnexpectedArgumentException(key, DOT_PRODUCT, NEAREST_NEIGHBOR, RANK, WAND, WEAK_AND, WEIGHTED_SET, PREDICATE);
        }
    }

    private void addItemsFromInspector(CompositeItem item, Inspector inspector){
        if (inspector.type() == ARRAY){
            inspector.traverse((ArrayTraverser) (index, new_value) -> {
                item.addItem(walkJson(new_value));
            });

        } else if (inspector.type() == OBJECT){
            if (inspector.field("children").valid()) {
                inspector.field("children").traverse((ArrayTraverser) (index, new_value) -> {
                    item.addItem(walkJson(new_value));
                });
            }

        }
    }

    private Inspector getChildren(Inspector inspector) {
        if (inspector.type() == ARRAY){
            return inspector;

        } else if (inspector.type() == OBJECT){
            if (inspector.field("children").valid()) {
                return inspector.field("children");
            }
            if (inspector.field(1).valid()) {
                return inspector.field(1);
            }
        }
        return null;
    }

    private HashMap<Integer, Inspector> childMap(Inspector inspector) {
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

    private Inspector getAnnotations(Inspector inspector) {
        if (inspector.type() == OBJECT && inspector.field("attributes").valid()){
            return inspector.field("attributes");
        }
        return null;
    }

    private HashMap<String, Inspector> getAnnotationMapFromAnnotationInspector(Inspector annotation) {
        HashMap<String, Inspector> attributes = new HashMap<>();
        if (annotation.type() == OBJECT){
            annotation.traverse((ObjectTraverser) (index, new_value) -> {
                attributes.put(index, new_value);
            });
        }
        return attributes;
    }

    private HashMap<String, Inspector> getAnnotationMap(Inspector inspector) {
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

    private Boolean getBoolAnnotation(String annotationName, HashMap<String, Inspector> annotations, Boolean defaultValue) {
        if (annotations != null){
            Inspector annotation = annotations.getOrDefault(annotationName, null);
            if (annotation != null){
                return annotation.asBool();
            }
        }
        return defaultValue;
    }

    private Integer getIntegerAnnotation(String annotationName, HashMap<String, Inspector> annotations, Integer defaultValue) {
        if (annotations != null){
            Inspector annotation = annotations.getOrDefault(annotationName, null);
            if (annotation != null){
                return (int)annotation.asLong();
            }
        }
        return defaultValue;
    }

    private Double getDoubleAnnotation(String annotationName, HashMap<String, Inspector> annotations, Double defaultValue) {
        if (annotations != null){
            Inspector annotation = annotations.getOrDefault(annotationName, null);
            if (annotation != null){
                return annotation.asDouble();
            }
        }
        return defaultValue;
    }

    private Inspector getAnnotationAsInspectorOrNull(String annotationName, HashMap<String, Inspector> annotations) {
        return annotations.get(annotationName);
    }

    private CompositeItem buildAnd(String key, Inspector value) {
        AndItem andItem = new AndItem();
        addItemsFromInspector(andItem, value);

        return andItem;
    }

    private CompositeItem buildNotAnd(String key, Inspector value) {
        NotItem notItem = new NotItem();
        addItemsFromInspector(notItem, value);

        return notItem;
    }

    private CompositeItem buildOr(String key, Inspector value) {
        OrItem orItem = new OrItem();
        addItemsFromInspector(orItem, value);
        return orItem;
    }

    private Item buildGeoLocation(String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        Preconditions.checkArgument(children.size() == 4, "Expected 4 arguments, got %s.", children.size());
        String field = children.get(0).asString();
        var arg1 = children.get(1);
        var arg2 = children.get(2);
        var arg3 = children.get(3);
        var loc = new Location();
        double radius = -1;
        if (arg3.type() == Type.STRING) {
           radius = new ParseDistance(arg3.asString()).degrees;
        } else if (arg3.type() == Type.LONG) {
           radius = new ParseDistance(String.valueOf(arg3.asLong())).degrees;
        } else {
           throw new IllegalArgumentException("Invalid geoLocation radius type "+arg3.type()+" for "+arg3);
        }
        if (arg1.type() == Type.STRING && arg2.type() == Type.STRING) {
            var coord_1 = new ParseDegree(true, children.get(1).asString());
            var coord_2 = new ParseDegree(false, children.get(2).asString());
            if (coord_1.foundLatitude && coord_2.foundLongitude) {
                loc.setGeoCircle(coord_1.latitude, coord_2.longitude, radius);
            } else if (coord_2.foundLatitude && coord_1.foundLongitude) {
                loc.setGeoCircle(coord_2.latitude, coord_1.longitude, radius);
            } else {
                throw new IllegalArgumentException("Invalid geoLocation coordinates '"+coord_1+"' and '"+coord_2+"'");
            }
        } else if (arg1.type() == Type.DOUBLE && arg2.type() == Type.DOUBLE) {
            loc.setGeoCircle(arg1.asDouble(), arg2.asDouble(), radius);
        } else {
            throw new IllegalArgumentException("Invalid geoLocation coordinate types "+arg1.type()+" and "+arg2.type());
        }
        var item = new GeoLocationItem(loc, field);
        Inspector annotations = getAnnotations(value);
        if (annotations != null){
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (LABEL.equals(annotation_name)) {
                    item.setLabel(annotation_value.asString());
                }
            });
        }
        return item;
    }

    private Item buildNearestNeighbor(String key, Inspector value) {

        HashMap<Integer, Inspector> children = childMap(value);
        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        String field = children.get(0).asString();
        String property = children.get(1).asString();
        NearestNeighborItem item = new NearestNeighborItem(field, property);
        Inspector annotations = getAnnotations(value);
        if (annotations != null){
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (TARGET_HITS.equals(annotation_name)){
                    item.setTargetNumHits((int)(annotation_value.asDouble()));
                }
                if (TARGET_NUM_HITS.equals(annotation_name)){
                    item.setTargetNumHits((int)(annotation_value.asDouble()));
                }
                if (HNSW_EXPLORE_ADDITIONAL_HITS.equals(annotation_name)) {
                    int hnswExploreAdditionalHits = (int)(annotation_value.asDouble());
                    item.setHnswExploreAdditionalHits(hnswExploreAdditionalHits);                    
                }
                if (APPROXIMATE.equals(annotation_name)) {
                    boolean allowApproximate = annotation_value.asBool();
                    item.setAllowApproximate(allowApproximate);
                }
                if (LABEL.equals(annotation_name)) {
                    item.setLabel(annotation_value.asString());
                }
            });
        }
        return item;
    }

    private CompositeItem buildWeakAnd(String key, Inspector value) {
        WeakAndItem weakAnd = new WeakAndItem();
        addItemsFromInspector(weakAnd, value);
        Inspector annotations = getAnnotations(value);

        if (annotations != null){
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (TARGET_HITS.equals(annotation_name)){
                    weakAnd.setN((int)(annotation_value.asDouble()));
                }
                if (TARGET_NUM_HITS.equals(annotation_name)){
                    weakAnd.setN((int)(annotation_value.asDouble()));
                }
                if (SCORE_THRESHOLD.equals(annotation_name)){
                    weakAnd.setScoreThreshold((int)(annotation_value.asDouble()));
                }
            });
        }

        return weakAnd;
    }

    private <T extends TaggableItem> T leafStyleSettings(Inspector annotations, T out) {
        {
            if (annotations != null) {
                Inspector  itemConnectivity= getAnnotationAsInspectorOrNull(CONNECTIVITY, getAnnotationMapFromAnnotationInspector(annotations));
                if (itemConnectivity != null) {
                    Integer[] id = {null};
                    Double[] weight = {null};
                    itemConnectivity.traverse((ObjectTraverser) (key, value) -> {
                        switch (key){
                            case CONNECTION_ID:
                                id[0] = (int) value.asLong();
                                break;
                            case CONNECTION_WEIGHT:
                                weight[0] = value.asDouble();
                                break;
                        }
                    });
                    connectedItems.add(new ConnectedItem(out, id[0], weight[0]));
                }

                annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {

                    if (SIGNIFICANCE.equals(annotation_name)) {
                        if (annotation_value != null) {
                            out.setSignificance(annotation_value.asDouble());
                        }
                    }
                    if (UNIQUE_ID.equals(annotation_name)) {
                        if (annotation_value != null) {
                            out.setUniqueID((int)annotation_value.asLong());
                            identifiedItems.put((int)annotation_value.asLong(), out);
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
        Integer[] hitLimit = {null};
        annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
            if (HIT_LIMIT.equals(annotation_name)) {
                if (annotation_value != null) {
                    hitLimit[0] = (int)(annotation_value.asDouble());
                }
            }
        });
        Boolean[] ascending = {null};
        Boolean[] descending = {null};

        if (hitLimit[0] != null) {
            annotations.traverse((ObjectTraverser) (annotation_name, annotation_value) -> {
                if (ASCENDING_HITS_ORDER.equals(annotation_name)) {
                    ascending[0] = annotation_value.asBool();
                }
                if (DESCENDING_HITS_ORDER.equals(annotation_name)) {
                    descending[0] = annotation_value.asBool();
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


    private Item buildEquals(String key, Inspector value) {
        Map<Integer, Inspector> children = childMap(value);
        if ( children.size() != 2)
            throw new IllegalArgumentException("The value of 'equals' should be an array containing a field name and " +
                                               "a value, but was " + value);
        if ( children.get(0).type() != STRING)
            throw new IllegalArgumentException("The first array element under 'equals' should be a field name string " +
                                               "but was " + children.get(0));
        String field = children.get(0).asString();
        switch (children.get(1).type()) {
            case BOOL: return new BoolItem(children.get(1).asBool(), field);
            case LONG: return new IntItem(children.get(1).asLong(), field);
            default: throw new IllegalArgumentException("The second array element under 'equals' should be a boolean " +
                                                        "or int value but was " + children.get(1));
        }
    }

    private Item buildRange(String key, Inspector value) {
        Map<Integer, Inspector> children = childMap(value);
        Inspector annotations = getAnnotations(value);

        boolean[] equals = {false};

        String field;
        Inspector boundInspector;
        if (children.get(0).type() == STRING){
            field = children.get(0).asString();
            boundInspector = children.get(1);
        } else {
            field = children.get(1).asString();
            boundInspector = children.get(0);
        }
        Number[] bounds = {null, null};
        String[] operators = {null, null};
        boundInspector.traverse((ObjectTraverser) (operator, bound) -> {
            if (bound.type() == STRING) {
                throw new IllegalArgumentException("Expected a numeric argument to range, but got the string '" + bound.asString() + "'");
            }
            if (operator.equals("=")) {
                bounds[0] = (bound.type() == DOUBLE) ? Number.class.cast(bound.asDouble()) : Number.class.cast(bound.asLong());
                operators[0] = operator;
                equals[0] = true;
            }
            if (operator.equals(">=") || operator.equals(">")){
                bounds[0] = (bound.type() == DOUBLE) ? Number.class.cast(bound.asDouble()) : Number.class.cast(bound.asLong());
                operators[0] = operator;
            } else if (operator.equals("<=") || operator.equals("<")){
                bounds[1] = (bound.type() == DOUBLE) ? Number.class.cast(bound.asDouble()) : Number.class.cast(bound.asLong());
                operators[1] = operator;
            }

        });
        IntItem range = null;
        if (equals[0]) {
            range = new IntItem(bounds[0].toString(), field);
        }
        else if (operators[0] == null || operators[1] == null) {
            int index = (operators[0] == null) ? 1 : 0;
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

    private IntItem buildGreaterThanOrEquals(String field, String bound) {
        return new IntItem("[" + bound + ";]", field);

    }

    private IntItem buildLessThanOrEquals(String field, String bound) {
        return new IntItem("[;" + bound + "]", field);
    }

    private IntItem buildGreaterThan(String field, String bound) {
        return new IntItem(">" + bound, field);
    }

    private IntItem buildLessThan(String field, String bound) {
        return new IntItem("<" + bound, field);
    }

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

    private Item buildWand(String key, Inspector value) {
        HashMap<String, Inspector> annotations = getAnnotationMap(value);
        HashMap<Integer, Inspector> children = childMap(value);

        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        Integer target_num_hits= getIntegerAnnotation(TARGET_HITS, annotations, null);
        if (target_num_hits == null) {
            target_num_hits= getIntegerAnnotation(TARGET_NUM_HITS, annotations, DEFAULT_TARGET_NUM_HITS);
        }

        WandItem out = new WandItem(children.get(0).asString(), target_num_hits);

        Double scoreThreshold = getDoubleAnnotation(SCORE_THRESHOLD, annotations, null);

        if (scoreThreshold != null) {
            out.setScoreThreshold(scoreThreshold);
        }

        Double thresholdBoostFactor = getDoubleAnnotation(THRESHOLD_BOOST_FACTOR, annotations, null);
        if (thresholdBoostFactor != null) {
            out.setThresholdBoostFactor(thresholdBoostFactor);
        }
        return fillWeightedSet(value, children, out);
    }

    private WeightedSetItem fillWeightedSet(Inspector value, HashMap<Integer, Inspector> children, WeightedSetItem out) {
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
        children.get(1).traverse((ObjectTraverser) (key, value) -> {
            if (value.type() == STRING){
                throw new IllegalArgumentException("Expected an integer argument, but got the string '" +
                                                   value.asString() + "'");
            }
            out.addToken(key, (int)value.asLong());
        });
    }

    private static void addLongItems(HashMap<Integer, Inspector> children, WeightedSetItem out) {
        children.get(1).traverse((ArrayTraverser) (index, pair) -> {
            List<Integer> pairValues = new ArrayList<>();
            pair.traverse((ArrayTraverser) (pairIndex, pairValue) -> {
                pairValues.add((int)pairValue.asLong());
            });
            Preconditions.checkArgument(pairValues.size() == 2,
                    "Expected item and weight, got %s.", pairValues);
            out.addToken(pairValues.get(0).longValue(), pairValues.get(1));
        });
    }

    private Item buildRegExpSearch(String key, Inspector value) {
        assertHasOperator(key, MATCHES);
        HashMap<Integer, Inspector> children = childMap(value);
        String field = children.get(0).asString();
        String wordData = children.get(1).asString();
        RegExpItem regExp = new RegExpItem(field, true, wordData);
        return leafStyleSettings(getAnnotations(value), regExp);
    }

    private Item buildWeightedSet(String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        String field = children.get(0).asString();
        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        return fillWeightedSet(value, children, new WeightedSetItem(field));
    }

    private Item buildDotProduct(String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        String field = children.get(0).asString();
        Preconditions.checkArgument(children.size() == 2, "Expected 2 arguments, got %s.", children.size());
        return fillWeightedSet(value, children, new DotProductItem(field));
    }

    private Item buildPredicate(String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        String field = children.get(0).asString();
        Inspector args = children.get(1);

        Preconditions.checkArgument(children.size() == 3, "Expected 3 arguments, got %s.", children.size());

        PredicateQueryItem item = new PredicateQueryItem();
        item.setIndexName(field);

        List<Inspector> argumentList = valueListFromInspector(getChildren(value));

        // Adding attributes
        argumentList.get(1).traverse((ObjectTraverser) (attrKey, attrValue) -> {
            if (attrValue.type() == ARRAY){
                List<Inspector> attributes = valueListFromInspector(attrValue);
                attributes.forEach( (attribute) -> item.addFeature(attrKey, attribute.asString()));
            } else {
                item.addFeature(attrKey, attrValue.asString());
            }
        });

        // Adding range attributes
        argumentList.get(2).traverse((ObjectTraverser) (attrKey, attrValue) -> item.addRangeFeature(attrKey, (int)attrValue.asDouble()));

        return leafStyleSettings(getAnnotations(value), item);
    }

    private CompositeItem buildRank(String key, Inspector value) {
        RankItem rankItem = new RankItem();
        addItemsFromInspector(rankItem, value);
        return rankItem;
    }

    private Item buildTermSearch(String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        String field = children.get(0).asString();

        return instantiateLeafItem(field, key, value);
    }

    private String getInspectorKey(Inspector inspector){
        String[] actualKey = {""};
        if (inspector.type() == OBJECT){
            inspector.traverse((ObjectTraverser) (key, value) -> {
                actualKey[0] = key;

            });
        }
        return actualKey[0];
    }

    private Item instantiateLeafItem(String field, String key, Inspector value) {
        List<Inspector> possibleLeafFunction = valueListFromInspector(value);
        String possibleLeafFunctionName = (possibleLeafFunction.size() > 1) ? getInspectorKey(possibleLeafFunction.get(1)) : "";
        if (FUNCTION_CALLS.contains(key)) {
            return instantiateCompositeLeaf(field, key, value);
        } else if ( ! possibleLeafFunctionName.equals("")){
           return instantiateCompositeLeaf(field, possibleLeafFunctionName, valueListFromInspector(value).get(1).field(possibleLeafFunctionName));
        } else {
            return instantiateWordItem(field, key, value);
        }
    }

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

    private Item instantiateWordItem(String field, String key, Inspector value) {
        var children = childMap(value);
        if (children.size() < 2)
            throw new IllegalArgumentException("Expected at least 2 children of '" + key + "', but got " + children.size());

        String wordData = children.get(1).asString();
        return instantiateWordItem(field, wordData, key, value, false, decideParsingLanguage(value, wordData));
    }

    private Item instantiateWordItem(String field, String rawWord, String key, Inspector value, boolean exactMatch, Language language) {
        String wordData = rawWord;
        HashMap<String, Inspector> annotations = getAnnotationMap(value);

        if (getBoolAnnotation(NFKC, annotations, Boolean.FALSE)) {
            // NOTE: If this is set to FALSE (default), we will still NFKC normalize text data
            // during tokenization/segmentation, as that is always turned on also on the indexing side.
            wordData = normalizer.normalize(wordData);
        }
        boolean fromQuery = getBoolAnnotation(IMPLICIT_TRANSFORMS, annotations,  Boolean.TRUE);
        boolean prefixMatch = getBoolAnnotation(PREFIX, annotations, Boolean.FALSE);
        boolean suffixMatch = getBoolAnnotation(SUFFIX, annotations,  Boolean.FALSE);
        boolean substrMatch = getBoolAnnotation(SUBSTRING,annotations, Boolean.FALSE);

        Preconditions.checkArgument((prefixMatch ? 1 : 0)
                        + (substrMatch ? 1 : 0) + (suffixMatch ? 1 : 0) < 2,
                "Only one of prefix, substring and suffix can be set.");
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
        if (language != Language.ENGLISH)
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
        if (annotations !=  null){
            Boolean usePositionData = getBoolAnnotation(USE_POSITION_DATA, annotations, null);
            if (usePositionData != null) {
                out.setPositionData(usePositionData);
            }
            Boolean stem = getBoolAnnotation(STEM, annotations, null);
            if (stem != null) {
                out.setStemmed(!stem);
            }

            Boolean normalizeCase = getBoolAnnotation(NORMALIZE_CASE, annotations, null);
            if (normalizeCase != null) {
                out.setLowercased(!normalizeCase);
            }
            Boolean accentDrop = getBoolAnnotation(ACCENT_DROP, annotations, null);
            if (accentDrop != null) {
                out.setNormalizable(accentDrop);
            }
            Boolean andSegmenting = getBoolAnnotation(AND_SEGMENTING, annotations,  null);
            if (andSegmenting != null) {
                if (andSegmenting) {
                    out.setSegmentingRule(SegmentingRule.BOOLEAN_AND);
                } else {
                    out.setSegmentingRule(SegmentingRule.PHRASE);
                }
            }
        }
    }

    private Substring getOrigin(Inspector annotations) {
        if (annotations != null) {
            Inspector origin = getAnnotationAsInspectorOrNull(ORIGIN, getAnnotationMapFromAnnotationInspector(annotations));
            if (origin == null) {
                return null;
            }
            String[] original = {null};
            Integer[] offset = {null};
            Integer[] length = {null};

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

    private Item instantiateSameElementItem(String field, String key, Inspector value) {
        assertHasOperator(key, SAME_ELEMENT);

        SameElementItem sameElement = new SameElementItem(field);
        // All terms below sameElement are relative to this.
        getChildren(value).traverse((ArrayTraverser) (index, term) -> {
            sameElement.addItem(walkJson(term));
        });

        return sameElement;
    }

    private Item instantiatePhraseItem(String field, String key, Inspector value) {
        assertHasOperator(key, PHRASE);

        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName(field);
        phrase.setExplicit(true);
        HashMap<Integer, Inspector> children = childMap(value);

        for (Inspector word :  children.values()) {
            if (word.type() == STRING)
                phrase.addItem(new WordItem(word.asString()));
            else if (word.type() == OBJECT && word.field(PHRASE).valid())
                phrase.addItem(instantiatePhraseItem(field, key, getChildren(word)));
        }
        return leafStyleSettings(getAnnotations(value), phrase);
    }

    private Item instantiateNearItem(String field, String key, Inspector value) {
        assertHasOperator(key, NEAR);

        NearItem near = new NearItem();
        near.setIndexName(field);

        HashMap<Integer, Inspector> children = childMap(value);

        for (Inspector word :  children.values()){
            near.addItem(new WordItem(word.asString(), field));
        }

        Integer distance = getIntegerAnnotation(DISTANCE, getAnnotationMap(value), null);

        if (distance != null) {
            near.setDistance((int)distance);
        }
        return near;
    }

    private Item instantiateONearItem(String field, String key, Inspector value) {
        assertHasOperator(key, ONEAR);

        NearItem onear = new ONearItem();
        onear.setIndexName(field);
        HashMap<Integer, Inspector> children = childMap(value);

        for (Inspector word :  children.values()){
            onear.addItem(new WordItem(word.asString(), field));
        }

        Integer distance = getIntegerAnnotation(DISTANCE, getAnnotationMap(value), null);
        if (distance != null) {
            onear.setDistance(distance);
        }
        return onear;
    }

    private Item instantiateEquivItem(String field, String key, Inspector value) {

        HashMap<Integer, Inspector> children = childMap(value);
        Preconditions.checkArgument(children.size() >= 2, "Expected 2 or more arguments, got %s.", children.size());

        EquivItem equiv = new EquivItem();
        equiv.setIndexName(field);

        for (Inspector word :  children.values()){
            if (word.type() == STRING || word.type() == LONG || word.type() == DOUBLE){
                equiv.addItem(new WordItem(word.asString(), field));
            }
            if (word.type() == OBJECT){
                word.traverse((ObjectTraverser) (key2, value2) -> {
                    assertHasOperator(key2, PHRASE);
                    equiv.addItem(instantiatePhraseItem(field, key2, value2));
                });
            }
        }

        return leafStyleSettings(getAnnotations(value), equiv);
    }

    private Item instantiateWordAlternativesItem(String field, String key, Inspector value) {
        HashMap<Integer, Inspector> children = childMap(value);
        Preconditions.checkArgument(children.size() >= 1, "Expected 1 or more arguments, got %s.", children.size());
        Preconditions.checkArgument(children.get(0).type() == OBJECT, "Expected OBJECT, got %s.", children.get(0).type());

        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();

        children.get(0).traverse((ObjectTraverser) (keys, values) -> {
            terms.add(new WordAlternativesItem.Alternative(keys, values.asDouble()));
        });
        return leafStyleSettings(getAnnotations(value), new WordAlternativesItem(field, Boolean.TRUE, null, terms));
    }

    //  Not in use yet
    private String getIndex(String field) {
        Preconditions.checkArgument(indexFactsSession.isIndex(field), "Field '%s' does not exist.", field);
        //return indexFactsSession.getCanonicName(field);
        return field;
    }

    private static void assertHasOperator(String key, String expectedKey) {
        Preconditions.checkArgument(key.equals(expectedKey), "Expected operator %s, got %s.", expectedKey, key);
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

    private void connectItems() {
        for (ConnectedItem entry : connectedItems) {
            TaggableItem to = identifiedItems.get(entry.toId);
            Preconditions.checkNotNull(to,
                    "Item '%s' was specified to connect to item with ID %s, which does not "
                            + "exist in the query.", entry.fromItem, entry.toId);
            entry.fromItem.setConnectivity((Item) to, entry.weight);
        }
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

}
