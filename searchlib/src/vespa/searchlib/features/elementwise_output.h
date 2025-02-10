// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <optional>
#include <variant>

namespace search::features {

/*
 * Class containing a locally built output tensor for elementwise features. The tensor has a single mapped dimension
 * with element id as label value and the score for the element as cell value. Lifetime of output tensor is until
 * build() or destructor is called.
 */
class ElementwiseOutput {
        struct CallBuilderHelper;
        friend struct CallBuilderHelper;
        std::optional<vespalib::SharedStringRepo::Handles> _labels;
        std::variant<std::monostate, std::vector<double>, std::vector<float>, std::vector<vespalib::BFloat16>, std::vector<vespalib::eval::Int8Float>> _cells;
        const vespalib::eval::Value& _empty_output;
        std::unique_ptr<vespalib::eval::Value> _output;

       template <typename CT>
       vespalib::eval::TypedCells build_helper(const vespalib::hash_map<uint32_t, double>& scores);
    public:
        ElementwiseOutput(const vespalib::eval::Value& empty_output);
        ~ElementwiseOutput();
        const vespalib::eval::Value &build(const vespalib::hash_map<uint32_t, double>& scores);
};

}
