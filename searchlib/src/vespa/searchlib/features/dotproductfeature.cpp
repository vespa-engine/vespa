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
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.dotproduct");

using namespace search::attribute;
using namespace search::fef;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
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

template class VectorBase<int64_t, int64_t, double>;
template class VectorBase<uint32_t, uint32_t, double>;

template class IntegerVectorT<int64_t>;


template <typename Vector, typename Buffer>
DotProductExecutorByCopy<Vector, Buffer>::DotProductExecutorByCopy(const IAttributeVector * attribute, const Vector & queryVector) :
    FeatureExecutor(),
    _attribute(attribute),
    _queryVector(queryVector),
    _end(_queryVector.getDimMap().end()),
    _buffer(),
    _backing()
{
    _buffer.allocate(_attribute->getMaxValueCount());
}

template <typename Vector, typename Buffer>
DotProductExecutorByCopy<Vector, Buffer>::DotProductExecutorByCopy(const IAttributeVector * attribute, std::unique_ptr<Vector> queryVector) :
    FeatureExecutor(),
    _attribute(attribute),
    _queryVector(*queryVector),
    _end(_queryVector.getDimMap().end()),
    _buffer(),
    _backing(std::move(queryVector))
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
    _buffer.fill(*_attribute, docId);
    for (size_t i = 0; i < _buffer.size(); ++i) {
        auto itr = _queryVector.getDimMap().find(_buffer[i].getValue());
        if (itr != _end) {
            val += _buffer[i].getWeight() * itr->second;
        }
    }
    outputs().set_number(0, val);
}

StringVector::StringVector() = default;
StringVector::~StringVector() = default;

template <typename BaseType>
DotProductExecutorBase<BaseType>::DotProductExecutorBase(const V & queryVector)
    : FeatureExecutor(),
      _queryVector(queryVector),
      _end(_queryVector.getDimMap().end())
{
}

template <typename BaseType>
DotProductExecutorBase<BaseType>::~DotProductExecutorBase() = default;

template <typename BaseType>
void DotProductExecutorBase<BaseType>::execute(uint32_t docId) {
    feature_t val = 0;
    const AT * values(nullptr);
    uint32_t sz = getAttributeValues(docId, values);
    for (size_t i = 0; i < sz; ++i) {
        auto itr = _queryVector.getDimMap().find(values[i].value());
        if (itr != _end) {
            val += values[i].weight() * itr->second;
        }
    }
    outputs().set_number(0, val);
}

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, const V & queryVector) :
    DotProductExecutorBase<typename A::BaseType>(queryVector),
    _attribute(attribute),
    _backing()
{
}

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, std::unique_ptr<V> queryVector) :
    DotProductExecutorBase<typename A::BaseType>(*queryVector),
    _attribute(attribute),
    _backing(std::move(queryVector))
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

class DotProductExecutorByEnum final : public fef::FeatureExecutor {
public:
    using V  = VectorBase<EnumHandle, EnumHandle, feature_t>;
private:
    const IWeightedIndexVector * _attribute;
    const V & _queryVector;
    const typename V::HashMap::const_iterator _end;
    std::unique_ptr<V>     _backing;
public:
    DotProductExecutorByEnum(const IWeightedIndexVector * attribute, const V & queryVector);
    DotProductExecutorByEnum(const IWeightedIndexVector * attribute, std::unique_ptr<V> queryVector);
    ~DotProductExecutorByEnum() override;
    void execute(uint32_t docId) override;
};

DotProductExecutorByEnum::DotProductExecutorByEnum(const IWeightedIndexVector * attribute, const V & queryVector)
    : FeatureExecutor(),
      _attribute(attribute),
      _queryVector(queryVector),
      _end(_queryVector.getDimMap().end()),
      _backing()
{
}


DotProductExecutorByEnum::DotProductExecutorByEnum(const IWeightedIndexVector * attribute, std::unique_ptr<V> queryVector)
    : FeatureExecutor(),
      _attribute(attribute),
      _queryVector(*queryVector),
      _end(_queryVector.getDimMap().end()),
      _backing(std::move(queryVector))
{
}

DotProductExecutorByEnum::~DotProductExecutorByEnum() = default;

void
DotProductExecutorByEnum::execute(uint32_t docId) {
    feature_t val = 0;
    const IWeightedIndexVector::WeightedIndex *values(nullptr);
    uint32_t sz = _attribute->getEnumHandles(docId, values);
    for (size_t i = 0; i < sz; ++i) {
        auto itr = _queryVector.getDimMap().find(values[i].value().ref());
        if (itr != _end) {
            val += values[i].weight() * itr->second;
        }
    }
    outputs().set_number(0, val);
}

