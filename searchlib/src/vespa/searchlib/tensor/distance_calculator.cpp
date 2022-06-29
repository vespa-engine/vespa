// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_calculator.h"
#include "distance_function_factory.h"
#include "i_tensor_attribute.h"
#include "nearest_neighbor_index.h"
#include <vespa/eval/eval/fast_value.h>

using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

template<typename LCT, typename RCT>
std::unique_ptr<Value>
convert_cells(const ValueType& new_type, std::unique_ptr<Value> old_value)
{
    auto old_cells = old_value->cells().typify<LCT>();
    auto builder = FastValueBuilderFactory::get().create_value_builder<RCT>(new_type);
    auto new_cells = builder->add_subspace();
    assert(old_cells.size() == new_cells.size());
    auto p = new_cells.begin();
    for (LCT value : old_cells) {
        RCT conv(value);
        *p++ = conv;
    }
    return builder->build(std::move(builder));
}

struct ConvertCellsSelector
{
    template <typename LCT, typename RCT>
    static auto invoke(const ValueType& new_type, std::unique_ptr<Value> old_value) {
        return convert_cells<LCT, RCT>(new_type, std::move(old_value));
    }
    auto operator() (CellType from, CellType to, std::unique_ptr<Value> old_value) const {
        using MyTypify = vespalib::eval::TypifyCellType;
        ValueType new_type = old_value->type().cell_cast(to);
        return vespalib::typify_invoke<2,MyTypify,ConvertCellsSelector>(from, to, new_type, std::move(old_value));
    }
};

}

namespace search::tensor {

DistanceCalculator::DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                                       std::unique_ptr<vespalib::eval::Value> query_tensor_in)
    : _attr_tensor(attr_tensor),
      _query_tensor_uptr(std::move(query_tensor_in)),
      _query_tensor(),
      _query_tensor_cells(),
      _dist_fun_uptr(make_distance_function(_attr_tensor.distance_metric(),
                                           _attr_tensor.getTensorType().cell_type())),
      _dist_fun(_dist_fun_uptr.get())
{
    assert(_dist_fun);
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (nns_index) {
        _dist_fun = nns_index->distance_function();
        assert(_dist_fun);
    }
    auto query_ct = _query_tensor_uptr->cells().type;
    CellType required_ct = _dist_fun->expected_cell_type();
    if (query_ct != required_ct) {
        ConvertCellsSelector converter;
        _query_tensor_uptr = converter(query_ct, required_ct, std::move(_query_tensor_uptr));
    }
    _query_tensor = _query_tensor_uptr.get();
    _query_tensor_cells = _query_tensor->cells();
}

DistanceCalculator::DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                                       const vespalib::eval::Value& query_tensor_in,
                                       const DistanceFunction& function_in)
    : _attr_tensor(attr_tensor),
      _query_tensor_uptr(),
      _query_tensor(&query_tensor_in),
      _query_tensor_cells(_query_tensor->cells()),
      _dist_fun_uptr(),
      _dist_fun(&function_in)
{
}

DistanceCalculator::~DistanceCalculator() = default;

double
DistanceCalculator::calc_with_limit(uint32_t docid, double limit) const
{
    auto rhs = _attr_tensor.extract_cells_ref(docid);
    return _dist_fun->calc_with_limit(_query_tensor_cells, rhs, limit);
}

}

