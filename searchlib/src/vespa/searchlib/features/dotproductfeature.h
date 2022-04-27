// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "utils.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::fef { class Property; }
namespace vespalib { class nbostream; }

namespace search::features {

namespace dotproduct {

struct ConstCharComparator {
    bool operator()(const char * lhs, const char * rhs) const {
        return strcmp(lhs, rhs) == 0;
    }
};

template <typename Src, typename Dst>
struct Converter {
    Dst convert(const Src & value) const { return value; }
};

template <>
struct Converter<vespalib::string, const char *> {
    const char * convert(const vespalib::string & value) const { return value.c_str(); }
};

template <typename T>
struct ArrayParam : public fef::Anything {
    ArrayParam(const fef::Property & prop);
    ArrayParam(vespalib::nbostream & stream);
    ArrayParam(std::vector<T> v) : values(std::move(v)) {}
    ~ArrayParam() override;
    std::vector<T>        values;
    std::vector<uint32_t> indexes;
};

namespace wset {

template <typename DimensionVType, typename DimensionHType, typename ComponentType, typename HashMapComparator = std::equal_to<DimensionHType> >
class VectorBase : public fef::Anything {
public:
    typedef std::pair<DimensionVType, ComponentType> Element; // <dimension, component>
    typedef std::vector<Element>                    Vector;
    typedef vespalib::hash_map<DimensionHType, ComponentType, vespalib::hash<DimensionHType>, HashMapComparator, vespalib::hashtable_base::and_modulator> HashMap;
protected:
    VectorBase();
    Vector _vector;
    HashMap _dimMap; // dimension -> component
public:
    VectorBase(VectorBase && rhs) = default;
    VectorBase & operator = (VectorBase && rhs) = default;
    ~VectorBase();
    const Vector & getVector() const { return _vector; }
    VectorBase & syncMap();
    const HashMap & getDimMap() const { return _dimMap; }
    bool empty() const { return _vector.empty(); }
};

template <typename T>
using NumericVectorBaseT = VectorBase<T, T, feature_t>;

/**
 * Represents a vector where the dimensions are integers.
 **/
template<typename T>
class IntegerVectorT : public NumericVectorBaseT<T> {
public:
    void insert(vespalib::stringref label, vespalib::stringref value) {
        this->_vector.emplace_back(util::strToNum<T>(label), util::strToNum<feature_t>(value));
    }
};

extern template class VectorBase<int64_t, int64_t, double>;
extern template class VectorBase<uint32_t, uint32_t, double>;
extern template class IntegerVectorT<int64_t>;

using IntegerVector = IntegerVectorT<int64_t>;

using StringVectorBase = VectorBase<vespalib::string, const char*, feature_t, ConstCharComparator>;

/**
 * Represents a vector where the dimensions are string values.
 **/
class StringVector : public StringVectorBase {
public:
    StringVector();
    StringVector(StringVector &&) = default;
    StringVector & operator = (StringVector &&) = default;
    ~StringVector();
    void insert(vespalib::stringref label, vespalib::stringref value) {
        _vector.emplace_back(label, util::strToNum<feature_t>(value));
    }
};

/**
 * Represents a vector where the dimensions are enum values for strings.
 **/
class EnumVector : public VectorBase<attribute::EnumHandle, attribute::EnumHandle, feature_t> {
private:
    const attribute::IAttributeVector * _attribute;
public:
    EnumVector(const attribute::IAttributeVector * attribute) : _attribute(attribute) {}
    void insert(vespalib::stringref label, vespalib::stringref value) {
        attribute::EnumHandle e;
        if (_attribute->findEnum(label.data(), e)) {
            _vector.emplace_back(e, util::strToNum<feature_t>(value));
        }
    }
};

/**
 * Common base for handling execution for all wset dot product executors.
 * Only cares about the underlying value type, not the concrete type of the
 * attribute vector itself.
 */
template <typename BaseType>
class DotProductExecutorBase : public fef::FeatureExecutor {
public:
    using AT = multivalue::WeightedValue<BaseType>;
    using V  = std::conditional_t<std::is_same_v<BaseType,const char*>,StringVectorBase,NumericVectorBaseT<BaseType>>;
private:
    const V                                    & _queryVector;
    const typename V::HashMap::const_iterator    _end;
    virtual vespalib::ConstArrayRef<AT> getAttributeValues(uint32_t docid) = 0;
public:
    DotProductExecutorBase(const V & queryVector);
    ~DotProductExecutorBase() override;
    void execute(uint32_t docId) override;
};

template <typename BaseType>
class DotProductByWeightedSetReadViewExecutor final : public DotProductExecutorBase<BaseType> {
public:
    using WeightedSetReadView = attribute::IWeightedSetReadView<BaseType>;
    using AT = typename DotProductExecutorBase<BaseType>::AT;
    using V  = typename DotProductExecutorBase<BaseType>::V;
protected:
    const WeightedSetReadView * _weighted_set_read_view;
private:
    std::unique_ptr<V> _backing;
    vespalib::ConstArrayRef<AT> getAttributeValues(uint32_t docid) override;
public:
    DotProductByWeightedSetReadViewExecutor(const WeightedSetReadView* weighted_set_read_view, const V & queryVector);
    DotProductByWeightedSetReadViewExecutor(const WeightedSetReadView * weighted_set_read_view, std::unique_ptr<V> queryVector);
    ~DotProductByWeightedSetReadViewExecutor();
};

}

namespace array {

/**
 * Common base for handling execution for all array dot product executors.
 * Only cares about the underlying value type, not the concrete type of the
 * attribute vector itself.
 */
template <typename BaseType>
class DotProductExecutorBase : public fef::FeatureExecutor {
public:
    using V  = std::vector<BaseType>;
private:
    const vespalib::hwaccelrated::IAccelrated   & _multiplier;
    V                                             _queryVector;
    virtual vespalib::ConstArrayRef<BaseType> getAttributeValues(uint32_t docid) = 0;
public:
    DotProductExecutorBase(const V & queryVector);
    ~DotProductExecutorBase() override;
    void execute(uint32_t docId) final override;
};

/**
 * Implements the executor for the dotproduct feature using array read view.
 */
template <typename BaseType>
class DotProductByArrayReadViewExecutor : public DotProductExecutorBase<BaseType> {
public:
    using V  = typename DotProductExecutorBase<BaseType>::V;
    using ArrayReadView = attribute::IArrayReadView<BaseType>;
protected:
    const ArrayReadView* _array_read_view;
private:
    vespalib::ConstArrayRef<BaseType> getAttributeValues(uint32_t docid) override;
public:
    DotProductByArrayReadViewExecutor(const ArrayReadView* array_read_view, const V & queryVector);
    ~DotProductByArrayReadViewExecutor();
};

/**
 * Implements the executor for the dotproduct feature.
 */
template <typename A>
class DotProductExecutor : public DotProductExecutorBase<typename A::BaseType> {
public:
    using V  = typename DotProductExecutorBase<typename A::BaseType>::V;
protected:
    const A * _attribute;
public:
    DotProductExecutor(const A * attribute, const V & queryVector);
    ~DotProductExecutor();
};

template <typename BaseType>
class SparseDotProductExecutorBase : public DotProductExecutorBase<BaseType> {
public:
    typedef std::vector<uint32_t> IV;
    typedef typename DotProductExecutorBase<BaseType>::V V;
    SparseDotProductExecutorBase(const V & queryVector, const IV & queryIndexes);
    ~SparseDotProductExecutorBase();
protected:
    IV              _queryIndexes;
    std::vector<BaseType> _scratch;
};

template <typename BaseType>
class SparseDotProductByArrayReadViewExecutor : public SparseDotProductExecutorBase<BaseType> {
public:
    using SparseDotProductExecutorBase<BaseType>::_queryIndexes;
    using SparseDotProductExecutorBase<BaseType>::_scratch;
    typedef std::vector<uint32_t> IV;
    typedef typename SparseDotProductExecutorBase<BaseType>::V V;
    using ArrayReadView = attribute::IArrayReadView<BaseType>;
    SparseDotProductByArrayReadViewExecutor(const ArrayReadView* array_read_view, const V & queryVector, const IV & queryIndexes);
    ~SparseDotProductByArrayReadViewExecutor();
private:
    vespalib::ConstArrayRef<BaseType> getAttributeValues(uint32_t docid) override;
    const ArrayReadView* _array_read_view;
};

}

}

/**
 * Implements the blueprint for the foreach executor.
 */
class DotProductBlueprint : public fef::Blueprint {
private:
    using IAttributeVector = attribute::IAttributeVector;
    vespalib::string _defaultAttribute;
    vespalib::string _attributeOverride;
    vespalib::string _queryVector;
    vespalib::string _attrKey;
    vespalib::string _queryVectorKey;

    const vespalib::string & getAttribute(const fef::IQueryEnvironment & env) const;
    const IAttributeVector * upgradeIfNecessary(const IAttributeVector * attribute, const fef::IQueryEnvironment & env) const;

public:
    DotProductBlueprint();
    ~DotProductBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;

    fef::ParameterDescriptions getDescriptions() const override;

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    void prepareSharedState(const fef::IQueryEnvironment & queryEnv, fef::IObjectStore & objectStore) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
