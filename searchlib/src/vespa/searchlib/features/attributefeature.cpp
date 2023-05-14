// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefeature.h"
#include "utils.h"
#include "valuefeature.h"
#include "constant_tensor_executor.h"
#include "dense_tensor_attribute_executor.h"
#include "direct_tensor_attribute_executor.h"
#include "tensor_attribute_executor.h"

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/singleboolattribute.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.attributefeature");

using search::attribute::IAttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::ConstCharContent;
using search::tensor::DenseTensorAttribute;
using search::tensor::DirectTensorAttribute;
using search::attribute::IntegerContent;
using search::attribute::FloatContent;
using search::tensor::ITensorAttribute;
using search::attribute::WeightedConstCharContent;
using search::attribute::WeightedIntegerContent;
using search::attribute::WeightedFloatContent;
using search::fef::FeatureExecutor;
using search::features::util::ConstCharPtr;
using vespalib::Issue;
using vespalib::eval::ValueType;
using search::fef::FeatureType;
using namespace search::index;

using namespace search::fef::indexproperties;

namespace search::features {
namespace {
template <typename X, typename Y>
bool equals(X lhs, Y rhs) {
    return lhs == rhs;
}

template <>
bool equals<ConstCharPtr, vespalib::stringref>(ConstCharPtr lhs, vespalib::stringref rhs) {
    return strcmp(lhs, rhs.data()) == 0;
}

template <typename T>
bool
isUndefined(T value, BasicType::Type type)
{
    switch (type) {
    case BasicType::INT8:
        return attribute::isUndefined<int8_t>(static_cast<int8_t>(value));
    case BasicType::INT16:
        return attribute::isUndefined<int16_t>(static_cast<int16_t>(value));
    case BasicType::INT32:
        return attribute::isUndefined<int32_t>(static_cast<int32_t>(value));
    case BasicType::INT64:
        return attribute::isUndefined<int64_t>(static_cast<int64_t>(value));
    case BasicType::FLOAT:
        return attribute::isUndefined<float>(static_cast<float>(value));
    case BasicType::DOUBLE:
        return attribute::isUndefined<double>(static_cast<double>(value));
    default:
        return false;
    }
}

template <>
bool
isUndefined<vespalib::stringref>(vespalib::stringref, BasicType::Type)
{
    return false;
}

template <typename T>
feature_t
considerUndefined(T value, BasicType::Type type)
{
    if (isUndefined(value, type)) {
        return attribute::getUndefined<feature_t>();
    }
    return util::getAsFeature(value);
}

template <>
feature_t
considerUndefined<ConstCharPtr>(ConstCharPtr value, BasicType::Type )
{
    return util::getAsFeature(value);
}

/**
 * Implements the executor for fetching values from a single or array attribute vector
 */
template <typename T>
class SingleAttributeExecutor final : public fef::FeatureExecutor {
private:
    const T & _attribute;
public:
    /**
     * Constructs an executor.
     *
     * @param attribute The attribute vector to use.
     */
    explicit SingleAttributeExecutor(const T & attribute) : _attribute(attribute) { }
    void handle_bind_outputs(vespalib::ArrayRef<fef::NumberOrObject> outputs_in) override {
        fef::FeatureExecutor::handle_bind_outputs(outputs_in);
        auto o = outputs().get_bound();
        o[1].as_number = 0;  // weight
        o[2].as_number = 0;  // contains
        o[3].as_number = 1;  // count
    }
    void execute(uint32_t docId) override;
};

class BoolAttributeExecutor final : public fef::FeatureExecutor {
private:
    const SingleBoolAttribute & _attribute;
public:
    explicit BoolAttributeExecutor(const SingleBoolAttribute & attribute)
        : _attribute(attribute)
    {}
    void execute(uint32_t docId) override {
        outputs().set_number(0, _attribute.getFloat(docId));
    }
};

/**
 * Implements the executor for fetching values from an array attribute vector
 */
template <typename BaseType>
class ArrayAttributeExecutor final : public fef::FeatureExecutor {
private:
    using ArrayReadView = attribute::IArrayReadView<BaseType>;
    const ArrayReadView* _array_read_view;
    uint32_t  _idx;
public:
    ArrayAttributeExecutor(const ArrayReadView* array_read_view, uint32_t idx) : _array_read_view(array_read_view), _idx(idx) { }
    void execute(uint32_t docId) override;
    void handle_bind_outputs(vespalib::ArrayRef<fef::NumberOrObject> outputs_in) override {
        fef::FeatureExecutor::handle_bind_outputs(outputs_in);
        auto o = outputs().get_bound();
        o[1].as_number = 0;  // weight
        o[2].as_number = 0;  // contains
        o[3].as_number = 0;  // count
    }
};

class CountOnlyAttributeExecutor final : public fef::FeatureExecutor {
private:
    const attribute::IAttributeVector & _attribute;

public:
    explicit CountOnlyAttributeExecutor(const attribute::IAttributeVector & attribute) : _attribute(attribute) { }
    void execute(uint32_t docId) override;
    void handle_bind_outputs(vespalib::ArrayRef<fef::NumberOrObject> outputs_in) override {
        fef::FeatureExecutor::handle_bind_outputs(outputs_in);
        auto o = outputs().get_bound();
        o[0].as_number = 0;  // value
        o[1].as_number = 0;  // weight
        o[2].as_number = 0;  // contains
    }
};
/**
 * Implements the executor for fetching values from a single or array attribute vector
 */
template <typename T>
class AttributeExecutor final : public fef::FeatureExecutor {
private:
    const attribute::IAttributeVector * _attribute;
    attribute::BasicType::Type _attrType;
    uint32_t  _idx;
    T         _buffer; // used when fetching values from the attribute
    feature_t _defaultCount;

public:
    /**
     * Constructs an executor.
     *
     * @param attribute The attribute vector to use.
     * @param idx       The index used for an array attribute.
     */
    AttributeExecutor(const attribute::IAttributeVector * attribute, uint32_t idx);
    void execute(uint32_t docId) override;
    void handle_bind_outputs(vespalib::ArrayRef<fef::NumberOrObject> outputs_in) override {
        fef::FeatureExecutor::handle_bind_outputs(outputs_in);
        auto o = outputs().get_bound();
        o[1].as_number = 0;  // weight
        o[2].as_number = 0;  // contains
        o[3].as_number = _defaultCount; // count
    }
};


/**
 * Implements the executor for fetching weights from a weighted set attribute
 */
template <typename BT, typename T>
class WeightedSetAttributeExecutor : public fef::FeatureExecutor {
private:
    const attribute::IAttributeVector * _attribute;
    attribute::BasicType::Type _attrType;
    BT   _buffer; // used when fetching values and weights from the attribute
    T    _key;    // the key to find a weight for

public:
    /**
     * Constructs an executor.
     *
     * @param attribue The attribute vector to use.
     * @param key      The key to find a corresponding weight for.
     */
    WeightedSetAttributeExecutor(const attribute::IAttributeVector * attribute, T key);
    void execute(uint32_t docId) override;
};

template <typename T>
void
SingleAttributeExecutor<T>::execute(uint32_t docId)
{
    typename T::LoadedValueType v = _attribute.getFast(docId);
    // value
    auto o = outputs().get_bound();
    o[0].as_number = __builtin_expect(attribute::isUndefined(v), false)
                     ? attribute::getUndefined<feature_t>()
                     : util::getAsFeature(v);
}

template <typename BaseType>
void
ArrayAttributeExecutor<BaseType>::execute(uint32_t docId)
{
    auto values = _array_read_view->get_values(docId);
    auto o = outputs().get_bound();
    o[0].as_number = __builtin_expect(_idx < values.size(), true) ? multivalue::get_value(values[_idx]) : 0;
}

void
CountOnlyAttributeExecutor::execute(uint32_t docId)
{
    auto o = outputs().get_bound();
    o[3].as_number = _attribute.getValueCount(docId); // count
}

template <typename T>
AttributeExecutor<T>::AttributeExecutor(const IAttributeVector * attribute, uint32_t idx) :
    fef::FeatureExecutor(),
    _attribute(attribute),
    _attrType(attribute->getBasicType()),
    _idx(idx),
    _buffer(),
    _defaultCount((attribute->getCollectionType() == CollectionType::ARRAY) ? 0 : 1)
{
    _buffer.allocate(_attribute->getMaxValueCount());
}

template <typename T>
void
AttributeExecutor<T>::execute(uint32_t docId)
{
    feature_t value = 0.0f;
    _buffer.fill(*_attribute, docId);
    if (_idx < _buffer.size()) {
        value = considerUndefined(_buffer[_idx], _attrType);
    }
    auto o = outputs().get_bound();
    o[0].as_number = value;  // value
}


template <typename BT, typename T>
WeightedSetAttributeExecutor<BT, T>::WeightedSetAttributeExecutor(const IAttributeVector * attribute, T key)
    : fef::FeatureExecutor(),
      _attribute(attribute),
      _attrType(attribute->getBasicType()),
      _buffer(),
      _key(key)
{
}

template <typename BT, typename T>
void
WeightedSetAttributeExecutor<BT, T>::execute(uint32_t docId)
{
    feature_t value = 0.0f;
    feature_t weight = 0.0f;
    feature_t contains = 0.0f;
    feature_t count = 0.0f;
    _buffer.fill(*_attribute, docId);
    for (uint32_t i = 0; i < _buffer.size(); ++i) {
        if (equals(_buffer[i].getValue(), _key)) {
            value = considerUndefined(_key, _attrType);
            weight = static_cast<feature_t>(_buffer[i].getWeight());
            contains = 1.0f;
            break;
        }
    }
    outputs().set_number(0, value);    // value
    outputs().set_number(1, weight);   // weight
    outputs().set_number(2, contains); // contains
    outputs().set_number(3, count);    // count
}

template <typename T>
struct SingleValueExecutorCreator {
    using AttrType = SingleValueNumericAttribute<T>;
    using PtrType = const AttrType *;
    using ExecType = SingleAttributeExecutor<AttrType>;
    SingleValueExecutorCreator() : ptr(nullptr) {}
    bool handle(const IAttributeVector *attribute) {
        ptr = dynamic_cast<PtrType>(attribute);
        return ptr != nullptr;
    }
    fef::FeatureExecutor & create(vespalib::Stash &stash) const {
        return stash.create<ExecType>(*ptr);
    }
private:
    PtrType ptr;
};

template <typename T>
struct ArrayExecutorCreator {
    using ArrayReadView = attribute::IArrayReadView<typename T::BaseType>;
    using ExecType = ArrayAttributeExecutor<typename T::BaseType>;
    ArrayExecutorCreator() : _array_read_view(nullptr) {}
    bool handle(vespalib::Stash &stash, const IAttributeVector *attribute) {
        auto multi_value_attribute = attribute->as_multi_value_attribute();
        if (multi_value_attribute != nullptr) {
            _array_read_view = multi_value_attribute->make_read_view(attribute::IMultiValueAttribute::ArrayTag<typename T::BaseType>(), stash);
        }
        return _array_read_view != nullptr;
    }
    fef::FeatureExecutor & create(vespalib::Stash &stash, uint32_t idx) const {
        return stash.create<ExecType>(_array_read_view, idx);
    }
private:
    const ArrayReadView* _array_read_view;
};

fef::FeatureExecutor &
createAttributeExecutor(uint32_t numOutputs, const IAttributeVector *attribute, const vespalib::string &attrName, const vespalib::string &extraParam, vespalib::Stash &stash)
{
    if (attribute == nullptr) {
        Issue::report("attribute feature: The attribute vector '%s' was not found, returning default values.",
                      attrName.c_str());
        std::vector<feature_t> values(numOutputs, 0.0f);
        return stash.create<ValueExecutor>(values);
    }
    CollectionType collectionType = attribute->getCollectionType();
    if (collectionType == CollectionType::WSET) {
        assert(numOutputs == 4);
        bool useKey = !extraParam.empty();
        if (useKey) {
            if (attribute->isStringType()) {
                return stash.create<WeightedSetAttributeExecutor<WeightedConstCharContent, vespalib::stringref>>(attribute, extraParam);
            } else if (attribute->isIntegerType()) {
                return stash.create<WeightedSetAttributeExecutor<WeightedIntegerContent, int64_t>>(attribute, util::strToNum<int64_t>(extraParam));
            } else { // FLOAT
                return stash.create<WeightedSetAttributeExecutor<WeightedFloatContent, double>>(attribute, util::strToNum<double>(extraParam));
            }
        } else {
            return stash.create<CountOnlyAttributeExecutor>(*attribute);
        }
    } else { // SINGLE or ARRAY
        BasicType basicType = attribute->getBasicType();
        if (collectionType == CollectionType::SINGLE) {
            if (attribute->isIntegerType()) {
                if (basicType == BasicType::BOOL) {
                    auto boolAttribute = dynamic_cast<const SingleBoolAttribute *>(attribute);
                    assert (boolAttribute && (numOutputs == 1));
                    return stash.create<BoolAttributeExecutor>(*boolAttribute);
                } else {
                    assert(numOutputs == 4);
                    if (basicType == BasicType::INT8) {
                        SingleValueExecutorCreator<IntegerAttributeTemplate<int8_t>> creator;
                        if (creator.handle(attribute)) return creator.create(stash);
                    } else if (basicType == BasicType::INT32) {
                        SingleValueExecutorCreator<IntegerAttributeTemplate<int32_t>> creator;
                        if (creator.handle(attribute)) return creator.create(stash);
                    }
                    SingleValueExecutorCreator<IntegerAttributeTemplate<int64_t>> creator;
                    if (creator.handle(attribute)) return creator.create(stash);
                }
            } else if (attribute->isFloatingPointType()) {
                assert(numOutputs == 4);
                if (basicType == BasicType::DOUBLE) {
                    SingleValueExecutorCreator<FloatingPointAttributeTemplate<double>> creator;
                    if (creator.handle(attribute)) return creator.create(stash);
                } else {
                    SingleValueExecutorCreator<FloatingPointAttributeTemplate<float>> creator;
                    if (creator.handle(attribute)) return creator.create(stash);
                }
            }
        }
        assert(numOutputs == 4);
        uint32_t idx = 0;
        if (!extraParam.empty()) {
            idx = util::strToNum<uint32_t>(extraParam);
        } else if (attribute->getCollectionType() == CollectionType::ARRAY) {
            return stash.create<CountOnlyAttributeExecutor>(*attribute);
        }
        if (attribute->isStringType()) {
            return stash.create<AttributeExecutor<ConstCharContent>>(attribute, idx);
        } else if (attribute->isIntegerType()) {
            if (basicType == BasicType::INT32) {
                ArrayExecutorCreator<IntegerAttributeTemplate<int32_t>> creator;
                if (creator.handle(stash, attribute)) return creator.create(stash, idx);
            } else if (basicType == BasicType::INT64) {
                ArrayExecutorCreator<IntegerAttributeTemplate<int64_t>> creator;
                if (creator.handle(stash, attribute)) return creator.create(stash, idx);
            }
            return stash.create<AttributeExecutor<IntegerContent>>(attribute, idx);
        } else { // FLOAT
            if (basicType == BasicType::DOUBLE) {
                ArrayExecutorCreator<FloatingPointAttributeTemplate<double>> creator;
                if (creator.handle(stash, attribute)) return creator.create(stash, idx);
            } else {
                ArrayExecutorCreator<FloatingPointAttributeTemplate<float>> creator;
                if (creator.handle(stash, attribute)) return creator.create(stash, idx);
            }
            return stash.create<AttributeExecutor<FloatContent>>(attribute, idx);
        }
    }
}

fef::FeatureExecutor &
createTensorAttributeExecutor(const IAttributeVector *attribute, const vespalib::string &attrName,
                              const ValueType &tensorType,
                              vespalib::Stash &stash)
{
    if (attribute == nullptr) {
        Issue::report("attribute feature: The attribute vector '%s' was not found."
                      " Returning empty tensor.", attrName.c_str());
        return ConstantTensorExecutor::createEmpty(tensorType, stash);
    }
    if (attribute->getCollectionType() != attribute::CollectionType::SINGLE ||
        attribute->getBasicType() != attribute::BasicType::TENSOR)
    {
        Issue::report("attribute feature: The attribute vector '%s' is NOT of type tensor."
                      "Returning empty tensor.", attribute->getName().c_str());
        return ConstantTensorExecutor::createEmpty(tensorType, stash);
    }
    const ITensorAttribute *tensorAttribute = attribute->asTensorAttribute();
    if (tensorAttribute == nullptr) {
        Issue::report("attribute feature: The attribute vector '%s' could not be converted to a tensor attribute."
                      " Returning empty tensor.", attribute->getName().c_str());
        return ConstantTensorExecutor::createEmpty(tensorType, stash);
    }
    if (tensorType != tensorAttribute->getTensorType()) {
        Issue::report("attribute feature: The tensor attribute '%s' has tensor type '%s',"
                      " while the feature executor expects type '%s'. Returning empty tensor.",
                      attribute->getName().c_str(),
                      tensorAttribute->getTensorType().to_spec().c_str(),
                      tensorType.to_spec().c_str());
        return ConstantTensorExecutor::createEmpty(tensorType, stash);
    }
    if (tensorAttribute->supports_extract_cells_ref()) {
        return stash.create<DenseTensorAttributeExecutor>(*tensorAttribute);
    }
    if (tensorAttribute->supports_get_tensor_ref()) {
        return stash.create<DirectTensorAttributeExecutor>(*tensorAttribute);
    }
    return stash.create<TensorAttributeExecutor>(*tensorAttribute);
}

bool
isSingleValueBoolField(const fef::FieldInfo & fInfo) {
    return (fInfo.collection() == schema::CollectionType::SINGLE)
           && (fInfo.get_data_type() == schema::DataType::BOOL);
}

}

AttributeBlueprint::AttributeBlueprint() :
    fef::Blueprint("attribute"),
    _attrName(),
    _attrKey(),
    _extra(),
    _tensorType(ValueType::double_type()),
    _numOutputs(0)
{
}

AttributeBlueprint::~AttributeBlueprint() = default;

void
AttributeBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &,
                                      fef::IDumpFeatureVisitor &) const
{
}

