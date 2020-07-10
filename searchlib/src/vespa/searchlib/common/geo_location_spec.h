// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <cstdint>

namespace search::common {

class GeoLocationSpec
{
public:
    GeoLocationSpec();

    bool isValid()        const { return _valid; }
    bool hasPoint()       const { return _hasPoint; }
    bool hasBoundingBox() const { return _hasBoundingBox; }

    uint32_t getXAspect()          const { return _x_aspect; }
    int32_t getX()                 const { return _x; }
    int32_t getY()                 const { return _y; }
    uint32_t getRadius()           const { return _radius; }
    const char * getParseError()   const { return _parseError; }

    int32_t getMinX() const { return _min_x; }
    int32_t getMinY() const { return _min_y; }
    int32_t getMaxX() const { return _max_x; }
    int32_t getMaxY() const { return _max_y; }

    bool parseOldFormat(const std::string &locStr);

    std::string getLocationString() const;

private:
    bool _valid;
    bool _hasPoint;
    bool _hasRadius;
    bool _hasBoundingBox;

    int32_t  _x;         /* Query X position */
    int32_t  _y;         /* Query Y position */
    uint32_t _x_aspect;  /* X distance multiplier fraction */
    uint32_t _radius;    /* Radius for euclidean distance */
    int32_t  _min_x;     /* Min X coordinate */
    int32_t  _max_x;     /* Max X coordinate */
    int32_t  _min_y;     /* Min Y coordinate */
    int32_t  _max_y;     /* Max Y coordinate */

    const char *_parseError;
    bool getDimensionality(const char * &p);
};

} // namespace
