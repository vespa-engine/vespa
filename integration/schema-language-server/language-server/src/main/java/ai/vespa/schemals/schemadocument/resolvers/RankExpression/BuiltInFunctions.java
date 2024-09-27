package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.EnumSet;
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
        put("term", new GenericFunction("term", new IntegerArgument(), Set.of(
            "", // empty is not actually allowed, but here for completion
            "significance",
            "weight",
            "connectedness"
        )));
        put("queryTermCount", new GenericFunction("queryTermCount"));
        
        // ==== Document features ====
        put("fieldLength", new GenericFunction("fieldLength", new FunctionSignature(new FieldArgument())));
        put("attribute", new GenericFunction("attribute", List.of( 
            new FunctionSignature(new FieldArgument(FieldArgument.NumericOrTensorFieldType, FieldArgument.IndexAttributeType), Set.of(
                "",
                "count"
            )),
            new FunctionSignature(List.of(
                new FieldArgument(FieldType.NUMERIC_ARRAY, FieldArgument.IndexAttributeType),
                new IntegerArgument()
            )),
            new FunctionSignature(List.of(
                new FieldArgument(FieldType.WSET, FieldArgument.IndexAttributeType),
                new StringArgument("key")
            ), Set.of(
                "",  // empty not actually allowed but here for completion
                "weight", 
                "contains"))
        )));

        // TODO: requires you to write attribute(name)
        put("tensorFromWeightedSet", new GenericFunction("tensorFromWeightedSet", List.of(
            new FunctionSignature(
                new FieldArgument(FieldType.WSET, FieldArgument.IndexAttributeType, "source")
            ),
            new FunctionSignature(List.of(
                new FieldArgument(FieldType.WSET, FieldArgument.IndexAttributeType, "source"),
                new StringArgument("dimension")
            ))
        ))); 
        
        // TODO: requires you to write attribute(name)
        put("tensorFromLabels", new GenericFunction("tensorFromLabels", List.of(
            new FunctionSignature(
                new FieldArgument(FieldArgument.SingleValueOrArrayType, FieldArgument.IndexAttributeType, "attribute")
            ),
            new FunctionSignature(List.of(
                new FieldArgument(FieldArgument.SingleValueOrArrayType, FieldArgument.IndexAttributeType, "attribute"),
                new StringArgument("dimension")
            ))
        )));

        // ==== Field match features - normalized ====
        put("fieldMatch", new GenericFunction("fieldMatch", new FieldArgument(FieldType.STRING), Set.of(
            "",
            "proximity",
            "completeness",
            "queryCompleteness",
            "fieldCompleteness",
            "orderness",
            "relatedness",
            "earliness",
            "longestSequenceRatio",
            "seqmentProximity",
            "unweightedProximity",
            "absoluteProximity",
            "occurrence",
            "absoluteOccurrence",
            "weightedOccureence",
            "weightedAbsoluteOccurence",
            "significantOccurence",
        
        // ==== Feild match features - normalized and relative to the whole query ====
            "weight",
            "significance",
            "importance",
        
        // ==== Field match features - not normalized ====
            "segments",
            "matches",
            "degradedMatches",
            "outOfOrder",
            "gaps",
            "gapLength",
            "longestSequence",
            "head",
            "tail",
            "segmentDistance"
        )));

        // ==== Query and field similarity ====
        put("textSimilarity", new GenericFunction("textSimilarity", new FieldArgument(FieldType.STRING), Set.of(
            "",
            "proximity",
            "order",
            "queryCoverage",
            "fieldCoverage"
        )));

        // ==== Query term and field match features ====
        put("fieldTermMatch", new GenericFunction("fieldTermMatch", new FunctionSignature(List.of(
            new FieldArgument(FieldType.STRING),
            new IntegerArgument()
        ), Set.of(
            "", // empty not actually allowed, but here for completion
            "firstPosition", 
            "occurences"
        ))));

        put("matchCount", new GenericFunction("mathCount", new FunctionSignature(new FieldArgument(FieldType.STRING, FieldArgument.IndexAttributeType))));

        put("matches", new GenericFunction("matches", List.of(
            new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType)),
            new FunctionSignature(List.of(
                new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType),
                new IntegerArgument()
            ))
        )));
        put("termDistance", new GenericFunction("termDistance", new FunctionSignature(List.of(
            new FieldArgument(),
            new ExpressionArgument("x"),
            new ExpressionArgument("y")
        ), Set.of( 
            "", // empty not actually allowed, but here for completion
            "forward",
            "forwardTermPosition",
            "reverse",
            "reverseTermPosition"
        ))));

        // ==== Features for idexed multivalue string fields ====
        put("elementCompleteness", new GenericFunction("elementCompleteness", new FunctionSignature(new FieldArgument(), Set.of(
            "", // empty not actually allowed, but here for completion
            "completeness",
            "fieldCompleteness",
            "queryCompleteness",
            "elementWeight"
        ))));
        put("elementSimilarity", new GenericFunction("elementSimilarity", new FunctionSignature(new FieldArgument())));

        // === Attribute match features  ===
        put("attributeMatch", new GenericFunction("attributeMatch", new FunctionSignature(new FieldArgument(), Set.of(
            // normalized
            "",
            "completeness",
            "queryCompleteness",
            "fieldCompleteness",
            "normalizedWeight",
            "normalizedWeightedWeight",

            // normalized and relative to the whole query
            "weight",
            "significance",
            "importance",

            // not normalized
            "matches",
            "totalWeight",
            "averageWeight",
            "maxWeight"
        ))));

        put("closeness", new GenericFunction("closeness", List.of( 
            new FunctionSignature(List.of(new KeywordArgument("field", "dimension"), new FieldArgument(
                FieldType.TENSOR,
                FieldArgument.IndexAttributeType,
                "name"
            ))),
            new FunctionSignature(List.of(new KeywordArgument("label", "dimension"), new LabelArgument("name"))),
            new FunctionSignature(new FieldArgument(FieldType.POSITION, FieldArgument.IndexAttributeType, "name"), Set.of(
                "",
                "logscale"
            ))
        )));

        put("freshness", new GenericFunction("freshness",
            new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType, "name"), Set.of(
                "",
                "logscale"
            ))
        ));
        
        // ==== Rank score ====
        put("bm25", new GenericFunction("bm25", new FunctionSignature(new FieldArgument("field"))));

        put("nativeRank", new GenericFunction("nativeRank", List.of(
            new FunctionSignature(),
            new FunctionSignature(new FieldArgument("field"), true)
        )));

        put("nativeDotProduct", new GenericFunction("nativeDotProduct"));
        put("firstPhase", new GenericFunction("firstPhase"));
        put("secondPhase", new GenericFunction("secondPhase"));
        put("firstPhaseRank", new GenericFunction("firstPhaseRank"));

        put("nativeFieldMatch", new GenericFunction("nativeFieldMatch", List.of(
            new FunctionSignature(),
            new FunctionSignature(new FieldArgument(), true)
        )));
        put("nativeAttributeMatch", new GenericFunction("nativeAttributeMatch", List.of(
            new FunctionSignature(),
            new FunctionSignature(new FieldArgument(), true)
        )));

        // ==== Utility features ====
        put("tokenInputIds", new GenericFunction("tokenInputIds", new FunctionSignature(List.of(
            new IntegerArgument("length"),
            new ExpressionArgument("input")
        ), true)));
        put("customTokenInputIds", new GenericFunction("customTokenInputIds", new FunctionSignature(List.of(
            new IntegerArgument("start_sequence_id"),
            new IntegerArgument("sep_sequence_idlength"),
            new ExpressionArgument("input")
        ), true)));
        put("tokenTypeIds", new GenericFunction("tokenTypeIds", new FunctionSignature(List.of(
            new IntegerArgument("length"),
            new ExpressionArgument("input")
        ), true)));
        put("tokenAttentionMask", new GenericFunction("tokenAttentionMask", new FunctionSignature(List.of(
            new IntegerArgument("length"),
            new ExpressionArgument("input")
        ), true)));

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
            new FunctionSignature(new FieldArgument(FieldType.POSITION, FieldArgument.IndexAttributeType, "name"), Set.of(
                "",
                "km",
                "index",
                "latitude",
                "longitude"
            ))
        )));

        put("age", new GenericFunction("age", new FunctionSignature(new FieldArgument(FieldArgument.AnyFieldType, FieldArgument.IndexAttributeType))));

        put("file", new GenericFunction("file"));

        put("constant", new GenericFunction("constant", new FunctionSignature(new SymbolArgument(SymbolType.RANK_CONSTANT, "name"))));


        put("distanceToPath", new GenericFunction("distanceToPath", new FunctionSignature(
            new FieldArgument(FieldType.POSITION, FieldArgument.IndexAttributeType),
            Set.of(
                "", // empty not actually allowed, but here for completion
                "distance", 
                "traveled", 
                "product"
            )
        )));

        put("dotProduct", new GenericFunction("dotProduct", new FunctionSignature(List.of(
            new FieldArgument(EnumSet.of(FieldType.STRING, FieldType.INTEGER, FieldType.NUMERIC_ARRAY)),
            new VectorArgument()
        ))));

        // === Match operator scores ===
        put("rawScore", new GenericFunction("rawScore", new FunctionSignature(new FieldArgument())));
        put("itemRawScore", new GenericFunction("itemRawScore", new FunctionSignature(new LabelArgument())));

        put("foreach", new GenericFunction("foreach", List.of(
            new FunctionSignature(List.of(
                new KeywordArgument("fields", "dimension"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            )),
            new FunctionSignature(List.of(
                new KeywordArgument("terms", "dimension"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            )),
            new FunctionSignature(List.of(
                new KeywordArgument("attributes", "dimension"), 
                new StringArgument("variable"), 
                new ExpressionArgument("feature"), 
                new StringArgument("condition"),
                new EnumArgument("operation", List.of("sum", "product", "average", "min", "max", "count"))
            ))
        )));

        // === ML Model features ===
        put("onnx", new GenericFunction("onnx", new FunctionSignature(new SymbolArgument(SymbolType.ONNX_MODEL, "onnx-model"))));
        put("onnxModel", new GenericFunction("onnxModel", new FunctionSignature(new SymbolArgument(SymbolType.ONNX_MODEL, "onnx-model"))));
        put("lightbgm", new GenericFunction("lightbgm", new FunctionSignature(new StringArgument("\"/path/to/lightbgm-model.json\""))));
        put("xgboost", new GenericFunction("xgboost", new FunctionSignature(new StringArgument("\"/path/to/xgboost-model.json\""))));
    }};

    // Some features that have not gotten a signature for various reasons
    public static final Set<String> simpleBuiltInFunctionsSet = new HashSet<>() {{
        add("match");
        add("nativeProximity");
        add("randomNormal");
        add("randomNormalStable");
        add("rankingExpression"); // TODO: deprecated (?)

        // TODO: these are only allowed in global-phase
        add("normalize_linear");
        add("reciprocal_rank");
        add("reciprocal_rank");
        add("reciprocal_rank_fusion");
    }};
}
