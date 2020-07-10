// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::GeoLocationSpec;

bool is_parseable(const char *str) {
    GeoLocationSpec loc;
    return loc.parse(str);
}

GeoLocationSpec parse(const char *str) {
    GeoLocationSpec loc;
    EXPECT_TRUE(loc.parse(str));
    return loc;
}

TEST(GeoLocationSpec, malformed_bounding_boxes_are_not_parseable) {
    EXPECT_TRUE(is_parseable("[2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40][2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[1,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[3,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2, 10, 20, 30, 40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40"));
    EXPECT_FALSE(is_parseable("[2,10,20,30]"));
    EXPECT_FALSE(is_parseable("[10,20,30,40]"));
}

TEST(GeoLocationSpec, malformed_circles_are_not_parseable) {
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

TEST(GeoLocationSpec, bounding_boxes_can_be_parsed) {
    auto loc = parse("[2,10,20,30,40]");
    EXPECT_EQ(false, loc.hasPoint());
    EXPECT_EQ(true, loc.hasBoundingBox());
    EXPECT_EQ(0u, loc.getXAspect());
    EXPECT_EQ(0, loc.getX());
    EXPECT_EQ(0, loc.getY());
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), loc.getRadius());
    EXPECT_EQ(10, loc.getMinX());
    EXPECT_EQ(20, loc.getMinY());
    EXPECT_EQ(30, loc.getMaxX());
    EXPECT_EQ(40, loc.getMaxY());
}

TEST(GeoLocationSpec, circles_can_be_parsed) {
    auto loc = parse("(2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.hasPoint());
    EXPECT_EQ(true, loc.hasBoundingBox());
    EXPECT_EQ(0u, loc.getXAspect());
    EXPECT_EQ(10, loc.getX());
    EXPECT_EQ(20, loc.getY());
    EXPECT_EQ(5u, loc.getRadius());
    EXPECT_EQ(5, loc.getMinX());
    EXPECT_EQ(15, loc.getMinY());
    EXPECT_EQ(15, loc.getMaxX());
    EXPECT_EQ(25, loc.getMaxY());    
}

TEST(GeoLocationSpec, circles_can_have_aspect_ratio) {
    auto loc = parse("(2,10,20,5,0,0,0,2147483648)");
    EXPECT_EQ(true, loc.hasPoint());
    EXPECT_EQ(true, loc.hasBoundingBox());
    EXPECT_EQ(2147483648u, loc.getXAspect());
    EXPECT_EQ(10, loc.getX());
    EXPECT_EQ(20, loc.getY());
    EXPECT_EQ(5u, loc.getRadius());
    EXPECT_EQ(-1, loc.getMinX());
    EXPECT_EQ(15, loc.getMinY());
    EXPECT_EQ(21, loc.getMaxX());
    EXPECT_EQ(25, loc.getMaxY());
}

TEST(GeoLocationSpec, bounding_box_can_be_specified_after_circle) {
    auto loc = parse("(2,10,20,5,0,0,0)[2,10,20,30,40]");
    EXPECT_EQ(true, loc.hasPoint());
    EXPECT_EQ(true, loc.hasBoundingBox());
    EXPECT_EQ(0u, loc.getXAspect());
    EXPECT_EQ(10, loc.getX());
    EXPECT_EQ(20, loc.getY());
    EXPECT_EQ(5u, loc.getRadius());
    EXPECT_EQ(10, loc.getMinX());
    EXPECT_EQ(20, loc.getMinY());
    EXPECT_EQ(15, loc.getMaxX());
    EXPECT_EQ(25, loc.getMaxY());
}

TEST(GeoLocationSpec, circles_can_be_specified_after_bounding_box) {
    auto loc = parse("[2,10,20,30,40](2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.hasPoint());
    EXPECT_EQ(true, loc.hasBoundingBox());
    EXPECT_EQ(0u, loc.getXAspect());
    EXPECT_EQ(10, loc.getX());
    EXPECT_EQ(20, loc.getY());
    EXPECT_EQ(5u, loc.getRadius());
    EXPECT_EQ(10, loc.getMinX());
    EXPECT_EQ(20, loc.getMinY());
    EXPECT_EQ(15, loc.getMaxX());
    EXPECT_EQ(25, loc.getMaxY());    
}

TEST(GeoLocationSpec, santa_search_gives_non_wrapped_bounding_box) {
    auto loc = parse("(2,122163600,89998536,290112,4,2000,0,109704)");
    EXPECT_GE(loc.getMaxX(), loc.getMinX());
    EXPECT_GE(loc.getMaxY(), loc.getMinY());
}

TEST(GeoLocationSpec, near_boundary_search_gives_non_wrapped_bounding_box) {
    auto loc1 = parse("(2,2000000000,2000000000,3000000000,0,1,0)");
 // fprintf(stderr, "positive near boundary: %s\n", loc1.getLocationString().c_str());
    EXPECT_GE(loc1.getMaxX(), loc1.getMinX());
    EXPECT_GE(loc1.getMaxY(), loc1.getMinY());
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.getMaxY());
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.getMaxY());    

    auto loc2 = parse("(2,-2000000000,-2000000000,3000000000,0,1,0)");
 // fprintf(stderr, "negative near boundary: %s\n", loc2.getLocationString().c_str());
    EXPECT_GE(loc2.getMaxX(), loc2.getMinX());
    EXPECT_GE(loc2.getMaxY(), loc2.getMinY());
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.getMinX());
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.getMinY());
}

GTEST_MAIN_RUN_ALL_TESTS()
