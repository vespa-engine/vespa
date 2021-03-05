// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>

using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::CellType;

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
    std::vector<vespalib::stringref> _addr_ref;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorFromAttributeExecutor(const search::attribute::IAttributeVector *attribute,
                                const vespalib::string &dimension)
        : _attribute(attribute),
          _type(vespalib::eval::ValueType::make_type(CellType::DOUBLE, {{dimension}})),
          _attrBuffer(),
          _addr_ref(),
          _tensor()
    {
        _attrBuffer.allocate(_attribute->getMaxValueCount());
        _addr_ref.reserve(1);
    }
    void execute(uint32_t docId) override;
};

template <typename WeightedBufferType>
void
TensorFromAttributeExecutor<WeightedBufferType>::execute(uint32_t docId)
{
    _attrBuffer.fill(*_attribute, docId);
    auto factory = FastValueBuilderFactory::get();
    auto builder = factory.create_value_builder<double>(_type, 1, 1, _attrBuffer.size());
    for (size_t i = 0; i < _attrBuffer.size(); ++i) {
        vespalib::string label(_attrBuffer[i].value());
        _addr_ref.clear();
        _addr_ref.push_back(label);
        auto cell_array = builder->add_subspace(_addr_ref);
        cell_array[0] = _attrBuffer[i].weight();
    }
    _tensor = builder->build(std::move(builder));
    outputs().set_object(0, *_tensor);
}

}
