package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.ExpressionArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.FieldType;

public class BuiltInFunctions {
    public static final Map<String, FunctionHandler> rankExpressionBultInFunctions = new HashMap<>() {{
        // ==== Query features ====
        put("query", new GenericFunction(new FunctionSignature(new SymbolArgument(SymbolType.QUERY_INPUT, "value"))));
        put("term", new GenericFunction(new IntegerArgument(), new HashSet<>() {{
            add("significance");
            add("weight");
            add("connectedness");
        }}));
        put("queryTermCount", new GenericFunction());
        
        // ==== Document features ====
        put("fieldLength", new GenericFunction(new FunctionSignature(new FieldArgument())));
        put("attribute", new GenericFunction(new ArrayList<>() {{
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
        put("fieldMatch", new GenericFunction(new FieldArgument(FieldType.STRING), new HashSet<>() {{
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
        put("textSimilarity", new GenericFunction(new FieldArgument(FieldType.STRING), new HashSet<>() {{
            add("");
            add("proximity");
            add("order");
            add("queryCoverage");
            add("fieldCoverage");
        }}));

        // ==== Query term and field match features ====
        put("fieldTermMatch", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
            add(new FieldArgument(FieldType.STRING));
            add(new IntegerArgument());
        }}, new HashSet<>() {{
            add("firstPosition");
            add("occurences");
        }})));
        put("matchCount", new GenericFunction(new FunctionSignature(new FieldArgument(FieldType.STRING, FieldArgument.IndexAttributeType))));
        put("matches", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType)));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType));
                add(new IntegerArgument());
            }}));
        }}));
        put("termDistance", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
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
        put("elementCompletness", new GenericFunction(new FunctionSignature(new FieldArgument(), new HashSet<>() {{
            add("completeness");
            add("fieldCompleteness");
            add("queryCompleteness");
            add("elementWeight");
        }})));
        put("elemenSimilarity", new GenericFunction(new FunctionSignature(new FieldArgument())));



        
        // ==== Rank score ====
        put("bm25", new GenericFunction(new FunctionSignature(new FieldArgument())));
        put("nativeRank", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature());
            add(new FunctionSignature(new FieldArgument())); // TODO: support unlimited number of fields
        }}));



        // ==== Global features ====
        put("globalSequence", new GenericFunction());
        put("now", new GenericFunction());
        put("random", new GenericFunction());
        put("random.match", new GenericFunction()); // This is buggy



        put("distance", new DistanceFunction());

        put("file", new GenericFunction());
    }};
}
