// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dotproductfeature.h"
#include "valuefeature.h"
#include "weighted_set_parser.hpp"
#include "array_parser.hpp"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/multienumattribute.h>
#include <type_traits>

#include <vespa/log/log.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>

LOG_SETUP(".features.dotproduct");

using namespace search::attribute;
using namespace search::fef;
using vespalib::hwaccelrated::IAccelrated;

namespace search::features {
namespace dotproduct::wset {

template <typename DimensionVType, typename DimensionHType, typename ComponentType, typename HashMapComparator>
VectorBase<DimensionVType, DimensionHType, ComponentType, HashMapComparator>::VectorBase() = default;

template <typename DimensionVType, typename DimensionHType, typename ComponentType, typename HashMapComparator>
VectorBase<DimensionVType, DimensionHType, ComponentType, HashMapComparator>::~VectorBase() = default;

template <typename DimensionVType, typename DimensionHType, typename ComponentType, typename HashMapComparator>
VectorBase<DimensionVType, DimensionHType, ComponentType, HashMapComparator> &
VectorBase<DimensionVType, DimensionHType, ComponentType, HashMapComparator>::syncMap() {
    Converter<DimensionVType, DimensionHType> conv;
    _dimMap.clear();
    _dimMap.resize(_vector.size()*2);
    for (size_t i = 0; i < _vector.size(); ++i) {
        _dimMap.insert(std::make_pair(conv.convert(_vector[i].first), _vector[i].second));
    }
    return *this;
}

template VectorBase<int64_t, int64_t, double> & VectorBase<int64_t, int64_t, double>::syncMap();


template <typename Vector, typename Buffer>
DotProductExecutorByCopy<Vector, Buffer>::DotProductExecutorByCopy(const IAttributeVector * attribute, Vector queryVector) :
    FeatureExecutor(),
    _attribute(attribute),
    _queryVector(std::move(queryVector)),
    _end(_queryVector.syncMap().getDimMap().end()),
    _buffer()
{
    _buffer.allocate(_attribute->getMaxValueCount());
}

template <typename Vector, typename Buffer>
DotProductExecutorByCopy<Vector, Buffer>::~DotProductExecutorByCopy() = default;

template <typename Vector, typename Buffer>
void
DotProductExecutorByCopy<Vector, Buffer>::execute(uint32_t docId)
{
    feature_t val = 0;
    if (!_queryVector.getDimMap().empty()) {
        _buffer.fill(*_attribute, docId);
        for (size_t i = 0; i < _buffer.size(); ++i) {
            typename Vector::HashMap::const_iterator itr = _queryVector.getDimMap().find(_buffer[i].getValue());
            if (itr != _end) {
                val += _buffer[i].getWeight() * itr->second;
            }
        }
    }
    outputs().set_number(0, val);
}

StringVector::StringVector() = default;
StringVector::~StringVector() = default;

template <typename BaseType>
DotProductExecutorBase<BaseType>::DotProductExecutorBase(V queryVector)
    : FeatureExecutor(),
      _queryVector(std::move(queryVector)),
      _end(_queryVector.syncMap().getDimMap().end())
{
}

template <typename BaseType>
DotProductExecutorBase<BaseType>::~DotProductExecutorBase() = default;

template <typename BaseType>
void DotProductExecutorBase<BaseType>::execute(uint32_t docId) {
    feature_t val = 0;
    if (!_queryVector.getDimMap().empty()) {
        const AT * values(nullptr);
        uint32_t sz = getAttributeValues(docId, values);
        for (size_t i = 0; i < sz; ++i) {
            typename V::HashMap::const_iterator itr = _queryVector.getDimMap().find(values[i].value());
            if (itr != _end) {
                val += values[i].weight() * itr->second;
            }
        }
    }
    outputs().set_number(0, val);
}

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, V queryVector) :
    DotProductExecutorBase<typename A::BaseType>(std::move(queryVector)),
    _attribute(attribute)
{
}

template <typename A>
DotProductExecutor<A>::~DotProductExecutor() = default;

template <typename A>
size_t
DotProductExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    return _attribute->getRawValues(docId, values);
}

