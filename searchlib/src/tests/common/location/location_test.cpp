// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/attribute/attributeguard.h>


using search::common::Location;

bool is_parseable(const char *str) {
    Location loc;
    return loc.parse(str);
}

Location parse(const char *str) {
    Location loc;
    if (!EXPECT_TRUE(loc.parse(str))) {
        fprintf(stderr, "  parse error: %s\n", loc.getParseError());
    }
    return loc;
}

TEST("require that malformed bounding boxes are not parseable") {
    EXPECT_TRUE(is_parseable("[2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40][2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[1,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[3,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2, 10, 20, 30, 40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40"));
    EXPECT_FALSE(is_parseable("[2,10,20,30]"));
    EXPECT_FALSE(is_parseable("[10,20,30,40]"));
}

TEST("require that malformed circles are not parseable") {
    EXPECT_TRUE(is_parseable("(2,10,20,5,0,0,0)"));
    EXPECT_FALSE(is_parseable("(2,10,20,5,0,0,0)(2,10,20,5,0,0,0)"));
    EXPECT_FALSE(is_parseable("(1,10,20,5,0,0,0)"));
    EXPECT_FALSE(is_parseable("(3,10,20,5,0,0,0)"));
    EXPECT_FALSE(is_parseable("(2, 10, 20, 5, 0, 0, 0)"));
    EXPECT_FALSE(is_parseable("(2,10,20,5)"));
    EXPECT_FALSE(is_parseable("(2,10,20,5,0,0,0"));
    EXPECT_FALSE(is_parseable("(2,10,20,5,0,0,0,1000"));
    EXPECT_FALSE(is_parseable("(10,20,5)"));
}

TEST("require that bounding boxes can be parsed") {
    Location loc = parse("[2,10,20,30,40]");
    EXPECT_EQUAL(false, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.getXAspect());
    EXPECT_EQUAL(0, loc.getX());
    EXPECT_EQUAL(0, loc.getY());
    EXPECT_EQUAL(std::numeric_limits<uint32_t>::max(), loc.getRadius());
    EXPECT_EQUAL(10, loc.getMinX());
    EXPECT_EQUAL(20, loc.getMinY());
    EXPECT_EQUAL(30, loc.getMaxX());
    EXPECT_EQUAL(40, loc.getMaxY());
}

TEST("require that circles can be parsed") {
    Location loc = parse("(2,10,20,5,0,0,0)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.getXAspect());
    EXPECT_EQUAL(10, loc.getX());
    EXPECT_EQUAL(20, loc.getY());
    EXPECT_EQUAL(5u, loc.getRadius());
    EXPECT_EQUAL(5, loc.getMinX());
    EXPECT_EQUAL(15, loc.getMinY());
    EXPECT_EQUAL(15, loc.getMaxX());
    EXPECT_EQUAL(25, loc.getMaxY());    
}

TEST("require that circles can have aspect ratio") {
    Location loc = parse("(2,10,20,5,0,0,0,2147483648)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(2147483648u, loc.getXAspect());
    EXPECT_EQUAL(10, loc.getX());
    EXPECT_EQUAL(20, loc.getY());
    EXPECT_EQUAL(5u, loc.getRadius());
    EXPECT_EQUAL(-1, loc.getMinX());
    EXPECT_EQUAL(15, loc.getMinY());
    EXPECT_EQUAL(21, loc.getMaxX());
    EXPECT_EQUAL(25, loc.getMaxY());
}

TEST("require that bounding box can be specified after circle") {
    Location loc = parse("(2,10,20,5,0,0,0)[2,10,20,30,40]");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.getXAspect());
    EXPECT_EQUAL(10, loc.getX());
    EXPECT_EQUAL(20, loc.getY());
    EXPECT_EQUAL(5u, loc.getRadius());
    EXPECT_EQUAL(10, loc.getMinX());
    EXPECT_EQUAL(20, loc.getMinY());
    EXPECT_EQUAL(15, loc.getMaxX());
    EXPECT_EQUAL(25, loc.getMaxY());
}

TEST("require that circles can be specified after bounding box") {
    Location loc = parse("[2,10,20,30,40](2,10,20,5,0,0,0)");
    EXPECT_EQUAL(true, loc.getRankOnDistance());
    EXPECT_EQUAL(true, loc.getPruneOnDistance());
    EXPECT_EQUAL(0u, loc.getXAspect());
    EXPECT_EQUAL(10, loc.getX());
    EXPECT_EQUAL(20, loc.getY());
    EXPECT_EQUAL(5u, loc.getRadius());
    EXPECT_EQUAL(10, loc.getMinX());
    EXPECT_EQUAL(20, loc.getMinY());
    EXPECT_EQUAL(15, loc.getMaxX());
    EXPECT_EQUAL(25, loc.getMaxY());    
}

TEST("require that santa search gives non-wrapped bounding box") {
    Location loc = parse("(2,122163600,89998536,290112,4,2000,0,109704)");
    EXPECT_GREATER_EQUAL(loc.getMaxX(), loc.getMinX());
    EXPECT_GREATER_EQUAL(loc.getMaxY(), loc.getMinY());
}

TEST_MAIN() { TEST_RUN_ALL(); }
