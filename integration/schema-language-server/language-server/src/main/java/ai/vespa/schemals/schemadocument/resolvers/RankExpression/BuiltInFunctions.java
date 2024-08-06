package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.KeywordArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.LabelArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.EnumArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.ExpressionArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.VectorArgument;
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

        // TODO: requires you to write attribute(name)
        put("tensorFromWeightedSet", new GenericFunction("tensorFromWeightedSet", new ArrayList<>() {{
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldType.WSET, IndexingType.ATTRIBUTE, "source"));
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldType.WSET, IndexingType.ATTRIBUTE, "source"));
                add(new StringArgument("dimension"));
            }}));
        }})); 
        
        // TODO: requires you to write attribute(name)
        put("tensorFromLabels", new GenericFunction("tensorFromLabels", new ArrayList<>() {{
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldArgument.SingleValueOrArrayType, IndexingType.ATTRIBUTE, "attribute"));
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument(FieldArgument.SingleValueOrArrayType, IndexingType.ATTRIBUTE, "attribute"));
                add(new StringArgument("dimension"));
            }}));
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
        
        // ==== Field match features - not normalized ====
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
        put("elementCompleteness", new GenericFunction("elementCompleteness", new FunctionSignature(new FieldArgument(), new HashSet<>() {{
            add("completeness");
            add("fieldCompleteness");
            add("queryCompleteness");
            add("elementWeight");
        }})));
        put("elementSimilarity", new GenericFunction("elementSimilarity", new FunctionSignature(new FieldArgument())));

        // === Attribute match features  ===
        put("attributeMatch", new GenericFunction("attributeMatch", new FunctionSignature(new FieldArgument(), new HashSet<>() {{
            // normalized
            add("");
            add("completeness");
            add("queryCompleteness");
            add("fieldCompleteness");
            add("normalizedWeight");
            add("normalizedWeightedWeight");

            // normalized and relative to the whole query
            add("weight");
            add("significance");
            add("importance");

            // not normalized
            add("matches");
            add("totalWeight");
            add("averageWeight");
            add("maxWeight");
        }})));

        put("closeness", new GenericFunction("closeness", List.of( 
            new FunctionSignature(List.of(new KeywordArgument("field", "dimension"), new FieldArgument(
                FieldType.TENSOR,
                IndexingType.ATTRIBUTE,
                "name"
            ))),
            new FunctionSignature(List.of(new KeywordArgument("label", "dimension"), new LabelArgument("name"))),
            new FunctionSignature(new FieldArgument(FieldType.POSITION, IndexingType.ATTRIBUTE, "position"), Set.of(
                "",
                "logscale"
            ))
        )));

        put("freshness", new GenericFunction("freshness",
            new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, IndexingType.ATTRIBUTE, "name"), Set.of(
                "",
                "logscale"
            ))
        ));
        
        // ==== Rank score ====
        put("bm25", new GenericFunction("bm25", new FunctionSignature(new FieldArgument("field"))));
        put("nativeRank", new GenericFunction("nativeRank", new ArrayList<>() {{
            add(new FunctionSignature());
            add(new FunctionSignature(new FieldArgument("field"))); // TODO: support unlimited number of fields
        }}));

        put("nativeDotProduct", new GenericFunction("nativeDotProduct"));
        put("firstPhase", new GenericFunction("firstPhase"));
        put("secondPhase", new GenericFunction("secondPhase"));
        put("firstPhaseRank", new GenericFunction("firstPhaseRank"));


        // ==== Global features ====
        put("globalSequence", new GenericFunction("globalSequence"));
        put("now", new GenericFunction("now"));
        // put("random", new GenericFunction());

        // put("random.match", new GenericFunction()); // This is buggy
        //
        put("random", new GenericFunction("random"));

        put("closest", new GenericFunction("closest", new ArrayList<>() {{
            add(new FunctionSignature(new FieldArgument()));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new FieldArgument());
                add(new LabelArgument());
            }}));
        }}));

        put("distance", new GenericFunction("distance", List.of( 
            new FunctionSignature(List.of(
                new KeywordArgument("field", "dimension"),
                new FieldArgument()
            )),
            new FunctionSignature(List.of(
                new KeywordArgument("label", "dimension"),
                new LabelArgument("name")
            )),
            new FunctionSignature(new FieldArgument(FieldType.POSITION, IndexingType.ATTRIBUTE, "position"), Set.of(
                "",
                "km",
                "index",
                "latitude",
                "longitude"
            ))
        )));

        put("age", new GenericFunction("age", new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, IndexingType.ATTRIBUTE))));

        put("file", new GenericFunction("file"));

        put("constant", new GenericFunction("constant", new FunctionSignature(new SymbolArgument(SymbolType.RANK_CONSTANT, "name"))));


        put("distanceToPath", new GenericFunction("distanceToPath", new FunctionSignature(
            new FieldArgument(FieldType.POSITION, IndexingType.ATTRIBUTE),
            Set.of("distance", "traveled", "product")
        )));

        put("dotProduct", new GenericFunction("dotProduct", new FunctionSignature(List.of(
            new FieldArgument(Set.of(FieldType.STRING, FieldType.INTEGER, FieldType.NUMERIC_ARRAY)),
            new VectorArgument()
        ))));

        // === Match operator scores ===
        put("rawScore", new GenericFunction("rawScore", new FunctionSignature(new FieldArgument())));
        put("itemRawScore", new GenericFunction("itemRawScore", new FunctionSignature(new LabelArgument())));

        put("foreach", new GenericFunction("foreach", List.of(
            new FunctionSignature(List.of(
                new KeywordArgument("fields"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            )),
            new FunctionSignature(List.of(
                new KeywordArgument("terms"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            )),
            new FunctionSignature(List.of(
                new KeywordArgument("attributes"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            ))
        )));
    }};

    public static final Set<String> simpleBuiltInFunctionsSet = new HashSet<>() {{
        // add("age");
        // add("attribute");
        // add("attributeMatch");
        // add("bm25");
        // add("closeness");
        // add("closest");
        // add("constant");
        add("customTokenInputIds");
        // add("distance");
        // add("distanceToPath");
        // add("dotProduct");
        // add("elementCompleteness");
        // add("elementSimilarity");
        // add("fieldLength");
        // add("fieldMatch");
        // add("fieldTermMatch");
        // add("firstPhase");
        // add("firstPhaseRank");
        // add("foreach");
        // add("freshness");
        // add("globalSequence");
        // add("itemRawScore");
        add("match");
        // add("matchCount");
        // add("matches");
        add("nativeAttributeMatch");
        // add("nativeDotProduct");
        add("nativeFieldMatch");
        add("nativeProximity");
        // add("nativeRank");
        // add("now");
        // add("query");
        // add("queryTermCount");
        // add("random");
        add("randomNormal");
        add("randomNormalStable");
        add("rankingExpression"); // TODO: deprecated (?)
        // add("rawScore");
        // add("secondPhase");

        // add("tensorFromLabels");
        // add("tensorFromWeightedSet");
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
