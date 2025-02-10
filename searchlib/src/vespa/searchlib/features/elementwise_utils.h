// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/fef/parameter.h>
#include <optional>

namespace search::features {

/*
 * Class containing shared code between elementwise ranking features.
 */
class ElementwiseUtils {
    static std::string _elementwise_feature_base_name;
public:
    static const std::string& elementwise_feature_base_name() noexcept { return _elementwise_feature_base_name; }
    /*
     * Create elementwise rank feature name from inner feature base name and parameter list. This name can be used as
     * rank property key prefix when handling tuning. e.g. "bm25", ["i", "x", "float"] maps to
     * "elementwise(bm25(i),x,float)".
     */
    static std::string feature_name(const std::string& nested_feature_base_name, const fef::ParameterList& params);
    static std::optional<std::string> build_output_tensor_type(vespalib::eval::ValueType& output_tensor_type,
                                                               const std::string& dimension_name,
                                                               const std::string& cell_type_name);
};

}
