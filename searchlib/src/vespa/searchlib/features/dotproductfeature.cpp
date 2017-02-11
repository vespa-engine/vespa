// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.dotproduct");
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/properties.h>

#include "dotproductfeature.h"
#include "array_parser.hpp"
#include "utils.h"
#include "valuefeature.h"
#include "weighted_set_parser.hpp"
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>

using namespace search::attribute;
using namespace search::fef;
using vespalib::hwaccelrated::IAccelrated;

namespace search {
namespace features {
namespace dotproduct {
namespace wset {

template <typename Vector, typename Buffer>
DotProductExecutor<Vector, Buffer>::DotProductExecutor(const IAttributeVector * attribute, const Vector & vector) :
    FeatureExecutor(),
    _attribute(attribute),
    _vector(vector),
    _buffer()
{
    _buffer.allocate(_attribute->getMaxValueCount());
    _vector.syncMap();
}

template <typename Vector, typename Buffer>
void
DotProductExecutor<Vector, Buffer>::execute(uint32_t docId)
{
    feature_t val = 0;
    if (!_vector.getDimMap().empty()) {
        _buffer.fill(*_attribute, docId);
        for (size_t i = 0; i < _buffer.size(); ++i) {
            typename Vector::HashMap::const_iterator itr = _vector.getDimMap().find(_buffer[i].getValue());
            if (itr != _vector.getDimMap().end()) {
                val += _buffer[i].getWeight() * itr->second;
            }
        }
    }
    outputs().set_number(0, val);
}

}

namespace array {

template <typename A>
DotProductExecutor<A>::DotProductExecutor(const A * attribute, const V & vector) :
    FeatureExecutor(),
    _attribute(attribute),
    _multiplier(IAccelrated::getAccelrator()),
    _vector(vector)
{ }

template <typename A>
size_t
DotProductExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    return _attribute->getRawValues(docId, values);
}

    constexpr size_t CACHE_LINE_SIZE = 64;
template <typename A>
void
DotProductExecutor<A>::execute(uint32_t docId)
{
    const AT *values(NULL);
    size_t count = getAttributeValues(docId, values);
    size_t commonRange = std::min(count, _vector.size());
    const size_t numPerCacheLine = CACHE_LINE_SIZE/sizeof(AT);
    for (size_t i(0); i < _vector.size()/numPerCacheLine; i++) {
        __builtin_prefetch(values+i*numPerCacheLine, 0, 0);
    }
    outputs().set_number(0, _multiplier->dotProduct(&_vector[0], reinterpret_cast<const typename A::BaseType *>(values), commonRange));
}

template <typename A>
SparseDotProductExecutor<A>::SparseDotProductExecutor(const A * attribute, const V & values, const IV & indexes) :
    DotProductExecutor<A>(attribute, values),
    _indexes(indexes),
    _scratch(std::max(static_cast<size_t>(attribute->getMaxValueCount()), indexes.size()))
{
}

template <typename A>
size_t
SparseDotProductExecutor<A>::getAttributeValues(uint32_t docId, const AT * & values)
{
    const AT *allValues(NULL);
    size_t count = this->_attribute->getRawValues(docId, allValues);
    values = &_scratch[0];
    size_t i(0);
    for (; (i < _indexes.size()) && (_indexes[i] < count); i++) {
        _scratch[i] = allValues[_indexes[i]];
    }
    return i;
}

template <typename A>
DotProductByCopyExecutor<A>::DotProductByCopyExecutor(const A * attribute, const V & values) :
    DotProductExecutor<A>(attribute, values),
    _copy(static_cast<size_t>(attribute->getMaxValueCount()))
{
}

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
SparseDotProductByCopyExecutor<A>::SparseDotProductByCopyExecutor(const A * attribute, const V & values, const IV & indexes) :
    SparseDotProductExecutor<A>(attribute, values, indexes),
    _copy(std::max(static_cast<size_t>(attribute->getMaxValueCount()), indexes.size()))
{
}

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
    for (const IV & iv(this->_indexes); (i < iv.size()) && (iv[i] < count); i++) {
        if (i != iv[i]) {
            _copy[i] = _copy[iv[i]];
        }
    }
    values = reinterpret_cast<const AT *>(&_copy[0]);
    return i;
}

}

}


DotProductBlueprint::DotProductBlueprint() :
    Blueprint("dotProduct"),
    _defaultAttribute(),
    _queryVector()
{
}

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

Blueprint::UP
DotProductBlueprint::createInstance() const
{
    return Blueprint::UP(new DotProductBlueprint());
}

