// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "utils.h"
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/attribute/multivalue.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search {
namespace fef {
class Property;
}

namespace features {

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
    std::vector<T>        values;
    std::vector<uint32_t> indexes;
};

namespace wset {

template <typename DimensionVType, typename DimensionHType, typename ComponentType, typename HashMapComparator = std::equal_to<DimensionHType> >
class VectorBase {
public:
    typedef std::pair<DimensionVType, ComponentType> Element; // <dimension, component>
    typedef std::vector<Element>                    Vector;
    typedef vespalib::hash_map<DimensionHType, ComponentType, vespalib::hash<DimensionHType>, HashMapComparator> HashMap;
protected:
    VectorBase();
    Vector _vector;
    HashMap _dimMap; // dimension -> component
public:
    ~VectorBase();
    const Vector & getVector() const { return _vector; }
    void syncMap() {
        Converter<DimensionVType, DimensionHType> conv;
        _dimMap.clear();
        _dimMap.resize(_vector.size()*2);
        for (size_t i = 0; i < _vector.size(); ++i) {
            _dimMap.insert(std::make_pair(conv.convert(_vector[i].first), _vector[i].second));
        }
    }
    const HashMap & getDimMap() const { return _dimMap; }
};

/**
 * Represents a vector where the dimensions are integers.
 **/
class IntegerVector : public VectorBase<int64_t, int64_t, feature_t> {
public:
    void insert(const vespalib::stringref & label, const vespalib::stringref & value) {
        _vector.push_back(std::make_pair(util::strToNum<int64_t>(label), util::strToNum<feature_t>(value)));
    }
};

/**
 * Represents a vector where the dimensions are string values.
 **/
class StringVector : public VectorBase<vespalib::string, const char *, feature_t, ConstCharComparator> {
public:
    StringVector();
    ~StringVector();
    void insert(const vespalib::stringref & label, const vespalib::stringref & value) {
        _vector.push_back(std::make_pair(label, util::strToNum<feature_t>(value)));
    }
};

/**
 * Represents a vector where the dimensions are enum values for strings.
 **/
class EnumVector : public VectorBase<search::attribute::EnumHandle, search::attribute::EnumHandle, feature_t> {
private:
    const search::attribute::IAttributeVector * _attribute;
public:
    EnumVector(const search::attribute::IAttributeVector * attribute) : _attribute(attribute) {}
    void insert(const vespalib::stringref & label, const vespalib::stringref & value) {
        search::attribute::EnumHandle e;
        if (_attribute->findEnum(label.c_str(), e)) {
            _vector.push_back(std::make_pair(e, util::strToNum<feature_t>(value)));
        }
    }
};


/**
 * Implements the executor for the dotproduct feature.
 */
template <typename Vector, typename Buffer>
class DotProductExecutor : public fef::FeatureExecutor {
private:
    const attribute::IAttributeVector *            _attribute;
    const Vector                                   _queryVector;
    const typename Vector::HashMap::const_iterator _end;
    Buffer                                         _buffer;

public:
    DotProductExecutor(const search::attribute::IAttributeVector * attribute, const Vector & queryVector);
    void execute(uint32_t docId) override;
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
    using AT = multivalue::Value<BaseType>;
    using V  = std::vector<BaseType>;
private:
    vespalib::hwaccelrated::IAccelrated::UP _multiplier;
    V                                       _queryVector;
    virtual size_t getAttributeValues(uint32_t docid, const AT * & count) = 0;
public:
    DotProductExecutorBase(const V & queryVector);
    ~DotProductExecutorBase();
    void execute(uint32_t docId) final override;
};

/**
 * Implements the executor for the dotproduct feature.
 */
template <typename A>
class DotProductExecutor : public DotProductExecutorBase<typename A::BaseType> {
public:
    using AT = typename DotProductExecutorBase<typename A::BaseType>::AT;
    using V  = typename DotProductExecutorBase<typename A::BaseType>::V;
protected:
    const A * _attribute;
private:
    size_t getAttributeValues(uint32_t docid, const AT * & count) override;
public:
    DotProductExecutor(const A * attribute, const V & queryVector);
    ~DotProductExecutor();
};

template <typename A>
class DotProductByCopyExecutor : public DotProductExecutor<A> {
public:
    typedef typename DotProductExecutor<A>::V V;
    DotProductByCopyExecutor(const A * attribute, const V & queryVector);
    ~DotProductByCopyExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    size_t getAttributeValues(uint32_t docid, const AT * & count) final override;
    std::vector<typename A::BaseType> _copy;
};

/**
 * Dot product executor which uses AttributeContent for the specified base value type
 * to extract array elements from a given attribute vector. Used for "synthetic"
 * attribute vectors such as imported attributes, where we cannot directly access
 * the memory of the underlying attribute store.
 *
 * Some caveats:
 *   - 64 bit value type width is enforced, so 32-bit value types will not benefit
 *     from extra SIMD register capacity.
 *   - Additional overhead caused by call indirection and copy step.
 */
template <typename BaseType>
class DotProductByContentFillExecutor : public DotProductExecutorBase<BaseType> {
public:
    using V  = typename DotProductExecutorBase<BaseType>::V;
    using AT = typename DotProductExecutorBase<BaseType>::AT;
    using ValueFiller = attribute::AttributeContent<BaseType>;

    DotProductByContentFillExecutor(const attribute::IAttributeVector * attribute, const V & queryVector);
    ~DotProductByContentFillExecutor();
private:
    size_t getAttributeValues(uint32_t docid, const AT * & values) final override;

    const attribute::IAttributeVector* _attribute;
    ValueFiller _filler;
};

template <typename A>
class SparseDotProductExecutor : public DotProductExecutor<A> {
public:
    typedef std::vector<uint32_t> IV;
    typedef typename DotProductExecutor<A>::V V;
    SparseDotProductExecutor(const A * attribute, const V & queryVector, const IV & queryIndexes);
    ~SparseDotProductExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    size_t getAttributeValues(uint32_t docid, const AT * & count) override;
protected:
    IV              _queryIndexes;
    std::vector<AT> _scratch;
};

template <typename A>
class SparseDotProductByCopyExecutor : public SparseDotProductExecutor<A> {
public:
    typedef std::vector<uint32_t> IV;
    typedef typename DotProductExecutor<A>::V V;
    SparseDotProductByCopyExecutor(const A * attribute, const V & queryVector, const IV & queryIndexes);
    ~SparseDotProductByCopyExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    size_t getAttributeValues(uint32_t docid, const AT * & count) final override;
    std::vector<typename A::BaseType> _copy;
};

/**
 * Dot product executor which uses AttributeContent for fetching values. See
 * DotProductByContentFillExecutor for a more in-depth description and caveats.
 */
template <typename BaseType>
class SparseDotProductByContentFillExecutor : public DotProductExecutorBase<BaseType> {
public:
    using IV = std::vector<uint32_t>;
    using V  = typename DotProductExecutorBase<BaseType>::V;
    using AT = typename DotProductExecutorBase<BaseType>::AT;
    using ValueFiller = attribute::AttributeContent<BaseType>;

    SparseDotProductByContentFillExecutor(const attribute::IAttributeVector * attribute,
                                          const V & queryVector,
                                          const IV & queryIndexes);
    ~SparseDotProductByContentFillExecutor();
private:
    size_t getAttributeValues(uint32_t docid, const AT * & values) final override;

    const attribute::IAttributeVector* _attribute;
    IV          _queryIndexes;
    ValueFiller _filler;
};

}

}


/**
 * Implements the blueprint for the foreach executor.
 */
class DotProductBlueprint : public fef::Blueprint {
private:
    vespalib::string _defaultAttribute;
    vespalib::string _queryVector;

    vespalib::string getAttribute(const fef::IQueryEnvironment & env) const;

public:
    DotProductBlueprint();
    ~DotProductBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;

    fef::ParameterDescriptions getDescriptions() const override;

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    void prepareSharedState(const fef::IQueryEnvironment & queryEnv, fef::IObjectStore & objectStore) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

};


} // namespace features
} // namespace search

