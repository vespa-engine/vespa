// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distancefeature.h"
#include "distance_calculator_bundle.h"
#include "utils.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>
#include <cmath>
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
    ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, const vespalib::string &label);
    void execute(uint32_t docId) override;
};

ConvertRawscoreToDistance::ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, uint32_t fieldId)
  : _bundle(env, fieldId, "distance"),
    _md(nullptr)
{
}

ConvertRawscoreToDistance::ConvertRawscoreToDistance(const fef::IQueryEnvironment &env, const vespalib::string &label)
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
        if (tfmd->getDocId() == docId) {
            feature_t invdist = tfmd->getRawScore();
            feature_t converted = (1.0 / invdist) - 1.0;
            min_distance = std::min(min_distance, converted);
        } else if (elem.calc) {
            feature_t invdist = elem.calc->calc_raw_score(docId);
            feature_t converted = (1.0 / invdist) - 1.0;
            min_distance = std::min(min_distance, converted);
        }
    }
    outputs().set_number(0, min_distance);
}


feature_t
DistanceExecutor::calculateDistance(uint32_t docId)
{
    _best_index = -1.0;
    _best_x = -180.0 * 1.0e6;
    _best_y = 90.0 * 1.0e6;
    if ((! _locations.empty()) && (_pos != nullptr)) {
        LOG(debug, "calculate 2D Z-distance from %zu locations", _locations.size());
        return calculate2DZDistance(docId);
    }
    return DEFAULT_DISTANCE;
}


feature_t
DistanceExecutor::calculate2DZDistance(uint32_t docId)
{
    _intBuf.fill(*_pos, docId);
    uint32_t numValues = _intBuf.size();
    uint64_t sqabsdist = std::numeric_limits<uint64_t>::max();
    int32_t docx = 0;
    int32_t docy = 0;
    for (auto loc : _locations) {
        assert(loc);
        assert(loc->location.valid());
        for (uint32_t i = 0; i < numValues; ++i) {
            vespalib::geo::ZCurve::decode(_intBuf[i], &docx, &docy);
            uint64_t sqdist = loc->location.sq_distance_to({docx, docy});
            if (sqdist < sqabsdist) {
                _best_index = i;
		_best_x = docx;
		_best_y = docy;
                sqabsdist = sqdist;
            }
        }
    }
    return static_cast<feature_t>(std::sqrt(static_cast<feature_t>(sqabsdist)));
}

DistanceExecutor::DistanceExecutor(GeoLocationSpecPtrs locations,
                                   const search::attribute::IAttributeVector * pos) :
    FeatureExecutor(),
    _locations(locations),
    _pos(pos),
    _intBuf()
{
    if (_pos != nullptr) {
        _intBuf.allocate(_pos->getMaxValueCount());
    }
}

void
DistanceExecutor::execute(uint32_t docId)
{
    static constexpr double earth_mean_radius = 6371.0088;
    static constexpr double deg_to_rad = M_PI / 180.0;
    static constexpr double km_from_internal = 1.0e-6 * deg_to_rad * earth_mean_radius;
    feature_t internal_d = calculateDistance(docId);
    outputs().set_number(0, internal_d);
    outputs().set_number(1, _best_index);
    outputs().set_number(2, _best_y * 1.0e-6); // latitude
    outputs().set_number(3, _best_x * 1.0e-6); // longitude
    outputs().set_number(4, internal_d * km_from_internal); // km
}

const feature_t DistanceExecutor::DEFAULT_DISTANCE(6400000000.0);


DistanceBlueprint::DistanceBlueprint() :
    Blueprint("distance"),
    _field_name(),
    _arg_string(),
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
DistanceBlueprint::setup_geopos(const IIndexEnvironment & env,
                                const vespalib::string &attr)
{
    _arg_string = attr;
    _use_geo_pos = true;
    describeOutput("out", "The euclidean distance from the query position.");
    describeOutput("index", "Index in array of closest point");
    describeOutput("latitude", "Latitude of closest point");
    describeOutput("longitude", "Longitude of closest point");
    describeOutput("km", "Distance in kilometer units");
    env.hintAttributeAccess(_arg_string);
    return true;
}