namespace {

class DotProductExecutorByEnum : public fef::FeatureExecutor {
public:
    using V  = VectorBase<EnumHandle, EnumHandle, feature_t>;
private:
    const IWeightedIndexVector * _attribute;
    V _queryVector;
    const typename V::HashMap::const_iterator _end;
public:
    DotProductExecutorByEnum(const IWeightedIndexVector * attribute, V queryVector);
    ~DotProductExecutorByEnum() override;
    void execute(uint32_t docId) override;
};

DotProductExecutorByEnum::DotProductExecutorByEnum(const IWeightedIndexVector * attribute, V queryVector)
    : FeatureExecutor(),
      _attribute(attribute),
      _queryVector(std::move(queryVector)),
      _end(_queryVector.syncMap().getDimMap().end())
{
}

DotProductExecutorByEnum::~DotProductExecutorByEnum() = default;

void DotProductExecutorByEnum::execute(uint32_t docId) {
    feature_t val = 0;
    if (!_queryVector.getDimMap().empty()) {
        const IWeightedIndexVector::WeightedIndex *values(nullptr);
        uint32_t sz = _attribute->getEnumHandles(docId, values);
        for (size_t i = 0; i < sz; ++i) {
            typename V::HashMap::const_iterator itr = _queryVector.getDimMap().find(values[i].value().ref());
            if (itr != _end) {
                val += values[i].weight() * itr->second;
            }
        }
    }
    outputs().set_number(0, val);
}

}

}

namespace dotproduct::array {

template <typename BaseType>
DotProductExecutorBase<BaseType>::DotProductExecutorBase(const V & queryVector)
    : FeatureExecutor(),
      _multiplier(IAccelrated::getAccelrator()),
      _queryVector(queryVector)
{
}

template <typename BaseType>
DotProductExecutorBase<BaseType>::~DotProductExecutorBase() = default;

template <typename BaseType>
void DotProductExecutorBase<BaseType>::execute(uint32_t docId) {
    const AT *values(nullptr);
    size_t count = getAttributeValues(docId, values);
    size_t commonRange = std::min(count, _queryVector.size());
    static_assert(std::is_same<typename AT::ValueType, BaseType>::value);
    outputs().set_number(0, _multiplier->dotProduct(
            &_queryVector[0], reinterpret_cast<const typename AT::ValueType *>(values), commonRange));
}

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, const V & queryVector) :
    DotProductExecutorBase<typename A::BaseType>(queryVector),
    _attribute(attribute)
{
}

template <typename A>
DotProductExecutor<A>::~DotProductExecutor() = default;

template <typename A>
size_t
DotProductExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    return _attribute->getRawValues(docId, values);
}

template <typename A>
SparseDotProductExecutor<A>::SparseDotProductExecutor(const A * attribute, const V & queryVector, const IV & queryIndexes) :
    DotProductExecutor<A>(attribute, queryVector),
    _queryIndexes(queryIndexes),
    _scratch(queryIndexes.size())
{
}

template <typename A>
SparseDotProductExecutor<A>::~SparseDotProductExecutor() = default;

template <typename A>
size_t
SparseDotProductExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    const AT *allValues(NULL);
    size_t count = this->_attribute->getRawValues(docId, allValues);
    values = &_scratch[0];
    size_t i(0);
    for (; (i < _queryIndexes.size()) && (_queryIndexes[i] < count); i++) {
        _scratch[i] = allValues[_queryIndexes[i]];
    }
    return i;
}

template <typename A>
DotProductByCopyExecutor<A>::DotProductByCopyExecutor(const A * attribute, const V & queryVector) :
    DotProductExecutor<A>(attribute, queryVector),
    _copy(static_cast<size_t>(attribute->getMaxValueCount()))
{
}

template <typename A>
DotProductByCopyExecutor<A>::~DotProductByCopyExecutor() = default;

template <typename A>
size_t
DotProductByCopyExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    size_t count = this->_attribute->getAll(docId, &_copy[0], _copy.size());
    if (count > _copy.size()) {
        _copy.resize(count);
        count = this->_attribute->getAll(docId, &_copy[0], _copy.size());
    }
    values = reinterpret_cast<const AT *>(&_copy[0]);
    return count;
}

template <typename A>
SparseDotProductByCopyExecutor<A>::SparseDotProductByCopyExecutor(const A * attribute, const V & queryVector, const IV & queryIndexes) :
    SparseDotProductExecutor<A>(attribute, queryVector, queryIndexes),
    _copy(std::max(static_cast<size_t>(attribute->getMaxValueCount()), queryIndexes.size()))
{
}

