// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/common/geo_gcd.h>

namespace search::features {

/** Convenience typedef. */
using GeoLocationSpecPtrs = std::vector<const search::common::GeoLocationSpec *>;

/**
 * Implements the executor for the great circle distance feature.
 */
class GCDExecutor : public fef::FeatureExecutor {
private:
    std::vector<search::common::GeoGcd> _locations;
    const attribute::IAttributeVector * _pos;
    attribute::IntegerContent           _intBuf;
    feature_t                           _best_lat;
    feature_t                           _best_lng;

    feature_t calculateGCD(uint32_t docId);
public:
    /**
     * Constructs an executor for the GCD feature.
     *
     * @param locations location objects associated with the query environment.
     * @param pos the attribute to use for positions (expects zcurve encoding).
     */
    GCDExecutor(GeoLocationSpecPtrs locations, const attribute::IAttributeVector * pos);
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for the GCD executor.
 */
class GreatCircleDistanceBlueprint : public fef::Blueprint {
private:
    vespalib::string _field_name;
    vespalib::string _attr_name;
    bool setup_geopos(const fef::IIndexEnvironment & env, const vespalib::string &attr);
public:
    GreatCircleDistanceBlueprint();
    ~GreatCircleDistanceBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().desc().string().string();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
