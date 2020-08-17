// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::GeoLocation;
using search::common::GeoLocationParser;

using Box = search::common::GeoLocation::Box;
using Point = search::common::GeoLocation::Point;
using Range = search::common::GeoLocation::Range;
using Aspect = search::common::GeoLocation::Aspect;

constexpr int32_t plus_inf = std::numeric_limits<int32_t>::max();
constexpr int32_t minus_inf = std::numeric_limits<int32_t>::min();

bool is_parseable(const char *str) {
    GeoLocationParser parser;
    return parser.parseOldFormat(str);
}

GeoLocation parse(const char *str) {
    GeoLocationParser parser;
    EXPECT_TRUE(parser.parseOldFormat(str));
    return parser.getGeoLocation();
}

TEST(GeoLocationParserTest, malformed_bounding_boxes_are_not_parseable) {
    EXPECT_TRUE(is_parseable("[2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40][2,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[1,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[3,10,20,30,40]"));
    EXPECT_FALSE(is_parseable("[2, 10, 20, 30, 40]"));
    EXPECT_FALSE(is_parseable("[2,10,20,30,40"));
    EXPECT_FALSE(is_parseable("[2,10,20,30]"));
    EXPECT_FALSE(is_parseable("[10,20,30,40]"));
}

TEST(GeoLocationParserTest, malformed_circles_are_not_parseable) {
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

TEST(GeoLocationParserTest, bounding_boxes_can_be_parsed) {
    auto loc = parse("[2,10,20,30,40]");
    EXPECT_EQ(false, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(0, loc.point.x);
    EXPECT_EQ(0, loc.point.y);
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.low);
    EXPECT_EQ(20, loc.bounding_box.y.low);
    EXPECT_EQ(30, loc.bounding_box.x.high);
    EXPECT_EQ(40, loc.bounding_box.y.high);
}

TEST(GeoLocationParserTest, circles_can_be_parsed) {
    auto loc = parse("(2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(5, loc.bounding_box.x.low);
    EXPECT_EQ(15, loc.bounding_box.y.low);
    EXPECT_EQ(15, loc.bounding_box.x.high);
    EXPECT_EQ(25, loc.bounding_box.y.high);    
}

TEST(GeoLocationParserTest, circles_can_have_aspect_ratio) {
    auto loc = parse("(2,10,20,5,0,0,0,2147483648)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(2147483648u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(-1, loc.bounding_box.x.low);
    EXPECT_EQ(15, loc.bounding_box.y.low);
    EXPECT_EQ(21, loc.bounding_box.x.high);
    EXPECT_EQ(25, loc.bounding_box.y.high);
}

TEST(GeoLocationParserTest, bounding_box_can_be_specified_after_circle) {
    auto loc = parse("(2,10,20,5,0,0,0)[2,10,20,30,40]");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.low);
    EXPECT_EQ(20, loc.bounding_box.y.low);
    EXPECT_EQ(15, loc.bounding_box.x.high);
    EXPECT_EQ(25, loc.bounding_box.y.high);
}

TEST(GeoLocationParserTest, circles_can_be_specified_after_bounding_box) {
    auto loc = parse("[2,10,20,30,40](2,10,20,5,0,0,0)");
    EXPECT_EQ(true, loc.has_point);
    EXPECT_EQ(true, loc.bounding_box.active());
    EXPECT_EQ(0u, loc.x_aspect.multiplier);
    EXPECT_EQ(10, loc.point.x);
    EXPECT_EQ(20, loc.point.y);
    EXPECT_EQ(5u, loc.radius);
    EXPECT_EQ(10, loc.bounding_box.x.low);
    EXPECT_EQ(20, loc.bounding_box.y.low);
    EXPECT_EQ(15, loc.bounding_box.x.high);
    EXPECT_EQ(25, loc.bounding_box.y.high);    
}

TEST(GeoLocationParserTest, santa_search_gives_non_wrapped_bounding_box) {
    auto loc = parse("(2,122163600,89998536,290112,4,2000,0,109704)");
    EXPECT_GE(loc.bounding_box.x.high, loc.bounding_box.x.low);
    EXPECT_GE(loc.bounding_box.y.high, loc.bounding_box.y.low);
}

TEST(GeoLocationParserTest, near_boundary_search_gives_non_wrapped_bounding_box) {
    auto loc1 = parse("(2,2000000000,2000000000,3000000000,0,1,0)");
    EXPECT_GE(loc1.bounding_box.x.high, loc1.bounding_box.x.low);
    EXPECT_GE(loc1.bounding_box.y.high, loc1.bounding_box.y.low);
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.bounding_box.y.high);
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), loc1.bounding_box.y.high);    

    auto loc2 = parse("(2,-2000000000,-2000000000,3000000000,0,1,0)");
    EXPECT_GE(loc2.bounding_box.x.high, loc2.bounding_box.x.low);
    EXPECT_GE(loc2.bounding_box.y.high, loc2.bounding_box.y.low);
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.bounding_box.x.low);
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), loc2.bounding_box.y.low);
}

