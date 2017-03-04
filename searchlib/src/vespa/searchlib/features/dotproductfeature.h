// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "utils.h"
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/attribute/multivalue.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search {
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
    const search::attribute::IAttributeVector * _attribute;
    Vector _vector;
    Buffer _buffer;

public:
    DotProductExecutor(const search::attribute::IAttributeVector * attribute, const Vector & vector);
    virtual void execute(uint32_t docId);
};

}

namespace array {

/**
 * Implements the executor for the dotproduct feature.
 */
template <typename A>
class DotProductExecutor : public fef::FeatureExecutor {
public:
    typedef multivalue::Value<typename A::BaseType> AT;
    typedef std::vector<typename A::BaseType> V;
protected:
    const A * _attribute;
private:
    vespalib::hwaccelrated::IAccelrated::UP _multiplier;
    V                                       _vector;
    virtual size_t getAttributeValues(uint32_t docid, const AT * & count);
public:
    DotProductExecutor(const A * attribute, const V & vector);
    ~DotProductExecutor();
    virtual void execute(uint32_t docId);
};

template <typename A>
class DotProductByCopyExecutor : public DotProductExecutor<A> {
public:
    typedef typename DotProductExecutor<A>::V V;
    DotProductByCopyExecutor(const A * attribute, const V & vector);
    ~DotProductByCopyExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    virtual size_t getAttributeValues(uint32_t docid, const AT * & count);
    std::vector<typename A::BaseType> _copy;
};

template <typename A>
class SparseDotProductExecutor : public DotProductExecutor<A> {
public:
    typedef std::vector<uint32_t> IV;
    typedef typename DotProductExecutor<A>::V V;
    SparseDotProductExecutor(const A * attribute, const V & vector, const IV & indexes);
    ~SparseDotProductExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    virtual size_t getAttributeValues(uint32_t docid, const AT * & count);
protected:
    IV              _indexes;
    std::vector<AT> _scratch;
};

template <typename A>
class SparseDotProductByCopyExecutor : public SparseDotProductExecutor<A> {
public:
    typedef std::vector<uint32_t> IV;
    typedef typename DotProductExecutor<A>::V V;
    SparseDotProductByCopyExecutor(const A * attribute, const V & vector, const IV & indexes);
    ~SparseDotProductByCopyExecutor();
private:
    typedef typename DotProductExecutor<A>::AT AT;
    virtual size_t getAttributeValues(uint32_t docid, const AT * & count);
    std::vector<typename A::BaseType> _copy;
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
    /**
     * Constructs a blueprint.
     */
    DotProductBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const fef::IIndexEnvironment & env,
                                   fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual fef::ParameterDescriptions getDescriptions() const {
        return fef::ParameterDescriptions().desc().attribute(fef::ParameterCollection::ANY).string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const fef::IIndexEnvironment & env,
                       const fef::ParameterList & params);

    virtual void prepareSharedState(const fef::IQueryEnvironment & queryEnv, fef::IObjectStore & objectStore) const;

    // Inherit doc from Blueprint.
    virtual fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

};


} // namespace features
} // namespace search