bool
DistanceBlueprint::setup_nns(const IIndexEnvironment & env,
                             const vespalib::string &attr)
{
    _arg_string = attr;
    _use_nns_tensor = true;
    describeOutput("out", "The euclidean distance from the query position.");
    env.hintAttributeAccess(_arg_string);
    return true;
}

bool
DistanceBlueprint::setup(const IIndexEnvironment & env,
                         const ParameterList & params)
{
    // params[0] = attribute name
    vespalib::string arg = params[0].getValue();
    if (params.size() == 2) {
        // params[0] = field / label
        // params[1] = attribute name / label value
        if (arg == "label") {
            _arg_string = params[1].getValue();
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
    vespalib::string z = document::PositionDataType::getZCurveFieldName(arg);
    const FieldInfo *fi = env.getFieldByName(z);
    if (fi != nullptr && fi->hasAttribute()) {
        // can't check anything here because streaming has wrong information
        return setup_geopos(env, z);
    }
    fi = env.getFieldByName(arg);
    if (fi != nullptr && fi->hasAttribute()) {
        auto dt = fi->get_data_type();
        auto ct = fi->collection();
        if (dt == DataType::TENSOR && ct == CollectionType::SINGLE) {
            _attr_id = fi->id();
            return setup_nns(env, arg);
        }
        // could check if ct is CollectionType::SINGLE or CollectionType::ARRAY)
        if (dt == DataType::INT64) {
            return setup_geopos(env, arg);
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
        DistanceCalculatorBundle::prepare_shared_state(env, store, _arg_string, "distance");
    }
}

FeatureExecutor &
DistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_use_nns_tensor) {
        return stash.create<ConvertRawscoreToDistance>(env, _attr_id);
    }
    if (_use_item_label) {
        return stash.create<ConvertRawscoreToDistance>(env, _arg_string);
    }
    // expect geo pos:
    const search::attribute::IAttributeVector * pos = nullptr;
    GeoLocationSpecPtrs matching_locs;
    GeoLocationSpecPtrs other_locs;

    for (auto loc_ptr : env.getAllLocations()) {
        if (_use_geo_pos && loc_ptr && loc_ptr->location.valid()) {
            if (loc_ptr->field_name == _arg_string ||
                loc_ptr->field_name == _field_name)
            {
                LOG(debug, "found loc from query env matching '%s'", _arg_string.c_str());
                matching_locs.push_back(loc_ptr);
            } else {
                LOG(debug, "found loc(%s) from query env not matching arg(%s)",
                    loc_ptr->field_name.c_str(), _arg_string.c_str());
                other_locs.push_back(loc_ptr);
            }
        }
    }
    if (matching_locs.empty() && other_locs.empty()) {
        LOG(debug, "createExecutor: no valid locations");
        return stash.create<DistanceExecutor>(matching_locs, nullptr);
    }
    LOG(debug, "createExecutor: valid location, attribute='%s'", _arg_string.c_str());

    if (_use_geo_pos) {
        pos = env.getAttributeContext().getAttribute(_arg_string);
        if (pos != nullptr) {
            if (!pos->isIntegerType()) {
                Issue::report("distance feature: The position attribute '%s' is not an integer attribute. Will use default distance.",
                              pos->getName().c_str());
                pos = nullptr;
            } else if (pos->getCollectionType() == attribute::CollectionType::WSET) {
                Issue::report("distance feature: The position attribute '%s' is a weighted set attribute. Will use default distance.",
                              pos->getName().c_str());
                pos = nullptr;
            }
        } else {
            Issue::report("distance feature: The position attribute '%s' was not found. Will use default distance.", _arg_string.c_str());
        }
    }
    LOG(debug, "use '%s' locations with pos=%p", matching_locs.empty() ? "other" : "matching", pos);
    return stash.create<DistanceExecutor>(matching_locs.empty() ? other_locs : matching_locs, pos);
}

}
