// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"
#include "nearest_neighbor_blueprint.h"
#include "nearest_neighbor_iterator.h"
#include "nns_index_iterator.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>

namespace search::queryeval {

NearestNeighborBlueprint::NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                                                   const tensor::DenseTensorAttribute& attr_tensor,
                                                   std::unique_ptr<vespalib::tensor::DenseTensorView> query_tensor,
                                                   uint32_t target_num_hits)
    : ComplexLeafBlueprint(field),
      _attr_tensor(attr_tensor),
      _query_tensor(std::move(query_tensor)),
      _target_num_hits(target_num_hits),
      _distance_heap(target_num_hits),
      _found_hits()
{
    setEstimate(HitEstimate(_attr_tensor.getNumDocs(), false));
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

void
NearestNeighborBlueprint::perform_top_k()
{
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (nns_index) {
        auto lhs_type = _query_tensor->fast_type();
        auto rhs_type = _attr_tensor.getTensorType();
        if (lhs_type == rhs_type) {
            auto lhs = _query_tensor->cellsRef();
            uint32_t k = _target_num_hits;
            uint32_t explore_k = k + 100; // XXX hardcoded for now
            _found_hits = nns_index->find_top_k(k, lhs, explore_k);
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
        return NnsIndexIterator::create(strict, tfmd, _found_hits);
    }
    const vespalib::tensor::DenseTensorView &qT = *_query_tensor;
    return NearestNeighborIterator::create(strict, tfmd, qT, _attr_tensor, _distance_heap);
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor->type().to_spec());
    visitor.visitInt("target_num_hits", _target_num_hits);
}

bool
NearestNeighborBlueprint::always_needs_unpack() const
{
    return true;
}

}