bool
AttributeBlueprint::setup(const fef::IIndexEnvironment & env,
                          const fef::ParameterList & params)
{
    // params[0] = attribute name
    // params[1] = index (array attribute) or key (weighted set attribute)
    _attrName = params[0].getValue();
    _attrKey = createAttributeKey(_attrName);
    if (params.size() == 2) {
        _extra = params[1].getValue();
    }
    vespalib::string attrType = type::Attribute::lookup(env.getProperties(), _attrName);
    if (!attrType.empty()) {
        _tensorType = ValueType::from_spec(attrType);
        if (_tensorType.is_error()) {
            LOG(error, "%s: invalid type: '%s'", getName().c_str(), attrType.c_str());
        }
    }
    FeatureType output_type = _tensorType.is_double()
                              ? FeatureType::number()
                              : FeatureType::object(_tensorType);
    describeOutput("value", "The value of a single value attribute, "
                   "the value at the given index of an array attribute, "
                   "the given key of a weighted set attribute, or"
                   "the tensor of a tensor attribute", output_type);
    const fef::FieldInfo * fInfo = env.getFieldByName(_attrName);
    if (_tensorType.has_dimensions() || isSingleValueBoolField(*fInfo)) {
        _numOutputs = 1;
    } else {
        describeOutput("weight", "The weight associated with the given key in a weighted set attribute.");
        describeOutput("contains", "1 if the given key is present in a weighted set attribute, 0 otherwise.");
        describeOutput("count", "Returns the number of elements in this array or weighted set attribute.");
        _numOutputs = 4;
    }
    return !_tensorType.is_error();
}

fef::Blueprint::UP
AttributeBlueprint::createInstance() const
{
    return std::make_unique<AttributeBlueprint>();
}

void
AttributeBlueprint::prepareSharedState(const fef::IQueryEnvironment & env, fef::IObjectStore & store) const
{
    lookupAndStoreAttribute(_attrKey, _attrName, env, store);
}

fef::FeatureExecutor &
AttributeBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = lookupAttribute(_attrKey, _attrName, env);
    if (_tensorType.has_dimensions()) {
        return createTensorAttributeExecutor(attribute, _attrName, _tensorType, stash);
    } else {
        return createAttributeExecutor(_numOutputs, attribute, _attrName, _extra, stash);
    }
}

fef::ParameterDescriptions
AttributeBlueprint::getDescriptions() const
{
    auto dataTypeSet = fef::ParameterDataTypeSet::normalOrTensorTypeSet();
    return fef::ParameterDescriptions().
        desc().attribute(dataTypeSet, fef::ParameterCollection::ANY).
        desc().attribute(dataTypeSet, fef::ParameterCollection::ANY).string();
}

}
