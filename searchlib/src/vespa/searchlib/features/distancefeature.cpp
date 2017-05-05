// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.distancefeature");
#include <cmath>
#include <limits>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/fef/location.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/geo/zcurve.h>
#include "distancefeature.h"

using namespace search::fef;

namespace search {
namespace features {

feature_t
DistanceExecutor::calculateDistance(uint32_t docId)
{
    if (_location.isValid() && _pos != NULL) {
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
    if (_pos != NULL) {
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
    _posAttr()
{
}

DistanceBlueprint::~DistanceBlueprint()
{
}

void
DistanceBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                     IDumpFeatureVisitor &) const
{
}

Blueprint::UP
DistanceBlueprint::createInstance() const
{
    return Blueprint::UP(new DistanceBlueprint());
}

bool
DistanceBlueprint::setup(const IIndexEnvironment & env,
                         const ParameterList & params)
{
    _posAttr = params[0].getValue();
    describeOutput("out", "The euclidian distance from the query position.");
    env.hintAttributeAccess(_posAttr);
    env.hintAttributeAccess(document::PositionDataType::getZCurveFieldName(_posAttr));
    return true;
}

FeatureExecutor &
DistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    const search::attribute::IAttributeVector * pos = NULL;
    const Location & location = env.getLocation();
    LOG(debug, "DistanceBlueprint::createExecutor location.valid='%s', '%s', alternatively '%s'",
               location.isValid() ? "true" : "false", _posAttr.c_str(), document::PositionDataType::getZCurveFieldName(_posAttr).c_str());
    if (location.isValid()) {
        pos = env.getAttributeContext().getAttribute(_posAttr);
        if (pos == NULL) {
            LOG(debug, "Failed to find attribute '%s', resorting too '%s'",
                       _posAttr.c_str(), document::PositionDataType::getZCurveFieldName(_posAttr).c_str());
            pos = env.getAttributeContext().getAttribute(document::PositionDataType::getZCurveFieldName(_posAttr));
        }
        if (pos != NULL) {
            if (!pos->isIntegerType()) {
                LOG(warning, "The position attribute '%s' is not an integer attribute. Will use default distance.",
                    pos->getName().c_str());
                pos = NULL;
            } else if (pos->getCollectionType() == attribute::CollectionType::WSET) {
                LOG(warning, "The position attribute '%s' is a weighted set attribute. Will use default distance.",
                    pos->getName().c_str());
                pos = NULL;
            }
        } else {
            LOG(warning, "The position attribute '%s' was not found. Will use default distance.", _posAttr.c_str());
        }
    }

    return stash.create<DistanceExecutor>(location, pos);
}



} // namespace features
} // namespace search
