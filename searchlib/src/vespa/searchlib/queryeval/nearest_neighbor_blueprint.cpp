// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"
#include "nearest_neighbor_blueprint.h"
#include "nearest_neighbor_iterator.h"
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
      _distance_heap(target_num_hits)
{
    setEstimate(HitEstimate(_attr_tensor.getNumDocs(), false));
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda, bool strict) const
{
    using StrictNN = NearestNeighborIterator<true>;
    using UnStrict = NearestNeighborIterator<false>;

    assert(tfmda.size() == 1);
    fef::TermFieldMatchData &tfmd = *tfmda[0]; // always search in only one field
    const vespalib::tensor::DenseTensorView &qT = *_query_tensor;

    if (strict) {
        return std::make_unique<StrictNN>(tfmd, qT, _attr_tensor, _distance_heap);
    } else {
        return std::make_unique<UnStrict>(tfmd, qT, _attr_tensor, _distance_heap);
    }
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
