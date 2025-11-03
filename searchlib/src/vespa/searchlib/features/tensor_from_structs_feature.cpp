// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_from_structs_feature.h"
#include "constant_tensor_executor.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.tensor_from_structs_feature");

using namespace search::fef;
using search::attribute::IAttributeVector;
using search::attribute::WeightedConstCharContent;
using search::attribute::WeightedStringContent;
using search::attribute::FloatContent;
using search::attribute::IntegerContent;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;
using vespalib::eval::CellType;
using vespalib::eval::TypifyCellType;
using vespalib::Issue;
using search::fef::FeatureType;

namespace search {
namespace features {

TensorFromStructsBlueprint::TensorFromStructsBlueprint()
    : TensorFactoryBlueprint("tensorFromStructs"),
      _keyField(),
      _valueField(),
      _cellType(CellType::DOUBLE)
{
}

bool
TensorFromStructsBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                   const search::fef::ParameterList &params)
{
    // _params[0] = source ('attribute(name)');
    // _params[1] = keyField;
    // _params[2] = valueField;
    // _params[3] = cellType (e.g., 'float', 'double');

    bool validSource = extractSource(params[0].getValue());
    if (! validSource) {
        return fail("invalid source: '%s'", params[0].getValue().c_str());
    }
    if (_sourceType != ATTRIBUTE_SOURCE) {
        return fail("only attribute source is supported for tensorFromStructs, got: '%s'", _sourceType.c_str());
    }

    _keyField = params[1].getValue();
    _valueField = params[2].getValue();

    auto cellTypeOpt = vespalib::eval::value_type::cell_type_from_name(params[3].getValue());
    if (!cellTypeOpt.has_value()) {
        return fail("invalid cell type: '%s'", params[3].getValue().c_str());
    }
    _cellType = cellTypeOpt.value();

    auto vt = ValueType::make_type(_cellType, {{_keyField}});
    _valueType = ValueType::from_spec(vt.to_spec());
    if (_valueType.is_error()) {
        return fail("invalid dimension name: '%s'", _keyField.c_str());
    }
    std::string keyAttrName = _sourceParam + "." + _keyField;
    std::string valueAttrName = _sourceParam + "." + _valueField;
    const fef::FieldInfo * kfInfo = env.getFieldByName(keyAttrName);
    const fef::FieldInfo * vfInfo = env.getFieldByName(valueAttrName);
    if (kfInfo == nullptr || ! kfInfo->hasAttribute()) {
        return fail("no such attribute '%s'", keyAttrName.c_str());
    }
    if (vfInfo == nullptr || ! vfInfo->hasAttribute()) {
        return fail("no such attribute '%s'", valueAttrName.c_str());
    }
    describeOutput("tensor",
                   "The tensor created from struct field attributes (key and value fields)",
                   FeatureType::object(_valueType));
    return true;
}

namespace {

template <typename KeyBufferType>
class TensorFromStructsExecutor : public fef::FeatureExecutor
{
private:
    const IAttributeVector *_keyAttribute;
    const IAttributeVector *_valueAttribute;
    vespalib::eval::ValueType _type;
    vespalib::eval::CellType _cellType;
    KeyBufferType _keyBuffer;
    FloatContent _valueBuffer;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorFromStructsExecutor(const IAttributeVector *keyAttr,
                             const IAttributeVector *valueAttr,
                             const vespalib::eval::ValueType &valueType,
                             vespalib::eval::CellType cellType)
        : _keyAttribute(keyAttr),
          _valueAttribute(valueAttr),
          _type(valueType),
          _cellType(cellType),
          _keyBuffer(),
          _valueBuffer(),
          _tensor()
    {
        _keyBuffer.allocate(_keyAttribute->getMaxValueCount());
        _valueBuffer.allocate(_valueAttribute->getMaxValueCount());
    }

