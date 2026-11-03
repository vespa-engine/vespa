// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "elementwise_output.h"

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/handle.h>
#include <vespa/vespalib/stllike/hash_map.h>

#include <vector>

namespace search::fef {
class FieldInfo;
class IQueryEnvironment;
class TermFieldMatchData;
} // namespace search::fef

namespace search::features {

/**
 * Executor for the elementwise matches feature over a single array of struct or map field. It builds an output tensor
 * with a single mapped dimension, with element id as label value and 1.0 as cell value for each element matched by a
 * sameElement query item searching the field. Element ids are taken from the positions in the match data for the
 * query items.
 */
class ElementwiseMatchesExecutor : public fef::FeatureExecutor {
    std::vector<fef::TermFieldHandle>           _handles;
    std::vector<const fef::TermFieldMatchData*> _tfmds;
    vespalib::hash_map<uint32_t, double>        _scores; // element id -> 1.0
    ElementwiseOutput                           _output;

public:
    ElementwiseMatchesExecutor(const fef::FieldInfo& field, const fef::IQueryEnvironment& env,
                               const vespalib::eval::Value& empty_output);
    ~ElementwiseMatchesExecutor() override;
    void handle_bind_match_data(const fef::MatchData& match_data) override;
    void execute(uint32_t docId) override;
};

} // namespace search::features
