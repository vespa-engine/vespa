package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.KeywordArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.LabelArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.ExpressionArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.FieldType;

public class BuiltInFunctions {
    public static final Map<String, GenericFunction> rankExpressionBuiltInFunctions = new HashMap<>() {{
        // ==== Query features ====
        put("query", new GenericFunction("query", new FunctionSignature(new SymbolArgument(SymbolType.QUERY_INPUT, "value"))));
        put("term", new GenericFunction("term", new IntegerArgument(), new HashSet<>() {{
            add("significance");
            add("weight");
            add("connectedness");
        }}));
        put("queryTermCount", new GenericFunction("queryTermCount"));
        
        // ==== Document features ====
        put("fieldLength", new GenericFunction("fieldLength", new FunctionSignature(new FieldArgument())));
        put("attribute", new GenericFunction("attribute", new ArrayList<>() {{
            add(new FunctionSignature(new FieldArgument(FieldArgument.NumericOrTensorFieldType, IndexingType.ATTRIBUTE)));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldType.NUMERIC_ARRAY, IndexingType.ATTRIBUTE));
                add(new IntegerArgument());
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldType.WSET, IndexingType.ATTRIBUTE));
                add(new StringArgument("key"));
            }}, new HashSet<>() {{
                add("weight");
                add("contains");
            }}));
            add(new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, IndexingType.ATTRIBUTE), "count"));
        }}));

        // ==== Field match features - normalized ====
        put("fieldMatch", new GenericFunction("fieldMatch", new FieldArgument(FieldType.STRING), new HashSet<>() {{
            add("");
            add("proximity");
            add("completeness");
            add("queryCompleteness");
            add("fieldCompleteness");
            add("orderness");
            add("relatedness");
            add("earliness");
            add("longestSequenceRatio");
            add("seqmentProximity");
            add("unweightedProximity");
            add("absoluteProximity");
            add("occurrence");
            add("absoluteOccurrence");
            add("weightedOccureence");
            add("weightedAbsoluteOccurence");
            add("significantOccurence");
        
        // ==== Feild match features - normalized and relative to the whole query ====
            add("weight");
            add("significance");
            add("importance");
        
        // ==== Field matche features - not normalized ====
            add("segments");
            add("matches");
            add("degradedMatches");
            add("outOfOrder");
            add("gaps");
            add("gapLength");
            add("longestSequence");
            add("head");
            add("tail");
            add("segmentDistance");
        }}));

        // ==== Query and field similarity ====
        put("textSimilarity", new GenericFunction("textSimilarity", new FieldArgument(FieldType.STRING), new HashSet<>() {{
            add("");
            add("proximity");
            add("order");
            add("queryCoverage");
            add("fieldCoverage");
        }}));

        // ==== Query term and field match features ====
        put("fieldTermMatch", new GenericFunction("fieldTermMatch", new FunctionSignature(new ArrayList<>() {{
            add(new FieldArgument(FieldType.STRING));
            add(new IntegerArgument());
        }}, new HashSet<>() {{
            add("firstPosition");
            add("occurences");
        }})));
        put("matchCount", new GenericFunction("mathCount", new FunctionSignature(new FieldArgument(FieldType.STRING, FieldArgument.IndexAttributeType))));
        put("matches", new GenericFunction("matches", new ArrayList<>() {{
            add(new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType)));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType));
                add(new IntegerArgument());
            }}));
        }}));
        put("termDistance", new GenericFunction("termDistance", new FunctionSignature(new ArrayList<>() {{
            add(new FieldArgument());
            add(new ExpressionArgument("x"));
            add(new ExpressionArgument("y"));
        }}, new HashSet<>() {{
            add("forward");
            add("forwardTermPosition");
            add("reverse");
            add("reverseTermPosition");
        }})));

        // ==== Features for idexed multivalue string fields ====
        put("elementCompletness", new GenericFunction("elementCompletness", new FunctionSignature(new FieldArgument(), new HashSet<>() {{
            add("completeness");
            add("fieldCompleteness");
            add("queryCompleteness");
            add("elementWeight");
        }})));
        put("elementSimilarity", new GenericFunction("elementSimilarity", new FunctionSignature(new FieldArgument())));



        
        // ==== Rank score ====
        put("bm25", new GenericFunction("bm25", new FunctionSignature(new FieldArgument())));
        put("nativeRank", new GenericFunction("nativeRank", new ArrayList<>() {{
            add(new FunctionSignature());
            add(new FunctionSignature(new FieldArgument())); // TODO: support unlimited number of fields
        }}));



        // ==== Global features ====
        put("globalSequence", new GenericFunction("globalSequence"));
        put("now", new GenericFunction("now"));
        // put("random", new GenericFunction());
        // put("random.match", new GenericFunction()); // This is buggy



        put("distance", new GenericFunction("distance", new ArrayList<>() {{
            add(new FunctionSignature(new ArrayList<>() {{
                add(new KeywordArgument("field", "dimension"));
                add(new FieldArgument());
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new KeywordArgument("label", "dimension"));
                add(new LabelArgument());
            }}));
        }}));

        put("file", new GenericFunction("file"));
        put("closest", new GenericFunction("closest", new ArrayList<>() {{
            add(new FunctionSignature(new FieldArgument()));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument());
                add(new LabelArgument());
            }}));
        }}));
    }};

    public static final Set<String> simpleBuiltInFunctionsSet = new HashSet<>() {{
        add("age");
        // add("attribute");
        add("attributeMatch");
        // add("bm25");
        add("closeness");
        // add("closest");
        add("closestdistanceage");
        add("constant");
        add("customTokenInputIds");
        // add("distance");
        add("distanceToPath");
        add("dotProduct");
        // add("elementCompleteness");
        // add("elementSimilarity");
        // add("fieldLength");
        // add("fieldMatch");
        // add("fieldTermMatch");
        add("firstPhase");
        add("firstPhaseRank");
        add("foreach");
        add("freshness");
        // add("globalSequence");
        add("itemRawScore");
        add("match");
        // add("matchCount");
        // add("matches");
        add("nativeAttributeMatch");
        add("nativeDotProduct");
        add("nativeFieldMatch");
        add("nativeProximity");
        // add("nativeRank");
        // add("now");
        // add("query");
        // add("queryTermCount");
        add("random");
        add("randomNormal");
        add("randomNormalStable");
        add("rankingExpression"); // TODO: deprecated (?)
        add("rawScore");
        add("secondPhase");
        add("tensorFromLabels");
        add("tensorFromWeightedSet");
        // add("term");
        // add("termDistance");
        // add("textSimilarity");
        add("tokenAttentionMask");
        add("tokenInputIds");
        add("tokenTypeIds");
        

        // TODO: these are only allowed in global-phase
        add("normalize_linear");
        add("reciprocal_rank");
        add("reciprocal_rank");
        add("reciprocal_rank_fusion");
    }};
}
