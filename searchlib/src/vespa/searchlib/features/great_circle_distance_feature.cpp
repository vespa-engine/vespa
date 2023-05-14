// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "great_circle_distance_feature.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>
#include <cmath>
#include <limits>
#include "utils.h"

#include <vespa/log/log.h>
LOG_SETUP(".features.great_circle_distance_feature");

using namespace search::fef;
using namespace search::index::schema;
using vespalib::Issue;

namespace search::features {

feature_t GCDExecutor::calculateGCD(uint32_t docId) {
    feature_t dist = std::numeric_limits<feature_t>::max();
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
                _best_lat = lat;
                _best_lng = lng;
            }
        }
    }
    return dist;
}

GCDExecutor::GCDExecutor(GeoLocationSpecPtrs locations, const attribute::IAttributeVector * pos)
    : FeatureExecutor(),
      _locations(),
      _pos(pos),
      _intBuf()
{
    if (_pos == nullptr) {
        return;
    }
    _intBuf.allocate(_pos->getMaxValueCount());
    for (const auto * p : locations) {
        if (p && p->location.valid()) {
            double lat = p->location.point.y * 1.0e-6;
            double lng = p->location.point.x * 1.0e-6;
            _locations.emplace_back(search::common::GeoGcd{lat, lng});
        }
    }
}

void
GCDExecutor::execute(uint32_t docId)
{
    outputs().set_number(0, calculateGCD(docId));
    outputs().set_number(1, _best_lat); // latitude
    outputs().set_number(2, _best_lng); // longitude
}


GreatCircleDistanceBlueprint::GreatCircleDistanceBlueprint() :
    Blueprint("great_circle_distance"),
    _attr_name()
{
}

GreatCircleDistanceBlueprint::~GreatCircleDistanceBlueprint() = default;

void GreatCircleDistanceBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                                     IDumpFeatureVisitor &) const
{
}

Blueprint::UP
GreatCircleDistanceBlueprint::createInstance() const
{
    return std::make_unique<GreatCircleDistanceBlueprint>();
}

bool
GreatCircleDistanceBlueprint::setup_geopos(const vespalib::string &attr)
{
    _attr_name = attr;
    describeOutput("km", "The distance (in km) from the query position.");
    describeOutput("latitude", "Latitude of closest point");
    describeOutput("longitude", "Longitude of closest point");
    return true;
}


bool
GreatCircleDistanceBlueprint::setup(const IIndexEnvironment & env,
                                    const ParameterList & params)
{
    if (params.size() == 1) {
        _field_name = params[0].getValue();
    } else if (params.size() == 2) {
        // params[0] = "field"
        // params[1] = attribute name
        if (params[0].getValue() == "field") {
            _field_name = params[1].getValue();
        } else {
            LOG(error, "first argument must be 'field' but was '%s'", params[0].getValue().c_str());
            return false;
        }
    } else {
        LOG(error, "Wants 2 parameters, but got %zd", params.size());
        return false;
    }
    vespalib::string z = document::PositionDataType::getZCurveFieldName(_field_name);
    const auto *fi = env.getFieldByName(z);
    if (fi != nullptr && fi->hasAttribute()) {
        auto dt = fi->get_data_type();
        auto ct = fi->collection();
        LOG(spam, "index env has attribute for field '%s' which is: %s%s",
            z.c_str(),
            (ct == CollectionType::SINGLE ? "" :
             (ct == CollectionType::ARRAY ? "array of " : "collection of ")),
            (dt == DataType::INT64 ? "int64" :
             (dt == DataType::DOUBLE ? "double" : "something")));
        /* we can't check these because streaming has wrong information
        if (dt == DataType::INT64) {
            if (ct == CollectionType::SINGLE || ct == CollectionType::ARRAY) {
                return setup_geopos(env, z);
            }
        }
        */
        return setup_geopos(z);
    }
    if (env.getFieldByName(_field_name) == nullptr && fi == nullptr) {
        LOG(error, "unknown field '%s' for rank feature %s\n", _field_name.c_str(), getName().c_str());
    } else {
        LOG(error, "field '%s' must be type position and attribute for rank feature %s\n", _field_name.c_str(), getName().c_str());
    }
    return false;
}

FeatureExecutor &
GreatCircleDistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    // expect geo pos:
    const search::attribute::IAttributeVector * pos = nullptr;
    GeoLocationSpecPtrs matching_locs;
    GeoLocationSpecPtrs other_locs;

    for (auto loc_ptr : env.getAllLocations()) {
        if (loc_ptr && loc_ptr->location.valid()) {
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
        return stash.create<GCDExecutor>(matching_locs, nullptr);
    }
    LOG(debug, "createExecutor: valid location, attribute='%s'", _attr_name.c_str());
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
    LOG(debug, "use '%s' locations with pos=%p", matching_locs.empty() ? "other" : "matching", pos);
    return stash.create<GCDExecutor>(matching_locs.empty() ? other_locs : matching_locs, pos);
}

}
