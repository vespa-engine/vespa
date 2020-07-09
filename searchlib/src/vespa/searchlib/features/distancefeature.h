// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchcommon/attribute/attributecontent.h>

namespace search::features {

/**
 * Implements the executor for the distance feature.
 */
class DistanceExecutor : public fef::FeatureExecutor {
private:
    std::vector<const fef::Location *>  _locations;
    const attribute::IAttributeVector * _pos;
    attribute::IntegerContent           _intBuf;

    feature_t calculateDistance(uint32_t docId);
    feature_t calculate2DZDistance(uint32_t docId);

public:
    /**
     * Constructs an executor for the distance feature.
     *
     * @param locations location objects associated with the query environment.
     * @param pos the attribute to use for positions (expects zcurve encoding).
     */
    DistanceExecutor(std::vector<const fef::Location *> locations,
                     const attribute::IAttributeVector * pos);
    void execute(uint32_t docId) override;

    static const feature_t DEFAULT_DISTANCE;
};

/**
 * Implements the blueprint for the distance executor.
 */
class DistanceBlueprint : public fef::Blueprint {
private:
    vespalib::string _arg_string;
    uint32_t _attr_id;
    bool _use_geo_pos;
    bool _use_nns_tensor;
    bool _use_item_label;

    bool setup_geopos(const fef::IIndexEnvironment & env, const vespalib::string &attr);
    bool setup_nns(const fef::IIndexEnvironment & env, const vespalib::string &attr);

public:
    DistanceBlueprint();
    ~DistanceBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().desc().string().string();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
