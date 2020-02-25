// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "closenessfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.closenessfeature");

using namespace search::fef;

namespace search::features {

ClosenessExecutor::ClosenessExecutor(feature_t maxDistance, feature_t scaleDistance) :
    FeatureExecutor(),
    _maxDistance(maxDistance),
    _logCalc(maxDistance, scaleDistance)
{
}

void
ClosenessExecutor::execute(uint32_t)
{
    feature_t distance = inputs().get_number(0);
    feature_t closeness = std::max(1 - (distance / _maxDistance), (feature_t)0);
    outputs().set_number(0, closeness);
    outputs().set_number(1, _logCalc.get(distance));
}


// Polar Earth radius r = 6356.8 km
// Polar Earth diameter = 2 * pi * r = 39940.952 km
// 1 diameter = 39940.952 km = 360 degrees = 360 * 1000000 microdegrees
// -> 1 km = 9013.30536007 microdegrees

ClosenessBlueprint::ClosenessBlueprint() :
    Blueprint("closeness"),
    _maxDistance(9013305.0),     // default value (about 250 km)
    _scaleDistance(5.0*9013.305), // default value (about 5 km)
    _halfResponse(1)
{
}

void
ClosenessBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                      IDumpFeatureVisitor &) const
{
}

bool
ClosenessBlueprint::setup(const IIndexEnvironment & env,
                          const search::fef::ParameterList & params)
{
    // params[0] = attribute name
    Property p = env.getProperties().lookup(getName(), "maxDistance");
    if (p.found()) {
        _maxDistance = util::strToNum<feature_t>(p.get());
    }
    p = env.getProperties().lookup(getName(), "halfResponse");
    bool useHalfResponse = false;
    if (p.found()) {
        _halfResponse = util::strToNum<feature_t>(p.get());
        useHalfResponse = true;
    }
    // sanity checks:
    if (_maxDistance < 1) {
        LOG(warning, "Invalid %s.maxDistance = %g, using 1.0",
            getName().c_str(), (double)_maxDistance);
        _maxDistance = 1.0;
    }
    if (_halfResponse < 1) {
        LOG(warning, "Invalid %s.halfResponse = %g, using 1.0",
            getName().c_str(), (double)_halfResponse);
        _halfResponse = 1.0;
    }
    if (_halfResponse >= _maxDistance / 2) {
        feature_t newResponse = (_maxDistance / 2) - 1;
        LOG(warning, "Invalid %s.halfResponse = %g, using %g ((%s.maxDistance / 2) - 1)",
            getName().c_str(), (double)_halfResponse, (double)newResponse, getName().c_str());
        _halfResponse = newResponse;
    }

    if (useHalfResponse) {
        _scaleDistance = LogarithmCalculator::getScale(_halfResponse, _maxDistance);
    }


    defineInput("distance(" + params[0].getValue() + ")");
    describeOutput("out", "The closeness of the document (linear)");
    describeOutput("logscale", "The closeness of the document (logarithmic shape)");

    return true;
}

Blueprint::UP
ClosenessBlueprint::createInstance() const
{
    return std::make_unique<ClosenessBlueprint>();
}

FeatureExecutor &
ClosenessBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<ClosenessExecutor>(_maxDistance, _scaleDistance);
}

}