class SingleDotProductExecutorByEnum final : public fef::FeatureExecutor {
public:
    SingleDotProductExecutorByEnum(const IWeightedIndexVector * attribute, EnumHandle key, feature_t value)
        : _attribute(attribute),
          _key(key),
          _value(value)
    {}

    void execute(uint32_t docId) override {
        const IWeightedIndexVector::WeightedIndex *values(nullptr);
        uint32_t sz = _attribute->getEnumHandles(docId, values);
        for (size_t i = 0; i < sz; ++i) {
            if (values[i].value().ref() == _key) {
                outputs().set_number(0, values[i].weight()*_value);
                return;
            }
        }
        outputs().set_number(0, 0);
    }
private:
    const IWeightedIndexVector * _attribute;
    EnumHandle                   _key;
    feature_t                    _value;
};

template <typename A>
class SingleDotProductExecutorByValue final : public fef::FeatureExecutor {
public:
    SingleDotProductExecutorByValue(const A * attribute, typename A::BaseType key, feature_t value)
        : _attribute(attribute),
          _key(key),
          _value(value)
    {}

    void execute(uint32_t docId) override {
        const multivalue::WeightedValue<typename A::BaseType> *values(nullptr);
        uint32_t sz = _attribute->getRawValues(docId, values);
        for (size_t i = 0; i < sz; ++i) {
            if (values[i].value() == _key) {
                outputs().set_number(0, values[i].weight() * _value);
                return;
            }
        }
        outputs().set_number(0, 0);
    }
private:
    const A               * _attribute;
    typename A::BaseType    _key;
    feature_t               _value;
};

}

}

