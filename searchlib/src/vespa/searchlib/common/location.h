// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentlocations.h"
#include <vespa/vespalib/geo/zcurve.h>

#include <vespa/vespalib/stllike/string.h>

namespace search::common {

class Location : public DocumentLocations
{
private:
    static int getInt(const char **pp);
    bool getDimensionality(const char **pp);

public:
    Location();
    bool getRankOnDistance()       const { return _rankOnDistance; }
    bool getPruneOnDistance()      const { return _pruneOnDistance; }
    uint32_t getXAspect()          const { return _xAspect; }
    int32_t getX()                 const { return _x; }
    int32_t getY()                 const { return _y; }
    uint32_t getRadius()           const { return _radius; }
    const char * getParseError()   const { return _parseError; }
    int32_t getMinX() const { return _minx; }
    int32_t getMinY() const { return _miny; }
    int32_t getMaxX() const { return _maxx; }
    int32_t getMaxY() const { return _maxy; }
    bool getzFailBoundingBoxTest(int64_t docxy) const {
        return _zBoundingBox.getzFailBoundingBoxTest(docxy);
    }

    bool parse(const vespalib::string &locStr);

private:
    vespalib::geo::ZCurve::BoundingBox _zBoundingBox;
    int32_t  _x;        /* Query X position */
    int32_t  _y;        /* Query Y position */
    uint32_t _xAspect;      /* X distance multiplier fraction */
    uint32_t _radius;       /* Radius for euclidian distance */
    int32_t  _minx;     /* Min X coordinate */
    int32_t  _maxx;     /* Max X coordinate */
    int32_t  _miny;     /* Min Y coordinate */
    int32_t  _maxy;     /* Max Y coordinate */

    bool _rankOnDistance;
    bool _pruneOnDistance;
    const char *_parseError;
};

}