void check_box(const GeoLocation &location, Box expected)
{
    int32_t lx = expected.x.low;
    int32_t hx = expected.x.high;
    int32_t ly = expected.y.low;
    int32_t hy = expected.y.high;
    EXPECT_TRUE(location.inside_limit(Point{lx,ly}));
    EXPECT_TRUE(location.inside_limit(Point{lx,hy}));
    EXPECT_TRUE(location.inside_limit(Point{hx,ly}));
    EXPECT_TRUE(location.inside_limit(Point{hx,hy}));

    EXPECT_FALSE(location.inside_limit(Point{lx,ly-1}));
    EXPECT_FALSE(location.inside_limit(Point{lx,hy+1}));
    EXPECT_FALSE(location.inside_limit(Point{lx-1,ly}));
    EXPECT_FALSE(location.inside_limit(Point{lx-1,hy}));
    EXPECT_FALSE(location.inside_limit(Point{hx,ly-1}));
    EXPECT_FALSE(location.inside_limit(Point{hx,hy+1}));
    EXPECT_FALSE(location.inside_limit(Point{hx+1,ly}));
    EXPECT_FALSE(location.inside_limit(Point{hx+1,hy}));

    EXPECT_FALSE(location.inside_limit(Point{plus_inf,plus_inf}));
    EXPECT_FALSE(location.inside_limit(Point{minus_inf,minus_inf}));
}

TEST(GeoLocationTest, invalid_location) {
    GeoLocation invalid;
    EXPECT_FALSE(invalid.valid());
    EXPECT_FALSE(invalid.has_radius());
    EXPECT_FALSE(invalid.can_limit());
    EXPECT_FALSE(invalid.has_point);
    EXPECT_FALSE(invalid.bounding_box.active());
    EXPECT_FALSE(invalid.x_aspect.active());

    EXPECT_EQ(invalid.sq_distance_to(Point{0,0}), 0);
    EXPECT_EQ(invalid.sq_distance_to(Point{999999,999999}), 0);
    EXPECT_EQ(invalid.sq_distance_to(Point{-999999,-999999}), 0);
    EXPECT_EQ(invalid.sq_distance_to(Point{plus_inf,plus_inf}), 0);
    EXPECT_EQ(invalid.sq_distance_to(Point{minus_inf,minus_inf}), 0);

    EXPECT_TRUE(invalid.inside_limit(Point{0,0}));
    EXPECT_TRUE(invalid.inside_limit(Point{999999,999999}));
    EXPECT_TRUE(invalid.inside_limit(Point{-999999,-999999}));
    EXPECT_TRUE(invalid.inside_limit(Point{plus_inf,plus_inf}));
    EXPECT_TRUE(invalid.inside_limit(Point{minus_inf,minus_inf}));
}

