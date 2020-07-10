// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_location_spec.h"
#include <limits>
#include <vespa/vespalib/stllike/asciistream.h>

namespace {

int getInt(const char * &p) {
    int val;
    bool isminus;
    val = 0;
    isminus = false;
    if (*p == '-') {
        isminus = true;
        p++;
    }
    while (*p >= '0' && *p <= '9') {
        val *= 10;
        val += (*p++ - '0');
    }
    return isminus ? - val : val;
}

} // namespace <unnamed>

namespace search::common {

GeoLocationSpec::GeoLocationSpec() :
      _valid(false),
      _has_point(false),
      _has_bounding_box(false),
      _field_name(),
      _x(0),
      _y(0),
      _x_aspect(0u),
      _radius(std::numeric_limits<uint32_t>::max()),
      _min_x(std::numeric_limits<int32_t>::min()),
      _max_x(std::numeric_limits<int32_t>::max()),
      _min_y(std::numeric_limits<int32_t>::min()),
      _max_y(std::numeric_limits<int32_t>::max())
{}

GeoLocationSpec::GeoLocationSpec(const GeoLocationSpec &other) = default;
GeoLocationSpec& GeoLocationSpec::operator=(const GeoLocationSpec &other) = default;

std::string
GeoLocationSpec::getOldFormatLocationString() const
{
    vespalib::asciistream loc;
    if (hasPoint()) {
        loc << "(2"  // dimensionality
            << "," << _x
            << "," << _y
            << "," << _radius
            << "," << "0"  // table id.
            << "," << "1"  // rank multiplier.
            << "," << "0" // rank only on distance.
            << "," << _x_aspect // aspect multiplier
            << ")";
    }
    if (hasBoundingBox()) {
        loc << "[2," << _min_x
            << "," << _min_y
            << "," << _max_x
            << "," << _max_y
            << "]" ;
    }
    return loc.str();
}

std::string
GeoLocationSpec::getOldFormatLocationStringWithField() const
{
    if (hasFieldName()) {
        return getFieldName() + ":" + getOldFormatLocationString();
    } else {
        return getOldFormatLocationString();
    }
}

void
GeoLocationSpec::adjust_bounding_box()
{
    if (hasPoint() && (_radius != std::numeric_limits<uint32_t>::max())) {
        uint32_t maxdx = _radius;
        if (_x_aspect != 0) {
            uint64_t maxdx2 = ((static_cast<uint64_t>(_radius) << 32) + 0xffffffffu) /
                              _x_aspect;
            if (maxdx2 >= 0xffffffffu)
                maxdx = 0xffffffffu;
            else
                maxdx = static_cast<uint32_t>(maxdx2);
        }
        int64_t implied_max_x = int64_t(_x) + int64_t(maxdx);
        int64_t implied_min_x = int64_t(_x) - int64_t(maxdx);

        int64_t implied_max_y = int64_t(_y) + int64_t(_radius);
        int64_t implied_min_y = int64_t(_y) - int64_t(_radius);

        if (implied_max_x < _max_x) _max_x = implied_max_x;
        if (implied_min_x > _min_x) _min_x = implied_min_x;

        if (implied_max_y < _max_y) _max_y = implied_max_y;
        if (implied_min_y > _min_y) _min_y = implied_min_y;
    }
    if ((_min_x != std::numeric_limits<int32_t>::min()) ||
        (_max_x != std::numeric_limits<int32_t>::max()) ||
        (_min_y != std::numeric_limits<int32_t>::min()) ||
        (_max_y != std::numeric_limits<int32_t>::max()))
    {
        _has_bounding_box = true;
    }
}



GeoLocationParser::GeoLocationParser()
  : GeoLocationSpec(), _parseError(NULL)
{}

bool
GeoLocationParser::getDimensionality(const char * &p) {
    if (*p == '2') {
        p++;
        if (*p != ',') {
            _parseError = "Missing comma after 2D dimensionality";
            return false;
        }
        p++;
        return true;
    }
    _parseError = "Bad dimensionality spec, not 2D";
    return false;
}

bool
GeoLocationParser::parseOldFormatWithField(const std::string &str)
{
     auto sep = str.find(':');
     if (sep == std::string::npos) {
         _parseError = "Location string lacks field specification.";
         return false;
     }
     _field_name = str.substr(0, sep);
     std::string only_loc = str.substr(sep + 1);
     return parseOldFormat(only_loc);
}

bool
GeoLocationParser::parseOldFormat(const std::string &locStr)
{
    bool foundBoundingBox = false;
    bool foundLoc = false;
    const char *p = locStr.c_str();
    while (*p != '\0') {
        if (*p == '[') {
            p++;
            if (foundBoundingBox) {
                _parseError = "Duplicate bounding box";
                return false;
            }
            foundBoundingBox = true;
            if (!getDimensionality(p))
                return false;
            _min_x = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after minx";
                return false;
            }
            p++;
            _min_y = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after miny";
                return false;
            }
            p++;
            _max_x = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after maxx";
                return false;
            }
            p++;
            _max_y = getInt(p);
            if (*p != ']') {
                _parseError = "Missing ']' after maxy";
                return false;
            }
            p++;
        } else if (*p == '(') {
            p++;
            if (foundLoc) {
                _parseError = "Duplicate location";
                return false;
            }
            foundLoc = true;
            if (!getDimensionality(p))
                return false;
            _x = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after x position";
                return false;
            }
            p++;
            _y = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after y position";
                return false;
            }
            p++;
            _radius = getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after radius";
                return false;
            }
            p++;
            /* _tableID = */ (void) getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after tableID";
                return false;
            }
            p++;
            /* _rankMultiplier = */ (void) getInt(p);
            if (*p != ',') {
                _parseError = "Missing ',' after rank multiplier";
                return false;
            }
            p++;
            /* _rankOnlyOnDistance = */ (void) getInt(p);
            if (*p == ',') {
                p++;
                _x_aspect = getInt(p);
                if (*p != ')') {
                    _parseError = "Missing ')' after xAspect";
                    return false;
                }
            } else {
                if (*p != ')') {
                    _parseError = "Missing ')' after rankOnlyOnDistance flag";
                    return false;
                }
            }
            p++;
        } else if (*p == ' ') {
            p++;
        } else {
            _parseError = "Unexpected char in location spec";
            return false;
        }
    }
    _has_point = foundLoc;
    _has_bounding_box = foundBoundingBox;
    _valid = (_has_point || _has_bounding_box);
    adjust_bounding_box();
    return _valid;
}

} // namespace
