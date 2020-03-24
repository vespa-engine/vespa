// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("distance_function_test");

using namespace search::tensor;
using vespalib::tensor::TypedCells;
using search::attribute::DistanceMetric;

TypedCells t(const std::vector<double> &v) { return TypedCells(v); }

void verify_geo_miles(const DistanceFunction *dist_fun,
                      const std::vector<double> &p1,
                      const std::vector<double> &p2,
                      double exp_miles)
{
    TypedCells t1(p1);
    TypedCells t2(p2);
    double abstract_distance = dist_fun->calc(t1, t2);
    double raw_score = dist_fun->to_rawscore(abstract_distance);
    double m = ((1.0/raw_score)-1.0);
    double d_miles = m / 1609.344;
    EXPECT_GE(d_miles, exp_miles*0.99);
    EXPECT_LE(d_miles, exp_miles*1.01);
}


TEST(DistanceFunctionsTest, gives_expected_score)
{
    auto ct = vespalib::eval::ValueType::CellType::DOUBLE;

    auto euclid = make_distance_function(DistanceMetric::Euclidean, ct);
    auto angular = make_distance_function(DistanceMetric::Angular, ct);

    std::vector<double> p0{0.0, 0.0, 0.0};
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p4{0.5, 0.5, 0.707107};
    std::vector<double> p5{0.0,-1.0, 0.0};

    double n4 = euclid->calc(t(p0), t(p4));
    EXPECT_GT(n4, 0.99999);
    EXPECT_LT(n4, 1.00001);
    double d12 = euclid->calc(t(p1), t(p2));
    EXPECT_EQ(d12, 2.0);

    double a12 = angular->calc(t(p1), t(p2));
    double a13 = angular->calc(t(p1), t(p3));
    double a23 = angular->calc(t(p2), t(p3));
    EXPECT_EQ(a12, 1.0);
    EXPECT_EQ(a13, 1.0);
    EXPECT_EQ(a23, 1.0);
    double a14 = angular->calc(t(p1), t(p4));
    double a24 = angular->calc(t(p2), t(p4));
    EXPECT_EQ(a14, 0.5);
    EXPECT_EQ(a24, 0.5);
    double a34 = angular->calc(t(p3), t(p4));
    EXPECT_GT(a34, 0.999999 - 0.707107);
    EXPECT_LT(a34, 1.000001 - 0.707107);

    double a25 = angular->calc(t(p2), t(p5));
    EXPECT_EQ(a25, 2.0);

    double a44 = angular->calc(t(p4), t(p4));
    EXPECT_GE(a44, 0.0);
    EXPECT_LT(a44, 0.000001);
}