TEST(GeoLocationTest, point_location) {
    GeoLocation location(Point{300,-400});
    EXPECT_TRUE(location.valid());
    EXPECT_FALSE(location.has_radius());
    EXPECT_FALSE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_FALSE(location.bounding_box.active());
    EXPECT_FALSE(location.x_aspect.active());

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{300,-400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{300,400}), 640000);

    EXPECT_TRUE(location.inside_limit(Point{0,0}));
    EXPECT_TRUE(location.inside_limit(Point{999999,999999}));
    EXPECT_TRUE(location.inside_limit(Point{-999999,-999999}));
    EXPECT_TRUE(location.inside_limit(Point{plus_inf,plus_inf}));
    EXPECT_TRUE(location.inside_limit(Point{minus_inf,minus_inf}));
}

TEST(GeoLocationTest, point_and_radius) {
    GeoLocation location(Point{300,-400}, 500);
    EXPECT_TRUE(location.valid());
    EXPECT_TRUE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_FALSE(location.x_aspect.active());

    EXPECT_EQ(location.radius, 500);

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{300,-400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{300,400}), 640000);

    EXPECT_TRUE(location.inside_limit(Point{0,0}));
    EXPECT_TRUE(location.inside_limit(Point{-200,-400}));
    EXPECT_TRUE(location.inside_limit(Point{800,-400}));
    EXPECT_TRUE(location.inside_limit(Point{300,-400}));
    EXPECT_TRUE(location.inside_limit(Point{300,100}));
    EXPECT_TRUE(location.inside_limit(Point{300,-900}));

    check_box(location, Box{Range{0,600},{-800,0}});
}

TEST(GeoLocationTest, point_and_aspect) {
    GeoLocation location(Point{600,400}, Aspect{0.5});
    // same: GeoLocation location(Point{600,400}, Aspect{1ul << 31});
    EXPECT_TRUE(location.valid());
    EXPECT_FALSE(location.has_radius());
    EXPECT_FALSE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_FALSE(location.bounding_box.active());
    EXPECT_TRUE(location.x_aspect.active());
    EXPECT_EQ(location.x_aspect.multiplier, 1ul << 31);

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{600,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{1200,800}), 500*500);

    EXPECT_TRUE(location.inside_limit(Point{0,0}));
    EXPECT_TRUE(location.inside_limit(Point{999999,999999}));
    EXPECT_TRUE(location.inside_limit(Point{-999999,-999999}));
    EXPECT_TRUE(location.inside_limit(Point{plus_inf,plus_inf}));
    EXPECT_TRUE(location.inside_limit(Point{minus_inf,minus_inf}));
}

TEST(GeoLocationTest, point_radius_and_aspect) {
    GeoLocation location(Point{1200,400}, 500, Aspect{0.25});
    EXPECT_TRUE(location.valid());
    EXPECT_TRUE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_TRUE(location.x_aspect.active());
    EXPECT_EQ(location.x_aspect.multiplier, 1ul << 30);

    EXPECT_EQ(location.radius, 500);

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{1200,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{1240,400}), 100);

    EXPECT_TRUE(location.inside_limit(Point{1200,400}));
    EXPECT_TRUE(location.inside_limit(Point{0,0}));
    EXPECT_TRUE(location.inside_limit(Point{2400,0}));
    EXPECT_TRUE(location.inside_limit(Point{2400,800}));
    EXPECT_TRUE(location.inside_limit(Point{0,800}));
    // note: must be 4 outside since 3*0.25 may be truncated to 0
    EXPECT_FALSE(location.inside_limit(Point{-4,0}));
    EXPECT_FALSE(location.inside_limit(Point{-4,800}));
    EXPECT_FALSE(location.inside_limit(Point{2404,0}));
    EXPECT_FALSE(location.inside_limit(Point{2404,800}));
    EXPECT_FALSE(location.inside_limit(Point{2400,-1}));
    EXPECT_FALSE(location.inside_limit(Point{2400,801}));
    EXPECT_FALSE(location.inside_limit(Point{0,-1}));
    EXPECT_FALSE(location.inside_limit(Point{0,801}));
    EXPECT_FALSE(location.inside_limit(Point{plus_inf,plus_inf}));
    EXPECT_FALSE(location.inside_limit(Point{minus_inf,minus_inf}));
}