template <typename A>
SparseDotProductByCopyExecutor<A>::~SparseDotProductByCopyExecutor() = default;

template <typename A>
size_t
SparseDotProductByCopyExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    size_t count = this->_attribute->getAll(docId, &_copy[0], _copy.size());
    if (count > _copy.size()) {
        _copy.resize(count);
        count = this->_attribute->getAll(docId, &_copy[0], _copy.size());
    }
    size_t i(0);
    for (const IV & iv(this->_queryIndexes); (i < iv.size()) && (iv[i] < count); i++) {
        _copy[i] = _copy[iv[i]];
    }
    values = reinterpret_cast<const AT *>(&_copy[0]);
    return i;
}

template <typename BaseType>
DotProductByContentFillExecutor<BaseType>::DotProductByContentFillExecutor(
        const attribute::IAttributeVector * attribute,
        const V & queryVector)
    : DotProductExecutorBase<BaseType>(queryVector),
      _attribute(attribute),
      _filler()
{
    _filler.allocate(attribute->getMaxValueCount());
}

template <typename BaseType>
DotProductByContentFillExecutor<BaseType>::~DotProductByContentFillExecutor() = default;

namespace {

template<typename T> struct IsNonWeightedType : std::false_type {};
template<typename BaseType> struct IsNonWeightedType<multivalue::Value<BaseType>> : std::true_type {};

// Compile-time sanity check for type compatibility of gnarly BaseType <-> multivalue::Value
// reinterpret_cast used by some getAttributeValues calls.
template <typename BaseType, typename AttributeValueType, typename FillerValueType>
constexpr void sanity_check_reinterpret_cast_compatibility() {
    static_assert(IsNonWeightedType<AttributeValueType>::value);
    static_assert(sizeof(BaseType) == sizeof(AttributeValueType));
    static_assert(sizeof(BaseType) == sizeof(FillerValueType));
    static_assert(std::is_same<BaseType, typename AttributeValueType::ValueType>::value);
}

}

template <typename BaseType>
size_t DotProductByContentFillExecutor<BaseType>::getAttributeValues(uint32_t docid, const AT * & values) {
    _filler.fill(*_attribute, docid);
    sanity_check_reinterpret_cast_compatibility<BaseType, AT, decltype(*_filler.data())>();
    values = reinterpret_cast<const AT *>(_filler.data());
    return _filler.size();
}

template <typename BaseType>
SparseDotProductByContentFillExecutor<BaseType>::SparseDotProductByContentFillExecutor(
        const attribute::IAttributeVector * attribute,
        const V & queryVector,
        const IV & queryIndexes)
    : DotProductExecutorBase<BaseType>(queryVector),
      _attribute(attribute),
      _queryIndexes(queryIndexes),
      _filler()
{
    _filler.allocate(std::max(static_cast<size_t>(attribute->getMaxValueCount()), queryIndexes.size()));
}

template <typename BaseType>
SparseDotProductByContentFillExecutor<BaseType>::~SparseDotProductByContentFillExecutor() = default;

template <typename BaseType>
size_t SparseDotProductByContentFillExecutor<BaseType>::getAttributeValues(uint32_t docid, const AT * & values) {
    _filler.fill(*_attribute, docid);

    const size_t count = _filler.size();
    BaseType * data = _filler.data();
    size_t i = 0;
    for (; (i < _queryIndexes.size()) && (_queryIndexes[i] < count); ++i) {
        data[i] = data[_queryIndexes[i]];
    }

    sanity_check_reinterpret_cast_compatibility<BaseType, AT, decltype(*_filler.data())>();
    values = reinterpret_cast<const AT *>(data);
    return i;
}


}

DotProductBlueprint::DotProductBlueprint() :
    Blueprint("dotProduct"),
    _defaultAttribute(),
    _queryVector(),
    _attribute(nullptr)
{ }

DotProductBlueprint::~DotProductBlueprint() = default;

vespalib::string
DotProductBlueprint::getAttribute(const IQueryEnvironment & env) const
{
    Property prop = env.getProperties().lookup(getBaseName(), _defaultAttribute + ".override.name");
    if (prop.found() && !prop.get().empty()) {
        return prop.get();
    }
    return _defaultAttribute;
}

