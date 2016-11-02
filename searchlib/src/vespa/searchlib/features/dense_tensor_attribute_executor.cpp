// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>

using search::attribute::DenseTensorAttribute;
using vespalib::eval::TensorValue;
using vespalib::eval::Tensor;
using vespalib::tensor::MutableDenseTensorView;

namespace search {
namespace features {

namespace {

MutableDenseTensorView *
extractConcreteTensor(TensorValue &tensorValue)
{
    Tensor *tensor = const_cast<Tensor *>(tensorValue.as_tensor());
    MutableDenseTensorView *tensorView = dynamic_cast<MutableDenseTensorView *>(tensor);
    assert(tensorView != nullptr);
    return tensorView;
}

}

DenseTensorAttributeExecutor::
DenseTensorAttributeExecutor(const DenseTensorAttribute *attribute)
    : _attribute(attribute),
      _tensor(std::make_unique<MutableDenseTensorView>(_attribute->getConfig().tensorType())),
      _tensorView(extractConcreteTensor(_tensor))
{
}

void
DenseTensorAttributeExecutor::execute(fef::MatchData &data)
{
    _attribute->getTensor(data.getDocId(), *_tensorView);
    *data.resolve_object_feature(outputs()[0]) = _tensor;
}

} // namespace features
} // namespace search
