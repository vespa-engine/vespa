// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_utils.h"

#include <memory>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::features {

using fef::FeatureNameBuilder;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;
using vespalib::make_string;

std::string ElementwiseUtils::_elementwise_feature_base_name = "elementwise";

std::string
ElementwiseUtils::feature_name(const std::string& nested_feature_base_name, const fef::ParameterList& params)
{
    FeatureNameBuilder builder;
    constexpr size_t extra_params = 2;
    builder.baseName(nested_feature_base_name);
    size_t num_params = params.size();
    size_t param_idx = 0;
    for (; param_idx + extra_params < num_params; ++param_idx) {
        builder.parameter(params[param_idx].getValue());
    }
    auto nested_feature_name = builder.buildName();
    builder.baseName(_elementwise_feature_base_name);
    builder.clearParameters();
    builder.parameter(nested_feature_name);
    for (; param_idx < num_params; ++param_idx) {
        builder.parameter(params[param_idx].getValue());
    }
    return builder.buildName();
}

std::optional<std::string>
ElementwiseUtils::build_output_tensor_type(ValueType& output_tensor_type, const std::string& dimension_name,
                                           const std::string& cell_type_name)
{
    auto cell_type = vespalib::eval::value_type::cell_type_from_name(cell_type_name);
    if (!cell_type.has_value()) {
        return make_string("'%s' is not a valid tensor cell type", cell_type_name.c_str());
    }
    output_tensor_type = ValueType::make_type(cell_type.value(), {{dimension_name}});
    return std::nullopt;
}

}