void
DotProductBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
DotProductBlueprint::setup(const IIndexEnvironment & env, const ParameterList & params)
{
    _defaultAttribute = params[0].getValue();
    _queryVector = params[1].getValue();
    describeOutput("scalar", "The result after calculating the dot product of the vector represented by the weighted set "
                             "and the vector sent down with the query");
    env.hintAttributeAccess(_defaultAttribute);
    return true;
}

ParameterDescriptions
DotProductBlueprint::getDescriptions() const {
    return ParameterDescriptions().desc().attribute(ParameterDataTypeSet::normalTypeSet(), ParameterCollection::ANY).string();
}

Blueprint::UP
DotProductBlueprint::createInstance() const
{
    return std::make_unique<DotProductBlueprint>();
}

namespace {

template <typename T>
void
parseVectors(const Property& prop, std::vector<T>& values, std::vector<uint32_t>& indexes)
{
    typedef std::vector<ArrayParser::ValueAndIndex<T>> SparseV;
    SparseV sparse;
    ArrayParser::parsePartial(prop.get(), sparse);
    if ( ! sparse.empty()) {
        std::sort(sparse.begin(), sparse.end());
        if ((sparse.back().getIndex() + 1) / sparse.size() < 10) {
            values.resize(sparse.back().getIndex() + 1);
            for (const typename SparseV::value_type & a : sparse) {
                values[a.getIndex()] = a.getValue();
            }
        } else {
            values.reserve(sparse.size());
            indexes.reserve(sparse.size());
            for (const typename SparseV::value_type & a : sparse) {
                values.push_back(a.getValue());
                indexes.push_back(a.getIndex());
            }
        }
    }
}

}

namespace dotproduct {

template <typename T>
ArrayParam<T>::ArrayParam(const Property & prop) {
    parseVectors(prop, values, indexes);
}

template <typename T>
ArrayParam<T>::ArrayParam(vespalib::nbostream & stream) {
    vespalib::tensor::TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(stream, values);
}

template <typename T>
ArrayParam<T>::~ArrayParam() = default;


// Explicit instantiation since these are inspected by unit tests.
// FIXME this feels a bit dirty, consider breaking up ArrayParam to remove dependencies
// on templated vector parsing. This is why it's defined in this translation unit as it is.
template ArrayParam<int64_t>::ArrayParam(const Property & prop);
template struct ArrayParam<double>;
template struct ArrayParam<float>;

} // namespace dotproduct