namespace {

template <typename T>
void
parseVectors(const Property & prop, std::vector<T> & values, std::vector<uint32_t> & indexes)
{
    typedef std::vector<ArrayParser::ValueAndIndex<T>> SparseV;
    SparseV sparse;
    ArrayParser::parsePartial(prop.get(), sparse);
    if ( ! sparse.empty()) {
        std::sort(sparse.begin(), sparse.end());
        if ((sparse.back().getIndex()+1)/sparse.size() < 10) {
            values.resize(sparse.back().getIndex()+1);
            for(const typename SparseV::value_type & a : sparse) {
                values[a.getIndex()] = a.getValue();
            }
        } else {
            values.reserve(sparse.size());
            indexes.reserve(sparse.size());
            for(const typename SparseV::value_type & a : sparse) {
                values.push_back(a.getValue());
                indexes.push_back(a.getIndex());
            }
        }
    }
}

template <typename A>
FeatureExecutor &
create(const IAttributeVector * attribute, const Property & prop, vespalib::Stash &stash)
{
    std::vector<typename A::BaseType> values;
    std::vector<uint32_t> indexes;
    parseVectors(prop, values, indexes);
    if (values.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    }
    const A & iattr = dynamic_cast<const A &>(*attribute);
    if (indexes.empty()) {
        try {
            const multivalue::Value<typename A::BaseType> * tmp;
            iattr.getRawValues(0, tmp);
            return stash.create<dotproduct::array::DotProductExecutor<A>>(&iattr, values);
        } catch (const std::runtime_error & e) {
            (void) e;
            return stash.create<dotproduct::array::DotProductByCopyExecutor<A>>(&iattr, values);
        }
    } else {
        try {
            const multivalue::Value<typename A::BaseType> * tmp;
            iattr.getRawValues(0, tmp);
            return stash.create<dotproduct::array::SparseDotProductExecutor<A>>(&iattr, values, indexes);
        } catch (const std::runtime_error & e) {
            (void) e;
            return stash.create<dotproduct::array::SparseDotProductByCopyExecutor<A>>(&iattr, values, indexes);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

template <typename T>
struct ArrayParam : public fef::Anything
{
    ArrayParam(const Property & prop) {
        parseVectors(prop, values, indexes);
    }
    std::vector<T>        values;
    std::vector<uint32_t> indexes;
};

template <typename A>
FeatureExecutor &
create(const IAttributeVector * attribute, const ArrayParam<typename A::BaseType> & arguments, vespalib::Stash &stash)
{
    if (arguments.values.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    }
    const A & iattr = dynamic_cast<const A &>(*attribute);
    if (arguments.indexes.empty()) {
        try {
            const multivalue::Value<typename A::BaseType> * tmp;
            iattr.getRawValues(0, tmp);
            return stash.create<dotproduct::array::DotProductExecutor<A>>(&iattr, arguments.values);
        } catch (const std::runtime_error & e) {
            (void) e;
            return stash.create<dotproduct::array::DotProductByCopyExecutor<A>>(&iattr, arguments.values);
        }
    } else {
        try {
            const multivalue::Value<typename A::BaseType> * tmp;
            iattr.getRawValues(0, tmp);
            return stash.create<dotproduct::array::SparseDotProductExecutor<A>>(&iattr, arguments.values, arguments.indexes);
        } catch (const std::runtime_error & e) {
            (void) e;
            return stash.create<dotproduct::array::SparseDotProductByCopyExecutor<A>>(&iattr, arguments.values, arguments.indexes);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

//const char * BINARY = "binary";
const char * OBJECT = "object";


FeatureExecutor &
createFromObject(const IAttributeVector * attribute, const fef::Anything & object, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
                return create<IntegerAttributeTemplate<int32_t>>(attribute, dynamic_cast<const ArrayParam<int32_t> &>(object), stash);
            case BasicType::INT64:
                return create<IntegerAttributeTemplate<int64_t>>(attribute, dynamic_cast<const ArrayParam<int64_t> &>(object), stash);
            case BasicType::FLOAT:
                return create<FloatingPointAttributeTemplate<float>>(attribute, dynamic_cast<const ArrayParam<float> &>(object), stash);
            case BasicType::DOUBLE:
                return create<FloatingPointAttributeTemplate<double>>(attribute, dynamic_cast<const ArrayParam<double> &>(object), stash);
            default:
                break;
        }
    }
    // TODO: Add support for creating executor for weighted set string / integer attribute
    //       where the query vector is represented as an object instead of a string.
    LOG(warning, "The attribute vector '%s' is NOT of type array<int/long/float/double>"
            ", returning executor with default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}

FeatureExecutor &
createFromString(const IAttributeVector * attribute, const Property & prop, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
        if (attribute->isStringType()) {
            if (attribute->hasEnum()) {
                dotproduct::wset::EnumVector vector(attribute);
                WeightedSetParser::parse(prop.get(), vector);
                return stash.create<dotproduct::wset::DotProductExecutor<dotproduct::wset::EnumVector, WeightedEnumContent>>(attribute, vector);
            } else {
                dotproduct::wset::StringVector vector;
                WeightedSetParser::parse(prop.get(), vector);
                return stash.create<dotproduct::wset::DotProductExecutor<dotproduct::wset::StringVector, WeightedConstCharContent>>(attribute, vector);
            }
        } else if (attribute->isIntegerType()) {
            if (attribute->hasEnum()) {
                dotproduct::wset::EnumVector vector(attribute);
                WeightedSetParser::parse(prop.get(), vector);
                return stash.create<dotproduct::wset::DotProductExecutor<dotproduct::wset::EnumVector, WeightedEnumContent>>(attribute, vector);
                
            } else {
                dotproduct::wset::IntegerVector vector;
                WeightedSetParser::parse(prop.get(), vector);
                return stash.create<dotproduct::wset::DotProductExecutor<dotproduct::wset::IntegerVector, WeightedIntegerContent>>(attribute, vector);
            }
        }
    } else if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
                return create<IntegerAttributeTemplate<int32_t>>(attribute, prop, stash);
            case BasicType::INT64:
                return create<IntegerAttributeTemplate<int64_t>>(attribute, prop, stash);
            case BasicType::FLOAT:
                return create<FloatingPointAttributeTemplate<float>>(attribute, prop, stash);
            case BasicType::DOUBLE:
                return create<FloatingPointAttributeTemplate<double>>(attribute, prop, stash);
            default:
                break;
        }
    }
    LOG(warning, "The attribute vector '%s' is not of type weighted set string/integer nor"
                 " array<int/long/float/double>, returning executor with default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}

}

void
DotProductBlueprint::prepareSharedState(const IQueryEnvironment & env, IObjectStore & store) const
{
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(getAttribute(env));
    if (attribute != NULL) {
        if ((attribute->getCollectionType() == attribute::CollectionType::WSET) &&
            attribute->hasEnum() &&
            (attribute->isStringType() || attribute->isIntegerType()))
        {
            attribute = env.getAttributeContext().getAttributeStableEnum(getAttribute(env));
        }
        Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
        if (prop.found() && !prop.get().empty()) {
            fef::Anything::UP arguments;
            if (attribute->getCollectionType() == attribute::CollectionType::WSET) {
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
            } else if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
                switch (attribute->getBasicType()) {
                    case BasicType::INT32:
                        arguments.reset(new ArrayParam<int32_t>(prop));
                        break;
                    case BasicType::INT64:
                        arguments.reset(new ArrayParam<int64_t>(prop));
                        break;
                    case BasicType::FLOAT:
                        arguments.reset(new ArrayParam<float>(prop));
                        break;
                    case BasicType::DOUBLE:
                        arguments.reset(new ArrayParam<double>(prop));
                        break;
                    default:
                        break;
                }
            }
            if ( arguments.get()) {
                store.add(getBaseName() + "." + _queryVector + "." + OBJECT, std::move(arguments));
            }
        }
    }
}

FeatureExecutor &
DotProductBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(getAttribute(env));
    if (attribute == NULL) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager, returning executor with default value.",
            getAttribute(env).c_str());
        return stash.create<SingleZeroValueExecutor>();
    }
    if ((attribute->getCollectionType() == attribute::CollectionType::WSET) &&
        attribute->hasEnum() &&
        (attribute->isStringType() || attribute->isIntegerType()))
    {
        attribute = env.getAttributeContext().getAttributeStableEnum(getAttribute(env));
    }
    const fef::Anything * argument = env.getObjectStore().get(getBaseName() + "." + _queryVector + "." + OBJECT);
    if (argument != NULL) {
        return createFromObject(attribute, *argument, stash);
    } else {
        Property prop = env.getProperties().lookup(getBaseName(), _queryVector);
        if (prop.found() && !prop.get().empty()) {
            return createFromString(attribute, prop, stash);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}

} // namespace features
} // namespace search
