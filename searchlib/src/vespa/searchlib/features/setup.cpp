// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "setup.h"

#include "agefeature.h"
#include "attributefeature.h"
#include "attributematchfeature.h"
#include "closenessfeature.h"
#include "debug_attribute_wait.h"
#include "debug_wait.h"
#include "distancefeature.h"
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
#include "proximityfeature.h"
#include "querycompletenessfeature.h"
#include "queryfeature.h"
#include "querytermcountfeature.h"
#include "randomfeature.h"
#include "random_normal_feature.h"
#include "random_normal_stable_feature.h"
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
#include "constant_feature.h"

#include <vespa/searchlib/features/max_reduce_prod_join_replacer.h>
#include <vespa/searchlib/features/rankingexpression/expression_replacer.h>

using search::fef::Blueprint;
using search::features::rankingexpression::ListExpressionReplacer;
using search::features::MaxReduceProdJoinReplacer;

namespace search {
namespace features {

void setup_search_features(fef::IBlueprintRegistry & registry)
{
    // Prod features.
    registry.addPrototype(Blueprint::SP(new AgeBlueprint()));
    registry.addPrototype(Blueprint::SP(new AttributeBlueprint()));
    registry.addPrototype(Blueprint::SP(new AttributeMatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new ClosenessBlueprint()));
    registry.addPrototype(Blueprint::SP(new MatchCountBlueprint()));
    registry.addPrototype(Blueprint::SP(new DistanceBlueprint()));
    registry.addPrototype(Blueprint::SP(new DistanceToPathBlueprint()));
    registry.addPrototype(Blueprint::SP(new DebugAttributeWaitBlueprint()));
    registry.addPrototype(Blueprint::SP(new DebugWaitBlueprint()));
    registry.addPrototype(Blueprint::SP(new DotProductBlueprint()));
    registry.addPrototype(Blueprint::SP(new ElementCompletenessBlueprint()));
    registry.addPrototype(Blueprint::SP(new ElementSimilarityBlueprint()));
    registry.addPrototype(Blueprint::SP(new EuclideanDistanceBlueprint()));
    registry.addPrototype(Blueprint::SP(new FieldInfoBlueprint()));
    registry.addPrototype(Blueprint::SP(new FlowCompletenessBlueprint()));
    registry.addPrototype(Blueprint::SP(new FieldLengthBlueprint()));
    registry.addPrototype(Blueprint::SP(new FieldMatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new FieldTermMatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new FirstPhaseBlueprint()));
    registry.addPrototype(Blueprint::SP(new ForeachBlueprint()));
    registry.addPrototype(Blueprint::SP(new FreshnessBlueprint()));
    registry.addPrototype(Blueprint::SP(new ItemRawScoreBlueprint()));
    registry.addPrototype(Blueprint::SP(new MatchesBlueprint()));
    registry.addPrototype(Blueprint::SP(new MatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new NativeAttributeMatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new NativeDotProductBlueprint()));
    registry.addPrototype(Blueprint::SP(new NativeFieldMatchBlueprint()));
    registry.addPrototype(Blueprint::SP(new NativeProximityBlueprint()));
    registry.addPrototype(Blueprint::SP(new NativeRankBlueprint()));
    registry.addPrototype(Blueprint::SP(new NowBlueprint()));
    registry.addPrototype(Blueprint::SP(new QueryBlueprint()));
    registry.addPrototype(Blueprint::SP(new QueryTermCountBlueprint()));
    registry.addPrototype(Blueprint::SP(new RandomBlueprint()));
    registry.addPrototype(Blueprint::SP(new RandomNormalBlueprint()));
    registry.addPrototype(Blueprint::SP(new RandomNormalStableBlueprint()));
    registry.addPrototype(Blueprint::SP(new RawScoreBlueprint()));
    registry.addPrototype(Blueprint::SP(new SubqueriesBlueprint));
    registry.addPrototype(Blueprint::SP(new TensorFromLabelsBlueprint()));
    registry.addPrototype(Blueprint::SP(new TensorFromWeightedSetBlueprint()));
    registry.addPrototype(Blueprint::SP(new TermBlueprint()));
    registry.addPrototype(Blueprint::SP(new TermDistanceBlueprint()));
    registry.addPrototype(Blueprint::SP(new TermInfoBlueprint()));
    registry.addPrototype(Blueprint::SP(new TextSimilarityBlueprint()));
    registry.addPrototype(Blueprint::SP(new ValueBlueprint()));

    // Beta features.
    registry.addPrototype(Blueprint::SP(new JaroWinklerDistanceBlueprint()));
    registry.addPrototype(Blueprint::SP(new ProximityBlueprint()));
    registry.addPrototype(Blueprint::SP(new QueryCompletenessBlueprint()));
    registry.addPrototype(Blueprint::SP(new ReverseProximityBlueprint()));
    registry.addPrototype(Blueprint::SP(new TermEditDistanceBlueprint()));
    registry.addPrototype(Blueprint::SP(new TermFieldMdBlueprint()));
    registry.addPrototype(std::make_shared<ConstantBlueprint>());

    // Ranking Expression
    auto replacers = std::make_unique<ListExpressionReplacer>();
    replacers->add(MaxReduceProdJoinReplacer::create());
    registry.addPrototype(std::make_shared<RankingExpressionBlueprint>(std::move(replacers)));
}

} // namespace features
} // namespace search
