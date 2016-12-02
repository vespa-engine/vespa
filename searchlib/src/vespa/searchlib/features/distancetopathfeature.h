// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

/**
 * Define the point type that makes up the end-points in our path.
 */
struct Vector2 {
    Vector2(double _x, double _y) : x(_x), y(_y) { }
    double x, y;
};

/**
 * Implements the executor for the distance to path feature.
 */
class DistanceToPathExecutor : public search::fef::FeatureExecutor {
private:
    search::attribute::IntegerContent          _intBuf; // Position value buffer.
    std::vector<Vector2>                 _path;   // Path given by query.
    const search::attribute::IAttributeVector *_pos;    // Position attribute.

public:
    /**
     * Constructs an executor for the distance to path feature.
     *
     * @param path The path associated with the query environment.
     * @param pos  The attribute to use for positions (expects zcurve encoding).
     */
    DistanceToPathExecutor(std::vector<Vector2> &path,
                           const search::attribute::IAttributeVector *pos);
    virtual void execute(search::fef::MatchData & data);

    /**
     * Defines a default distance value to use if a proper one can not be determined.
     */
    static const feature_t DEFAULT_DISTANCE;
};

/**
 * Implements the blueprint for the distance to path feature.
 */
class DistanceToPathBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _posAttr; // Name of the position attribute.

public:
    /**
     * Constructs a blueprint for the distance to path feature.
     */
    DistanceToPathBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

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
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment &env) const override;
};


} // namespace features
} // namespace search

