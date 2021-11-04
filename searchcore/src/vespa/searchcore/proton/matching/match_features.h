// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/engine/searchreply.h>
#include <functional>

namespace proton::matching {

using HitIdVector = std::vector<uint32_t>; // D document ids

// must contain D*N feature values:

using FeatureValuesPerHit = search::engine::SearchReply::FeatureValues;

// must return values in same order as input "docs" param:
using MatchFeatureFinder = std::function<FeatureValuesPerHit::UP(const HitIdVector &docs)>;

}