    void execute(uint32_t docId) override;
};

template <typename KeyBufferType>
void
TensorFromStructsExecutor<KeyBufferType>::execute(uint32_t docId)
{
    _keyBuffer.fill(*_keyAttribute, docId);
    _valueBuffer.fill(*_valueAttribute, docId);

    size_t size = std::min(_keyBuffer.size(), _valueBuffer.size());

    auto factory = FastValueBuilderFactory::get();

    // Use TypifyCellType to handle all cell types
    _tensor = TypifyCellType::resolve(_cellType, [&](auto cell_type) {
        using CellType = typename decltype(cell_type)::type;
        auto builder = factory.create_value_builder<CellType>(_type, 1, 1, size);
        for (size_t i = 0; i < size; ++i) {
            std::string key(_keyBuffer[i].value());
            std::vector<std::string_view> addr = {key};
            auto cell_array = builder->add_subspace(addr);
            cell_array[0] = static_cast<CellType>(_valueBuffer[i]);
        }
        return builder->build(std::move(builder));
    });

    outputs().set_object(0, *_tensor);
}

FeatureExecutor &
createAttributeExecutor(const search::fef::IQueryEnvironment &env,
                        const std::string &baseAttrName,
                        const std::string &keyField,
                        const std::string &valueField,
                        const ValueType &valueType,
                        CellType cellType,
                        vespalib::Stash &stash)
{
    std::string keyAttrName = baseAttrName + "." + keyField;
    std::string valueAttrName = baseAttrName + "." + valueField;

    const IAttributeVector *keyAttribute = env.getAttributeContext().getAttribute(keyAttrName);
    if (keyAttribute == nullptr) {
        Issue::report("tensor_from_structs feature: The key attribute '%s' was not found."
                      " Returning empty tensor.", keyAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    const IAttributeVector *valueAttribute = env.getAttributeContext().getAttribute(valueAttrName);
    if (valueAttribute == nullptr) {
        Issue::report("tensor_from_structs feature: The value attribute '%s' was not found."
                      " Returning empty tensor.", valueAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    // Validate key attribute type
    if (keyAttribute->isFloatingPointType()) {
        Issue::report("tensor_from_structs feature: The key attribute '%s' must have basic type string or integer."
                      " Returning empty tensor.", keyAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    // Validate value attribute type
    if (!valueAttribute->isFloatingPointType() && !valueAttribute->isIntegerType()) {
        Issue::report("tensor_from_structs feature: The value attribute '%s' must have numeric type."
                      " Returning empty tensor.", valueAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    // Check collection type compatibility
    if (keyAttribute->getCollectionType() != valueAttribute->getCollectionType()) {
        Issue::report("tensor_from_structs feature: The key attribute '%s' and value attribute '%s' "
                      "must have the same collection type. Returning empty tensor.",
                      keyAttrName.c_str(), valueAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    // Weighted sets not supported (arrays are supported)
    if (keyAttribute->getCollectionType() == search::attribute::CollectionType::WSET) {
        Issue::report("tensor_from_structs feature: Weighted set attributes are not supported."
                      " Key attribute '%s' is a weighted set. Returning empty tensor.",
                      keyAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    // Choose appropriate key buffer type
    if (keyAttribute->isIntegerType()) {
        // Using WeightedStringContent ensures that the integer values are converted
        // to strings while extracting them from the attribute.
        return stash.create<TensorFromStructsExecutor<WeightedStringContent>>(
            keyAttribute, valueAttribute, valueType, cellType);
    }
    // When the underlying attribute is of type string we can reference these values
    // using WeightedConstCharContent.
    return stash.create<TensorFromStructsExecutor<WeightedConstCharContent>>(
        keyAttribute, valueAttribute, valueType, cellType);
}

}

FeatureExecutor &
TensorFromStructsBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_sourceType == ATTRIBUTE_SOURCE) {
        return createAttributeExecutor(env, _sourceParam, _keyField, _valueField, _valueType, _cellType, stash);
    }
    return ConstantTensorExecutor::createEmpty(_valueType, stash);
}

} // namespace features
} // namespace search
