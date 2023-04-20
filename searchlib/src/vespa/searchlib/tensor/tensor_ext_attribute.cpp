// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_ext_attribute.h"
#include "serialized_tensor_ref.h"
#include "vector_bundle.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.tensor_ext_attribute");

using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

std::unique_ptr<Value>
create_empty_tensor(const ValueType& type)
{
    const auto &factory = FastValueBuilderFactory::get();
    TensorSpec empty_spec(type.to_spec());
    return vespalib::eval::value_from_spec(empty_spec, factory);
}

}

TensorExtAttribute::TensorExtAttribute(const vespalib::string& name, const Config& cfg)
    : NotImplementedAttribute(name, cfg),
      ITensorAttribute(),
      IExtendAttribute(),
      _distance_function_factory(make_distance_function_factory(cfg.distance_metric(), cfg.tensorType().cell_type())),
      _subspace_type(cfg.tensorType()),
      _empty(_subspace_type),
      _empty_tensor(create_empty_tensor(cfg.tensorType()))
{
}

TensorExtAttribute::~TensorExtAttribute() = default;

const ITensorAttribute*
TensorExtAttribute::asTensorAttribute() const
{
    return this;
}

void
TensorExtAttribute::onCommit()
{
    LOG_ABORT("should not be reached");
}

void
TensorExtAttribute::onUpdateStat()
{
}

bool
TensorExtAttribute::addDoc(DocId& docId)
{
    docId = _data.size();
    _data.emplace_back(nullptr);
    incNumDocs();
    setCommittedDocIdLimit(getNumDocs());
    return true;
}

bool
TensorExtAttribute::add(const vespalib::eval::Value& v, int32_t)
{
    _data.back() = &v;
    return true;
}

IExtendAttribute*
TensorExtAttribute::getExtendInterface()
{
    return this;
}

TypedCells
TensorExtAttribute::get_vector(uint32_t docid, uint32_t subspace) const
{
    auto vectors = get_vectors(docid);
    return (subspace < vectors.subspaces()) ? vectors.cells(subspace) : _empty.cells();
}

VectorBundle
TensorExtAttribute::get_vectors(uint32_t docid) const
{
    auto tensor = _data[docid];
    if (tensor == nullptr) {
        return VectorBundle();
    }
    return VectorBundle(tensor->cells().data, tensor->index().size(), _subspace_type);
}

std::unique_ptr<Value>
TensorExtAttribute::getTensor(uint32_t docid) const
{
    auto tensor = _data[docid];
    if (tensor == nullptr) {
        return {};
    }
    return FastValueBuilderFactory::get().copy(*tensor);
}

std::unique_ptr<Value>
TensorExtAttribute::getEmptyTensor() const
{
    return FastValueBuilderFactory::get().copy(*_empty_tensor);
}

TypedCells
TensorExtAttribute::extract_cells_ref(uint32_t docid) const
{
    return get_vector(docid, 0);
}

const vespalib::eval::Value&
TensorExtAttribute::get_tensor_ref(uint32_t docid) const
{
    auto tensor = _data[docid];
    return (tensor == nullptr) ? *_empty_tensor : *tensor;
}

SerializedTensorRef
TensorExtAttribute::get_serialized_tensor_ref(uint32_t) const
{
    notImplemented();
}

bool
TensorExtAttribute::supports_extract_cells_ref() const
{
    return getConfig().tensorType().is_dense();
}

bool
TensorExtAttribute::supports_get_tensor_ref() const
{
    return true;
}

bool
TensorExtAttribute::supports_get_serialized_tensor_ref() const
{
    return false;
}

const ValueType&
TensorExtAttribute::getTensorType() const
{
    return getConfig().tensorType();
}

TensorExtAttribute::DistanceMetric
TensorExtAttribute::distance_metric() const
{
    return getConfig().distance_metric();
}

uint32_t
TensorExtAttribute::get_num_docs() const
{
    return _data.size();
}

void
TensorExtAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    (void) inserter;
}

}
