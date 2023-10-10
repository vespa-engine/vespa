// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchcommon/attribute/attributecontent.h>

namespace search::features {

/**
 * Implements the executor for the eucledian distance feature.
 */
template <typename DataType>
class EuclideanDistanceExecutor : public fef::FeatureExecutor {

public:
    using BufferType = search::attribute::AttributeContent<DataType>;
    using QueryVectorType = std::vector<DataType>;

private:
    const search::attribute::IAttributeVector &_attribute;
    const QueryVectorType _vector;
    BufferType _attributeBuffer;

    feature_t euclideanDistance(const BufferType &v1, const QueryVectorType &v2);

public:

    EuclideanDistanceExecutor(const search::attribute::IAttributeVector &attribute, QueryVectorType vector);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the euclidean distance executor.
 */
class EuclideanDistanceBlueprint : public fef::Blueprint {
private:
    vespalib::string _attributeName;
    vespalib::string _queryVector;

public:
    EuclideanDistanceBlueprint();
    ~EuclideanDistanceBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().attribute(fef::ParameterCollection::ANY).string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

};

}
