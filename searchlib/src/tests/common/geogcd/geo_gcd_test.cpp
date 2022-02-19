// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::common;

struct Point {
    const char *name;
    double lat;
    double lng;
};

constexpr size_t NUM = 9;

const Point airports[NUM] = {
    { "SFO", 37.61, -122.38},
    { "LHR", 51.47, -0.46},
    { "OSL", 60.20, 11.08},
    { "GIG", -22.8, -43.25},
    { "HKG", 22.31, 113.91},
    { "TRD", 63.45, 10.92},
    { "SYD", -33.95, 151.17},
    { "LAX", 33.94, -118.41},
    { "JFK", 40.64, -73.78},
};

const double exact_distances[NUM][NUM] = {
    { 0, 5367, 5196, 6604, 6927, 5012, 7417, 337, 2586 },
    { 5367, 0, 750, 5734, 5994, 928, 10573, 5456, 3451 },
    { 5196, 750, 0, 6479, 5319, 226, 9888, 5345, 3687 },
    { 6604, 5734, 6479, 0, 10989, 6623, 8414, 6294, 4786 },
    { 6927, 5994, 5319, 10989, 0, 5240, 4581, 7260, 8072 },
    { 5012, 928, 226, 6623, 5240, 0, 9782, 5171, 3611 },
    { 7417, 10573, 9888, 8414, 4581, 9782, 0, 7488, 9950 },
    { 337, 5456, 5345, 6294, 7260, 5171, 7488, 0, 2475 },
    { 2586, 3451, 3687, 4786, 8072, 3611, 9950, 2475, 0 }
};

TEST(GeoGcdTest, computed_distances_seem_legit) {
    for (size_t i = 0; i < NUM; ++i) {
        const Point &from = airports[i];
        printf("\n");
        GeoGcd geo_from{from.lat, from.lng};
        for (size_t j = 0; j < NUM; ++j) {
            const Point &to = airports[j];
            double km = geo_from.km_great_circle_distance(to.lat, to.lng);
            double miles = km / 1.609344;
            EXPECT_GE(miles, 0);
            if (from.name == to.name) {
                EXPECT_NEAR(miles, 0.0, 1e-9); // EXPECT_DOUBLE_EQ does not work on arm64 for some reason
            } else {
                double exact = exact_distances[i][j];
                printf("Distance from %s to %s (in miles): %.1f [more exact would be %.1f]\n", from.name, to.name, miles, exact);
                EXPECT_LT(miles*0.99, exact);
                EXPECT_GT(miles*1.01, exact);
            }
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
