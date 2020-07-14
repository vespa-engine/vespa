// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/searchlib/attribute/attributeguard.h>

using search::common::Location;
using search::common::GeoLocationParser;

Location parse(const char *str) {
    GeoLocationParser parser;
    if (!EXPECT_TRUE(parser.parseOldFormat(str))) {
        fprintf(stderr, "  parse error: %s\n", parser.getParseError());
    }
    return Location(parser.getGeoLocation());
}

TEST("require that bounding boxes can be parsed") {
    Location loc = parse("[2,10,20,30,40]");
    EXPECT_EQUAL(false, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.x_aspect.multiplier);
    EXPECT_EQUAL(0, loc.point.x);
    EXPECT_EQUAL(0, loc.point.y);
    EXPECT_EQUAL(std::numeric_limits<uint32_t>::max(), loc.radius);
    EXPECT_EQUAL(10, loc.bounding_box.x.lo);
    EXPECT_EQUAL(20, loc.bounding_box.y.lo);
    EXPECT_EQUAL(30, loc.bounding_box.x.hi);
    EXPECT_EQUAL(40, loc.bounding_box.y.hi);
}

TEST("require that circles can be parsed") {
    Location loc = parse("(2,10,20,5,0,0,0)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.x_aspect.multiplier);
    EXPECT_EQUAL(10, loc.point.x);
    EXPECT_EQUAL(20, loc.point.y);
    EXPECT_EQUAL(5u, loc.radius);
    EXPECT_EQUAL(5, loc.bounding_box.x.lo);
    EXPECT_EQUAL(15, loc.bounding_box.y.lo);
    EXPECT_EQUAL(15, loc.bounding_box.x.hi);
    EXPECT_EQUAL(25, loc.bounding_box.y.hi);    
}

TEST("require that circles can have aspect ratio") {
    Location loc = parse("(2,10,20,5,0,0,0,2147483648)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(2147483648u, loc.x_aspect.multiplier);
    EXPECT_EQUAL(10, loc.point.x);
    EXPECT_EQUAL(20, loc.point.y);
    EXPECT_EQUAL(5u, loc.radius);
    EXPECT_EQUAL(-1, loc.bounding_box.x.lo);
    EXPECT_EQUAL(15, loc.bounding_box.y.lo);
    EXPECT_EQUAL(21, loc.bounding_box.x.hi);
    EXPECT_EQUAL(25, loc.bounding_box.y.hi);
}

TEST("require that bounding box can be specified after circle") {
    Location loc = parse("(2,10,20,5,0,0,0)[2,10,20,30,40]");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.x_aspect.multiplier);
    EXPECT_EQUAL(10, loc.point.x);
    EXPECT_EQUAL(20, loc.point.y);
    EXPECT_EQUAL(5u, loc.radius);
    EXPECT_EQUAL(10, loc.bounding_box.x.lo);
    EXPECT_EQUAL(20, loc.bounding_box.y.lo);
    EXPECT_EQUAL(15, loc.bounding_box.x.hi);
    EXPECT_EQUAL(25, loc.bounding_box.y.hi);
}

TEST("require that circles can be specified after bounding box") {
    Location loc = parse("[2,10,20,30,40](2,10,20,5,0,0,0)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.x_aspect.multiplier);
    EXPECT_EQUAL(10, loc.point.x);
    EXPECT_EQUAL(20, loc.point.y);
    EXPECT_EQUAL(5u, loc.radius);
    EXPECT_EQUAL(10, loc.bounding_box.x.lo);
    EXPECT_EQUAL(20, loc.bounding_box.y.lo);
    EXPECT_EQUAL(15, loc.bounding_box.x.hi);
    EXPECT_EQUAL(25, loc.bounding_box.y.hi);    
}

TEST("require that santa search gives non-wrapped bounding box") {
    Location loc = parse("(2,122163600,89998536,290112,4,2000,0,109704)");
    EXPECT_GREATER_EQUAL(loc.bounding_box.x.hi, loc.bounding_box.x.lo);
    EXPECT_GREATER_EQUAL(loc.bounding_box.y.hi, loc.bounding_box.y.lo);
}

TEST_MAIN() { TEST_RUN_ALL(); }
