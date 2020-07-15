// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::GeoLocation;
using search::common::GeoLocationParser;

bool is_parseable(const char *str) {
    GeoLocationParser parser;
    return parser.parseOldFormat(str);
}

GeoLocation parse(const char *str) {
    GeoLocationParser parser;
    EXPECT_TRUE(parser.parseOldFormat(str));
    return parser.getGeoLocation();
}

TEST(GeoLocationTest, malformed_bounding_boxes_are_not_parseable) {
    EXPECT_TRUE(is_parseable("[2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40][2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[1,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[3,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2, 10, 20, 30, 40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40"));
    EXPECT_FALSE(is_parseable("[2,10,20,30]"));
    EXPECT_FALSE(is_parseable("[10,20,30,40]"));
}

TEST(GeoLocationTest, malformed_circles_are_not_parseable) {
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

TEST(GeoLocationTest, bounding_boxes_can_be_parsed) {
    auto loc = parse("[2,10,20,30,40]");
    EXPECT_EQ(false, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(0, loc.point.x);
    EXPECT_EQ(0, loc.point.y);
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.lo);
    EXPECT_EQ(20, loc.bounding_box.y.lo);
    EXPECT_EQ(30, loc.bounding_box.x.hi);
    EXPECT_EQ(40, loc.bounding_box.y.hi);
}

TEST(GeoLocationTest, circles_can_be_parsed) {
    auto loc = parse("(2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(5, loc.bounding_box.x.lo);
    EXPECT_EQ(15, loc.bounding_box.y.lo);
    EXPECT_EQ(15, loc.bounding_box.x.hi);
    EXPECT_EQ(25, loc.bounding_box.y.hi);    
}

TEST(GeoLocationTest, circles_can_have_aspect_ratio) {
    auto loc = parse("(2,10,20,5,0,0,0,2147483648)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(2147483648u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(-1, loc.bounding_box.x.lo);
    EXPECT_EQ(15, loc.bounding_box.y.lo);
    EXPECT_EQ(21, loc.bounding_box.x.hi);
    EXPECT_EQ(25, loc.bounding_box.y.hi);
}

TEST(GeoLocationTest, bounding_box_can_be_specified_after_circle) {
    auto loc = parse("(2,10,20,5,0,0,0)[2,10,20,30,40]");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.lo);
    EXPECT_EQ(20, loc.bounding_box.y.lo);
    EXPECT_EQ(15, loc.bounding_box.x.hi);
    EXPECT_EQ(25, loc.bounding_box.y.hi);
}

TEST(GeoLocationTest, circles_can_be_specified_after_bounding_box) {
    auto loc = parse("[2,10,20,30,40](2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.lo);
    EXPECT_EQ(20, loc.bounding_box.y.lo);
    EXPECT_EQ(15, loc.bounding_box.x.hi);
    EXPECT_EQ(25, loc.bounding_box.y.hi);    
}

TEST(GeoLocationTest, santa_search_gives_non_wrapped_bounding_box) {
    auto loc = parse("(2,122163600,89998536,290112,4,2000,0,109704)");
    EXPECT_GE(loc.bounding_box.x.hi, loc.bounding_box.x.lo);
    EXPECT_GE(loc.bounding_box.y.hi, loc.bounding_box.y.lo);
}

TEST(GeoLocationTest, near_boundary_search_gives_non_wrapped_bounding_box) {
    auto loc1 = parse("(2,2000000000,2000000000,3000000000,0,1,0)");
    EXPECT_GE(loc1.bounding_box.x.hi, loc1.bounding_box.x.lo);
    EXPECT_GE(loc1.bounding_box.y.hi, loc1.bounding_box.y.lo);
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.bounding_box.y.hi);
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.bounding_box.y.hi);    

    auto loc2 = parse("(2,-2000000000,-2000000000,3000000000,0,1,0)");
    EXPECT_GE(loc2.bounding_box.x.hi, loc2.bounding_box.x.lo);
    EXPECT_GE(loc2.bounding_box.y.hi, loc2.bounding_box.y.lo);
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.bounding_box.x.lo);
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.bounding_box.y.lo);
}

GTEST_MAIN_RUN_ALL_TESTS()
