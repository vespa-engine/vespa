// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_gcd.h"

namespace search::common {

namespace {

// in km, as defined by IUGG, see:
// https://en.wikipedia.org/wiki/Earth_radius#Mean_radius
static constexpr double earth_mean_radius = 6371.0088;

static constexpr double degrees_to_radians = M_PI / 180.0;

// with input in radians
double greatCircleDistance(double theta_A, double phi_A,
                           double theta_B, double phi_B)
{
    // convert to radians:
    double theta_diff = theta_A - theta_B;
    double phi_diff = phi_A - phi_B;
    // haversines of differences:
    double hav_theta = GeoGcd::haversine(theta_diff);
    double hav_phi   = GeoGcd::haversine(phi_diff);
    // haversine of central angle between the two points:
    double hav_central_angle = hav_theta + cos(theta_A)*cos(theta_B)*hav_phi;
    // sine of half the central angle:
    double half_sine_diff = sqrt(hav_central_angle);
    // distance in kilometers:
    double d = 2 * asin(half_sine_diff) * earth_mean_radius;
    return d;
}

}

GeoGcd::GeoGcd(double lat, double lng)
    : _latitude_radians(lat * degrees_to_radians),
      _longitude_radians(lng * degrees_to_radians)
{}


double GeoGcd::km_great_circle_distance(double lat, double lng) const {
    double theta_A = _latitude_radians;
    double phi_A   = _longitude_radians;
    double theta_B = lat * degrees_to_radians;
    double phi_B   = lng * degrees_to_radians;
    return  greatCircleDistance(theta_A, phi_A, theta_B, phi_B);
}

} // namespace search::common
