// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "setup.h"

#include "agefeature.h"
#include "attributefeature.h"
#include "attributematchfeature.h"
#include "bm25_feature.h"
#include "closenessfeature.h"
#include "constant_feature.h"
#include "debug_attribute_wait.h"
#include "debug_wait.h"
#include "distancefeature.h"
#include "great_circle_distance_feature.h"
#include "distancetopathfeature.h"
#include "dotproductfeature.h"
#include "element_completeness_feature.h"
#include "element_similarity_feature.h"
#include "euclidean_distance_feature.h"
#include "fieldinfofeature.h"
#include "fieldlengthfeature.h"
#include "fieldmatchfeature.h"
#include "fieldtermmatchfeature.h"
#include "firstphasefeature.h"
#include "flow_completeness_feature.h"
#include "foreachfeature.h"
#include "freshnessfeature.h"
#include "global_sequence_feature.h"
#include "item_raw_score_feature.h"
#include "jarowinklerdistancefeature.h"
#include "matchcountfeature.h"
#include "matchesfeature.h"
#include "matchfeature.h"
#include "native_dot_product_feature.h"
#include "nativeattributematchfeature.h"
#include "nativefieldmatchfeature.h"
#include "nativeproximityfeature.h"
#include "nativerankfeature.h"
#include "nowfeature.h"
#include "onnx_feature.h"
#include "proximityfeature.h"
#include "querycompletenessfeature.h"
#include "queryfeature.h"
#include "querytermcountfeature.h"
#include "random_normal_feature.h"
#include "random_normal_stable_feature.h"
#include "randomfeature.h"
#include "rankingexpressionfeature.h"
#include "raw_score_feature.h"
#include "reverseproximityfeature.h"
#include "subqueries_feature.h"
#include "tensor_from_labels_feature.h"
#include "tensor_from_weighted_set_feature.h"
#include "term_field_md_feature.h"
#include "termdistancefeature.h"
#include "termeditdistancefeature.h"
#include "termfeature.h"
#include "terminfofeature.h"
#include "text_similarity_feature.h"
#include "valuefeature.h"

#include "max_reduce_prod_join_replacer.h"
#include <vespa/searchlib/features/rankingexpression/expression_replacer.h>

using search::fef::Blueprint;
using search::features::rankingexpression::ListExpressionReplacer;
using search::features::MaxReduceProdJoinReplacer;

namespace search::features {

void setup_search_features(fef::IBlueprintRegistry & registry)
{
    // Prod features.
    registry.addPrototype(std::make_shared<AgeBlueprint>());
    registry.addPrototype(std::make_shared<AttributeBlueprint>());
    registry.addPrototype(std::make_shared<AttributeMatchBlueprint>());
    registry.addPrototype(std::make_shared<Bm25Blueprint>());
    registry.addPrototype(std::make_shared<ClosenessBlueprint>());
    registry.addPrototype(std::make_shared<DebugAttributeWaitBlueprint>());
    registry.addPrototype(std::make_shared<DebugWaitBlueprint>());
    registry.addPrototype(std::make_shared<DistanceBlueprint>());
    registry.addPrototype(std::make_shared<DistanceToPathBlueprint>());
    registry.addPrototype(std::make_shared<DotProductBlueprint>());
    registry.addPrototype(std::make_shared<ElementCompletenessBlueprint>());
    registry.addPrototype(std::make_shared<ElementSimilarityBlueprint>());
    registry.addPrototype(std::make_shared<EuclideanDistanceBlueprint>());
    registry.addPrototype(std::make_shared<FieldInfoBlueprint>());
    registry.addPrototype(std::make_shared<FieldLengthBlueprint>());
    registry.addPrototype(std::make_shared<FieldMatchBlueprint>());
    registry.addPrototype(std::make_shared<FieldTermMatchBlueprint>());
    registry.addPrototype(std::make_shared<FirstPhaseBlueprint>());
    registry.addPrototype(std::make_shared<FlowCompletenessBlueprint>());
    registry.addPrototype(std::make_shared<ForeachBlueprint>());
    registry.addPrototype(std::make_shared<FreshnessBlueprint>());
    registry.addPrototype(std::make_shared<ItemRawScoreBlueprint>());
    registry.addPrototype(std::make_shared<MatchBlueprint>());
    registry.addPrototype(std::make_shared<MatchCountBlueprint>());
    registry.addPrototype(std::make_shared<MatchesBlueprint>());
    registry.addPrototype(std::make_shared<NativeAttributeMatchBlueprint>());
    registry.addPrototype(std::make_shared<NativeDotProductBlueprint>());
    registry.addPrototype(std::make_shared<NativeFieldMatchBlueprint>());
    registry.addPrototype(std::make_shared<NativeProximityBlueprint>());
    registry.addPrototype(std::make_shared<NativeRankBlueprint>());
    registry.addPrototype(std::make_shared<NowBlueprint>());
    registry.addPrototype(std::make_shared<QueryBlueprint>());
    registry.addPrototype(std::make_shared<QueryTermCountBlueprint>());
    registry.addPrototype(std::make_shared<RandomBlueprint>());
    registry.addPrototype(std::make_shared<RandomNormalBlueprint>());
    registry.addPrototype(std::make_shared<RandomNormalStableBlueprint>());
    registry.addPrototype(std::make_shared<RawScoreBlueprint>());
    registry.addPrototype(std::make_shared<SubqueriesBlueprint>());
    registry.addPrototype(std::make_shared<TensorFromLabelsBlueprint>());
    registry.addPrototype(std::make_shared<TensorFromWeightedSetBlueprint>());
    registry.addPrototype(std::make_shared<TermBlueprint>());
    registry.addPrototype(std::make_shared<TermDistanceBlueprint>());
    registry.addPrototype(std::make_shared<TermInfoBlueprint>());
    registry.addPrototype(std::make_shared<TextSimilarityBlueprint>());
    registry.addPrototype(std::make_shared<ValueBlueprint>());

    // Beta features.
    registry.addPrototype(std::make_shared<JaroWinklerDistanceBlueprint>());
    registry.addPrototype(std::make_shared<ProximityBlueprint>());
    registry.addPrototype(std::make_shared<QueryCompletenessBlueprint>());
    registry.addPrototype(std::make_shared<ReverseProximityBlueprint>());
    registry.addPrototype(std::make_shared<TermEditDistanceBlueprint>());
    registry.addPrototype(std::make_shared<TermFieldMdBlueprint>());
    registry.addPrototype(std::make_shared<ConstantBlueprint>());
    registry.addPrototype(std::make_shared<GlobalSequenceBlueprint>());
    registry.addPrototype(std::make_shared<OnnxBlueprint>("onnx"));
    registry.addPrototype(std::make_shared<OnnxBlueprint>("onnxModel"));
    registry.addPrototype(std::make_shared<GreatCircleDistanceBlueprint>());

    // Ranking Expression
    auto replacers = std::make_unique<ListExpressionReplacer>();
    replacers->add(MaxReduceProdJoinReplacer::create());
    registry.addPrototype(std::make_shared<RankingExpressionBlueprint>(std::move(replacers)));
}

}