namespace {

using dotproduct::ArrayParam;

template <typename A, typename B>
bool supportsGetRawValues(const A & attr) noexcept {
    try {
        const B * tmp = nullptr;
        attr.getRawValues(0, tmp); // Throws if unsupported
        return true;
    } catch (const std::runtime_error & e) {
        (void) e;
        return false;
    }
}

bool supportsGetEnumHandles(const IWeightedIndexVector * attr) noexcept {
    if (attr == nullptr) return false;
    try {
        const IWeightedIndexVector::WeightedIndex * tmp = nullptr;
        attr->getEnumHandles(0, tmp); // Throws if unsupported
        return true;
    } catch (const std::runtime_error & e) {
        (void) e;
        return false;
    }
}


// Precondition: attribute->isImported() == false
template <typename A>
FeatureExecutor &
createForDirectArrayImpl(const IAttributeVector * attribute,
                         const std::vector<typename A::BaseType> & values,
                         const std::vector<uint32_t> & indexes,
                         vespalib::Stash & stash)
{
    if (values.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    }
    const A * iattr = dynamic_cast<const A *>(attribute);
    using T = typename A::BaseType;
    using VT = multivalue::Value<T>;
    if (indexes.empty()) {
        if (supportsGetRawValues<A,VT>(*iattr)) {
            using ExactA = MultiValueNumericAttribute<A, VT>;

            const ExactA * exactA = dynamic_cast<const ExactA *>(iattr);
            if (exactA != nullptr) {
                return stash.create<dotproduct::array::DotProductExecutor<ExactA>>(exactA, values);
            }
            return stash.create<dotproduct::array::DotProductExecutor<A>>(iattr, values);
        } else {
            return stash.create<dotproduct::array::DotProductByCopyExecutor<A>>(iattr, values);
        }
    } else {
        if (supportsGetRawValues<A, VT>(*iattr)) {
            return stash.create<dotproduct::array::SparseDotProductExecutor<A>>(iattr, values, indexes);
        } else {
            return stash.create<dotproduct::array::SparseDotProductByCopyExecutor<A>>(iattr, values, indexes);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

template <typename BaseType>
FeatureExecutor &
createForImportedArrayImpl(const IAttributeVector * attribute,
                           const std::vector<BaseType> & values,
                           const std::vector<uint32_t> & indexes,
                           vespalib::Stash & stash) {
    if (values.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    }
    if (indexes.empty()) {
        using ExecutorType = dotproduct::array::DotProductByContentFillExecutor<BaseType>;
        return stash.create<ExecutorType>(attribute, values);
    } else {
        using ExecutorType = dotproduct::array::SparseDotProductByContentFillExecutor<BaseType>;
        return stash.create<ExecutorType>(attribute, values, indexes);
    }
}

template <typename BaseType>
FeatureExecutor&
createForImportedArray(const IAttributeVector * attribute,
                       const Property & prop,
                       vespalib::Stash & stash) {
    std::vector<BaseType> values;
    std::vector<uint32_t> indexes;
    parseVectors(prop, values, indexes);
    return createForImportedArrayImpl<BaseType>(attribute, values, indexes, stash);
}

template <typename BaseType>
FeatureExecutor&
createForImportedArray(const IAttributeVector * attribute,
                       const ArrayParam<BaseType> & arguments,
                       vespalib::Stash & stash) {
    return createForImportedArrayImpl<BaseType>(attribute, arguments.values, arguments.indexes, stash);
}

template <typename A>
FeatureExecutor &
createForDirectArray(const IAttributeVector * attribute,
                     const Property & prop,
                     vespalib::Stash & stash) {
    std::vector<typename A::BaseType> values;
    std::vector<uint32_t> indexes;
    parseVectors(prop, values, indexes);
    return createForDirectArrayImpl<A>(attribute, values, indexes, stash);
}

template <typename A>
FeatureExecutor &
createForDirectArray(const IAttributeVector * attribute,
                     const ArrayParam<typename A::BaseType> & arguments,
                     vespalib::Stash & stash) {
    return createForDirectArrayImpl<A>(attribute, arguments.values, arguments.indexes, stash);
}

const char * OBJECT = "object";

FeatureExecutor &
createFromObject(const IAttributeVector * attribute, const fef::Anything & object, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        if (!attribute->isImported()) {
            switch (attribute->getBasicType()) {
                case BasicType::INT32:
                    return createForDirectArray<IntegerAttributeTemplate<int32_t>>(attribute, dynamic_cast<const ArrayParam<int32_t> &>(object), stash);
                case BasicType::INT64:
                    return createForDirectArray<IntegerAttributeTemplate<int64_t>>(attribute, dynamic_cast<const ArrayParam<int64_t> &>(object), stash);
                case BasicType::FLOAT:
                    return createForDirectArray<FloatingPointAttributeTemplate<float>>(attribute, dynamic_cast<const ArrayParam<float> &>(object), stash);
                case BasicType::DOUBLE:
                    return createForDirectArray<FloatingPointAttributeTemplate<double>>(attribute, dynamic_cast<const ArrayParam<double> &>(object), stash);
                default:
                    break;
            }
        } else {
            switch (attribute->getBasicType()) {
                case BasicType::INT32:
                case BasicType::INT64:
                    return createForImportedArray<int64_t>(attribute, dynamic_cast<const ArrayParam<int64_t> &>(object), stash);
                case BasicType::FLOAT:
                case BasicType::DOUBLE:
                    return createForImportedArray<double>(attribute, dynamic_cast<const ArrayParam<double> &>(object), stash);
                default:
                    break;
            }
        }
    }
    // TODO: Add support for creating executor for weighted set string / integer attribute
    //       where the query vector is represented as an object instead of a string.
    LOG(warning, "The attribute vector '%s' is NOT of type array<int/long/float/double>"
            ", returning executor with default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}

FeatureExecutor *
createTypedArrayExecutor(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash) {
    if (!attribute->isImported()) {
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
                return &createForDirectArray<IntegerAttributeTemplate<int32_t>>(attribute, prop, stash);
            case BasicType::INT64:
                return &createForDirectArray<IntegerAttributeTemplate<int64_t>>(attribute, prop, stash);
            case BasicType::FLOAT:
                return &createForDirectArray<FloatingPointAttributeTemplate<float>>(attribute, prop, stash);
            case BasicType::DOUBLE:
                return &createForDirectArray<FloatingPointAttributeTemplate<double>>(attribute, prop, stash);
            default:
                break;
        }
    } else {
        // When using AttributeContent, integers are always extracted as largeint_t and
        // floats always as double. This means that we cannot allow type specializations
        // on int32_t or float, or reinterpreting type casts will end up pointing at
        // data that is not of the correct size. Which would be Bad(tm).
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
            case BasicType::INT64:
                return &createForImportedArray<IAttributeVector::largeint_t>(attribute, prop, stash);
            case BasicType::FLOAT:
            case BasicType::DOUBLE:
                return &createForImportedArray<double>(attribute, prop, stash);
            default:
                break;
        }
    }
    return nullptr;
}

template <typename A, typename V>
FeatureExecutor *
createForDirectWSetImpl(const IAttributeVector * attribute, V vector, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    using T = typename A::BaseType;
    const A * iattr = dynamic_cast<const A *>(attribute);
    using VT = multivalue::WeightedValue<T>;
    using ExactA = MultiValueNumericAttribute<A, VT>;
    if (!attribute->isImported() && (iattr != nullptr) && supportsGetRawValues<A, VT>(*iattr)) {
        const ExactA * exactA = dynamic_cast<const ExactA *>(iattr);
        if (exactA != nullptr) {
            return &stash.create<DotProductExecutor<ExactA>>(exactA, std::move(vector));
        }
        return &stash.create<DotProductExecutor<A>>(iattr, std::move(vector));
    }
    return &stash.create<DotProductExecutorByCopy<IntegerVectorT<T>, WeightedIntegerContent>>(attribute, std::move(vector));
}

template <typename T>
FeatureExecutor *
createForDirectIntegerWSet(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    IntegerVectorT<T> vector;
    WeightedSetParser::parse(prop.get(), vector);
    return createForDirectWSetImpl<IntegerAttributeTemplate<T>>(attribute, std::move(vector), stash);
}


FeatureExecutor *
createTypedWsetExecutor(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash) {
    using namespace dotproduct::wset;
    if (attribute->hasEnum()) {
        EnumVector vector(attribute);
        WeightedSetParser::parse(prop.get(), vector);
        const IWeightedIndexVector * getEnumHandles = dynamic_cast<const IWeightedIndexVector *>(attribute);
        if (supportsGetEnumHandles(getEnumHandles)) {
            return &stash.create<DotProductExecutorByEnum>(getEnumHandles, std::move(vector));
        }
        return &stash.create<DotProductExecutorByCopy<EnumVector, WeightedEnumContent>>(attribute, std::move(vector));
    } else {
        if (attribute->isStringType()) {
            StringVector vector;
            WeightedSetParser::parse(prop.get(), vector);
            return &stash.create<DotProductExecutorByCopy<StringVector, WeightedConstCharContent>>(attribute, std::move(vector));
        } else if (attribute->isIntegerType()) {
            if (attribute->getBasicType() == BasicType::INT32) {
                return createForDirectIntegerWSet<int32_t>(attribute, prop, stash);
            } else if (attribute->getBasicType() == BasicType::INT64) {
                return createForDirectIntegerWSet<int64_t>(attribute, prop, stash);
            }
        }
    }
    return nullptr;
}

FeatureExecutor &
createFromString(const IAttributeVector * attribute, const Property & prop, vespalib::Stash &stash)
{
    FeatureExecutor * executor = nullptr;
    if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        executor = createTypedWsetExecutor(attribute, prop, stash);
    } else if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        executor = createTypedArrayExecutor(attribute, prop, stash);
    }

    if (executor == nullptr) {
        LOG(warning, "The attribute vector '%s' is not of type weighted set string/integer nor"
                " array<int/long/float/double>, returning executor with default value.", attribute->getName().c_str());
        executor = &stash.create<SingleZeroValueExecutor>();
    }
    return *executor;
}

fef::Anything::UP
attemptParseArrayQueryVector(const IAttributeVector & attribute, const Property & prop) {
    if (!attribute.isImported()) {
        switch (attribute.getBasicType()) {
            case BasicType::INT32:
                return std::make_unique<ArrayParam<int32_t>>(prop);
            case BasicType::INT64:
                return std::make_unique<ArrayParam<int64_t>>(prop);
            case BasicType::FLOAT:
                return std::make_unique<ArrayParam<float>>(prop);
            case BasicType::DOUBLE:
                return std::make_unique<ArrayParam<double>>(prop);
            default:
                break;
        }
    } else {
        // See rationale in createTypedArrayExecutor() as to why we promote < 64 bit types
        // to their full-width equivalent when dealing with imported attributes.
        switch (attribute.getBasicType()) {
            case BasicType::INT32:
            case BasicType::INT64:
                return std::make_unique<ArrayParam<int64_t>>(prop);
            case BasicType::FLOAT:
            case BasicType::DOUBLE:
                return std::make_unique<ArrayParam<double>>(prop);
            default:
                break;
        }
    }
    return std::unique_ptr<fef::Anything>();
}

} // anon ns

