// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distancetopathfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <boost/algorithm/string/split.hpp>
#include <boost/algorithm/string/classification.hpp>
#include <cmath>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".features.distancetopathfeature");

namespace search::features {

const feature_t DistanceToPathExecutor::DEFAULT_DISTANCE(6400000000.0);

DistanceToPathExecutor::DistanceToPathExecutor(std::vector<Vector2> &path,
                                               const search::attribute::IAttributeVector *pos) :
    search::fef::FeatureExecutor(),
    _intBuf(),
    _path(),
    _pos(pos)
{
    if (_pos != NULL) {
        _intBuf.allocate(_pos->getMaxValueCount());
    }
    _path.swap(path); // avoid copy
}

void
DistanceToPathExecutor::execute(uint32_t docId)
{
    if (_path.size() > 1 && _pos != NULL) {
        double pos = -1, trip = 0, product = 0;
        double minSqDist = std::numeric_limits<double>::max();
        _intBuf.fill(*_pos, docId);

        // For each line segment, do
        for (uint32_t seg = 1; seg < _path.size(); ++seg) {
            const Vector2 &p1 = _path[seg - 1];
            const Vector2 &p2 = _path[seg];
            double len2 = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
            double len = std::sqrt(len2);

            // For each document location, do
            for (uint32_t loc = 0; loc < _intBuf.size(); ++loc) {
                int32_t x = 0, y = 0;
                vespalib::geo::ZCurve::decode(_intBuf[loc], &x, &y);

                double u = 0, dx, dy;
                if (len < 1e-6) {
                    dx = p1.x - x; // process as point
                    dy = p1.y - y;
                } else {
                    u = std::min(1.0, std::max(0.0, (((x - p1.x) * (p2.x - p1.x)) + ((y - p1.y) * (p2.y - p1.y))) / len2));
                    if (u == 0) {
                        dx = p1.x - x; // intersection before segment
                        dy = p1.y - y;
                    } else if (u == 1) {
                        dx = p2.x - x; // intersection after segment
                        dy = p2.y - y;
                    } else {
                        dx = p1.x + u * (p2.x - p1.x) - x;
                        dy = p1.y + u * (p2.y - p1.y) - y;
                    }
                }

                double sqDist = dx * dx + dy * dy;
                if (sqDist < minSqDist) {
                    minSqDist = sqDist;
                    pos = trip + u * len;
                    product = (p2.x - p1.x) * dy - (p2.y - p1.y) * dx;
                }
            }
            trip += len;
        }

        outputs().set_number(0, static_cast<feature_t>(std::sqrt(static_cast<feature_t>(minSqDist))));
        outputs().set_number(1, static_cast<feature_t>(pos > -1 ? (trip > 0 ? pos / trip : 0) : 1));
        outputs().set_number(2, static_cast<feature_t>(product));
    } else {
        outputs().set_number(0, DEFAULT_DISTANCE);
        outputs().set_number(1, 1);
        outputs().set_number(2, 0);
    }
}

DistanceToPathBlueprint::DistanceToPathBlueprint() :
    Blueprint("distanceToPath"),
    _posAttr()
{
}

DistanceToPathBlueprint::~DistanceToPathBlueprint() = default;

void
DistanceToPathBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                           search::fef::IDumpFeatureVisitor &) const
{
}

search::fef::Blueprint::UP
DistanceToPathBlueprint::createInstance() const
{
    return Blueprint::UP(new DistanceToPathBlueprint());
}

bool
DistanceToPathBlueprint::setup(const search::fef::IIndexEnvironment & env,
                               const search::fef::ParameterList & params)
{
    _posAttr = params[0].getValue();
    describeOutput("distance", "The euclidian distance from the query path.");
    describeOutput("traveled", "The normalized distance traveled along the path before intersection.");
    describeOutput("product",  "The cross-product of the intersecting line segment and the intersection-to-document vector.");
    env.hintAttributeAccess(_posAttr);
    env.hintAttributeAccess(document::PositionDataType::getZCurveFieldName(_posAttr));
    return true;
}

search::fef::FeatureExecutor &
DistanceToPathBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    // Retrieve path from query using the name of this and "path" as property.
    std::vector<Vector2> path;
    search::fef::Property pro = env.getProperties().lookup(getName(), "path");
    if (pro.found()) {
        vespalib::string str = pro.getAt(0);
        uint32_t len = str.size();
        if (str[0] == '(' && len > 1 && str[len - 1] == ')') {
            str = str.substr(1, len - 1); // remove braces
            std::vector<vespalib::string> arr;
            boost::split(arr, str, boost::is_any_of(","));
            len = arr.size() - 1;
            for (uint32_t i = 0; i < len; i += 2) {
                double x = util::strToNum<double>(arr[i]);
                double y = util::strToNum<double>(arr[i + 1]);
                path.push_back(Vector2(x, y));
            }
        }
    }

    // Lookup the attribute vector that holds document positions.
    const search::attribute::IAttributeVector *pos = NULL;
    if (path.size() > 1) {
        pos = env.getAttributeContext().getAttribute(_posAttr);
        if (pos == NULL) {
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
    } else {
        LOG(warning, "No path given in query. Will use default distance.");
    }

    // Create and return a compatible executor.
    return stash.create<DistanceToPathExecutor>(path, pos);
}

}
