// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distancefeature.h"
#include "distance_calculator_bundle.h"
#include "utils.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".features.distancefeature");

using namespace search::fef;
using namespace search::index::schema;
using vespalib::Issue;

namespace search::features {

/** Implements the executor for converting NNS rawscore to a distance feature. */
class ConvertRawscoreToDistance : public fef::FeatureExecutor {
private:
    DistanceCalculatorBundle _bundle;
    const fef::MatchData    *_md;
    void handle_bind_match_data(const fef::MatchData &md) override {
        _md = &md;
    }
public:
    ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, uint32_t fieldId);
    ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, const std::string &label);
    void execute(uint32_t docId) override;
};

ConvertRawscoreToDistance::ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, uint32_t fieldId)
  : _bundle(env, fieldId, "distance"),
    _md(nullptr)
{
}

ConvertRawscoreToDistance::ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, const std::string &label)
  : _bundle(env, std::nullopt, label, "distance"),
    _md(nullptr)
{
}

void
ConvertRawscoreToDistance::execute(uint32_t docId)
{
    feature_t min_distance = std::numeric_limits<feature_t>::max();
    assert(_md);
    for (const auto& elem : _bundle.elements()) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(elem.handle);
        if (tfmd->has_ranking_data(docId)) {
            feature_t invdist = tfmd->getRawScore();
            feature_t converted = elem.calc ? elem.calc->function().to_distance(invdist) : ((1.0 / invdist) - 1.0);
            min_distance = std::min(min_distance, converted);
        } else if (elem.calc) {
            feature_t invdist = elem.calc->calc_raw_score<false>(docId);
            feature_t converted = elem.calc->function().to_distance(invdist);
            min_distance = std::min(min_distance, converted);
        }
    }
    outputs().set_number(0, min_distance);
}

const feature_t DistanceExecutor::DEFAULT_DISTANCE(6400000000.0);

/**
 * Implements the executor for the great circle distance feature.
 */
class GeoGCDExecutor : public fef::FeatureExecutor {
private:
    std::vector<search::common::GeoGcd> _locations;
    const attribute::IAttributeVector * _pos;
    attribute::IntegerContent           _intBuf;
    feature_t                           _best_index;
    feature_t                           _best_lat;
    feature_t                           _best_lng;

    feature_t calculateGeoGCD(uint32_t docId);
public:
    /**
     * Constructs an executor for the GeoGCD feature.
     *
     * @param locations location objects associated with the query environment.
     * @param pos the attribute to use for positions (expects zcurve encoding).
     */
    GeoGCDExecutor(GeoLocationSpecPtrs locations, const attribute::IAttributeVector * pos);
    void execute(uint32_t docId) override;
};


feature_t GeoGCDExecutor::calculateGeoGCD(uint32_t docId) {
    feature_t dist = std::numeric_limits<feature_t>::max();
    _best_index = -1;
    _best_lat = 90.0;
    _best_lng = -180.0;
    if (_locations.empty()) {
        return dist;
    }
    _intBuf.fill(*_pos, docId);
    uint32_t numValues = _intBuf.size();
    int32_t docx = 0;
    int32_t docy = 0;
    for (auto loc : _locations) {
        for (uint32_t i = 0; i < numValues; ++i) {
            vespalib::geo::ZCurve::decode(_intBuf[i], &docx, &docy);
            double lat = docy / 1.0e6;
            double lng = docx / 1.0e6;
            double d = loc.km_great_circle_distance(lat, lng);
            if (d < dist) {
                dist = d;
                _best_index = i;
                _best_lat = lat;
                _best_lng = lng;
            }
        }
    }
    return dist;
}

GeoGCDExecutor::GeoGCDExecutor(GeoLocationSpecPtrs locations, const attribute::IAttributeVector * pos)
    : FeatureExecutor(),
      _locations(),
      _pos(pos),
      _intBuf(),
      _best_index(0.0),
      _best_lat(0.0),
      _best_lng(0.0)
{
    if (_pos == nullptr) {
        return;
    }
    _intBuf.allocate(_pos->getMaxValueCount());
    for (const auto * p : locations) {
        if (p && p->location.valid() && p->location.has_point) {
            double lat = p->location.point.y / 1.0e6;
            double lng = p->location.point.x / 1.0e6;
            _locations.emplace_back(lat, lng);
        }
    }
}


void
GeoGCDExecutor::execute(uint32_t docId)
{
    double dist_km = calculateGeoGCD(docId);
    double micro_degrees = search::common::GeoGcd::km_to_internal(dist_km);
    if (_best_index < 0) {
        dist_km = 40000.0;
        micro_degrees = DistanceExecutor::DEFAULT_DISTANCE;
    }
    outputs().set_number(0, micro_degrees);
    outputs().set_number(1, _best_index);
    outputs().set_number(2, _best_lat); // latitude
    outputs().set_number(3, _best_lng); // longitude
    outputs().set_number(4, dist_km);
}

DistanceBlueprint::DistanceBlueprint() :
    Blueprint("distance"),
    _field_name(),
    _label_name(),
    _attr_name(),
    _attr_id(search::index::Schema::UNKNOWN_FIELD_ID),
    _use_geo_pos(false),
    _use_nns_tensor(false),
    _use_item_label(false)
{
}

