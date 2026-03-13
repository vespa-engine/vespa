// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "closenessfeature.h"
#include "distance_calculator_bundle.h"
#include "utils.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.closenessfeature");

using namespace search::fef;

namespace search::features {

/** Implements the executor for converting NNS rawscore to a closeness feature. */
class ConvertRawScoreToCloseness : public fef::FeatureExecutor {
private:
    DistanceCalculatorBundle _bundle;
    const fef::MatchData    *_md;
    void handle_bind_match_data(const fef::MatchData &md) override {
        _md = &md;
    }
public:
    ConvertRawScoreToCloseness(const fef::IQueryEnvironment &env, uint32_t fieldId);
    ConvertRawScoreToCloseness(const fef::IQueryEnvironment &env, const std::string &label);
    void execute(uint32_t docId) override;
};

ConvertRawScoreToCloseness::ConvertRawScoreToCloseness(const fef::IQueryEnvironment &env, uint32_t fieldId)
  : _bundle(env, fieldId, "closeness"),
    _md(nullptr)
{
}

ConvertRawScoreToCloseness::ConvertRawScoreToCloseness(const fef::IQueryEnvironment &env, const std::string &label)
  : _bundle(env, std::nullopt, label, "closeness"),
    _md(nullptr)
{
}

void
ConvertRawScoreToCloseness::execute(uint32_t docId)
{
    feature_t max_closeness = _bundle.min_rawscore();
    assert(_md);
    for (const auto& elem : _bundle.elements()) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(elem.handle);
        if (tfmd->has_ranking_data(docId)) {
            feature_t converted = tfmd->getRawScore();
            max_closeness = std::max(max_closeness, converted);
        } else if (elem.calc) {
            feature_t converted = elem.calc->calc_raw_score<false>(docId);
            max_closeness = std::max(max_closeness, converted);
        }
    }
    outputs().set_number(0, max_closeness);
}


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
    _halfResponse(1),
    _arg_string(),
    _attr_id(search::index::Schema::UNKNOWN_FIELD_ID),
    _use_geo_pos(false),
    _use_nns_tensor(false),
    _use_item_label(false)
{
}

ClosenessBlueprint::~ClosenessBlueprint() = default;

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
    std::string arg = params[0].getValue();
    if (params.size() == 2) {
        // params[0] = field / label
        // params[0] = attribute name / label value
        if (arg == "label") {
            _arg_string = params[1].getValue();
            _use_item_label = true;
            describeOutput("out", "The closeness from the labeled query item.");
            return true;
        } else if (arg == "field") {
            arg = params[1].getValue();
            // sanity checking happens in distance feature
        } else {
            LOG(error, "first argument must be 'field' or 'label', but was '%s'",
                arg.c_str());
            return false;
        }
    }
    const FieldInfo *fi = env.getFieldByName(arg);
    if (fi != nullptr && fi->hasAttribute()) {
        auto dt = fi->get_data_type();
        auto ct = fi->collection();
        if (dt == search::index::schema::DataType::TENSOR &&
            ct == search::index::schema::CollectionType::SINGLE)
        {
            _arg_string = arg;
            _use_nns_tensor = true;
            _attr_id = fi->id();
            describeOutput("out", "The closeness for the given tensor field.");
            return true;
        }
    }
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

    _use_geo_pos = true;
    if (params.size() == 2) {
        defineInput("distance(field," + arg + ")");
    } else {
        defineInput("distance(" + arg + ")");
    }
    describeOutput("out", "The closeness of the document (linear)");
    describeOutput("logscale", "The closeness of the document (logarithmic shape)");
    return true;
}

Blueprint::UP
ClosenessBlueprint::createInstance() const
{
    return std::make_unique<ClosenessBlueprint>();
}

void
ClosenessBlueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    if (_use_nns_tensor) {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _attr_id, "closeness");
    }
    if (_use_item_label) {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _arg_string, "closeness");
    }
}

FeatureExecutor &
ClosenessBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_use_nns_tensor) {
        return stash.create<ConvertRawScoreToCloseness>(env, _attr_id);
    }
    if (_use_item_label) {
        return stash.create<ConvertRawScoreToCloseness>(env, _arg_string);
    }
    assert(_use_geo_pos);
    return stash.create<ClosenessExecutor>(_maxDistance, _scaleDistance);
}

}
