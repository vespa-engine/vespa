// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <cstdint>
#include "geo_location.h"
#include "geo_location_spec.h"

namespace search::common {

class GeoLocationParser
{
public:
    GeoLocationParser();

    bool isValid()        const { return _valid; }
    bool hasPoint()       const { return _has_point; }
    bool hasBoundingBox() const { return _has_bounding_box; }
    bool hasFieldName()   const { return ! _field_name.empty(); }

    uint32_t getXAspect() const { return _x_aspect; }
    int32_t getX()        const { return _x; }
    int32_t getY()        const { return _y; }
    uint32_t getRadius()  const { return _radius; }

    int32_t getMinX() const { return _min_x; }
    int32_t getMinY() const { return _min_y; }
    int32_t getMaxX() const { return _max_x; }
    int32_t getMaxY() const { return _max_y; }

    std::string getOldFormatLocationString() const;
    std::string getOldFormatLocationStringWithField() const;
    std::string getFieldName() const { return _field_name; }

    bool parseOldFormat(const std::string &locStr);
    void setFieldName(const std::string &name) { _field_name = name; }
    bool parseOldFormatWithField(const std::string &str);
    GeoLocation getGeoLocation() const;
#if 0
    GeoLocationSpec spec() const {
        return GeoLocationSpec{_field_name, getGeoLocation()};
    }
#endif
    const char * getParseError() const { return _parseError; }

private:
    bool _valid;
    bool _has_point;
    bool _has_bounding_box;

    std::string _field_name;

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