TEST(GeoLocationTest, box_location) {
    Box mybox{Range{300,350},Range{400,450}};
    GeoLocation location(mybox);
    EXPECT_TRUE(location.valid());
    EXPECT_FALSE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_FALSE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_FALSE(location.x_aspect.active());

    // currently does not measure distance outside box:
    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{300,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{350,450}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{450,550}), 0);

    EXPECT_TRUE(location.inside_limit(Point{333,444}));
    EXPECT_FALSE(location.inside_limit(Point{0,0}));
    check_box(location, mybox);
}

TEST(GeoLocationTest, box_and_point) {
    Box mybox{Range{287,343},Range{366,401}};
    GeoLocation location(mybox, Point{300,400});
    EXPECT_TRUE(location.valid());
    EXPECT_FALSE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_FALSE(location.x_aspect.active());

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{300,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{300,423}), 23*23);

    check_box(location, mybox);
}

TEST(GeoLocationTest, box_point_and_aspect) {
    Box mybox{Range{-1000,350},Range{-1000,600}};
    GeoLocation location(mybox, Point{600,400}, Aspect{0.5});
    EXPECT_TRUE(location.valid());
    EXPECT_FALSE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_TRUE(location.x_aspect.active());

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{600,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{600,407}), 7*7);
    EXPECT_EQ(location.sq_distance_to(Point{614,400}), 7*7);

    check_box(location, mybox);
}

TEST(GeoLocationTest, box_point_and_radius) {
    Box mybox{Range{-1000,350},Range{-1000,600}};
    GeoLocation location(mybox, Point{300,400}, 500);
    EXPECT_TRUE(location.valid());
    EXPECT_TRUE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_FALSE(location.x_aspect.active());

    EXPECT_EQ(location.radius, 500);

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{300,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{300,423}), 23*23);

    EXPECT_EQ(location.bounding_box.x.low, -200);
    EXPECT_EQ(location.bounding_box.y.low, -100);
    EXPECT_EQ(location.bounding_box.x.high, 350);
    EXPECT_EQ(location.bounding_box.y.high, 600);
}

TEST(GeoLocationTest, box_point_radius_and_aspect) {
    Box mybox{Range{-1000,650},Range{-1000,700}};
    GeoLocation location(mybox, Point{600,400}, 500, Aspect{0.5});
    EXPECT_TRUE(location.valid());
    EXPECT_TRUE(location.has_radius());
    EXPECT_TRUE(location.can_limit());
    EXPECT_TRUE(location.has_point);
    EXPECT_TRUE(location.bounding_box.active());
    EXPECT_TRUE(location.x_aspect.active());

    EXPECT_EQ(location.radius, 500);

    EXPECT_EQ(location.sq_distance_to(Point{0,0}), 500*500);
    EXPECT_EQ(location.sq_distance_to(Point{600,400}), 0);
    EXPECT_EQ(location.sq_distance_to(Point{600,407}), 7*7);
    EXPECT_EQ(location.sq_distance_to(Point{614,400}), 7*7);

    EXPECT_GE(location.bounding_box.x.low, -402);
    EXPECT_LE(location.bounding_box.x.low, -400);
    EXPECT_EQ(location.bounding_box.y.low, -100);
    EXPECT_EQ(location.bounding_box.x.high, 650);
    EXPECT_EQ(location.bounding_box.y.high, 700);
}

GTEST_MAIN_RUN_ALL_TESTS()
