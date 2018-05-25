// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search::features {

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
class DistanceToPathExecutor : public fef::FeatureExecutor {
private:
    attribute::IntegerContent          _intBuf; // Position value buffer.
    std::vector<Vector2>               _path;   // Path given by query.
    const attribute::IAttributeVector *_pos;    // Position attribute.

public:
    /**
     * Constructs an executor for the distance to path feature.
     *
     * @param path The path associated with the query environment.
     * @param pos  The attribute to use for positions (expects zcurve encoding).
     */
    DistanceToPathExecutor(std::vector<Vector2> &path,
                           const attribute::IAttributeVector *pos);
    void execute(uint32_t docId) override;

    /**
     * Defines a default distance value to use if a proper one can not be determined.
     */
    static const feature_t DEFAULT_DISTANCE;
};

/**
 * Implements the blueprint for the distance to path feature.
 */
class DistanceToPathBlueprint : public fef::Blueprint {
private:
    vespalib::string _posAttr; // Name of the position attribute.

public:
    DistanceToPathBlueprint();
    ~DistanceToPathBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string();
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


}
