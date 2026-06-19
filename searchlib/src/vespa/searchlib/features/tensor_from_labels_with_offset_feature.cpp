// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_from_labels_with_offset_feature.h"

#include "constant_tensor_executor.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.tensor_from_labels_with_offset_feature");

using namespace search::fef;
using search::attribute::IAttributeVector;
using search::attribute::WeightedConstCharContent;
using search::attribute::WeightedStringContent;
using search::fef::FeatureType;
using vespalib::Issue;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;

namespace search {
namespace features {

TensorFromLabelsWithOffsetBlueprint::TensorFromLabelsWithOffsetBlueprint()
    : TensorFactoryBlueprint("tensorFromLabelsWithOffset"), _offset_dimension() {
}

TensorFromLabelsWithOffsetBlueprint::~TensorFromLabelsWithOffsetBlueprint() = default;

bool TensorFromLabelsWithOffsetBlueprint::setup(const search::fef::IIndexEnvironment& env,
                                                const search::fef::ParameterList&     params) {
    (void)env;
    // _params[0] = source ('attribute(name)');
    // _params[1] = dimension name for label values;
    // _params[2] = dimension name for array indices (offset);
    bool validSource = extractSource(params[0].getValue());
    if (!validSource) {
        return fail("invalid source: '%s'", params[0].getValue().c_str());
    }
    if (_sourceType != ATTRIBUTE_SOURCE) {
        return fail("invalid source: '%s', only 'attribute(name)' is supported", params[0].getValue().c_str());
    }
    _dimension = params[1].getValue();
    _offset_dimension = params[2].getValue();
    auto vt = ValueType::make_type(CellType::FLOAT, {{_dimension}, {_offset_dimension}});
    _valueType = ValueType::from_spec(vt.to_spec());
    if (_valueType.is_error()) {
        return fail("invalid dimension names: '%s', '%s'", _dimension.c_str(), _offset_dimension.c_str());
    }
    describeOutput("tensor",
                   "The tensor created from the given source with label values and array-index offset dimensions",
                   FeatureType::object(_valueType));
    return true;
}

namespace {

// ValueType::make_type sorts dimensions alphabetically; this executor resolves
// the correct address slot for each dimension at construction time.
template <typename WeightedBufferType>
class TensorFromLabelsWithOffsetAttributeExecutor : public fef::FeatureExecutor {
private:
    const search::attribute::IAttributeVector* _attribute;
    vespalib::eval::ValueType                  _type;
    WeightedBufferType                         _attrBuffer;
    std::vector<std::string_view>              _addr_ref;
    std::unique_ptr<vespalib::eval::Value>     _tensor;
    size_t                                     _label_idx;
    size_t                                     _offset_idx;
    bool                                       _is_single_value;

public:
    TensorFromLabelsWithOffsetAttributeExecutor(const search::attribute::IAttributeVector* attribute,
                                                const vespalib::eval::ValueType&           valueType,
                                                const std::string& label_dim, const std::string& offset_dim)
        : _attribute(attribute),
          _type(valueType),
          _attrBuffer(),
          _addr_ref(2),
          _tensor(),
          _label_idx(0),
          _offset_idx(1),
          _is_single_value(attribute->getCollectionType() == search::attribute::CollectionType::SINGLE) {
        _attrBuffer.allocate(_attribute->getMaxValueCount());
        const auto& dims = _type.dimensions();
        for (size_t d = 0; d < dims.size(); ++d) {
            if (dims[d].name == label_dim)
                _label_idx = d;
            if (dims[d].name == offset_dim)
                _offset_idx = d;
        }
    }

    void execute(uint32_t docId) override;
};

template <typename WeightedBufferType>
void TensorFromLabelsWithOffsetAttributeExecutor<WeightedBufferType>::execute(uint32_t docId) {
    _attrBuffer.fill(*_attribute, docId);
    auto factory = FastValueBuilderFactory::get();
    auto builder = factory.create_value_builder<float>(_type, 2, 1, _attrBuffer.size());
    bool ignore = _is_single_value && _attribute->isUndefined(docId);
    for (size_t i = 0; i < _attrBuffer.size() && !ignore; ++i) {
        std::string label(_attrBuffer[i].value());
        std::string index_str = std::to_string(i);
        _addr_ref[_label_idx] = label;
        _addr_ref[_offset_idx] = index_str;
        auto cell_array = builder->add_subspace(_addr_ref);
        cell_array[0] = 1.0;
    }
    _tensor = builder->build(std::move(builder));
    outputs().set_object(0, *_tensor);
}

FeatureExecutor& createAttributeExecutor(const search::fef::IQueryEnvironment& env, const std::string& attrName,
                                         const ValueType& valueType, const std::string& label_dim,
                                         const std::string& offset_dim, vespalib::Stash& stash) {
    const IAttributeVector* attribute = env.getAttributeContext().getAttribute(attrName);
    if (attribute == nullptr) {
        Issue::report("tensor_from_labels_with_offset feature: The attribute vector '%s' was not found."
                      " Returning empty tensor.",
                      attrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }
    if (attribute->isFloatingPointType()) {
        Issue::report("tensor_from_labels_with_offset feature: The attribute vector '%s' must have basic type"
                      " string or integer. Returning empty tensor.",
                      attrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }
    if (attribute->getCollectionType() == search::attribute::CollectionType::WSET) {
        Issue::report("tensor_from_labels_with_offset feature: The attribute vector '%s' is a weighted set - use"
                      " tensorFromWeightedSet instead. Returning empty tensor.",
                      attrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }
    if (attribute->isIntegerType()) {
        return stash.create<TensorFromLabelsWithOffsetAttributeExecutor<WeightedStringContent>>(
            attribute, valueType, label_dim, offset_dim);
    }
    return stash.create<TensorFromLabelsWithOffsetAttributeExecutor<WeightedConstCharContent>>(attribute, valueType,
                                                                                               label_dim, offset_dim);
}

} // namespace

FeatureExecutor& TensorFromLabelsWithOffsetBlueprint::createExecutor(const search::fef::IQueryEnvironment& env,
                                                                     vespalib::Stash& stash) const {
    if (_sourceType == ATTRIBUTE_SOURCE) {
        return createAttributeExecutor(env, _sourceParam, _valueType, _dimension, _offset_dimension, stash);
    }
    return ConstantTensorExecutor::createEmpty(_valueType, stash);
}

} // namespace features
} // namespace search
