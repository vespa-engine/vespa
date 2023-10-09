// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "freshnessfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.freshnessfeature");

using namespace search::fef;

namespace search::features {

FreshnessExecutor::FreshnessExecutor(feature_t maxAge, feature_t scaleAge) :
    FeatureExecutor(),
    _maxAge(maxAge),
    _logCalc(maxAge, scaleAge)
{
}

void
FreshnessExecutor::execute(uint32_t)
{
    feature_t age = inputs().get_number(0);
    LOG(debug, "Age: %f  Maxage: %f res: %f\n", age, _maxAge, (age / _maxAge));
    feature_t freshness = std::max(1 - (age / _maxAge), (feature_t)0);
    outputs().set_number(0, freshness);
    outputs().set_number(1, _logCalc.get(age));
}


FreshnessBlueprint::FreshnessBlueprint() :
    Blueprint("freshness"),
    _maxAge(3*30*24*60*60), // default value (90 days)
    _halfResponse(7*24*60*60), // makes sure freshness.logscale = 0.5 when age is 7 days
    _scaleAge(LogarithmCalculator::getScale(_halfResponse, _maxAge))
{
}

void
FreshnessBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                      IDumpFeatureVisitor &) const
{
}

bool
FreshnessBlueprint::setup(const IIndexEnvironment & env,
                          const ParameterList & params)
{
    // params[0] = attribute name
    Property p = env.getProperties().lookup(getName(), "maxAge");
    if (p.found()) {
        _maxAge = util::strToNum<feature_t>(p.get());
    }
    p = env.getProperties().lookup(getName(), "halfResponse");
    if (p.found()) {
        _halfResponse = util::strToNum<feature_t>(p.get());
    }
    // sanity checks:
    if (_maxAge < 1) {
        LOG(warning, "Invalid %s.maxAge = %g, using 1.0",
            getName().c_str(), (double)_maxAge);
        _maxAge = 1.0;
    }
    if (_halfResponse < 1) {
        LOG(warning, "Invalid %s.halfResponse = %g, using 1.0",
            getName().c_str(), (double)_halfResponse);
        _halfResponse = 1.0;
    }
    if (_halfResponse >= _maxAge / 2) {
        feature_t newResponse = (_maxAge / 2) - 1;
        LOG(warning, "Invalid %s.halfResponse = %g, using %g ((%s.maxAge / 2) - 1)",
            getName().c_str(), (double)_halfResponse, (double)newResponse, getName().c_str());
        _halfResponse = newResponse;
    }
    _scaleAge = LogarithmCalculator::getScale(_halfResponse, _maxAge);

    defineInput("age(" + params[0].getValue() + ")");
    describeOutput("out", "The freshness of the document (linear)");
    describeOutput("logscale", "The freshness of the document (logarithmic shape)");

    return true;
}

Blueprint::UP
FreshnessBlueprint::createInstance() const
{
    return std::make_unique<FreshnessBlueprint>();
}

fef::ParameterDescriptions
FreshnessBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().attribute(fef::ParameterDataTypeSet::normalTypeSet(), fef::ParameterCollection::ANY);
}

FeatureExecutor &
FreshnessBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<FreshnessExecutor>(_maxAge, _scaleAge);
}

}
