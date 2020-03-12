// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distancefeature.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/location.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/stash.h>
#include <cmath>
#include <limits>
#include "utils.h"

#include <vespa/log/log.h>
LOG_SETUP(".features.distancefeature");

using namespace search::fef;
using namespace search::index::schema;

namespace search::features {

/** Implements the executor for converting NNS rawscore to a distance feature. */
class ConvertRawscoreExecutor : public fef::FeatureExecutor {
private:
    std::vector<fef::TermFieldHandle> _handles;
    const fef::MatchData             *_md;
    void handle_bind_match_data(const fef::MatchData &md) override {
        _md = &md;
    }
public:
    ConvertRawscoreExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
    void execute(uint32_t docId) override;
};

ConvertRawscoreExecutor::ConvertRawscoreExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId)
  : _handles(),
    _md(nullptr)
{
    _handles.reserve(env.getNumTerms());
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void
ConvertRawscoreExecutor::execute(uint32_t docId)
{
    feature_t output = std::numeric_limits<feature_t>::max();
    assert(_md);
    for (auto handle : _handles) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(handle);
        if (tfmd->getDocId() == docId) {
            // add conversion from "closeness" RawScore later:
            feature_t converted =  tfmd->getRawScore();
            output = std::min(output, converted);
        }
    }
    outputs().set_number(0, output);
}


feature_t
DistanceExecutor::calculateDistance(uint32_t docId)
{
    if (_location.isValid() && _pos != nullptr) {
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
    for (uint32_t i = 0; i < numValues; ++i) {
        vespalib::geo::ZCurve::decode(_intBuf[i], &docx, &docy);
        uint32_t dx;
        uint32_t dy;
        if (_location.getXPosition() > docx) {
            dx = _location.getXPosition() - docx;
        } else {
            dx = docx - _location.getXPosition();
        }
        if (_location.getXAspect() != 0) {
            dx = ((uint64_t) dx * _location.getXAspect()) >> 32;
        }
        if (_location.getYPosition() > docy) {
            dy = _location.getYPosition() - docy;
        } else {
            dy = docy - _location.getYPosition();
        }
        uint64_t sqdist = (uint64_t) dx * dx + (uint64_t) dy * dy;
        if (sqdist < sqabsdist) {
            sqabsdist = sqdist;
        }
    }
    return static_cast<feature_t>(std::sqrt(static_cast<feature_t>(sqabsdist)));
}

DistanceExecutor::DistanceExecutor(const Location & location,
                                   const search::attribute::IAttributeVector * pos) :
    FeatureExecutor(),
    _location(location),
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
    outputs().set_number(0, calculateDistance(docId));
}

const feature_t DistanceExecutor::DEFAULT_DISTANCE(6400000000.0);


DistanceBlueprint::DistanceBlueprint() :
    Blueprint("distance"),
    _posAttr(),
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
    _posAttr = attr;
    _use_geo_pos = true;
    describeOutput("out", "The euclidean distance from the query position.");
    env.hintAttributeAccess(_posAttr);
    return true;
}

bool
DistanceBlueprint::setup_nns(const IIndexEnvironment & env,
                             const vespalib::string &attr)
{
    _posAttr = attr;
    _use_nns_tensor = true;
    describeOutput("out", "The euclidean distance from the query position.");
    env.hintAttributeAccess(_posAttr);
    return true;
}

bool
DistanceBlueprint::setup(const IIndexEnvironment & env,
                         const ParameterList & params)
{
    vespalib::string arg = params[0].getValue();
    const FieldInfo *fi = env.getFieldByName(arg);
    if (fi != nullptr && fi->hasAttribute()) {
        auto dt = fi->get_data_type();
        auto ct = fi->collection();
        if (dt == DataType::TENSOR && ct == CollectionType::SINGLE) {
            _attr_id = fi->id();
            return setup_nns(env, arg);
        }
        // could check if dt is DataType::INT64
        // could check if ct is CollectionType::SINGLE or CollectionType::ARRAY)
        return setup_geopos(env, arg);
    }
    vespalib::string z = document::PositionDataType::getZCurveFieldName(arg);
    fi = env.getFieldByName(z);
    if (fi != nullptr && fi->hasAttribute()) {
        return setup_geopos(env, z);
    }
    return false;
}

FeatureExecutor &
DistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_use_nns_tensor) {
        const search::attribute::IAttributeVector * attr = env.getAttributeContext().getAttribute(_posAttr);
        if (attr != nullptr) {
             return stash.create<ConvertRawscoreExecutor>(env, _attr_id);
        } else {
             LOG(warning, "unexpected missing attribute '%s'\n", _posAttr.c_str());
        }
    }
    const search::attribute::IAttributeVector * pos = nullptr;
    const Location & location = env.getLocation();
    LOG(debug, "DistanceBlueprint::createExecutor location.valid='%s', attribute='%s'",
        location.isValid() ? "true" : "false", _posAttr.c_str());
    if (_use_geo_pos && location.isValid()) {
        pos = env.getAttributeContext().getAttribute(_posAttr);
        if (pos != nullptr) {
            if (!pos->isIntegerType()) {
                LOG(warning, "The position attribute '%s' is not an integer attribute. Will use default distance.",
                    pos->getName().c_str());
                pos = nullptr;
            } else if (pos->getCollectionType() == attribute::CollectionType::WSET) {
                LOG(warning, "The position attribute '%s' is a weighted set attribute. Will use default distance.",
                    pos->getName().c_str());
                pos = nullptr;
            }
        } else {
            LOG(warning, "The position attribute '%s' was not found. Will use default distance.", _posAttr.c_str());
        }
    }

    return stash.create<DistanceExecutor>(location, pos);
}

}
