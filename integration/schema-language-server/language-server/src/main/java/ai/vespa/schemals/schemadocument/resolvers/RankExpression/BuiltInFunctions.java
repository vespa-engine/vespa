package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.ExpressionArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;

public class BuiltInFunctions {
    public static final Map<String, FunctionHandler> rankExpressionBultInFunctions = new HashMap<>() {{
        // ==== Query features ====
        put("query", GenericFunction.singleSymbolArugmnet(SymbolType.QUERY_INPUT));
        put("term", new GenericFunction(new IntegerArgument(), new HashSet<>() {{
            add("significance");
            add("weight");
            add("connectedness");
        }}));
        put("queryTermCount", new GenericFunction());
        
        // ==== Document features ====
        put("fieldLength", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("attribute", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
                add("");
                add("count");
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new IntegerArgument());
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new StringArgument("key"));
            }}, new HashSet<>() {{
                add("weight");
                add("contains");
            }}));
        }}));

        // ==== Field match features - normalized ====
        put("fieldMatch", new GenericFunction(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
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
        put("textSimilarity", new GenericFunction(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
            add("");
            add("proximity");
            add("order");
            add("queryCoverage");
            add("fieldCoverage");
        }}));

        // ==== Query term and field match features ====
        put("fieldTermMatch", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
            add(new SymbolArgument(SymbolType.FIELD));
            add(new IntegerArgument());
        }}, new HashSet<>() {{
            add("firstPosition");
            add("occurences");
        }})));
        put("matchCount", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("matches", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature(new SymbolArgument(SymbolType.FIELD)));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new IntegerArgument());
            }}));
        }}));
        put("termDistance", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
            add(new SymbolArgument(SymbolType.FIELD));
            add(new ExpressionArgument("x"));
            add(new ExpressionArgument("y"));
        }}, new HashSet<>() {{
            add("forward");
            add("forwardTermPosition");
            add("reverse");
            add("reverseTermPosition");
        }})));

        // ==== Features for idexed multivalue string fields ====
        put("elementCompletness", new GenericFunction(new FunctionSignature(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
            add("completeness");
            add("fieldCompleteness");
            add("queryCompleteness");
            add("elementWeight");
        }})));
        put("elemenSimilarity", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));



        
        // ==== Rank score ====
        put("bm25", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("nativeRank", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature());
            add(FunctionSignature.singleSymbolSignature(SymbolType.FIELD)); // TODO: support unlimited number of fields
        }}));



        // ==== Global features ====
        put("globalSequence", new GenericFunction());
        put("now", new GenericFunction());
        put("random", new GenericFunction());
        put("random.match", new GenericFunction());



        put("distance", new DistanceFunction());

        put("file", new GenericFunction());
    }};
}
