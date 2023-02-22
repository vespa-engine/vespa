// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_tensor_attribute_vector_read_guard.h"
#include "serialized_tensor_ref.h"
#include "vector_bundle.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/eval/eval/value.h>

namespace search::tensor {

namespace {

const ITensorAttribute &
getTensorAttribute(const search::attribute::IAttributeVector &attr)
{
    const ITensorAttribute *result = attr.asTensorAttribute();
    assert(result != nullptr);
    return *result;
}

}

ImportedTensorAttributeVectorReadGuard::ImportedTensorAttributeVectorReadGuard(std::shared_ptr<MetaStoreReadGuard> targetMetaStoreReadGuard,
                                                                               const attribute::ImportedAttributeVector &imported_attribute,
                                                                               bool stableEnumGuard)
    : ImportedAttributeVectorReadGuard(std::move(targetMetaStoreReadGuard), imported_attribute, stableEnumGuard),
      _target_tensor_attribute(getTensorAttribute(_target_attribute))
{
}

ImportedTensorAttributeVectorReadGuard::~ImportedTensorAttributeVectorReadGuard()
{
}

const ITensorAttribute *
ImportedTensorAttributeVectorReadGuard::asTensorAttribute() const
{
    return this;
}

std::unique_ptr<vespalib::eval::Value>
ImportedTensorAttributeVectorReadGuard::getTensor(uint32_t docId) const
{
    return _target_tensor_attribute.getTensor(getTargetLid(docId));
}

std::unique_ptr<vespalib::eval::Value>
ImportedTensorAttributeVectorReadGuard::getEmptyTensor() const
{
    return _target_tensor_attribute.getEmptyTensor();
}

vespalib::eval::TypedCells
ImportedTensorAttributeVectorReadGuard::extract_cells_ref(uint32_t docid) const
{
    return _target_tensor_attribute.extract_cells_ref(getTargetLid(docid));
}

const vespalib::eval::Value&
ImportedTensorAttributeVectorReadGuard::get_tensor_ref(uint32_t docid) const
{
    return _target_tensor_attribute.get_tensor_ref(getTargetLid(docid));
}

vespalib::eval::TypedCells
ImportedTensorAttributeVectorReadGuard::get_vector(uint32_t docid, uint32_t subspace) const
{
    return _target_tensor_attribute.get_vector(getTargetLid(docid), subspace);
}

search::tensor::VectorBundle
ImportedTensorAttributeVectorReadGuard::get_vectors(uint32_t docid) const
{
    return _target_tensor_attribute.get_vectors(getTargetLid(docid));
}

const vespalib::eval::ValueType &
ImportedTensorAttributeVectorReadGuard::getTensorType() const
{
    return _target_tensor_attribute.getTensorType();
}

SerializedTensorRef
ImportedTensorAttributeVectorReadGuard::get_serialized_tensor_ref(uint32_t docid) const
{
    return _target_tensor_attribute.get_serialized_tensor_ref(getTargetLid(docid));
}

bool
ImportedTensorAttributeVectorReadGuard::supports_get_serialized_tensor_ref() const
{
    return _target_tensor_attribute.supports_get_serialized_tensor_ref();
}

void
ImportedTensorAttributeVectorReadGuard::get_state(const vespalib::slime::Inserter& inserter) const
{
    _target_tensor_attribute.get_state(inserter);
}

}
