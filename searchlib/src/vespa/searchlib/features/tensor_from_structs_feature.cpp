// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_from_structs_feature.h"

#include "constant_tensor_executor.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.tensor_from_structs_feature");

using namespace search::fef;
using search::attribute::FloatContent;
using search::attribute::IAttributeVector;
using search::attribute::IntegerContent;
using search::attribute::WeightedConstCharContent;
using search::attribute::WeightedStringContent;
using search::fef::FeatureType;
using vespalib::Issue;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypifyCellType;
using vespalib::eval::ValueType;

namespace search {
namespace features {

TensorFromStructsBlueprint::TensorFromStructsBlueprint()
    : TensorFactoryBlueprint("tensorFromStructs"), _keyFields(), _valueField(), _cellType(CellType::DOUBLE) {
}

TensorFromStructsBlueprint::~TensorFromStructsBlueprint() = default;

bool TensorFromStructsBlueprint::setup(const search::fef::IIndexEnvironment& env,
                                       const search::fef::ParameterList&     params) {
    // params[0]       = source ('attribute(name)')
    // params[1..N]    = key fields (1 to 5)
    // params[N+1]     = value field
    // params[N+2]     = cell type (e.g. 'float', 'double')

    bool validSource = extractSource(params[0].getValue());
    if (!validSource) {
        return fail("invalid source: '%s'", params[0].getValue().c_str());
    }
    if (_sourceType != ATTRIBUTE_SOURCE) {
        return fail("only attribute source is supported for tensorFromStructs, got: '%s'", _sourceType.c_str());
    }

    const size_t total = params.size();
    if (total < 4 || total > 8) {
        // Note: this should be checked already from ParameterDescriptions
        return fail("expected 4 to 8 parameters, got %zu", total);
    }
    const size_t numKeys = total - 3;

    _keyFields.clear();
    _keyFields.reserve(numKeys);
    for (size_t i = 0; i < numKeys; ++i) {
        _keyFields.push_back(params[1 + i].getValue());
    }
    _valueField = params[1 + numKeys].getValue();

    auto cellTypeOpt = vespalib::eval::value_type::cell_type_from_name(params[2 + numKeys].getValue());
    if (!cellTypeOpt.has_value()) {
        return fail("invalid cell type: '%s'", params[2 + numKeys].getValue().c_str());
    }
    _cellType = cellTypeOpt.value();

    std::vector<ValueType::Dimension> dims;
    dims.reserve(numKeys);
    for (const auto& kf : _keyFields) {
        dims.emplace_back(kf);
    }
    auto vt = ValueType::make_type(_cellType, std::move(dims));
    _valueType = ValueType::from_spec(vt.to_spec());
    if (_valueType.is_error()) {
        return fail("invalid or duplicate dimension name(s) for key field(s)");
    }
    for (const auto& kf : _keyFields) {
        std::string           keyAttrName = _sourceParam + "." + kf;
        const fef::FieldInfo* kfInfo = env.getFieldByName(keyAttrName);
        if (kfInfo == nullptr || !kfInfo->hasAttribute()) {
            return fail("no such attribute '%s'", keyAttrName.c_str());
        }
    }
    std::string           valueAttrName = _sourceParam + "." + _valueField;
    const fef::FieldInfo* vfInfo = env.getFieldByName(valueAttrName);
    if (vfInfo == nullptr || !vfInfo->hasAttribute()) {
        return fail("no such attribute '%s'", valueAttrName.c_str());
    }
    describeOutput("tensor", "The tensor created from struct field attributes (key field(s) and value field)",
                   FeatureType::object(_valueType));
    return true;
}

namespace {

template <typename KeyBufferType> class TensorFromStructsExecutor : public fef::FeatureExecutor {
private:
    const IAttributeVector*                _keyAttribute;
    const IAttributeVector*                _valueAttribute;
    vespalib::eval::ValueType              _type;
    vespalib::eval::CellType               _cellType;
    KeyBufferType                          _keyBuffer;
    FloatContent                           _valueBuffer;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorFromStructsExecutor(const IAttributeVector* keyAttr, const IAttributeVector* valueAttr,
                              const vespalib::eval::ValueType& valueType, vespalib::eval::CellType cellType)
        : _keyAttribute(keyAttr),
          _valueAttribute(valueAttr),
          _type(valueType),
          _cellType(cellType),
          _keyBuffer(),
          _valueBuffer(),
          _tensor() {
        _keyBuffer.allocate(_keyAttribute->getMaxValueCount());
        _valueBuffer.allocate(_valueAttribute->getMaxValueCount());
    }
    ~TensorFromStructsExecutor() override;

    void execute(uint32_t docId) override;
};

template <typename KeyBufferType> TensorFromStructsExecutor<KeyBufferType>::~TensorFromStructsExecutor() = default;

template <typename KeyBufferType> void TensorFromStructsExecutor<KeyBufferType>::execute(uint32_t docId) {
    _keyBuffer.fill(*_keyAttribute, docId);
    _valueBuffer.fill(*_valueAttribute, docId);

    size_t size = std::min(_keyBuffer.size(), _valueBuffer.size());

    auto factory = FastValueBuilderFactory::get();

    // Use TypifyCellType to handle all cell types
    _tensor = TypifyCellType::resolve(_cellType, [&](auto cell_type) {
        using CellType = typename decltype(cell_type)::type;
        auto builder = factory.create_value_builder<CellType>(_type, 1, 1, size);
        for (size_t i = 0; i < size; ++i) {
            std::string                   key(_keyBuffer[i].value());
            std::vector<std::string_view> addr = {key};
            auto                          cell_array = builder->add_subspace(addr);
            cell_array[0] = static_cast<CellType>(_valueBuffer[i]);
        }
        return builder->build(std::move(builder));
    });

    outputs().set_object(0, *_tensor);
}

class TensorFromStructsMultiKeyExecutor : public fef::FeatureExecutor {
private:
    std::vector<const IAttributeVector*> _keyAttributes;
    const IAttributeVector*              _valueAttribute;
    vespalib::eval::ValueType            _type;
    vespalib::eval::CellType             _cellType;
    // _keyDimSlot[k] = index in the tensor's (sorted) dimension list where
    // the k-th user-supplied key field's value belongs in the subspace address.
    std::vector<size_t>                    _keyDimSlot;
    std::vector<WeightedStringContent>     _keyBuffers;
    FloatContent                           _valueBuffer;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorFromStructsMultiKeyExecutor(std::vector<const IAttributeVector*> keyAttrs,
                                      const IAttributeVector* valueAttr, const vespalib::eval::ValueType& valueType,
                                      vespalib::eval::CellType cellType, std::vector<size_t> keyDimSlot)
        : _keyAttributes(std::move(keyAttrs)),
          _valueAttribute(valueAttr),
          _type(valueType),
          _cellType(cellType),
          _keyDimSlot(std::move(keyDimSlot)),
          _keyBuffers(_keyAttributes.size()),
          _valueBuffer(),
          _tensor() {
        for (size_t i = 0; i < _keyAttributes.size(); ++i) {
            _keyBuffers[i].allocate(_keyAttributes[i]->getMaxValueCount());
        }
        _valueBuffer.allocate(_valueAttribute->getMaxValueCount());
    }
    ~TensorFromStructsMultiKeyExecutor() override = default;

