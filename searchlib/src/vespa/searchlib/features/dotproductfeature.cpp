// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dotproductfeature.h"
#include "valuefeature.h"
#include "weighted_set_parser.hpp"
#include "array_parser.hpp"
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.dotproduct");

using namespace search::attribute;
using namespace search::fef;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::Issue;
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
    auto values = getAttributeValues(docId);
    for (size_t i = 0; i < values.size(); ++i) {
        auto itr = _queryVector.getDimMap().find(values[i].value());
        if (itr != _end) {
            val += values[i].weight() * itr->second;
        }
    }
    outputs().set_number(0, val);
}

template <typename BaseType>
DotProductByWeightedSetReadViewExecutor<BaseType>::DotProductByWeightedSetReadViewExecutor(const WeightedSetReadView* weighted_set_read_view, const V & queryVector) :
    DotProductExecutorBase<BaseType>(queryVector),
    _weighted_set_read_view(weighted_set_read_view),
    _backing()
{
}

template <typename BaseType>
DotProductByWeightedSetReadViewExecutor<BaseType>::DotProductByWeightedSetReadViewExecutor(const WeightedSetReadView* weighted_set_read_view, std::unique_ptr<V> queryVector) :
    DotProductExecutorBase<BaseType>(*queryVector),
    _weighted_set_read_view(weighted_set_read_view),
    _backing(std::move(queryVector))
{
}

template <typename BaseType>
DotProductByWeightedSetReadViewExecutor<BaseType>::~DotProductByWeightedSetReadViewExecutor() = default;

template <typename BaseType>
vespalib::ConstArrayRef<typename DotProductByWeightedSetReadViewExecutor<BaseType>::AT>
DotProductByWeightedSetReadViewExecutor<BaseType>::getAttributeValues(uint32_t docId)
{
    return _weighted_set_read_view->get_values(docId);
}

namespace {

class DotProductExecutorByEnum final : public fef::FeatureExecutor {
public:
    using IWeightedSetEnumReadView = attribute::IWeightedSetEnumReadView;
    using V  = VectorBase<EnumHandle, EnumHandle, feature_t>;
private:
    const IWeightedSetEnumReadView* _weighted_set_enum_read_view;
    const V & _queryVector;
    const typename V::HashMap::const_iterator _end;
    std::unique_ptr<V>     _backing;
public:
    DotProductExecutorByEnum(const IWeightedSetEnumReadView* weighted_set_enum_read_view, const V & queryVector);
    DotProductExecutorByEnum(const IWeightedSetEnumReadView* weighted_set_enum_read_view, std::unique_ptr<V> queryVector);
    ~DotProductExecutorByEnum() override;
    void execute(uint32_t docId) override;
};

DotProductExecutorByEnum::DotProductExecutorByEnum(const IWeightedSetEnumReadView* weighted_set_enum_read_view, const V & queryVector)
    : FeatureExecutor(),
      _weighted_set_enum_read_view(weighted_set_enum_read_view),
      _queryVector(queryVector),
      _end(_queryVector.getDimMap().end()),
      _backing()
{
}


DotProductExecutorByEnum::DotProductExecutorByEnum(const IWeightedSetEnumReadView* weighted_set_enum_read_view, std::unique_ptr<V> queryVector)
    : FeatureExecutor(),
      _weighted_set_enum_read_view(weighted_set_enum_read_view),
      _queryVector(*queryVector),
      _end(_queryVector.getDimMap().end()),
      _backing(std::move(queryVector))
{
}

DotProductExecutorByEnum::~DotProductExecutorByEnum() = default;

void
DotProductExecutorByEnum::execute(uint32_t docId) {
    feature_t val = 0;
    auto values = _weighted_set_enum_read_view->get_values(docId);
    for (size_t i = 0; i < values.size(); ++i) {
        auto itr = _queryVector.getDimMap().find(values[i].value_ref().load_relaxed().ref());
        if (itr != _end) {
            val += values[i].weight() * itr->second;
        }
    }
    outputs().set_number(0, val);
}

class SingleDotProductExecutorByEnum final : public fef::FeatureExecutor {
public:
    using IWeightedSeEnumReadView = attribute::IWeightedSetEnumReadView;
    SingleDotProductExecutorByEnum(const IWeightedSetEnumReadView * weighted_set_enum_read_view, EnumHandle key, feature_t value)
        : _weighted_set_enum_read_view(weighted_set_enum_read_view),
          _key(key),
          _value(value)
    {}

