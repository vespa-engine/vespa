// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/tensor/sparse/direct_sparse_tensor_builder.h>

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
    vespalib::eval::ValueType _type;
    WeightedBufferType _attrBuffer;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorFromAttributeExecutor(const search::attribute::IAttributeVector *attribute,
                                const vespalib::string &dimension)
        : _attribute(attribute),
          _type(vespalib::eval::ValueType::tensor_type({{dimension}})),
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
    vespalib::tensor::DirectSparseTensorBuilder<double> builder(_type);
    builder.reserve(_attrBuffer.size());
    vespalib::tensor::SparseTensorAddressBuilder address;
    for (size_t i = 0; i < _attrBuffer.size(); ++i) {
        address.clear();
        address.add(vespalib::string(_attrBuffer[i].value()));
        builder.insertCell(address, _attrBuffer[i].weight(), [](double, double v){ return v; });
    }
    _tensor = builder.build();
    outputs().set_object(0, *_tensor);
}

}
