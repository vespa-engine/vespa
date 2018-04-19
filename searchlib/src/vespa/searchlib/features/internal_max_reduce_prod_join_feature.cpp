// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "internal_max_reduce_prod_join_feature.h"
#include "valuefeature.h"
#include "weighted_set_parser.h"

#include <vespa/log/log.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/features/dotproductfeature.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchcommon/common/datatype.h>

LOG_SETUP(".features.internalmaxreduceprodjoin");

using namespace search::attribute;
using namespace search::fef;

using search::features::dotproduct::wset::IntegerVector;

namespace search {
namespace features {

/**
 * Executor used when array can be accessed directly
 */
template <typename BaseType>
class RawExecutor : public FeatureExecutor {
protected:
    const IAttributeVector *_attribute;
    IntegerVector _queryVector;

public:
    RawExecutor(const IAttributeVector *attribute, const IntegerVector &queryVector);
    void execute(uint32_t docId) override;
};

template <typename BaseType>
RawExecutor<BaseType>::RawExecutor(const IAttributeVector *attribute,
                         const IntegerVector &queryVector) :
        FeatureExecutor(),
        _attribute(attribute),
        _queryVector(queryVector)
{
    _queryVector.syncMap();
}

template <typename A, typename V>
feature_t maxProduct(const A &array, size_t count, const V &query)
{
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

template <typename BaseType>
void
RawExecutor<BaseType>::execute(uint32_t docId)
{
    using A = IntegerAttributeTemplate<BaseType>;
    const multivalue::Value<BaseType> *values(nullptr);
    const A *iattr = dynamic_cast<const A *>(_attribute);
    size_t count = iattr->getRawValues(docId, values);
    outputs().set_number(0, maxProduct(values, count, _queryVector));
}

/**
 * Executor when array can't be accessed directly
 */
template <typename BaseType>
class BufferedExecutor : public RawExecutor<BaseType> {
private:
    WeightedIntegerContent _buffer;

public:
    BufferedExecutor(const IAttributeVector *attribute, const IntegerVector &queryVector);
    void execute(uint32_t docId) override;
};

template <typename BaseType>
BufferedExecutor<BaseType>::BufferedExecutor(const IAttributeVector *attribute, const IntegerVector &queryVector) :
    RawExecutor<BaseType>(attribute, queryVector),
    _buffer()
{
}


template <typename BaseType>
void
BufferedExecutor<BaseType>::execute(uint32_t docId)
{
    _buffer.fill(*(this->_attribute), docId);
    this->outputs().set_number(0, maxProduct(_buffer, _buffer.size(), this->_queryVector));
}

/**
 * Blueprint
 */
InternalMaxReduceProdJoinBlueprint::InternalMaxReduceProdJoinBlueprint() :
        Blueprint("internalMaxReduceProdJoin")
{
}

InternalMaxReduceProdJoinBlueprint::~InternalMaxReduceProdJoinBlueprint()
{
}

void
InternalMaxReduceProdJoinBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                                           IDumpFeatureVisitor &) const
{
}

Blueprint::UP
InternalMaxReduceProdJoinBlueprint::createInstance() const
{
    return Blueprint::UP(new InternalMaxReduceProdJoinBlueprint());
}

ParameterDescriptions
InternalMaxReduceProdJoinBlueprint::getDescriptions() const
{
    return ParameterDescriptions().desc().attribute(ParameterDataTypeSet::int32OrInt64TypeSet(), ParameterCollection::ARRAY).string();
}

bool
InternalMaxReduceProdJoinBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params)
{
    _attribute = params[0].getValue();
    _query = params[1].getValue();
    describeOutput("scalar", "Internal executor for optimized execution of reduce(join(A,Q,f(x,y)(x*y)),max)");
    env.hintAttributeAccess(_attribute);
    return true;
}

bool isImportedAttribute(const IAttributeVector &attribute) noexcept {
    return dynamic_cast<const ImportedAttributeVectorReadGuard*>(&attribute) != nullptr;
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

template <typename BaseType>
FeatureExecutor &
selectTypedExecutor(const IAttributeVector *attribute, const IntegerVector &vector, vespalib::Stash &stash)
{
    if (!isImportedAttribute(*attribute)) {
        using A = IntegerAttributeTemplate<BaseType>;
        using VT = multivalue::Value<BaseType>;
        using ExactA = MultiValueNumericAttribute<A, VT>;

        const A *iattr = dynamic_cast<const A *>(attribute);
        if (supportsGetRawValues(*iattr)) {
            const ExactA *exactA = dynamic_cast<const ExactA *>(iattr);
            if (exactA != nullptr) {
                return stash.create<RawExecutor<BaseType>>(attribute, vector);
            }
        }
    }
    return stash.create<BufferedExecutor<BaseType>>(attribute, vector);
}

FeatureExecutor &
selectExecutor(const IAttributeVector *attribute, const IntegerVector &vector, vespalib::Stash &stash)
{
    if (attribute->getCollectionType() == CollectionType::ARRAY) {
        switch (attribute->getBasicType()) {
            case BasicType::INT32:
                return selectTypedExecutor<int32_t>(attribute, vector, stash);
            case BasicType::INT64:
                return selectTypedExecutor<int64_t>(attribute, vector, stash);
            default:
                break;
        }
    }
    LOG(warning, "The attribute vector '%s' is not of type "
            "array<int/long>, returning executor with default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();
}


FeatureExecutor &
InternalMaxReduceProdJoinBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    const IAttributeVector *attribute = env.getAttributeContext().getAttribute(_attribute);
    if (attribute == nullptr) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager, "
                "returning executor with default value.",
            _attribute.c_str());
        return stash.create<SingleZeroValueExecutor>();
    }
    Property prop = env.getProperties().lookup(_query);
    if (prop.found() && !prop.get().empty()) {
        IntegerVector vector;
        WeightedSetParser::parse(prop.get(), vector);
        if (!vector.getVector().empty()) {
            return selectExecutor(attribute, vector, stash);
        }
    }
    return stash.create<SingleZeroValueExecutor>();
}


}
}