namespace dotproduct::array {

template <typename BaseType>
DotProductExecutorBase<BaseType>::DotProductExecutorBase(const V & queryVector)
    : FeatureExecutor(),
      _multiplier(IAccelrated::getAccelerator()),
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
    outputs().set_number(0, _multiplier.dotProduct(
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

namespace {

template <typename T, typename AsT = T>
void
parseVectors(const Property& prop, std::vector<T>& values, std::vector<uint32_t>& indexes)
{
    typedef std::vector<ArrayParser::ValueAndIndex<AsT>> SparseV;
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

template <>
void
parseVectors<int8_t, int8_t>(const Property& prop, std::vector<int8_t>& values, std::vector<uint32_t>& indexes) {
    parseVectors<int8_t, int16_t>(prop, values, indexes);
}

template <typename TCT>
struct CopyCellsToVector {
    template<typename ICT>
    static void invoke(TypedCells source, std::vector<TCT> &target) {
        target.reserve(source.size);
        auto cells = source.typify<ICT>();
        for (auto value : cells) {
            target.push_back(value);
        }
    }
};

} // namespace <unnamed>

namespace dotproduct {

template <typename T>
ArrayParam<T>::ArrayParam(const Property & prop) {
    parseVectors(prop, values, indexes);
}

template <typename T>
ArrayParam<T>::ArrayParam(vespalib::nbostream & stream) {
    using vespalib::typify_invoke;
    using vespalib::eval::TypifyCellType;
    auto tensor = vespalib::eval::decode_value(stream, FastValueBuilderFactory::get());
    if (tensor->type().is_dense()) {
        TypedCells cells = tensor->cells();
        typify_invoke<1,TypifyCellType,CopyCellsToVector<T>>(cells.type, cells, values);
    } else {
        LOG(warning, "Expected dense tensor, but got type '%s'", tensor->type().to_spec().c_str());
    }
}

template <typename T>
ArrayParam<T>::~ArrayParam() = default;


// Explicit instantiation since these are inspected by unit tests.
// FIXME this feels a bit dirty, consider breaking up ArrayParam to remove dependencies
// on templated vector parsing. This is why it's defined in this translation unit as it is.
template ArrayParam<int64_t>::ArrayParam(const Property & prop);
#ifdef __clang__
template ArrayParam<int64_t>::~ArrayParam();
#endif
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

            auto * exactA = dynamic_cast<const ExactA *>(iattr);
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

template<typename T>
size_t extractSize(const dotproduct::wset::IntegerVectorT<T> & v) {
    return v.getVector().size();
}

template<typename T>
std::pair<T, feature_t> extractElem(const dotproduct::wset::IntegerVectorT<T> & v, size_t idx) {
    const auto & pair = v.getVector()[idx];
    return std::pair<T, feature_t>(pair.first, pair.second);
}

template<typename T>
size_t extractSize(const std::unique_ptr<dotproduct::wset::IntegerVectorT<T>> & v) {
    return extractSize(*v);
}

template<typename T>
std::pair<T, feature_t> extractElem(const std::unique_ptr<dotproduct::wset::IntegerVectorT<T>> & v, size_t idx) {
    return extractElem(*v, idx);
}

template <typename A, typename V>
FeatureExecutor &
createForDirectWSetImpl(const IAttributeVector * attribute, V && vector, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    using T = typename A::BaseType;
    const A * iattr = dynamic_cast<const A *>(attribute);
    using VT = multivalue::WeightedValue<T>;
    using ExactA = MultiValueNumericAttribute<A, VT>;
    if (!attribute->isImported() && (iattr != nullptr) && supportsGetRawValues<A, VT>(*iattr)) {
        auto * exactA = dynamic_cast<const ExactA *>(iattr);
        if (exactA != nullptr) {
            if (extractSize(vector) == 1) {
                auto elem = extractElem(vector, 0ul);
                return stash.create<SingleDotProductExecutorByValue<ExactA>>(exactA, elem.first, elem.second);
            }
            return stash.create<DotProductExecutor<ExactA>>(exactA, std::forward<V>(vector));
        }
        return stash.create<DotProductExecutor<A>>(iattr, std::forward<V>(vector));
    }
    return stash.create<DotProductExecutorByCopy<IntegerVectorT<T>, WeightedIntegerContent>>(attribute, std::forward<V>(vector));
}

template <typename T>
FeatureExecutor &
createForDirectIntegerWSet(const IAttributeVector * attribute, const dotproduct::wset::IntegerVectorT<T> & vector, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    return vector.empty()
           ? stash.create<SingleZeroValueExecutor>()
           : createForDirectWSetImpl<IntegerAttributeTemplate<T>>(attribute, vector, stash);
}

FeatureExecutor &
createFromObject(const IAttributeVector * attribute, const fef::Anything & object, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        if (!attribute->isImported()) {
            switch (attribute->getBasicType()) {
                case BasicType::INT8:
                    return createForDirectArray<IntegerAttributeTemplate<int8_t>>(attribute, dynamic_cast<const ArrayParam<int8_t> &>(object), stash);
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
                case BasicType::INT8:
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
    } else if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        using namespace dotproduct::wset;
        if (attribute->hasEnum()) {
            const auto & vector = dynamic_cast<const EnumVector &>(object);
            if (vector.empty()) {
                return stash.create<SingleZeroValueExecutor>();
            }
            const auto * getEnumHandles = dynamic_cast<const IWeightedIndexVector *>(attribute);
            if (supportsGetEnumHandles(getEnumHandles)) {
                if (vector.getVector().size() == 1) {
                    const auto & elem = vector.getVector()[0];
                    return stash.create<SingleDotProductExecutorByEnum>(getEnumHandles, elem.first, elem.second);
                }
                return stash.create<DotProductExecutorByEnum>(getEnumHandles, vector);
            }
            return stash.create<DotProductExecutorByCopy<EnumVector, WeightedEnumContent>>(attribute, vector);
        } else {
            if (attribute->isStringType()) {
                const auto & vector = dynamic_cast<const StringVector &>(object);
                if (vector.empty()) {
                    return stash.create<SingleZeroValueExecutor>();
                }
                return stash.create<DotProductExecutorByCopy<StringVector, WeightedConstCharContent>>(attribute, vector);
            } else if (attribute->isIntegerType()) {
                if (attribute->getBasicType() == BasicType::INT32) {
                    return createForDirectIntegerWSet<int32_t>(attribute, dynamic_cast<const IntegerVectorT<int32_t> &>(object), stash);
                } else if (attribute->getBasicType() == BasicType::INT64) {
                    return createForDirectIntegerWSet<int64_t>(attribute, dynamic_cast<const IntegerVectorT<int64_t> &>(object), stash);
                } else if (attribute->getBasicType() == BasicType::INT8) {
                    return createForDirectIntegerWSet<int8_t>(attribute, dynamic_cast<const IntegerVectorT<int8_t> &>(object), stash);
                }
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
            case BasicType::INT8:
                return &createForDirectArray<IntegerAttributeTemplate<int8_t>>(attribute, prop, stash);
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
            case BasicType::INT8:
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

template <typename T>
FeatureExecutor &
createForDirectIntegerWSet(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    auto vector = std::make_unique<IntegerVectorT<T>>();
    WeightedSetParser::parse(prop.get(), *vector);
    vector->syncMap();
    return vector->empty()
           ? stash.create<SingleZeroValueExecutor>()
           : createForDirectWSetImpl<IntegerAttributeTemplate<T>>(attribute, std::move(vector), stash);
}

FeatureExecutor &
createTypedWsetExecutor(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash) {
    using namespace dotproduct::wset;
    if (attribute->hasEnum()) {
        auto vector = std::make_unique<EnumVector>(attribute);
        WeightedSetParser::parse(prop.get(), *vector);
        if (vector->empty()) {
            return stash.create<SingleZeroValueExecutor>();
        }
        vector->syncMap();
        auto * getEnumHandles = dynamic_cast<const IWeightedIndexVector *>(attribute);
        if (supportsGetEnumHandles(getEnumHandles)) {
            if (vector->getVector().size() == 1) {
                const auto & elem = vector->getVector()[0];
                return stash.create<SingleDotProductExecutorByEnum>(getEnumHandles, elem.first, elem.second);
            }
            return stash.create<DotProductExecutorByEnum>(getEnumHandles, std::move(vector));
        }
        return stash.create<DotProductExecutorByCopy<EnumVector, WeightedEnumContent>>(attribute, std::move(vector));
    } else {
        if (attribute->isStringType()) {
            auto vector = std::make_unique<StringVector>();
            WeightedSetParser::parse(prop.get(), *vector);
            if (vector->empty()) {
                return stash.create<SingleZeroValueExecutor>();
            }
            vector->syncMap();
            return stash.create<DotProductExecutorByCopy<StringVector, WeightedConstCharContent>>(attribute, std::move(vector));
        } else if (attribute->isIntegerType()) {
            if (attribute->getBasicType() == BasicType::INT32) {
                return createForDirectIntegerWSet<int32_t>(attribute, prop, stash);
            } else if (attribute->getBasicType() == BasicType::INT64) {
                return createForDirectIntegerWSet<int64_t>(attribute, prop, stash);
            } else if (attribute->getBasicType() == BasicType::INT8) {
                return createForDirectIntegerWSet<int8_t>(attribute, prop, stash);
            }
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

FeatureExecutor &
createFromString(const IAttributeVector * attribute, const Property & prop, vespalib::Stash &stash)
{
    FeatureExecutor * executor = nullptr;
    if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        executor = &createTypedWsetExecutor(attribute, prop, stash);
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
            case BasicType::INT8:
                return std::make_unique<ArrayParam<int8_t>>(prop);
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
            case BasicType::INT8:
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

vespalib::string
make_queryvector_key(const vespalib::string & base, const vespalib::string & subKey) {
    vespalib::string key(base);
    key.append(".vector.");
    key.append(subKey);
    return key;
}

const vespalib::string &
make_queryvector_key_for_attribute(const IAttributeVector & attribute, const vespalib::string & key, vespalib::string & scratchPad) {
    if (attribute.hasEnum() && (attribute.getCollectionType() == attribute::CollectionType::WSET)) {
        scratchPad = key;
        scratchPad.append(".").append(attribute.getName());
        return scratchPad;
    }
    return key;
}

vespalib::string
make_attribute_key(const vespalib::string & base, const vespalib::string & subKey) {
    vespalib::string key(base);
    key.append(".attribute.");
    key.append(subKey);
    return key;
}

} // anon ns

const IAttributeVector *
DotProductBlueprint::upgradeIfNecessary(const IAttributeVector * attribute, const IQueryEnvironment & env) const {
    if ((attribute != nullptr) &&
        (attribute->getCollectionType() == attribute::CollectionType::WSET) &&
        attribute->hasEnum() &&
        (attribute->isStringType() || attribute->isIntegerType()))
    {
        attribute = env.getAttributeContext().getAttributeStableEnum(attribute->getName());
    }
    return attribute;
}

namespace {

fef::Anything::UP
createQueryVector(const IQueryEnvironment & env, const IAttributeVector * attribute,
                  const vespalib::string & baseName, const vespalib::string & queryVector)
{
    fef::Anything::UP arguments;
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        Property tensorBlob = env.getProperties().lookup(baseName, queryVector, "tensor");
        if (attribute->isFloatingPointType() && tensorBlob.found() && !tensorBlob.get().empty()) {
            const Property::Value & blob = tensorBlob.get();
            vespalib::nbostream stream(blob.data(), blob.size());
            if (attribute->getBasicType() == BasicType::FLOAT) {
                arguments = std::make_unique<ArrayParam<float>>(stream);
            } else {
                arguments = std::make_unique<ArrayParam<double>>(stream);
            }
        } else {
            Property prop = env.getProperties().lookup(baseName, queryVector);
            if (prop.found() && !prop.get().empty()) {
                arguments = attemptParseArrayQueryVector(*attribute, prop);
            }
        }
    } else if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        Property prop = env.getProperties().lookup(baseName, queryVector);
        if (prop.found() && !prop.get().empty()) {
            if (attribute->isStringType() && attribute->hasEnum()) {
                auto vector = std::make_unique<dotproduct::wset::EnumVector>(attribute);
                WeightedSetParser::parse(prop.get(), *vector);
                vector->syncMap();
                arguments = std::move(vector);
            } else if (attribute->isIntegerType()) {
                if (attribute->hasEnum()) {
                    auto vector = std::make_unique<dotproduct::wset::EnumVector>(attribute);
                    WeightedSetParser::parse(prop.get(), *vector);
                    vector->syncMap();
                    arguments = std::move(vector);
                } else {
                    if (attribute->getBasicType() == BasicType::INT32) {
                        auto vector = std::make_unique<dotproduct::wset::IntegerVectorT<int32_t>>();
                        WeightedSetParser::parse(prop.get(), *vector);
                        vector->syncMap();
                        arguments = std::move(vector);
                    } else if (attribute->getBasicType() == BasicType::INT64) {
                        auto vector = std::make_unique<dotproduct::wset::IntegerVectorT<int64_t>>();
                        WeightedSetParser::parse(prop.get(), *vector);
                        vector->syncMap();
                        arguments = std::move(vector);
                    } else if (attribute->getBasicType() == BasicType::INT8) {
                        auto vector = std::make_unique<dotproduct::wset::IntegerVectorT<int8_t>>();
                        WeightedSetParser::parse(prop.get(), *vector);
                        vector->syncMap();
                        arguments = std::move(vector);
                    }
                }
            }
        }
    }
    return arguments;
}

}

DotProductBlueprint::DotProductBlueprint() :
    Blueprint("dotProduct"),
    _defaultAttribute(),
    _attributeOverride(),
    _queryVector(),
    _attrKey(),
    _queryVectorKey()
{ }

DotProductBlueprint::~DotProductBlueprint() = default;

const vespalib::string &
DotProductBlueprint::getAttribute(const IQueryEnvironment & env) const
{
    Property prop = env.getProperties().lookup(getBaseName(), _attributeOverride);
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
    _attributeOverride = _defaultAttribute + ".override.name";
    _queryVector = params[1].getValue();
    _attrKey = make_attribute_key(getBaseName(), _defaultAttribute);
    _queryVectorKey = make_queryvector_key(getBaseName(), _queryVector);
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

void
DotProductBlueprint::prepareSharedState(const IQueryEnvironment & env, IObjectStore & store) const
{
    const IAttributeVector * attribute = lookupAndStoreAttribute(_attrKey, getAttribute(env), env, store);
    if (attribute == nullptr) return;

    const fef::Anything * queryVector = env.getObjectStore().get(_queryVectorKey);
    if (queryVector == nullptr) {
        fef::Anything::UP arguments = createQueryVector(env, attribute, getBaseName(), _queryVector);
        if (arguments) {
            vespalib::string scratchPad;
            store.add(make_queryvector_key_for_attribute(*attribute, _queryVectorKey, scratchPad), std::move(arguments));
        }
    }

    upgradeIfNecessary(attribute, env);
}

FeatureExecutor &
DotProductBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    // Doing it "manually" here to avoid looking up attribute override unless needed.
    const fef::Anything * attributeArg = env.getObjectStore().get(_attrKey);
    const IAttributeVector * attribute = nullptr;
    if (attributeArg != nullptr) {
        attribute = static_cast<const fef::AnyWrapper<const IAttributeVector *> *>(attributeArg)->getValue();
    } else {
        attribute = env.getAttributeContext().getAttribute(getAttribute(env));
        attribute = upgradeIfNecessary(attribute, env);
    }
    if (attribute == nullptr) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager, returning executor with default value.",
            getAttribute(env).c_str());
        return stash.create<SingleZeroValueExecutor>();
    }
    vespalib::string scratchPad;
    const fef::Anything * queryVectorArg = env.getObjectStore().get(make_queryvector_key_for_attribute(*attribute, _queryVectorKey, scratchPad));
    if (queryVectorArg != nullptr) {
        return createFromObject(attribute, *queryVectorArg, stash);
    } else {
        Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
        if (prop.found() && !prop.get().empty()) {
            return createFromString(attribute, prop, stash);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

}
