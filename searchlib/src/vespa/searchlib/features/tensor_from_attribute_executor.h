// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/tensor/default_tensor.h>

namespace search::features {

/**
 * Feature executor that extracts the content from an attribute vector
 * and converts that into a tensor.
 */
template <typename WeightedBufferType>
class TensorFromAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::attribute::IAttributeVector *_attribute;
    vespalib::string _dimension;
    WeightedBufferType _attrBuffer;
    std::unique_ptr<vespalib::tensor::Tensor> _tensor;

public:
    TensorFromAttributeExecutor(const search::attribute::IAttributeVector *attribute,
                                const vespalib::string &dimension)
        : _attribute(attribute),
          _dimension(dimension),
          _attrBuffer(),
          _tensor()
    {
        _attrBuffer.allocate(_attribute->getMaxValueCount());
    }
    void execute(uint32_t docId) override;
};

template <typename WeightedBufferType>
void
TensorFromAttributeExecutor<WeightedBufferType>::execute(uint32_t docId)
{
    _attrBuffer.fill(*_attribute, docId);
    vespalib::tensor::DefaultTensor::builder builder;
    vespalib::tensor::TensorBuilder::Dimension dimensionEnum = builder.define_dimension(_dimension);
    for (size_t i = 0; i < _attrBuffer.size(); ++i) {
        builder.add_label(dimensionEnum, vespalib::string(_attrBuffer[i].value()));
        builder.add_cell(_attrBuffer[i].weight());
    }
    _tensor = builder.build();
    outputs().set_object(0, *_tensor);
}

}
