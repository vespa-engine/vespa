// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_location.h"

using vespalib::geo::ZCurve;

namespace search::common {

namespace {

uint64_t abs_diff(int32_t a, int32_t b) {
    return (a > b)
        ? (int64_t(a) - int64_t(b))
        : (int64_t(b) - int64_t(a));
}

ZCurve::BoundingBox to_z(GeoLocation::Box box) {
    return ZCurve::BoundingBox(box.x.low, box.x.high,
                               box.y.low, box.y.high);
}

GeoLocation::Box
adjust_bounding_box(GeoLocation::Box orig, GeoLocation::Point point, uint32_t radius, GeoLocation::Aspect x_aspect)
{
    if (radius == GeoLocation::radius_inf) {
        // only happens if GeoLocation is explicitly constructed with "infinite" radius
        return orig;
    }
    uint32_t maxdx = radius;
    if (x_aspect.active()) {
        // x_aspect is a 32-bit fixed-point number in range [0,1]
        // so this implements maxdx = ceil(radius/x_aspect)
        uint64_t maxdx2 = ((static_cast<uint64_t>(radius) << 32) + 0xffffffffu) / x_aspect.multiplier;
        if (maxdx2 >= 0xffffffffu) {
            maxdx = 0xffffffffu;
        } else {
            maxdx = static_cast<uint32_t>(maxdx2);
        }
    }
    // implied limits from radius and point:
    int64_t implied_max_x = int64_t(point.x) + int64_t(maxdx);
    int64_t implied_min_x = int64_t(point.x) - int64_t(maxdx);

    int64_t implied_max_y = int64_t(point.y) + int64_t(radius);
    int64_t implied_min_y = int64_t(point.y) - int64_t(radius);

    int32_t max_x = orig.x.high;
    int32_t min_x = orig.x.low;

    int32_t max_y = orig.y.high;
    int32_t min_y = orig.y.low;

    if (implied_max_x < max_x) max_x = implied_max_x;
    if (implied_min_x > min_x) min_x = implied_min_x;

    if (implied_max_y < max_y) max_y = implied_max_y;
    if (implied_min_y > min_y) min_y = implied_min_y;

    return GeoLocation::Box{GeoLocation::Range{min_x, max_x},
                            GeoLocation::Range{min_y, max_y}};
}

} // namespace <unnamed>

GeoLocation::GeoLocation()
  : has_point(false),
    point{0, 0},
    radius(radius_inf),
    x_aspect(),
    bounding_box(no_box),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(no_box))
{}

GeoLocation::GeoLocation(Point p)
  : has_point(true),
    point(p),
    radius(radius_inf),
    x_aspect(),
    bounding_box(no_box),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(no_box))
{}

GeoLocation::GeoLocation(Point p, Aspect xa)
  : has_point(true),
    point(p),
    radius(radius_inf),
    x_aspect(xa),
    bounding_box(no_box),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(no_box))
{}

GeoLocation::GeoLocation(Point p, uint32_t r)
  : has_point(true),
    point(p),
    radius(r),
    x_aspect(),
    bounding_box(adjust_bounding_box(no_box, p, r, Aspect())),
    _sq_radius(uint64_t(r) * uint64_t(r)),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Point p, uint32_t r, Aspect xa)
  : has_point(true),
    point(p),
    radius(r),
    x_aspect(xa),
    bounding_box(adjust_bounding_box(no_box, p, r, xa)),
    _sq_radius(uint64_t(r) * uint64_t(r)),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Box b)
  : has_point(false),
    point{0, 0},
    radius(radius_inf),
    x_aspect(),
    bounding_box(b),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Box b, Point p)
  : has_point(true),
    point(p),
    radius(radius_inf),
    x_aspect(),
    bounding_box(b),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Box b, Point p, Aspect xa)
  : has_point(true),
    point(p),
    radius(radius_inf),
    x_aspect(xa),
    bounding_box(b),
    _sq_radius(sq_radius_inf),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Box b, Point p, uint32_t r)
  : has_point(true),
    point(p),
    radius(r),
    x_aspect(),
    bounding_box(adjust_bounding_box(b, p, r, Aspect())),
    _sq_radius(uint64_t(r) * uint64_t(r)),
    _z_bounding_box(to_z(bounding_box))
{}

GeoLocation::GeoLocation(Box b, Point p, uint32_t r, Aspect xa)
  : has_point(true),
    point(p),
    radius(r),
    x_aspect(xa),
    bounding_box(adjust_bounding_box(b, p, r, xa)),
    _sq_radius(uint64_t(r) * uint64_t(r)),
    _z_bounding_box(to_z(bounding_box))
{}

uint64_t GeoLocation::sq_distance_to(Point p) const {
    if (has_point) {
        uint64_t dx = abs_diff(p.x, point.x);
        if (x_aspect.active()) {
            // x_aspect is a 32-bit fixed-point number in range [0,1]
            // this implements dx = (dx * x_aspect)
            dx = (dx * x_aspect.multiplier) >> 32;
        }
        uint64_t dy = abs_diff(p.y, point.y);
        return dx*dx + dy*dy;
    }
    return 0;
}

bool GeoLocation::inside_limit(Point p) const {
    if (p.x < bounding_box.x.low) return false;
    if (p.x > bounding_box.x.high) return false;

    if (p.y < bounding_box.y.low) return false;
    if (p.y > bounding_box.y.high) return false;

    uint64_t sq_dist = sq_distance_to(p);
    return sq_dist <= _sq_radius;
}

} // namespace search::common
