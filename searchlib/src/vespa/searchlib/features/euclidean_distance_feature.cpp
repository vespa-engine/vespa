// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance_feature.h"
#include "valuefeature.h"
#include "array_parser.hpp"
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP(".features.euclidean_distance_feature");

using namespace search::attribute;
using namespace search::fef;
using vespalib::Issue;

namespace search::features {


template <typename DataType>
EuclideanDistanceExecutor<DataType>::EuclideanDistanceExecutor(const search::attribute::IAttributeVector &attribute, QueryVectorType vector) :
    FeatureExecutor(),
    _attribute(attribute),
    _vector(std::move(vector)),
    _attributeBuffer()
{
}

template <typename DataType>
feature_t EuclideanDistanceExecutor<DataType>::euclideanDistance(const BufferType &v1, const QueryVectorType &v2)
{
    feature_t val = 0;
    size_t commonRange = std::min(static_cast<size_t>( v1.size() ), v2.size());
    for (size_t i = 0; i < commonRange; ++i)  {
        feature_t diff = v1[i] - v2[i];
        val += diff * diff;
    }
    return std::sqrt(val);
}


template <typename DataType>
void
EuclideanDistanceExecutor<DataType>::execute(uint32_t docId)
{
    _attributeBuffer.fill(_attribute, docId);
    outputs().set_number(0, euclideanDistance(_attributeBuffer, _vector));
}


EuclideanDistanceBlueprint::EuclideanDistanceBlueprint() :
    Blueprint("euclideanDistance"),
    _attributeName(),
    _queryVector()
{
}

EuclideanDistanceBlueprint::~EuclideanDistanceBlueprint() = default;

void
EuclideanDistanceBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
EuclideanDistanceBlueprint::setup(const IIndexEnvironment &env, const ParameterList &params)
{
    _attributeName = params[0].getValue();
    _queryVector = params[1].getValue();
    describeOutput("distance", "The result after calculating the euclidean distance of the vector represented by the array "
                             "and the vector sent down with the query");
    env.hintAttributeAccess(_attributeName);
    return true;
}

Blueprint::UP
EuclideanDistanceBlueprint::createInstance() const
{
    return std::make_unique<EuclideanDistanceBlueprint>();
}

namespace {

template <typename DataType> 
FeatureExecutor & create(const IAttributeVector &attribute, const Property &queryVector, vespalib::Stash &stash)
{
    std::vector<DataType> v;
    ArrayParser::parse(queryVector.get(), v);
    return stash.create<EuclideanDistanceExecutor<DataType>>(attribute, std::move(v));
}

}

FeatureExecutor &
EuclideanDistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(_attributeName);
    if (attribute == nullptr) {
        Issue::report("euclidean_distance feature: The attribute vector '%s' was not found, returning default value.",
                      _attributeName.c_str());
        return stash.create<SingleZeroValueExecutor>();
    }

    Property queryVector = env.getProperties().lookup(getBaseName(), _queryVector);

    if (attribute->getCollectionType() == attribute::CollectionType::ARRAY) {
        if (attribute->isIntegerType()) {
            return create<IAttributeVector::largeint_t>(*attribute, queryVector, stash);
        } else if (attribute->isFloatingPointType()) {
            return create<double>(*attribute, queryVector, stash);
        }
    }
    Issue::report("euclidean_distance feature: The attribute vector '%s' is NOT of type array<int/long/float/double>"
                  ", returning default value.", attribute->getName().c_str());
    return stash.create<SingleZeroValueExecutor>();

}

}