TEST(GeoDegreesTest, gives_expected_score)
{
    auto ct = vespalib::eval::ValueType::CellType::DOUBLE;
    auto geodeg = make_distance_function(DistanceMetric::GeoDegrees, ct);

    std::vector<double> g1_sfo{37.61, -122.38};
    std::vector<double> g2_lhr{51.47, -0.46};
    std::vector<double> g3_osl{60.20, 11.08};
    std::vector<double> g4_gig{-22.8, -43.25};
    std::vector<double> g5_hkg{22.31, 113.91};
    std::vector<double> g6_trd{63.45, 10.92};
    std::vector<double> g7_syd{-33.95, 151.17};
    std::vector<double> g8_lax{33.94, -118.41};
    std::vector<double> g9_jfk{40.64, -73.78};

    double g63_a = geodeg->calc(t(g6_trd), t(g3_osl));
    double g63_r = geodeg->to_rawscore(g63_a);
    double g63_km = ((1.0/g63_r)-1.0) *.001;
    EXPECT_GT(g63_km, 350);
    EXPECT_LT(g63_km, 375);

    // all distances from gcmap.com, the
    // Great Circle Mapper for airports using
    // a more accurate formula - we should agree
    // with < 1.0% deviation
    verify_geo_miles(geodeg.get(), g1_sfo, g1_sfo, 0);
    verify_geo_miles(geodeg.get(), g1_sfo, g2_lhr, 5367);
    verify_geo_miles(geodeg.get(), g1_sfo, g3_osl, 5196);
    verify_geo_miles(geodeg.get(), g1_sfo, g4_gig, 6604);
    verify_geo_miles(geodeg.get(), g1_sfo, g5_hkg, 6927);
    verify_geo_miles(geodeg.get(), g1_sfo, g6_trd, 5012);
    verify_geo_miles(geodeg.get(), g1_sfo, g7_syd, 7417);
    verify_geo_miles(geodeg.get(), g1_sfo, g8_lax, 337);
    verify_geo_miles(geodeg.get(), g1_sfo, g9_jfk, 2586);

    verify_geo_miles(geodeg.get(), g2_lhr, g1_sfo, 5367);
    verify_geo_miles(geodeg.get(), g2_lhr, g2_lhr, 0);
    verify_geo_miles(geodeg.get(), g2_lhr, g3_osl, 750);
    verify_geo_miles(geodeg.get(), g2_lhr, g4_gig, 5734);
    verify_geo_miles(geodeg.get(), g2_lhr, g5_hkg, 5994);
    verify_geo_miles(geodeg.get(), g2_lhr, g6_trd, 928);
    verify_geo_miles(geodeg.get(), g2_lhr, g7_syd, 10573);
    verify_geo_miles(geodeg.get(), g2_lhr, g8_lax, 5456);
    verify_geo_miles(geodeg.get(), g2_lhr, g9_jfk, 3451);

    verify_geo_miles(geodeg.get(), g3_osl, g1_sfo, 5196);
    verify_geo_miles(geodeg.get(), g3_osl, g2_lhr, 750);
    verify_geo_miles(geodeg.get(), g3_osl, g3_osl, 0);
    verify_geo_miles(geodeg.get(), g3_osl, g4_gig, 6479);
    verify_geo_miles(geodeg.get(), g3_osl, g5_hkg, 5319);
    verify_geo_miles(geodeg.get(), g3_osl, g6_trd, 226);
    verify_geo_miles(geodeg.get(), g3_osl, g7_syd, 9888);
    verify_geo_miles(geodeg.get(), g3_osl, g8_lax, 5345);
    verify_geo_miles(geodeg.get(), g3_osl, g9_jfk, 3687);

    verify_geo_miles(geodeg.get(), g4_gig, g1_sfo, 6604);
    verify_geo_miles(geodeg.get(), g4_gig, g2_lhr, 5734);
    verify_geo_miles(geodeg.get(), g4_gig, g3_osl, 6479);
    verify_geo_miles(geodeg.get(), g4_gig, g4_gig, 0);
    verify_geo_miles(geodeg.get(), g4_gig, g5_hkg, 10989);
    verify_geo_miles(geodeg.get(), g4_gig, g6_trd, 6623);
    verify_geo_miles(geodeg.get(), g4_gig, g7_syd, 8414);
    verify_geo_miles(geodeg.get(), g4_gig, g8_lax, 6294);
    verify_geo_miles(geodeg.get(), g4_gig, g9_jfk, 4786);

    verify_geo_miles(geodeg.get(), g5_hkg, g1_sfo, 6927);
    verify_geo_miles(geodeg.get(), g5_hkg, g2_lhr, 5994);
    verify_geo_miles(geodeg.get(), g5_hkg, g3_osl, 5319);
    verify_geo_miles(geodeg.get(), g5_hkg, g4_gig, 10989);
    verify_geo_miles(geodeg.get(), g5_hkg, g5_hkg, 0);
    verify_geo_miles(geodeg.get(), g5_hkg, g6_trd, 5240);
    verify_geo_miles(geodeg.get(), g5_hkg, g7_syd, 4581);
    verify_geo_miles(geodeg.get(), g5_hkg, g8_lax, 7260);
    verify_geo_miles(geodeg.get(), g5_hkg, g9_jfk, 8072);

    verify_geo_miles(geodeg.get(), g6_trd, g1_sfo, 5012);
    verify_geo_miles(geodeg.get(), g6_trd, g2_lhr, 928);
    verify_geo_miles(geodeg.get(), g6_trd, g3_osl, 226);
    verify_geo_miles(geodeg.get(), g6_trd, g4_gig, 6623);
    verify_geo_miles(geodeg.get(), g6_trd, g5_hkg, 5240);
    verify_geo_miles(geodeg.get(), g6_trd, g6_trd, 0);
    verify_geo_miles(geodeg.get(), g6_trd, g7_syd, 9782);
    verify_geo_miles(geodeg.get(), g6_trd, g8_lax, 5171);
    verify_geo_miles(geodeg.get(), g6_trd, g9_jfk, 3611);

    verify_geo_miles(geodeg.get(), g7_syd, g1_sfo, 7417);
    verify_geo_miles(geodeg.get(), g7_syd, g2_lhr, 10573);
    verify_geo_miles(geodeg.get(), g7_syd, g3_osl, 9888);
    verify_geo_miles(geodeg.get(), g7_syd, g4_gig, 8414);
    verify_geo_miles(geodeg.get(), g7_syd, g5_hkg, 4581);
    verify_geo_miles(geodeg.get(), g7_syd, g6_trd, 9782);
    verify_geo_miles(geodeg.get(), g7_syd, g7_syd, 0);
    verify_geo_miles(geodeg.get(), g7_syd, g8_lax, 7488);
    verify_geo_miles(geodeg.get(), g7_syd, g9_jfk, 9950);

    verify_geo_miles(geodeg.get(), g8_lax, g1_sfo, 337);
    verify_geo_miles(geodeg.get(), g8_lax, g2_lhr, 5456);
    verify_geo_miles(geodeg.get(), g8_lax, g3_osl, 5345);
    verify_geo_miles(geodeg.get(), g8_lax, g4_gig, 6294);
    verify_geo_miles(geodeg.get(), g8_lax, g5_hkg, 7260);
    verify_geo_miles(geodeg.get(), g8_lax, g6_trd, 5171);
    verify_geo_miles(geodeg.get(), g8_lax, g7_syd, 7488);
    verify_geo_miles(geodeg.get(), g8_lax, g8_lax, 0);
    verify_geo_miles(geodeg.get(), g8_lax, g9_jfk, 2475);

    verify_geo_miles(geodeg.get(), g9_jfk, g1_sfo, 2586);
    verify_geo_miles(geodeg.get(), g9_jfk, g2_lhr, 3451);
    verify_geo_miles(geodeg.get(), g9_jfk, g3_osl, 3687);
    verify_geo_miles(geodeg.get(), g9_jfk, g4_gig, 4786);
    verify_geo_miles(geodeg.get(), g9_jfk, g5_hkg, 8072);
    verify_geo_miles(geodeg.get(), g9_jfk, g6_trd, 3611);
    verify_geo_miles(geodeg.get(), g9_jfk, g7_syd, 9950);
    verify_geo_miles(geodeg.get(), g9_jfk, g8_lax, 2475);
    verify_geo_miles(geodeg.get(), g9_jfk, g9_jfk, 0);

}

GTEST_MAIN_RUN_ALL_TESTS()

