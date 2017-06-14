// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include "location.h"

namespace search::common {

Location::Location() :
      _zBoundingBox(0,0,0,0),
      _x(0),
      _y(0),
      _xAspect(0u),
      _radius(std::numeric_limits<uint32_t>::max()),
      _minx(std::numeric_limits<int32_t>::min()),
      _maxx(std::numeric_limits<int32_t>::max()),
      _miny(std::numeric_limits<int32_t>::min()),
      _maxy(std::numeric_limits<int32_t>::max()),
      _rankOnDistance(false),
      _pruneOnDistance(false),
      _parseError(NULL)
{
}


bool
Location::getDimensionality(const char **pp)
{
    if (**pp == '2') {
        (*pp)++;
        if (**pp != ',') {
            _parseError = "Missing comma after 2D dimensionality";
            return false;
        }
        (*pp)++;
        return true;
    }
    _parseError = "Bad dimensionality spec, not 2D";
    return false;
}


int
Location::getInt(const char **pp)
{
    const char *p = *pp;
    int val;
    bool isminus;

    val = 0;
    isminus = false;
    if (*p == '-') {
        isminus = true;
        p++;
    }
    while (*p >= '0' && *p <= '9')
        val = val * 10 + *p++ - '0';
    *pp = p;
    return isminus ? - val : val;
}

bool Location::parse(const vespalib::string &locStr)
{
    bool hadCutoff = false;
    bool hadLoc = false;
    const char *p = locStr.c_str();
    while (*p != '\0') {
        if (*p == '[') {
            p++;
            if (hadCutoff) {
                _parseError = "Duplicate square cutoff";
                return false;
            }
            hadCutoff = true;
            if (!getDimensionality(&p))
                return false;
            _minx = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after minx";
                return false;
            }
            p++;
            _miny = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after miny";
                return false;
            }
            p++;
            _maxx = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after maxx";
                return false;
            }
            p++;
            _maxy = getInt(&p);
            if (*p != ']') {
                _parseError = "Missing ']' after maxy";
                return false;
            }
            p++;
        } else if (*p == '(') {
            p++;
            if (hadLoc) {
                _parseError = "Duplicate location";
                return false;
            }
            hadLoc = true;
            if (!getDimensionality(&p))
                return false;
            _x = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after x position";
                return false;
            }
            p++;
            _y = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after y position";
                return false;
            }
            p++;
            _radius = getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after radius";
                return false;
            }
            p++;
            /* _tableID = */ (void) getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after tableID";
                return false;
            }
            p++;
            /* _rankMultiplier = */ (void) getInt(&p);
            if (*p != ',') {
                _parseError = "Missing ',' after rank multiplier";
                return false;
            }
            p++;
            /* _rankOnlyOnDistance = */ (void) (getInt(&p) != 0);
            if (*p == ',') {
                p++;
                _xAspect = getInt(&p);
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
        } else if (*p == ' ')
            p++;
        else {
            _parseError = "Unexpected char in location spec";
            return false;
        }
    }

    if (hadLoc) {
        _rankOnDistance = true;
        uint32_t maxdx = _radius;
        if (_xAspect != 0) {
            uint64_t maxdx2 = ((static_cast<uint64_t>(_radius) << 32) + 0xffffffffu) /
                              _xAspect;
            if (maxdx2 >= 0xffffffffu)
                maxdx = 0xffffffffu;
            else
                maxdx = static_cast<uint32_t>(maxdx2);
        }
        if (static_cast<int32_t>(_x - maxdx) > _minx &&
            static_cast<int64_t>(_x) - static_cast<int64_t>(maxdx) >
            static_cast<int64_t>(_minx))
            _minx = _x - maxdx;
        if (static_cast<int32_t>(_x + maxdx) < _maxx &&
            static_cast<int64_t>(_x) + static_cast<int64_t>(maxdx) <
            static_cast<int64_t>(_maxx))
            _maxx = _x + maxdx;
        if (static_cast<int32_t>(_y - _radius) > _miny &&
            static_cast<int64_t>(_y) - static_cast<int64_t>(_radius) >
            static_cast<int64_t>(_miny))
            _miny = _y - _radius;
        if (static_cast<int32_t>(_y + _radius) < _maxy &&
            static_cast<int64_t>(_y) + static_cast<int64_t>(_radius) <
            static_cast<int64_t>(_maxy))
            _maxy = _y + _radius;
    }
    if (_minx != std::numeric_limits<int32_t>::min() ||
        _maxx != std::numeric_limits<int32_t>::max() ||
        _miny != std::numeric_limits<int32_t>::min() ||
        _maxy != std::numeric_limits<int32_t>::max())
    {
        _pruneOnDistance = true;
    }
    _zBoundingBox = vespalib::geo::ZCurve::BoundingBox(_minx, _maxx, _miny, _maxy);

    return true;
}

}
