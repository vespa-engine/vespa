// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <cstdint>
#include <limits>
#include <vespa/vespalib/geo/zcurve.h>

namespace search::common {

/**
 * An immutable struct for a (geo) location.
 * Contains a point with optional radius, a bounding box, or both.
 **/
struct GeoLocation
{
    // contained structs and helper constants:
    static constexpr int32_t range_low = std::numeric_limits<int32_t>::min();
    static constexpr int32_t range_high = std::numeric_limits<int32_t>::max();
    static constexpr uint32_t radius_inf = std::numeric_limits<uint32_t>::max();
    struct Point {
        Point(int32_t x_in, int32_t y_in) : x(x_in), y(y_in) {}
        const int32_t x;
        const int32_t y;
        Point() = delete;
    };
    struct Aspect {
        uint32_t multiplier;
        Aspect() : multiplier(0) {}
        Aspect(uint32_t multiplier_in) : multiplier(multiplier_in) {}
        // for unit tests:
        Aspect(double multiplier_in) : multiplier(multiplier_in*4294967296.0) {}
        bool active() const { return multiplier != 0; }
    };
    struct Range {
        const int32_t low;
        const int32_t high;
        bool active() const {
            return (low != range_low) || (high != range_high);
        }
    };
    static constexpr Range no_range = {range_low, range_high};
    struct Box {
        const Range x;
        const Range y;
        bool active() const { return x.active() || y.active(); }
    };
    static constexpr Box no_box = {no_range, no_range};

    // actual content of struct:
    const bool has_point;
    Point point;
    uint32_t radius;
    Aspect x_aspect;
    Box bounding_box;
    GeoLocation();

    // constructors:
    GeoLocation(Point p);
    GeoLocation(Point p, Aspect xa);
    GeoLocation(Point p, uint32_t r);
    GeoLocation(Point p, uint32_t r, Aspect xa);
    GeoLocation(Box b);
    GeoLocation(Box b, Point p);
    GeoLocation(Box b, Point p, Aspect xa);
    GeoLocation(Box b, Point p, uint32_t r);
    GeoLocation(Box b, Point p, uint32_t r, Aspect xa);

    // helper methods:
    bool has_radius() const { return radius != radius_inf; }
    bool valid() const { return has_point || bounding_box.active(); }
    bool can_limit() const { return bounding_box.active(); }

    uint64_t sq_distance_to(Point p) const;
    bool inside_limit(Point p) const;

    bool inside_limit(int64_t zcurve_encoded_xy) const {
        if (_z_bounding_box.getzFailBoundingBoxTest(zcurve_encoded_xy)) return false;
        int32_t x = 0;
        int32_t y = 0;
        vespalib::geo::ZCurve::decode(zcurve_encoded_xy, &x, &y);
        return inside_limit(Point(x, y));
    }

private:
    // constants for implementation of helper methods:
    static constexpr uint64_t sq_radius_inf = std::numeric_limits<uint64_t>::max();
    const uint64_t _sq_radius;
    const vespalib::geo::ZCurve::BoundingBox _z_bounding_box;
};

} // namespace
