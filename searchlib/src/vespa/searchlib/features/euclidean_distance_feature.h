// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchcommon/attribute/attributecontent.h>


namespace search {
namespace features {


/**
 * Implements the executor for the eucledian distance feature.
 */
template <typename DataType>
class EuclideanDistanceExecutor : public fef::FeatureExecutor {

public:
    typedef search::attribute::AttributeContent<DataType> BufferType;
    typedef std::vector<DataType> QueryVectorType;

private:
    const search::attribute::IAttributeVector &_attribute;
    const QueryVectorType _vector;
    BufferType _attributeBuffer;

    feature_t euclideanDistance(const BufferType &v1, const QueryVectorType &v2);

public:

    EuclideanDistanceExecutor(const search::attribute::IAttributeVector &attribute, QueryVectorType vector);
    virtual void execute(fef::MatchData &data) override;
};


/**
 * Implements the blueprint for the euclidean distance executor.
 */
class EuclideanDistanceBlueprint : public fef::Blueprint {
private:
    vespalib::string _attributeName;
    vespalib::string _queryVector;

public:
    /**
     * Constructs a blueprint.
     */
    EuclideanDistanceBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const fef::IIndexEnvironment &env,
                                   fef::IDumpFeatureVisitor &visitor) const override;

    // Inherit doc from Blueprint.
    virtual fef::Blueprint::UP createInstance() const override;

    // Inherit doc from Blueprint.
    virtual fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().attribute(fef::ParameterCollection::ANY).string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const fef::IIndexEnvironment &env,
                       const fef::ParameterList &params) override;

    // Inherit doc from Blueprint.
    virtual fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment &env) const override;

};


} // namespace features
} // namespace search