    void execute(uint32_t docId) override {
        const size_t numKeys = _keyAttributes.size();
        for (size_t k = 0; k < numKeys; ++k) {
            _keyBuffers[k].fill(*_keyAttributes[k], docId);
        }
        _valueBuffer.fill(*_valueAttribute, docId);

        uint32_t size = _valueBuffer.size();
        for (size_t k = 0; k < numKeys; ++k) {
            size = std::min(size, _keyBuffers[k].size());
        }

        auto factory = FastValueBuilderFactory::get();
        _tensor = TypifyCellType::resolve(_cellType, [&](auto cell_type) {
            using CT = typename decltype(cell_type)::type;
            auto                          builder = factory.create_value_builder<CT>(_type, numKeys, 1, size);
            std::vector<std::string_view> addr(numKeys);
            for (size_t i = 0; i < size; ++i) {
                for (size_t k = 0; k < numKeys; ++k) {
                    addr[_keyDimSlot[k]] = _keyBuffers[k][i].value();
                }
                auto cell_array = builder->add_subspace(addr);
                cell_array[0] = static_cast<CT>(_valueBuffer[i]);
            }
            return builder->build(std::move(builder));
        });

        outputs().set_object(0, *_tensor);
    }
};

FeatureExecutor& createAttributeExecutor(const search::fef::IQueryEnvironment& env, const std::string& baseAttrName,
                                         const std::vector<std::string>& keyFields, const std::string& valueField,
                                         const ValueType& valueType, CellType cellType, vespalib::Stash& stash) {
    std::string valueAttrName = baseAttrName + "." + valueField;

    const IAttributeVector* valueAttribute = env.getAttributeContext().getAttribute(valueAttrName);
    if (valueAttribute == nullptr) {
        Issue::report("tensor_from_structs feature: The value attribute '%s' was not found."
                      " Returning empty tensor.",
                      valueAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    if (!valueAttribute->isFloatingPointType() && !valueAttribute->isIntegerType()) {
        Issue::report("tensor_from_structs feature: The value attribute '%s' must have numeric type."
                      " Returning empty tensor.",
                      valueAttrName.c_str());
        return ConstantTensorExecutor::createEmpty(valueType, stash);
    }

    std::vector<const IAttributeVector*> keyAttributes;
    keyAttributes.reserve(keyFields.size());
    for (const auto& kf : keyFields) {
        std::string             keyAttrName = baseAttrName + "." + kf;
        const IAttributeVector* keyAttribute = env.getAttributeContext().getAttribute(keyAttrName);
        if (keyAttribute == nullptr) {
            Issue::report("tensor_from_structs feature: The key attribute '%s' was not found."
                          " Returning empty tensor.",
                          keyAttrName.c_str());
            return ConstantTensorExecutor::createEmpty(valueType, stash);
        }
        if (!keyAttribute->isStringType() && !keyAttribute->isIntegerType()) {
            Issue::report(
                "tensor_from_structs feature: The key attribute '%s' must have basic type string or integer."
                " Returning empty tensor.",
                keyAttrName.c_str());
            return ConstantTensorExecutor::createEmpty(valueType, stash);
        }
        if (keyAttribute->getCollectionType() != valueAttribute->getCollectionType()) {
            Issue::report("tensor_from_structs feature: The key attribute '%s' and value attribute '%s' "
                          "must have the same collection type. Returning empty tensor.",
                          keyAttrName.c_str(), valueAttrName.c_str());
            return ConstantTensorExecutor::createEmpty(valueType, stash);
        }
        if (keyAttribute->getCollectionType() == search::attribute::CollectionType::WSET) {
            Issue::report("tensor_from_structs feature: Weighted set attributes are not supported."
                          " Key attribute '%s' is a weighted set. Returning empty tensor.",
                          keyAttrName.c_str());
            return ConstantTensorExecutor::createEmpty(valueType, stash);
        }
        keyAttributes.push_back(keyAttribute);
    }

    if (keyAttributes.size() == 1) {
        const IAttributeVector* keyAttribute = keyAttributes[0];
        if (keyAttribute->isIntegerType()) {
            // Using WeightedStringContent ensures that the integer values are converted
            // to strings while extracting them from the attribute.
            return stash.create<TensorFromStructsExecutor<WeightedStringContent>>(keyAttribute, valueAttribute,
                                                                                  valueType, cellType);
        }
        // When the underlying attribute is of type string we can reference these values
        // using WeightedConstCharContent.
        return stash.create<TensorFromStructsExecutor<WeightedConstCharContent>>(keyAttribute, valueAttribute,
                                                                                 valueType, cellType);
    }
    // valueType has its dimensions in sorted (canonical) order, which is what
    // the value-builder expects in the subspace address. Map each user-supplied
    // key field to its slot in that sorted dimension list.
    const auto&         dims = valueType.dimensions();
    std::vector<size_t> keyDimSlot(keyFields.size());
    for (size_t k = 0; k < keyFields.size(); ++k) {
        size_t slot = dims.size();
        for (size_t d = 0; d < dims.size(); ++d) {
            if (dims[d].name == keyFields[k]) {
                slot = d;
                break;
            }
        }
        keyDimSlot[k] = slot;
    }
    return stash.create<TensorFromStructsMultiKeyExecutor>(std::move(keyAttributes), valueAttribute, valueType,
                                                           cellType, std::move(keyDimSlot));
}

} // namespace

FeatureExecutor& TensorFromStructsBlueprint::createExecutor(const search::fef::IQueryEnvironment& env,
                                                            vespalib::Stash&                      stash) const {
    if (_sourceType == ATTRIBUTE_SOURCE) {
        return createAttributeExecutor(env, _sourceParam, _keyFields, _valueField, _valueType, _cellType, stash);
    }
    return ConstantTensorExecutor::createEmpty(_valueType, stash);
}

} // namespace features
} // namespace search
