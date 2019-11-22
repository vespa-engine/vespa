// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"
#include "nearest_neighbor_blueprint.h"
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
      _target_num_hits(target_num_hits)
{
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda, bool strict) const
{
    (void) tfmda;
    (void) strict;
    // TODO (geirst): implement
    return std::make_unique<EmptySearch>();
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor->type().to_spec());
}

}