    void execute(uint32_t docId) override {
        auto values = _weighted_set_enum_read_view->get_values(docId);
        for (size_t i = 0; i < values.size(); ++i) {
            if (values[i].value_ref().load_relaxed().ref() == _key) {
                outputs().set_number(0, values[i].weight()*_value);
                return;
            }
        }
        outputs().set_number(0, 0);
    }
private:
    const IWeightedSetEnumReadView * _weighted_set_enum_read_view;
    EnumHandle                   _key;
    feature_t                    _value;
};

template <typename BaseType>
class SingleDotProductByWeightedValueExecutor final : public fef::FeatureExecutor {
public:
    using WeightedSetReadView = attribute::IWeightedSetReadView<BaseType>;
    using StoredKeyType = std::conditional_t<std::is_same_v<BaseType,const char*>,vespalib::string,BaseType>;
    SingleDotProductByWeightedValueExecutor(const WeightedSetReadView * weighted_set_read_view, BaseType key, feature_t value)
        : _weighted_set_read_view(weighted_set_read_view),
          _key(key),
          _value(value)
    {}

    void execute(uint32_t docId) override {
        auto values = _weighted_set_read_view->get_values(docId);
        for (size_t i = 0; i < values.size(); ++i) {
            if (values[i].value() == _key) {
                outputs().set_number(0, values[i].weight() * _value);
                return;
            }
        }
        outputs().set_number(0, 0);
    }
private:
    const WeightedSetReadView* _weighted_set_read_view;
    StoredKeyType              _key;
    feature_t                  _value;
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
    auto values = getAttributeValues(docId);
    size_t commonRange = std::min(values.size(), _queryVector.size());
    outputs().set_number(0, _multiplier.dotProduct(
            &_queryVector[0], values.data(), commonRange));
}

template <typename BaseType>
DotProductByArrayReadViewExecutor<BaseType>::DotProductByArrayReadViewExecutor(const ArrayReadView* array_read_view, const V & queryVector) :
    DotProductExecutorBase<BaseType>(queryVector),
    _array_read_view(array_read_view)
{
}

template <typename BaseType>
DotProductByArrayReadViewExecutor<BaseType>::~DotProductByArrayReadViewExecutor() = default;

template <typename BaseType>
vespalib::ConstArrayRef<BaseType>
DotProductByArrayReadViewExecutor<BaseType>::getAttributeValues(uint32_t docId)
{
    return _array_read_view->get_values(docId);
}

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, const V & queryVector) :
    DotProductExecutorBase<typename A::BaseType>(queryVector),
    _attribute(attribute)
{
}

template <typename A>
DotProductExecutor<A>::~DotProductExecutor() = default;

template <typename BaseType>
SparseDotProductExecutorBase<BaseType>::SparseDotProductExecutorBase(const V & queryVector, const IV & queryIndexes) :
    DotProductExecutorBase<BaseType>(queryVector),
    _queryIndexes(queryIndexes),
    _scratch(queryIndexes.size())
{
}

template <typename BaseType>
SparseDotProductExecutorBase<BaseType>::~SparseDotProductExecutorBase() = default;

template <typename BaseType>
SparseDotProductByArrayReadViewExecutor<BaseType>::SparseDotProductByArrayReadViewExecutor(const ArrayReadView* array_read_view, const V & queryVector, const IV & queryIndexes)
    : SparseDotProductExecutorBase<BaseType>(queryVector, queryIndexes),
      _array_read_view(array_read_view)
{
}

template <typename BaseType>
SparseDotProductByArrayReadViewExecutor<BaseType>::~SparseDotProductByArrayReadViewExecutor() = default;

template <typename BaseType>
vespalib::ConstArrayRef<BaseType>
SparseDotProductByArrayReadViewExecutor<BaseType>::getAttributeValues(uint32_t docid)
{
    auto allValues = _array_read_view->get_values(docid);
    size_t i(0);
    for (; (i < _queryIndexes.size()) && (_queryIndexes[i] < allValues.size()); i++) {
        _scratch[i] = allValues[_queryIndexes[i]];
    }
    return vespalib::ConstArrayRef(_scratch.data(), i);
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
    try {
        auto tensor = vespalib::eval::decode_value(stream, FastValueBuilderFactory::get());
        if (tensor->type().is_dense()) {
            TypedCells cells = tensor->cells();
            typify_invoke<1,TypifyCellType,CopyCellsToVector<T>>(cells.type, cells, values);
        } else {
            Issue::report("dot_product feature: Expected dense tensor, but got type '%s'", tensor->type().to_spec().c_str());
        }
    } catch (const vespalib::eval::DecodeValueException &e) {
        Issue::report("dot_product feature: Failed to decode tensor: %s", e.what());
    }
}