const IAttributeVector *
DotProductBlueprint::upgradeIfNecessary(const IAttributeVector * attribute, const IQueryEnvironment & env) const {
    if ((attribute->getCollectionType() == attribute::CollectionType::WSET) &&
        attribute->hasEnum() &&
        (attribute->isStringType() || attribute->isIntegerType()))
    {
        attribute = env.getAttributeContext().getAttributeStableEnum(getAttribute(env));
    }
    return attribute;
}

void
DotProductBlueprint::prepareSharedState(const IQueryEnvironment & env, IObjectStore & store) const
{
    _attribute = env.getAttributeContext().getAttribute(getAttribute(env));
    const IAttributeVector * attribute = _attribute;
    if (attribute == nullptr) return;

    attribute = upgradeIfNecessary(attribute, env);
    fef::Anything::UP arguments;
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        Property tensorBlob = env.getProperties().lookup(getBaseName(), _queryVector, "tensor");
        if (attribute->isFloatingPointType() && tensorBlob.found() && !tensorBlob.get().empty()) {
            const Property::Value & blob = tensorBlob.get();
            vespalib::nbostream stream(blob.data(), blob.size());
            if (attribute->getBasicType() == BasicType::FLOAT) {
                arguments = std::make_unique<ArrayParam<float>>(stream);
            } else {
                arguments = std::make_unique<ArrayParam<double>>(stream);
            }
        } else {
            Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
            if (prop.found() && !prop.get().empty()) {
                arguments = attemptParseArrayQueryVector(*attribute, prop);
            }
        }
    } else if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
        if (prop.found() && !prop.get().empty()) {
            if (attribute->isStringType() && attribute->hasEnum()) {
                dotproduct::wset::EnumVector vector(attribute);
                WeightedSetParser::parse(prop.get(), vector);
            } else if (attribute->isIntegerType()) {
                if (attribute->hasEnum()) {
                    dotproduct::wset::EnumVector vector(attribute);
                    WeightedSetParser::parse(prop.get(), vector);
                } else {
                    dotproduct::wset::IntegerVector vector;
                    WeightedSetParser::parse(prop.get(), vector);
                }
            }
            // TODO actually use the parsed output for wset operations!
        }
    }
    if (arguments) {
        store.add(getBaseName() + "." + _queryVector + "." + OBJECT, std::move(arguments));
    }
}

FeatureExecutor &
DotProductBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = (_attribute != nullptr) ? _attribute : env.getAttributeContext().getAttribute(getAttribute(env));
    if (attribute == nullptr) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager, returning executor with default value.",
            getAttribute(env).c_str());
        return stash.create<SingleZeroValueExecutor>();
    }
    attribute = upgradeIfNecessary(attribute, env);
    const fef::Anything * argument = env.getObjectStore().get(getBaseName() + "." + _queryVector + "." + OBJECT);
    if (argument != nullptr) {
        return createFromObject(attribute, *argument, stash);
    } else {
        Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
        if (prop.found() && !prop.get().empty()) {
            return createFromString(attribute, prop, stash);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

}
