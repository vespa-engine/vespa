// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

/**
 * Implements the executor for the distance feature.
 */
class DistanceExecutor : public search::fef::FeatureExecutor {
private:
    const search::fef::Location         & _location;
    const search::attribute::IAttributeVector * _pos;
    search::attribute::IntegerContent           _intBuf;

    feature_t calculateDistance(uint32_t docId);
    feature_t calculate2DZDistance(uint32_t docId);

public:
    /**
     * Constructs an executor for the distance feature.
     *
     * @param location the location object associated with the query environment.
     * @param pos the attribute to use for positions (expects zcurve encoding).
     */
    DistanceExecutor(const search::fef::Location & location,
                     const search::attribute::IAttributeVector * pos);
    virtual void execute(search::fef::MatchData & data);

    static const feature_t DEFAULT_DISTANCE;
};

/**
 * Implements the blueprint for the distance executor.
 */
class DistanceBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _posAttr;

public:
    /**
     * Constructs a blueprint for the distance executor.
     */
    DistanceBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & env) const override;
};


} // namespace features
} // namespace search

