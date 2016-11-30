// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>

using search::tensor::DenseTensorAttribute;
using vespalib::eval::Tensor;
using vespalib::eval::TensorValue;
using vespalib::tensor::MutableDenseTensorView;

namespace search {
namespace features {

DenseTensorAttributeExecutor::
DenseTensorAttributeExecutor(const DenseTensorAttribute *attribute)
    : _attribute(attribute),
      _tensorView(),
      _tensor(std::unique_ptr<Tensor>())
{
    std::unique_ptr<MutableDenseTensorView> tensorView = std::make_unique<MutableDenseTensorView>(_attribute->getConfig().tensorType());
    _tensorView = tensorView.get();
    _tensor = TensorValue(std::move(tensorView));
}

void
DenseTensorAttributeExecutor::execute(fef::MatchData &data)
{
    _attribute->getTensor(data.getDocId(), *_tensorView);
    *data.resolve_object_feature(outputs()[0]) = _tensor;
}

} // namespace features
} // namespace search
