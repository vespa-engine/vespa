// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "average_field_length_feature.h"

#include "valuefeature.h"

#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/vespalib/util/stash.h>

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::IDumpFeatureVisitor;
using search::fef::IIndexEnvironment;
using search::fef::IQueryEnvironment;
using search::fef::ParameterList;

namespace search::features {

AverageFieldLengthBlueprint::AverageFieldLengthBlueprint() : Blueprint("averageFieldLength"), _field(nullptr) {
}

void AverageFieldLengthBlueprint::visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const {
}

bool AverageFieldLengthBlueprint::setup(const IIndexEnvironment& env, const ParameterList& params) {
    const auto& field_name = params[0].getValue();
    _field = env.getFieldByName(field_name);
    if (_field == nullptr) {
        return false;
    }
    describeOutput("out", "The average length of this index field.");
    return true;
}

Blueprint::UP AverageFieldLengthBlueprint::createInstance() const {
    return std::make_unique<AverageFieldLengthBlueprint>();
}

FeatureExecutor& AverageFieldLengthBlueprint::createExecutor(const IQueryEnvironment& env,
                                                             vespalib::Stash&         stash) const {
    if (_field == nullptr) {
        return stash.create<SingleValueExecutor>(0.0);
    }
    // Local content-node field-length statistic; not cluster-wide and not bm25(field).averageFieldLength overrides.
    double avg_len = env.get_field_length_info(_field->name()).get_average_field_length();
    return stash.create<SingleValueExecutor>(avg_len);
}

} // namespace search::features