template <typename T>
ArrayParam<T>::~ArrayParam() = default;


// Explicit instantiation since these are inspected by unit tests.
// FIXME this feels a bit dirty, consider breaking up ArrayParam to remove dependencies
// on templated vector parsing. This is why it's defined in this translation unit as it is.
template struct ArrayParam<int64_t>;
template struct ArrayParam<int32_t>;
template struct ArrayParam<double>;
template struct ArrayParam<float>;

} // namespace dotproduct

namespace {

using dotproduct::ArrayParam;

template <typename AT>
const attribute::IMultiValueReadView<AT>*
make_multi_value_read_view(const IAttributeVector& attribute, vespalib::Stash& stash)
{
    auto multi_value_attribute = attribute.as_multi_value_attribute();
    if (multi_value_attribute != nullptr) {
        return multi_value_attribute->make_read_view(attribute::IMultiValueAttribute::MultiValueTag<AT>(), stash);
    }
    return nullptr;
}

template <typename T>
FeatureExecutor &
createForDirectArrayImpl(const IAttributeVector * attribute,
                         const std::vector<T> & values,
                         const std::vector<uint32_t> & indexes,
                         vespalib::Stash & stash)
{
    if (values.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    }
    auto array_read_view = make_multi_value_read_view<T>(*attribute, stash);
    if (array_read_view != nullptr) {
        if (indexes.empty()) {
            return stash.create<dotproduct::array::DotProductByArrayReadViewExecutor<T>>(array_read_view, values);
        } else {
            return stash.create<dotproduct::array::SparseDotProductByArrayReadViewExecutor<T>>(array_read_view, values, indexes);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

template <typename T>
FeatureExecutor &
createForDirectArray(const IAttributeVector * attribute,
                     const Property & prop,
                     vespalib::Stash & stash) {
    std::vector<T> values;
    std::vector<uint32_t> indexes;
    parseVectors(prop, values, indexes);
    return createForDirectArrayImpl<T>(attribute, values, indexes, stash);
}

template <typename T>
FeatureExecutor &
createForDirectArray(const IAttributeVector * attribute,
                     const ArrayParam<T> & arguments,
                     vespalib::Stash & stash) {
    return createForDirectArrayImpl<T>(attribute, arguments.values, arguments.indexes, stash);
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

size_t extractSize(const dotproduct::wset::StringVector& v) {
    return v.getVector().size();
}

std::pair<const char*, feature_t> extractElem(const dotproduct::wset::StringVector& v, size_t idx) {
    const auto & pair = v.getVector()[idx];
    return std::pair<const char*, feature_t>(pair.first.c_str(), pair.second);
}

size_t extractSize(const std::unique_ptr<dotproduct::wset::StringVector>& v) {
    return extractSize(*v);
}

std::pair<const char*, feature_t> extractElem(const std::unique_ptr<dotproduct::wset::StringVector>& v, size_t idx) {
    return extractElem(*v, idx);
}

template <typename T, typename V>
FeatureExecutor &
createForDirectWSetImpl(const IAttributeVector * attribute, V && vector, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    using VT = multivalue::WeightedValue<T>;
    auto weighted_set_read_view = make_multi_value_read_view<VT>(*attribute, stash);
    if (weighted_set_read_view != nullptr) {
        if (extractSize(vector) == 1) {
            auto elem = extractElem(vector, 0ul);
            return stash.create<SingleDotProductByWeightedValueExecutor<T>>(weighted_set_read_view, elem.first, elem.second);
        }
        return stash.create<DotProductByWeightedSetReadViewExecutor<T>>(weighted_set_read_view, std::forward<V>(vector));
    }
    return stash.create<SingleZeroValueExecutor>();
}

template <typename T>
FeatureExecutor &
createForDirectIntegerWSet(const IAttributeVector * attribute, const dotproduct::wset::IntegerVectorT<T> & vector, vespalib::Stash & stash)
{
    using namespace dotproduct::wset;
    return vector.empty()
           ? stash.create<SingleZeroValueExecutor>()
           : createForDirectWSetImpl<T>(attribute, vector, stash);
}

FeatureExecutor &
createFromObject(const IAttributeVector * attribute, const fef::Anything & object, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        switch (attribute->getBasicType()) {
            case BasicType::INT8:
                return createForDirectArray<int8_t>(attribute, dynamic_cast<const ArrayParam<int8_t> &>(object), stash);
            case BasicType::INT32:
                return createForDirectArray<int32_t>(attribute, dynamic_cast<const ArrayParam<int32_t> &>(object), stash);
            case BasicType::INT64:
                return createForDirectArray<int64_t>(attribute, dynamic_cast<const ArrayParam<int64_t> &>(object), stash);
            case BasicType::FLOAT:
                return createForDirectArray<float>(attribute, dynamic_cast<const ArrayParam<float> &>(object), stash);
            case BasicType::DOUBLE:
                return createForDirectArray<double>(attribute, dynamic_cast<const ArrayParam<double> &>(object), stash);
            default:
                break;
        }
    } else if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        using namespace dotproduct::wset;
        if (attribute->hasEnum()) {
            const auto & vector = dynamic_cast<const EnumVector &>(object);
            if (vector.empty()) {
                return stash.create<SingleZeroValueExecutor>();
            }
            using VT = multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;
            auto* weighted_set_enum_read_view = make_multi_value_read_view<VT>(*attribute, stash);
            if (weighted_set_enum_read_view != nullptr) {
                if (vector.getVector().size() == 1) {
                    const auto & elem = vector.getVector()[0];
                    return stash.create<SingleDotProductExecutorByEnum>(weighted_set_enum_read_view, elem.first, elem.second);
                }
                return stash.create<DotProductExecutorByEnum>(weighted_set_enum_read_view, vector);
            }
        } else {
            if (attribute->isStringType()) {
                const auto & vector = dynamic_cast<const StringVector &>(object);
                if (vector.empty()) {
                    return stash.create<SingleZeroValueExecutor>();
                }
                return createForDirectWSetImpl<const char*>(attribute, vector, stash);
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
    Issue::report("dot_product feature: The attribute vector '%s' is NOT of type array<int/long/float/double>"
                  ", returning default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}

FeatureExecutor *
createTypedArrayExecutor(const IAttributeVector * attribute, const Property & prop, vespalib::Stash & stash) {
    switch (attribute->getBasicType()) {
        case BasicType::INT8:
            return &createForDirectArray<int8_t>(attribute, prop, stash);
        case BasicType::INT32:
            return &createForDirectArray<int32_t>(attribute, prop, stash);
        case BasicType::INT64:
            return &createForDirectArray<int64_t>(attribute, prop, stash);
        case BasicType::FLOAT:
            return &createForDirectArray<float>(attribute, prop, stash);
        case BasicType::DOUBLE:
            return &createForDirectArray<double>(attribute, prop, stash);
        default:
            break;
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
           : createForDirectWSetImpl<T>(attribute, std::move(vector), stash);
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
        using VT = multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;
        auto* weighted_set_enum_read_view = make_multi_value_read_view<VT>(*attribute, stash);
        if (weighted_set_enum_read_view != nullptr) {
            if (vector->getVector().size() == 1) {
                const auto & elem = vector->getVector()[0];
                return stash.create<SingleDotProductExecutorByEnum>(weighted_set_enum_read_view, elem.first, elem.second);
            }
            return stash.create<DotProductExecutorByEnum>(weighted_set_enum_read_view, std::move(vector));
        }
    } else {
        if (attribute->isStringType()) {
            auto vector = std::make_unique<StringVector>();
            WeightedSetParser::parse(prop.get(), *vector);
            if (vector->empty()) {
                return stash.create<SingleZeroValueExecutor>();
            }
            vector->syncMap();
            return createForDirectWSetImpl<const char*>(attribute, std::move(vector), stash);
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
        Issue::report("dot_product feature: The attribute vector '%s' is not of type weighted set string/integer nor"
                      " array<int/long/float/double>, returning default value.", attribute->getName().c_str());
        executor = &stash.create<SingleZeroValueExecutor>();
    }
    return *executor;
}

fef::Anything::UP
attemptParseArrayQueryVector(const IAttributeVector & attribute, const Property & prop) {
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
        Issue::report("dot_product feature: The attribute vector '%s' was not found, returning default value.",
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
