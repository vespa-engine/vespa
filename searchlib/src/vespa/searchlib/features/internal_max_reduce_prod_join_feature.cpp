// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "internal_max_reduce_prod_join_feature.h"
#include "valuefeature.h"
#include "weighted_set_parser.h"
#include "dotproductfeature.h"

#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchcommon/common/datatype.h>
#include <vespa/vespalib/util/stash.h>


#include <vespa/log/log.h>
LOG_SETUP(".features.internalmaxreduceprodjoin");

using namespace search::attribute;
using namespace search::fef;

using search::features::dotproduct::wset::IntegerVector;

namespace search::features {

namespace {

/**
 * Executor used when array can be accessed directly
 */
template<typename BaseType>
class RawExecutor : public FeatureExecutor {
private:
    std::unique_ptr<IntegerVector> _backing;
protected:
    const IAttributeVector *_attribute;
    const IntegerVector    &_queryVector;

public:
    RawExecutor(const IAttributeVector *attribute, const IntegerVector & queryVector);
    RawExecutor(const IAttributeVector *attribute, std::unique_ptr<IntegerVector> queryVector);

    void execute(uint32_t docId) override;
};

template<typename BaseType>
RawExecutor<BaseType>::RawExecutor(const IAttributeVector *attribute,  std::unique_ptr<IntegerVector> queryVector)
    : FeatureExecutor(),
      _backing(std::move(queryVector)),
      _attribute(attribute),
      _queryVector(*_backing)
{
}

template<typename BaseType>
RawExecutor<BaseType>::RawExecutor(const IAttributeVector *attribute, const IntegerVector & queryVector)
    : FeatureExecutor(),
      _backing(),
      _attribute(attribute),
      _queryVector(queryVector)
{
}

template<typename A, typename V>
feature_t maxProduct(const A &array, size_t count, const V &query) {
    feature_t val = -std::numeric_limits<double>::max();
    for (size_t i = 0; i < count; ++i) {
        auto itr = query.getDimMap().find(array[i].value());
        if (itr != query.getDimMap().end()) {
            feature_t v = itr->second; // weight from attribute is assumed to be 1.0
            if (v > val) {
                val = v;
            }
        }
    }
    return val == -std::numeric_limits<double>::max() ? 0.0 : val;
}

template<typename BaseType>
void
RawExecutor<BaseType>::execute(uint32_t docId) {
    using A = IntegerAttributeTemplate<BaseType>;
    const multivalue::Value<BaseType> *values(nullptr);
    const A *iattr = static_cast<const A *>(_attribute);
    size_t count = iattr->getRawValues(docId, values);
    outputs().set_number(0, maxProduct(values, count, _queryVector));
}

/**
 * Executor when array can't be accessed directly
 */
template<typename BaseType>
class BufferedExecutor : public RawExecutor<BaseType> {
private:
    WeightedIntegerContent _buffer;

public:
    BufferedExecutor(const IAttributeVector *attribute, const IntegerVector & queryVector);
    BufferedExecutor(const IAttributeVector *attribute, std::unique_ptr<IntegerVector> queryVector);

