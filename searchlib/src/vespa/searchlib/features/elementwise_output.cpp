// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_output.h"
#include <vespa/searchlib/tensor/fast_value_view.h>


using search::tensor::FastValueView;
using vespalib::SharedStringRepo;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;

namespace search::features {

namespace {

struct InitializeCells {
    template <typename CT, typename VCT>
    static void invoke(VCT& cells) { cells = std::vector<CT>(); };
};

}

struct ElementwiseOutput::CallBuilderHelper {
    template <typename CT>
    static TypedCells invoke(ElementwiseOutput& output, const vespalib::hash_map<uint32_t, double>& scores) {
        return output.build_helper<CT>(scores);
    }
};


ElementwiseOutput::ElementwiseOutput(const vespalib::eval::Value& empty_output)
    : _labels(),
      _cells(),
      _empty_output(empty_output),
      _output()
{
    typify_invoke<1, TypifyCellType, InitializeCells>(_empty_output.type().cell_type(), _cells);
}

ElementwiseOutput::~ElementwiseOutput() = default;

template <typename CT>
TypedCells
ElementwiseOutput::build_helper(const vespalib::hash_map<uint32_t, double>& scores)
{
    auto& cells = std::get<std::vector<CT>>(_cells);
    cells.clear();
    for (auto& elem : scores) {
        _labels.push_back(SharedStringRepo::Handle::handle_from_number(elem.first));
        cells.emplace_back(static_cast<CT>(elem.second));
    }
    return TypedCells(cells);
}

const vespalib::eval::Value&
ElementwiseOutput::build(const vespalib::hash_map<uint32_t, double>& scores)
{
    if (scores.empty()) {
        return _empty_output;
    }
    _labels.clear();
    auto cells = typify_invoke<1, TypifyCellType, CallBuilderHelper>(_empty_output.type().cell_type(), *this, scores);
    _output = std::make_unique<FastValueView>(_empty_output.type(), _labels.view(), cells, 1, (size_t)cells.size);
    return *_output;
}

}
