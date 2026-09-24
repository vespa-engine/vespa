// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_matches_blueprint.h"

#include "elementwise_matches_executor.h"
#include "elementwise_utils.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

using fef::FeatureType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;

ElementwiseMatchesBlueprint::ElementwiseMatchesBlueprint()
    : fef::Blueprint("elementwiseMatches"),
      _field(nullptr),
      _output_tensor_type(ValueType::error_type()),
      _empty_output() {
}

ElementwiseMatchesBlueprint::~ElementwiseMatchesBlueprint() = default;

void ElementwiseMatchesBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const {
}

std::unique_ptr<fef::Blueprint> ElementwiseMatchesBlueprint::createInstance() const {
    return std::make_unique<ElementwiseMatchesBlueprint>();
}

fef::ParameterDescriptions ElementwiseMatchesBlueprint::getDescriptions() const {
    return fef::ParameterDescriptions().desc().field().string().string();
}

bool ElementwiseMatchesBlueprint::setup(const fef::IIndexEnvironment&, const fef::ParameterList& params) {
    _field = params[0].asField();
    if (_field->type() != fef::FieldType::VIRTUAL) {
        return fail("field '%s' is not an array of struct or map field with struct-fields that can be matched "
                    "(with indexed search, at least one struct-field must be an attribute)",
                    _field->name().c_str());
    }
    auto fail_message =
        ElementwiseUtils::build_output_tensor_type(_output_tensor_type, params[1].getValue(), params[2].getValue());
    if (fail_message.has_value()) {
        return fail("%s", fail_message.value().c_str());
    }
    _empty_output = vespalib::eval::value_from_spec(_output_tensor_type.to_spec(), FastValueBuilderFactory::get());
    FeatureType output_type = FeatureType::object(_output_tensor_type);
    describeOutput("score", "Tensor with value 1.0 for each element in the given field matched by the query",
                   output_type);
    return true;
}

fef::FeatureExecutor& ElementwiseMatchesBlueprint::createExecutor(const fef::IQueryEnvironment& env,
                                                                  vespalib::Stash&              stash) const {
    return stash.create<ElementwiseMatchesExecutor>(*_field, env, *_empty_output);
}

} // namespace search::features
