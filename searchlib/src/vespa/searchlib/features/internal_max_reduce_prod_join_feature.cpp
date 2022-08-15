// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "internal_max_reduce_prod_join_feature.h"
#include "valuefeature.h"
#include "weighted_set_parser.h"
#include "dotproductfeature.h"

#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchcommon/common/datatype.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>


#include <vespa/log/log.h>
LOG_SETUP(".features.internalmaxreduceprodjoin");

using namespace search::attribute;
using namespace search::fef;

using search::features::dotproduct::wset::IntegerVector;
using vespalib::Issue;

namespace search::features {

namespace {

/**
 * Executor used when array can be accessed directly
 */
template<typename BaseType>
class RawExecutor : public FeatureExecutor {
    using ArrayReadView = attribute::IArrayReadView<BaseType>;
    std::unique_ptr<IntegerVector> _backing;
    const ArrayReadView*           _array_read_view;
    const IntegerVector&           _queryVector;

public:
    RawExecutor(const ArrayReadView* array_read_view, const IntegerVector& queryVector);
    RawExecutor(const ArrayReadView* array_read_view, std::unique_ptr<IntegerVector> queryVector);

    void execute(uint32_t docId) override;
};

template<typename BaseType>
RawExecutor<BaseType>::RawExecutor(const ArrayReadView* array_read_view,  std::unique_ptr<IntegerVector> queryVector)
    : FeatureExecutor(),
      _backing(std::move(queryVector)),
      _array_read_view(array_read_view),
      _queryVector(*_backing)
{
}

template<typename BaseType>
RawExecutor<BaseType>::RawExecutor(const ArrayReadView* array_read_view, const IntegerVector& queryVector)
    : FeatureExecutor(),
      _backing(),
      _array_read_view(array_read_view),
      _queryVector(queryVector)
{
}

namespace {

template <typename T>
inline T get_array_element_value(const T& value) noexcept { return multivalue::get_value(value); }

template <typename T>
inline T get_array_element_value(const search::attribute::WeightedType<T>& value) noexcept { return value.value(); }

}

template<typename A, typename V>
feature_t maxProduct(const A &array, size_t count, const V &query) {
    feature_t val = -std::numeric_limits<double>::max();
    for (size_t i = 0; i < count; ++i) {
        auto itr = query.getDimMap().find(get_array_element_value(array[i]));
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
    auto values = _array_read_view->get_values(docId);
    outputs().set_number(0, maxProduct(values.data(), values.size(), _queryVector));
}

/**
 * Executor when array can't be accessed directly
 */
template<typename BaseType>
class BufferedExecutor : public FeatureExecutor {
private:
    std::unique_ptr<IntegerVector> _backing;
    const IAttributeVector*        _attribute;
    const IntegerVector&           _queryVector;
    WeightedIntegerContent         _buffer;

public:
    BufferedExecutor(const IAttributeVector *attribute, const IntegerVector & queryVector);
    BufferedExecutor(const IAttributeVector *attribute, std::unique_ptr<IntegerVector> queryVector);

    void execute(uint32_t docId) override;
};

template<typename BaseType>
BufferedExecutor<BaseType>::BufferedExecutor(const IAttributeVector *attribute, const IntegerVector& queryVector)
    : FeatureExecutor(),
      _backing(),
      _attribute(attribute),
      _queryVector(queryVector),
      _buffer()
{
}

template<typename BaseType>
BufferedExecutor<BaseType>::BufferedExecutor(const IAttributeVector *attribute, std::unique_ptr<IntegerVector> queryVector)
    : FeatureExecutor(),
      _backing(std::move(queryVector)),
      _attribute(attribute),
      _queryVector(*_backing),
      _buffer()
{
}


template<typename BaseType>
void
BufferedExecutor<BaseType>::execute(uint32_t docId) {
    _buffer.fill(*(this->_attribute), docId);
    this->outputs().set_number(0, maxProduct(_buffer, _buffer.size(), this->_queryVector));
}

template<typename BaseType, typename V>
FeatureExecutor &
selectTypedExecutor(const IAttributeVector *attribute, V && vector, vespalib::Stash &stash) {
    if (!attribute->isImported()) {
        auto multi_value_attribute = attribute->as_multi_value_attribute();
        if (multi_value_attribute != nullptr) {
            auto array_read_view = multi_value_attribute->make_read_view(attribute::IMultiValueAttribute::ArrayTag<BaseType>(), stash);
            if (array_read_view != nullptr) {
                return stash.create<RawExecutor<BaseType>>(array_read_view, std::forward<V>(vector));
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
    Issue::report("intrinsic max_reduce_prod_join feature: The attribute vector '%s' is not of type "
                  "array<int/long>, returning default value.", attribute->getName().c_str());
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
        Issue::report("intrinsic max_reduce_prod_join feature: The attribute vector '%s' was not found, "
                      "returning default value.", _attribute.c_str());
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