    void execute(uint32_t docId) override;
};

template<typename BaseType>
BufferedExecutor<BaseType>::BufferedExecutor(const IAttributeVector *attribute, const IntegerVector & queryVector)
    : RawExecutor<BaseType>(attribute, queryVector),
      _buffer()
{
}

template<typename BaseType>
BufferedExecutor<BaseType>::BufferedExecutor(const IAttributeVector *attribute, std::unique_ptr<IntegerVector> queryVector)
    : RawExecutor<BaseType>(attribute, std::move(queryVector)),
      _buffer()
{
}


template<typename BaseType>
void
BufferedExecutor<BaseType>::execute(uint32_t docId) {
    _buffer.fill(*(this->_attribute), docId);
    this->outputs().set_number(0, maxProduct(_buffer, _buffer.size(), this->_queryVector));
}


template<typename A>
bool supportsGetRawValues(const A &attr) noexcept {
    try {
        const multivalue::Value<typename A::BaseType> *tmp = nullptr;
        attr.getRawValues(0, tmp); // Throws if unsupported
        return true;
    } catch (const std::runtime_error &e) {
        (void) e;
        return false;
    }
}

template<typename BaseType, typename V>
FeatureExecutor &
selectTypedExecutor(const IAttributeVector *attribute, V && vector, vespalib::Stash &stash) {
    if (!attribute->isImported()) {
        using A = IntegerAttributeTemplate<BaseType>;
        using VT = multivalue::Value<BaseType>;
        using ExactA = MultiValueNumericAttribute<A, VT>;

        const A *iattr = dynamic_cast<const A *>(attribute);
        if (supportsGetRawValues(*iattr)) {
            const ExactA *exactA = dynamic_cast<const ExactA *>(iattr);
            if (exactA != nullptr) {
                return stash.create<RawExecutor<BaseType>>(attribute, std::forward<V>(vector));
            }
        }
    }
    return stash.create<BufferedExecutor<BaseType>>(attribute, std::forward<V>(vector));
}

template<typename V>
FeatureExecutor &
selectExecutor(const IAttributeVector *attribute, V && vector, vespalib::Stash &stash) {
    if (attribute->getCollectionType() == CollectionType::ARRAY) {
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
                return selectTypedExecutor<int32_t, V>(attribute, std::forward<V>(vector), stash);
            case BasicType::INT64:
                return selectTypedExecutor<int64_t, V>(attribute, std::forward<V>(vector), stash);
            default:
                break;
        }
    }
    LOG(warning, "The attribute vector '%s' is not of type "
                 "array<int/long>, returning executor with default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}

vespalib::string
make_queryvector_key(const vespalib::string & base, const vespalib::string & subKey) {
    vespalib::string key(base);
    key.append(".vector.");
    key.append(subKey);
    return key;
}

std::unique_ptr<IntegerVector>
createQueryVector(const Property & prop) {
    if (prop.found() && !prop.get().empty()) {
        auto vector = std::make_unique<IntegerVector>();
        WeightedSetParser::parse(prop.get(), *vector);
        if (!vector->getVector().empty()) {
            vector->syncMap();
            return vector;
        }
    }
    return std::unique_ptr<IntegerVector>();
}

}

InternalMaxReduceProdJoinBlueprint::InternalMaxReduceProdJoinBlueprint()
    : Blueprint("internalMaxReduceProdJoin"),
      _attribute(),
      _queryVector(),
      _attrKey(),
      _queryVectorKey()
{
}

InternalMaxReduceProdJoinBlueprint::~InternalMaxReduceProdJoinBlueprint() = default;

void
InternalMaxReduceProdJoinBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const {
}

Blueprint::UP
InternalMaxReduceProdJoinBlueprint::createInstance() const {
    return std::make_unique<InternalMaxReduceProdJoinBlueprint>();
}

ParameterDescriptions
InternalMaxReduceProdJoinBlueprint::getDescriptions() const {
    return ParameterDescriptions().desc().attribute(ParameterDataTypeSet::int32OrInt64TypeSet(),
                                                    ParameterCollection::ARRAY).string();
}

bool
InternalMaxReduceProdJoinBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params) {
    _attribute = params[0].getValue();
    _attrKey = createAttributeKey(_attribute);
    _queryVector = params[1].getValue();
    _queryVectorKey = make_queryvector_key(getBaseName(), _queryVector);
    describeOutput("scalar", "Internal executor for optimized execution of reduce(join(A,Q,f(x,y)(x*y)),max)");
    env.hintAttributeAccess(_attribute);
    return true;
}

void
InternalMaxReduceProdJoinBlueprint::prepareSharedState(const fef::IQueryEnvironment & env, fef::IObjectStore & store) const
{
    const IAttributeVector * attribute = lookupAndStoreAttribute(_attrKey, _attribute, env, store);
    if (attribute == nullptr) return;

    const fef::Anything * queryVector = env.getObjectStore().get(_queryVectorKey);
    if (queryVector == nullptr) {
        std::unique_ptr<IntegerVector> vector = createQueryVector(env.getProperties().lookup(_queryVector));
        if (vector) {
            store.add(_queryVectorKey, std::move(vector));
        }
    }
}

FeatureExecutor &
InternalMaxReduceProdJoinBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = lookupAttribute(_attrKey, _attribute, env);
    if (attribute == nullptr) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager, "
                "returning executor with default value.", _attribute.c_str());
        return stash.create<SingleZeroValueExecutor>();
    }
    const fef::Anything * queryVectorArg = env.getObjectStore().get(_queryVectorKey);
    if (queryVectorArg != nullptr) {
        // Vector is not copied as it is safe in ObjectStore
        return selectExecutor<const IntegerVector &>(attribute, *dynamic_cast<const IntegerVector *>(queryVectorArg), stash);
    } else {
        std::unique_ptr<IntegerVector> vector = createQueryVector(env.getProperties().lookup(_queryVector));
        if (vector) {
            // Vector is moved and handed over to the executor.
            return selectExecutor(attribute, std::move(vector), stash);
        }
    }

    return stash.create<SingleZeroValueExecutor>();
}

}