DistanceBlueprint::~DistanceBlueprint() = default;

void
DistanceBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                     IDumpFeatureVisitor &) const
{
}

Blueprint::UP
DistanceBlueprint::createInstance() const
{
    return std::make_unique<DistanceBlueprint>();
}

bool
DistanceBlueprint::setup_geopos(const std::string &attr)
{
    _attr_name = attr;
    _use_geo_pos = true;
    describeOutput("out", "The euclidean distance from the query position.");
    describeOutput("index", "Index in array of closest point");
    describeOutput("latitude", "Latitude of closest point");
    describeOutput("longitude", "Longitude of closest point");
    describeOutput("km", "Distance in kilometer units");
    return true;
}

bool
DistanceBlueprint::setup_nns(const std::string &attr)
{
    _attr_name = attr;
    _use_nns_tensor = true;
    describeOutput("out", "The euclidean distance from the query position.");
    return true;
}

bool
DistanceBlueprint::setup(const IIndexEnvironment & env,
                         const ParameterList & params)
{
    // params[0] = attribute name
    std::string arg = params[0].getValue();
    if (params.size() == 2) {
        // params[0] = field / label
        // params[1] = attribute name / label value
        if (arg == "label") {
            _label_name = params[1].getValue();
            _use_item_label = true;
            describeOutput("out", "The euclidean distance from the labeled query item.");
            return true;
        } else if (arg == "field") {
            arg = params[1].getValue();
        } else {
            LOG(error, "first argument must be 'field' or 'label', but was '%s'", arg.c_str());
            return false;
        }
    }
    _field_name = arg;
    std::string z = document::PositionDataType::getZCurveFieldName(arg);
    const FieldInfo *fi = env.getFieldByName(z);
    if (fi != nullptr && fi->hasAttribute()) {
        // can't check anything here because streaming has wrong information
        return setup_geopos(z);
    }
    fi = env.getFieldByName(arg);
    if (fi != nullptr && fi->hasAttribute()) {
        auto dt = fi->get_data_type();
        auto ct = fi->collection();
        if (dt == DataType::TENSOR && ct == CollectionType::SINGLE) {
            _attr_id = fi->id();
            return setup_nns(arg);
        }
        // could check if ct is CollectionType::SINGLE or CollectionType::ARRAY)
        if (dt == DataType::INT64) {
            return setup_geopos(arg);
        }
    }
    if (env.getFieldByName(arg) == nullptr) {
        LOG(error, "unknown field '%s' for rank feature %s\n", arg.c_str(), getName().c_str());
    } else {
        LOG(error, "field '%s' must be an attribute for rank feature %s\n", arg.c_str(), getName().c_str());
    }
    return false;
}

void
DistanceBlueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    if (_use_nns_tensor) {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _attr_id, "distance");
    }
    if (_use_item_label) {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _label_name, "distance");
    }
}

FeatureExecutor &
DistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_use_nns_tensor) {
        return stash.create<ConvertRawscoreToDistance>(env, _attr_id);
    }
    if (_use_item_label) {
        return stash.create<ConvertRawscoreToDistance>(env, _label_name);
    }
    // expect geo pos:
    const search::attribute::IAttributeVector * pos = nullptr;
    GeoLocationSpecPtrs matching_locs;
    GeoLocationSpecPtrs other_locs;

    for (auto loc_ptr : env.getAllLocations()) {
        if (_use_geo_pos && loc_ptr && loc_ptr->location.valid()) {
            if (loc_ptr->field_name == _attr_name ||
                loc_ptr->field_name == _field_name)
            {
                LOG(debug, "found loc from query env matching '%s'", _attr_name.c_str());
                matching_locs.push_back(loc_ptr);
            } else {
                LOG(debug, "found loc(%s) from query env not matching arg(%s)",
                    loc_ptr->field_name.c_str(), _attr_name.c_str());
                other_locs.push_back(loc_ptr);
            }
        }
    }
    if (matching_locs.empty() && other_locs.empty()) {
        LOG(debug, "createExecutor: no valid locations");
        return stash.create<GeoGCDExecutor>(matching_locs, nullptr);
    }
    LOG(debug, "createExecutor: valid location, attribute='%s'", _attr_name.c_str());
    if (_use_geo_pos) {
        pos = env.getAttributeContext().getAttribute(_attr_name);
        if (pos != nullptr) {
            if (!pos->isIntegerType()) {
                Issue::report("distance feature: The position attribute '%s' is not an integer attribute.",
                              pos->getName().c_str());
                pos = nullptr;
            } else if (pos->getCollectionType() == attribute::CollectionType::WSET) {
                Issue::report("distance feature: The position attribute '%s' is a weighted set attribute.",
                              pos->getName().c_str());
                pos = nullptr;
            }
        } else {
            Issue::report("distance feature: The position attribute '%s' was not found.", _attr_name.c_str());
        }
    }
    LOG(debug, "use '%s' locations with pos=%p", matching_locs.empty() ? "other" : "matching", pos);
    return stash.create<GeoGCDExecutor>(matching_locs.empty() ? other_locs : matching_locs, pos);
}

}
