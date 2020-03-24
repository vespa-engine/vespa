// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"
#include "nearest_neighbor_blueprint.h"
#include "nearest_neighbor_iterator.h"
#include "nns_index_iterator.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>

using vespalib::tensor::DenseTensorView;
using vespalib::tensor::DenseTensor;

namespace search::queryeval {

namespace {

template<typename LCT, typename RCT>
void
convert_cells(std::unique_ptr<DenseTensorView> &original, vespalib::eval::ValueType want_type)
{
    auto old_cells = original->cellsRef().typify<LCT>();
    std::vector<RCT> new_cells;
    new_cells.reserve(old_cells.size());
    for (LCT value : old_cells) {
        RCT conv = value;
        new_cells.push_back(conv);
    }
    original = std::make_unique<DenseTensor<RCT>>(want_type, std::move(new_cells));
}

template<>
void
convert_cells<float,float>(std::unique_ptr<DenseTensorView> &, vespalib::eval::ValueType) {}

template<>
void
convert_cells<double,double>(std::unique_ptr<DenseTensorView> &, vespalib::eval::ValueType) {}

struct ConvertCellsSelector
{
    template <typename LCT, typename RCT>
    static auto get_fun() { return convert_cells<LCT, RCT>; }
};

} // namespace <unnamed>

NearestNeighborBlueprint::NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                                                   const tensor::DenseTensorAttribute& attr_tensor,
                                                   std::unique_ptr<vespalib::tensor::DenseTensorView> query_tensor,
                                                   uint32_t target_num_hits, bool approximate, uint32_t explore_additional_hits)
    : ComplexLeafBlueprint(field),
      _attr_tensor(attr_tensor),
      _query_tensor(std::move(query_tensor)),
      _target_num_hits(target_num_hits),
      _approximate(approximate),
      _explore_additional_hits(explore_additional_hits),
      _fallback_dist_fun(),
      _distance_heap(target_num_hits),
      _found_hits()
{
    auto lct = _query_tensor->cellsRef().type;
    auto rct = _attr_tensor.getTensorType().cell_type();
    auto fixup_fun = vespalib::tensor::select_2<ConvertCellsSelector>(lct, rct);
    fixup_fun(_query_tensor, _attr_tensor.getTensorType());
    auto def_dm = search::attribute::DistanceMetric::Euclidean;
    _fallback_dist_fun = search::tensor::make_distance_function(def_dm, rct);
    _dist_fun = _fallback_dist_fun.get();
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (nns_index) {
        _dist_fun = nns_index->distance_function();
    }
    uint32_t est_hits = _attr_tensor.getNumDocs();
    if (_approximate && nns_index) {
        est_hits = std::min(target_num_hits, est_hits);
    }
    setEstimate(HitEstimate(est_hits, false));
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

void
NearestNeighborBlueprint::perform_top_k()
{
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (_approximate && nns_index) {
        auto lhs_type = _query_tensor->fast_type();
        auto rhs_type = _attr_tensor.getTensorType();
        // XXX deal with different cell types later
        if (lhs_type == rhs_type) {
            auto lhs = _query_tensor->cellsRef();
            uint32_t k = _target_num_hits;
            _found_hits = nns_index->find_top_k(k, lhs, k + _explore_additional_hits);
        }
    }
}

void
NearestNeighborBlueprint::fetchPostings(const ExecuteInfo &execInfo) {
    if (execInfo.isStrict()) {
        perform_top_k();
    }
}

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    fef::TermFieldMatchData &tfmd = *tfmda[0]; // always search in only one field
    if (strict && ! _found_hits.empty()) {
        return NnsIndexIterator::create(tfmd, _found_hits, _dist_fun);
    }
    const vespalib::tensor::DenseTensorView &qT = *_query_tensor;
    return NearestNeighborIterator::create(strict, tfmd, qT, _attr_tensor, _distance_heap, _dist_fun);
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor->type().to_spec());
    visitor.visitInt("target_num_hits", _target_num_hits);
    visitor.visitBool("approximate", _approximate);
    visitor.visitInt("explore_additional_hits", _explore_additional_hits);
}

bool
NearestNeighborBlueprint::always_needs_unpack() const
{
    return true;
}

}
