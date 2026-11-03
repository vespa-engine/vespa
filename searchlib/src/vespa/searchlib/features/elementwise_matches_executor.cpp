// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_matches_executor.h"

#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/match_data_details.h>
#include <vespa/searchlib/fef/matchdata.h>

namespace search::features {

using fef::ITermData;
using fef::ITermFieldData;
using fef::MatchDataDetails;
using vespalib::eval::Value;

ElementwiseMatchesExecutor::ElementwiseMatchesExecutor(const fef::FieldInfo& field, const fef::IQueryEnvironment& env,
                                                       const Value& empty_output)
    : FeatureExecutor(), _handles(), _tfmds(), _scores(), _output(empty_output) {
    for (size_t i = 0; i < env.getNumTerms(); ++i) {
        const ITermData* term = env.getTerm(i);
        for (size_t j = 0; j < term->numFields(); ++j) {
            const ITermFieldData& term_field = term->field(j);
            if (field.id() == term_field.getFieldId()) {
                auto handle = term_field.getHandle(MatchDataDetails::Normal);
                if (handle != fef::IllegalHandle) {
                    _handles.emplace_back(handle);
                }
            }
        }
    }
}

ElementwiseMatchesExecutor::~ElementwiseMatchesExecutor() = default;

void ElementwiseMatchesExecutor::handle_bind_match_data(const fef::MatchData& match_data) {
    _tfmds.clear();
    for (auto handle : _handles) {
        _tfmds.emplace_back(match_data.resolveTermField(handle));
    }
}

void ElementwiseMatchesExecutor::execute(uint32_t doc_id) {
    _scores.clear();
    for (const auto* tfmd : _tfmds) {
        if (tfmd->has_ranking_data(doc_id)) {
            for (const auto& pos : *tfmd) {
                _scores.insert(std::make_pair(pos.getElementId(), 1.0));
            }
        }
    }
    outputs().set_object(0, _output.build(_scores));
}

} // namespace search::features
