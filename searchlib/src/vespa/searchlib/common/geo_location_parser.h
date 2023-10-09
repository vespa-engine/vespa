// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <cstdint>
#include "geo_location.h"
#include "geo_location_spec.h"

namespace search::common {

/**
 * Parser for a geo-location string representation.
 **/
class GeoLocationParser
{
public:
    GeoLocationParser();

    bool parseNoField(const std::string &locStr);
    bool parseWithField(const std::string &locStr);

    std::string getFieldName() const { return _field_name; }
    GeoLocation getGeoLocation() const;

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
    bool correctDimensionalitySkip(const char * &p);
    bool parseJsonFormat(const std::string &locStr);
    bool parseOldFormat(const std::string &locStr);
};

} // namespace
