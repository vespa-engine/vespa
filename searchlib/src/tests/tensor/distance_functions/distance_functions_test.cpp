// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/searchlib/tensor/mips_distance_transform.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("distance_function_test");

using namespace search::tensor;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using search::attribute::DistanceMetric;

template <typename T>
TypedCells t(const std::vector<T> &v) { return TypedCells(v); }

void verify_geo_miles(const std::vector<double> &p1,
                      const std::vector<double> &p2,
                      double exp_miles)
{
    static GeoDistanceFunctionFactory dff;
    TypedCells t1(p1);
    TypedCells t2(p2);
    auto dist_fun = dff.for_query_vector(t1);
    double abstract_distance = dist_fun->calc(t2);
    EXPECT_EQ(dff.for_insertion_vector(t1)->calc(t2), abstract_distance);
    EXPECT_FLOAT_EQ(dff.for_query_vector(t2)->calc(t1), abstract_distance);
    double raw_score = dist_fun->to_rawscore(abstract_distance);
    double km = ((1.0/raw_score)-1.0);
    double d_miles = km / 1.609344;
    if (exp_miles != 0.0) {
        EXPECT_GE(d_miles, exp_miles*0.99);
        EXPECT_LE(d_miles, exp_miles*1.01);
        double threshold = dist_fun->convert_threshold(km);
        EXPECT_DOUBLE_EQ(threshold, abstract_distance);
        // compare with common Great Circle Distance implementation:
        search::common::GeoGcd gp1{p1[0], p1[1]};
        double km_gcd = gp1.km_great_circle_distance(p2[0], p2[1]);
        EXPECT_NEAR(km, km_gcd, 1e-9); // EXPECT_DOUBLE_EQ does not work on arm64 for some reason
    } else {
        EXPECT_LE(d_miles, 7e-13);
        EXPECT_LE(abstract_distance, 6e-33);
    }
}

double computeEuclideanChecked(TypedCells a, TypedCells b) {
    static EuclideanDistanceFunctionFactory<Int8Float> i8f_dff;
    static EuclideanDistanceFunctionFactory<float> flt_dff;
    static EuclideanDistanceFunctionFactory<double> dbl_dff;
    auto d_n = dbl_dff.for_query_vector(a);
    auto d_f = flt_dff.for_query_vector(a);
    auto d_r = dbl_dff.for_query_vector(b);
    auto d_i = dbl_dff.for_insertion_vector(a);
    // normal:
    double result = d_n->calc(b);
     // insert is exactly same:
    EXPECT_EQ(d_i->calc(b), result);
    // reverse:
    EXPECT_DOUBLE_EQ(d_r->calc(a), result);
    // float factory:
    EXPECT_FLOAT_EQ(d_f->calc(b), result);
    if (a.type == vespalib::eval::CellType::INT8 ||
        b.type == vespalib::eval::CellType::INT8)
    {
        auto d_8 = i8f_dff.for_query_vector(a);
        EXPECT_DOUBLE_EQ(d_8->calc(b), result);
    }
    return result;
}

namespace { const double sq_root_half = std::sqrt(0.5); }

TEST(DistanceFunctionsTest, euclidean_gives_expected_score)
{
    std::vector<double> p0{0.0, 0.0, 0.0};
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p4{0.5, 0.5, sq_root_half};
    std::vector<double> p5{0.0,-1.0, 0.0};
    std::vector<double> p6{1.0, 2.0, 2.0};

    double n4 = computeEuclideanChecked(t(p0), t(p4));
    EXPECT_FLOAT_EQ(n4, 1.0);
    double d12 = computeEuclideanChecked(t(p1), t(p2));
    EXPECT_EQ(d12, 2.0);

    EuclideanDistanceFunctionFactory<double> dff;
    auto euclid = dff.for_query_vector(t(p0));
    EXPECT_DOUBLE_EQ(euclid->to_rawscore(d12), 1.0/(1.0 + sqrt(2.0)));
    double threshold = euclid->convert_threshold(8.0);
    EXPECT_EQ(threshold, 64.0);
    threshold = euclid->convert_threshold(0.5);
    EXPECT_EQ(threshold, 0.25);

    // simple hand-checked distances:
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p0)), 0.0);
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p1)), 1.0);
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p2)), 1.0);
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p3)), 1.0);
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p5)), 1.0);
    EXPECT_EQ(computeEuclideanChecked(t(p0), t(p6)), 9.0);

    EXPECT_EQ(computeEuclideanChecked(t(p1), t(p1)), 0.0);
    EXPECT_EQ(computeEuclideanChecked(t(p1), t(p2)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(p1), t(p3)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(p1), t(p5)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(p1), t(p6)), 8.0);

    EXPECT_EQ(computeEuclideanChecked(t(p2), t(p2)), 0.0);
    EXPECT_EQ(computeEuclideanChecked(t(p2), t(p3)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(p2), t(p5)), 4.0);
    EXPECT_EQ(computeEuclideanChecked(t(p2), t(p6)), 6.0);

    EXPECT_EQ(computeEuclideanChecked(t(p3), t(p3)), 0.0);
    EXPECT_EQ(computeEuclideanChecked(t(p3), t(p5)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(p3), t(p6)), 6.0);

    EXPECT_EQ(computeEuclideanChecked(t(p5), t(p5)), 0.0);
    EXPECT_EQ(computeEuclideanChecked(t(p5), t(p6)), 14.0);

    EXPECT_EQ(computeEuclideanChecked(t(p6), t(p6)), 0.0);

    // smoke test for bfloat16:
    std::vector<vespalib::BFloat16> bf16v;
    bf16v.emplace_back(1.0);
    bf16v.emplace_back(1.0);
    bf16v.emplace_back(1.0);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p0)), 3.0);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p1)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p2)), 2.0);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p3)), 2.0);
    EXPECT_FLOAT_EQ(computeEuclideanChecked(t(bf16v), t(p4)), 0.5857863);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p5)), 6.0);
    EXPECT_EQ(computeEuclideanChecked(t(bf16v), t(p6)), 2.0);
}

TEST(DistanceFunctionsTest, euclidean_int8_smoketest)
{
    std::vector<Int8Float> p0{0.0, 0.0, 0.0};
    std::vector<Int8Float> p1{1.0, 0.0, 0.0};
    std::vector<Int8Float> p5{0.0,-1.0, 0.0};
    std::vector<Int8Float> p7{-1.0, 2.0, -2.0};

    EXPECT_DOUBLE_EQ(1.0, computeEuclideanChecked(t(p0), t(p1)));
    EXPECT_DOUBLE_EQ(1.0, computeEuclideanChecked(t(p0), t(p5)));
    EXPECT_DOUBLE_EQ(9.0, computeEuclideanChecked(t(p0), t(p7)));

    EXPECT_DOUBLE_EQ(2.0, computeEuclideanChecked(t(p1), t(p5)));
    EXPECT_DOUBLE_EQ(12.0, computeEuclideanChecked(t(p1), t(p7)));
    EXPECT_DOUBLE_EQ(14.0, computeEuclideanChecked(t(p5), t(p7)));
}

double computeAngularChecked(TypedCells a, TypedCells b) {
    static AngularDistanceFunctionFactory<float> flt_dff;
    static AngularDistanceFunctionFactory<double> dbl_dff;
    auto d_n = dbl_dff.for_query_vector(a);
    auto d_f = flt_dff.for_query_vector(a);
    auto d_r = dbl_dff.for_query_vector(b);
    auto d_i = dbl_dff.for_insertion_vector(a);
    // normal:
    double result = d_n->calc(b);
     // insert is exactly same:
    EXPECT_EQ(d_i->calc(b), result);
    // reverse:
    EXPECT_DOUBLE_EQ(d_r->calc(a), result);
    // float factory:
    EXPECT_FLOAT_EQ(d_f->calc(b), result);
    return result;
}

TEST(DistanceFunctionsTest, angular_gives_expected_score)
{
    std::vector<double> p0{0.0, 0.0, 0.0};
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p4{0.5, 0.5, sq_root_half};
    std::vector<double> p5{0.0,-1.0, 0.0};
    std::vector<double> p6{1.0, 2.0, 2.0};

    AngularDistanceFunctionFactory<double> dff;
    auto angular = dff.for_query_vector(t(p0));

    constexpr double pi = 3.14159265358979323846;
    double a12 = computeAngularChecked(t(p1), t(p2));
    double a13 = computeAngularChecked(t(p1), t(p3));
    double a23 = computeAngularChecked(t(p2), t(p3));
    EXPECT_DOUBLE_EQ(a12, 1.0);
    EXPECT_DOUBLE_EQ(a13, 1.0);
    EXPECT_DOUBLE_EQ(a23, 1.0);
    EXPECT_FLOAT_EQ(angular->to_rawscore(a12), 1.0/(1.0 + pi/2));

    double threshold = angular->convert_threshold(pi/2);
    EXPECT_DOUBLE_EQ(threshold, 1.0);

    double a14 = computeAngularChecked(t(p1), t(p4));
    double a24 = computeAngularChecked(t(p2), t(p4));
    EXPECT_FLOAT_EQ(a14, 0.5);
    EXPECT_FLOAT_EQ(a24, 0.5);
    EXPECT_FLOAT_EQ(angular->to_rawscore(a14), 1.0/(1.0 + pi/3));
    threshold = angular->convert_threshold(pi/3);
    EXPECT_DOUBLE_EQ(threshold, 0.5);

    double a34 = computeAngularChecked(t(p3), t(p4));
    EXPECT_FLOAT_EQ(a34, (1.0 - sq_root_half));
    EXPECT_FLOAT_EQ(angular->to_rawscore(a34), 1.0/(1.0 + pi/4));
    threshold = angular->convert_threshold(pi/4);
    EXPECT_FLOAT_EQ(threshold, a34);

    double a25 = computeAngularChecked(t(p2), t(p5));
    EXPECT_DOUBLE_EQ(a25, 2.0);
    EXPECT_FLOAT_EQ(angular->to_rawscore(a25), 1.0/(1.0 + pi));
    threshold = angular->convert_threshold(pi);
    EXPECT_FLOAT_EQ(threshold, 2.0);

    double a44 = computeAngularChecked(t(p4), t(p4));
    EXPECT_GE(a44, 0.0);
    EXPECT_LT(a44, 0.000001);
    EXPECT_FLOAT_EQ(angular->to_rawscore(a44), 1.0);

    double a66 = computeAngularChecked(t(p6), t(p6));
    EXPECT_GE(a66, 0.0);
    EXPECT_LT(a66, 0.000001);
    EXPECT_FLOAT_EQ(angular->to_rawscore(a66), 1.0);
    threshold = angular->convert_threshold(0.0);
    EXPECT_FLOAT_EQ(threshold, 0.0);

    double a16 = computeAngularChecked(t(p1), t(p6));
    double a26 = computeAngularChecked(t(p2), t(p6));
    double a36 = computeAngularChecked(t(p3), t(p6));
    EXPECT_FLOAT_EQ(a16, 1.0 - (1.0/3.0));
    EXPECT_FLOAT_EQ(a26, 1.0 - (2.0/3.0));
    EXPECT_FLOAT_EQ(a36, 1.0 - (2.0/3.0));

    // check also that cell type conversion works:
    std::vector<Int8Float> iv0{0.0, 0.0, 0.0};
    std::vector<Int8Float> iv1{1.0, 0.0, 0.0};
    std::vector<Int8Float> iv2{0.0, 1.0, 0.0};
    std::vector<Int8Float> iv3{0.0, 0.0, 1.0};
    std::vector<Int8Float> iv5{0.0,-1.0, 0.0};
    std::vector<Int8Float> iv6{1.0, 2.0, 2.0};

    EXPECT_DOUBLE_EQ(a12, computeAngularChecked(t(iv1), t(iv2)));
    EXPECT_DOUBLE_EQ(a13, computeAngularChecked(t(iv1), t(iv3)));
    EXPECT_DOUBLE_EQ(a14, computeAngularChecked(t(iv1), t(p4)));
    EXPECT_DOUBLE_EQ(a24, computeAngularChecked(t(iv2), t(p4)));
    EXPECT_DOUBLE_EQ(a34, computeAngularChecked(t(iv3), t(p4)));
    EXPECT_DOUBLE_EQ(a25, computeAngularChecked(t(iv2), t(iv5)));
    EXPECT_DOUBLE_EQ(a16, computeAngularChecked(t(iv1), t(iv6)));
    EXPECT_DOUBLE_EQ(a26, computeAngularChecked(t(iv2), t(iv6)));
    EXPECT_DOUBLE_EQ(a36, computeAngularChecked(t(iv3), t(iv6)));
    EXPECT_DOUBLE_EQ(a66, computeAngularChecked(t(iv6), t(iv6)));
}

double computePrenormalizedAngularChecked(TypedCells a, TypedCells b) {
    static PrenormalizedAngularDistanceFunctionFactory<float> flt_dff;
    static PrenormalizedAngularDistanceFunctionFactory<double> dbl_dff;
    auto d_n = dbl_dff.for_query_vector(a);
    auto d_f = flt_dff.for_query_vector(a);
    auto d_r = dbl_dff.for_query_vector(b);
    auto d_i = dbl_dff.for_insertion_vector(a);
    // normal:
    double result = d_n->calc(b);
     // insert is exactly same:
    EXPECT_EQ(d_i->calc(b), result);
    // note: for this distance, reverse is not necessarily equal,
    // since we normalize based on length of LHS only
    EXPECT_FLOAT_EQ(d_r->calc(a), result);
    // float factory:
    EXPECT_FLOAT_EQ(d_f->calc(b), result);
    double closeness_n = d_n->to_rawscore(result);
    double closeness_f = d_f->to_rawscore(result);
    double closeness_r = d_r->to_rawscore(result);
    double closeness_i = d_i->to_rawscore(result);
    EXPECT_DOUBLE_EQ(closeness_n, closeness_f);
    EXPECT_DOUBLE_EQ(closeness_n, closeness_r);
    EXPECT_DOUBLE_EQ(closeness_n, closeness_i);
    EXPECT_GT(closeness_n, 0.0);
    EXPECT_LE(closeness_n, 1.0);
    return result;
}

TEST(DistanceFunctionsTest, prenormalized_angular_gives_expected_score)
{
    std::vector<double> p0{0.0, 0.0, 0.0};
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p4{0.5, 0.5, sq_root_half};
    std::vector<double> p5{0.0,-1.0, 0.0};
    std::vector<double> p6{1.0, 2.0, 2.0};
    std::vector<double> p7{2.0, -1.0, -2.0};
    std::vector<double> p8{3.0, 0.0, 0.0};

    PrenormalizedAngularDistanceFunctionFactory<double> dff;
    auto pnad = dff.for_query_vector(t(p0));

    double i12 = computePrenormalizedAngularChecked(t(p1), t(p2));
    double i13 = computePrenormalizedAngularChecked(t(p1), t(p3));
    double i23 = computePrenormalizedAngularChecked(t(p2), t(p3));
    EXPECT_DOUBLE_EQ(i12, 1.0);
    EXPECT_DOUBLE_EQ(i13, 1.0);
    EXPECT_DOUBLE_EQ(i23, 1.0);

    double i14 = computePrenormalizedAngularChecked(t(p1), t(p4));
    double i24 = computePrenormalizedAngularChecked(t(p2), t(p4));
    EXPECT_DOUBLE_EQ(i14, 0.5);
    EXPECT_DOUBLE_EQ(i24, 0.5);
    double i34 = computePrenormalizedAngularChecked(t(p3), t(p4));
    EXPECT_FLOAT_EQ(i34, 1.0 - sq_root_half);

    double i25 = computePrenormalizedAngularChecked(t(p2), t(p5));
    EXPECT_DOUBLE_EQ(i25, 2.0);

    double i44 = computePrenormalizedAngularChecked(t(p4), t(p4));
    EXPECT_GE(i44, 0.0);
    EXPECT_LT(i44, 0.000001);

    double i66 = computePrenormalizedAngularChecked(t(p6), t(p6));
    EXPECT_GE(i66, 0.0);
    EXPECT_LT(i66, 0.000001);

    double i67 = computePrenormalizedAngularChecked(t(p6), t(p7));
    EXPECT_DOUBLE_EQ(i67, 13.0);
    double i68 = computePrenormalizedAngularChecked(t(p6), t(p8));
    EXPECT_DOUBLE_EQ(i68, 6.0);
    double i78 = computePrenormalizedAngularChecked(t(p7), t(p8));
    EXPECT_DOUBLE_EQ(i78, 3.0);

    double threshold = pnad->convert_threshold(0.25);
    EXPECT_DOUBLE_EQ(threshold, 0.25);
    threshold = pnad->convert_threshold(0.5);
    EXPECT_DOUBLE_EQ(threshold, 0.5);
    threshold = pnad->convert_threshold(1.0);
    EXPECT_DOUBLE_EQ(threshold, 1.0);
}


TEST(DistanceFunctionsTest, hamming_gives_expected_score)
{
    static HammingDistanceFunctionFactory<double> dff;
    std::vector<std::vector<double>>
        points{{0.0, 0.0, 0.0},
               {1.0, 0.0, 0.0},
               {0.0, 1.0, 1.0},
               {2.0, 2.0, 2.0},
               {0.5, 0.5, 0.5},
               {0.0,-1.0, 1.0},
               {1.0, 1.0, 1.0}};
    auto hamming = dff.for_query_vector(t(points[0]));
    for (const auto & p : points) {
        auto dist_fun = dff.for_query_vector(t(p));
        double h0 = dist_fun->calc(t(p));
        EXPECT_EQ(h0, 0.0);
        EXPECT_EQ(dist_fun->to_rawscore(h0), 1.0);
    }
    double d12 = dff.for_query_vector(t(points[1]))->calc(t(points[2]));
    EXPECT_EQ(d12, 3.0);
    EXPECT_DOUBLE_EQ(hamming->to_rawscore(d12), 1.0/(1.0 + 3.0));

    double d16 = dff.for_query_vector(t(points[1]))->calc(t(points[6]));
    EXPECT_EQ(d16, 2.0);
    EXPECT_DOUBLE_EQ(hamming->to_rawscore(d16), 1.0/(1.0 + 2.0));

    double d23 = dff.for_query_vector(t(points[2]))->calc(t(points[3]));
    EXPECT_EQ(d23, 3.0);
    EXPECT_DOUBLE_EQ(hamming->to_rawscore(d23), 1.0/(1.0 + 3.0));

    double d24 = dff.for_query_vector(t(points[2]))->calc(t(points[4]));
    EXPECT_EQ(d24, 3.0);
    EXPECT_DOUBLE_EQ(hamming->to_rawscore(d24), 1.0/(1.0 + 3.0));

    double d25 = dff.for_query_vector(t(points[2]))->calc(t(points[5]));
    EXPECT_EQ(d25, 1.0);
    EXPECT_DOUBLE_EQ(hamming->to_rawscore(d25), 1.0/(1.0 + 1.0));

    double threshold = hamming->convert_threshold(0.25);
    EXPECT_DOUBLE_EQ(threshold, 0.25);
    threshold = hamming->convert_threshold(0.5);
    EXPECT_DOUBLE_EQ(threshold, 0.5);
    threshold = hamming->convert_threshold(1.0);
    EXPECT_DOUBLE_EQ(threshold, 1.0);

    std::vector<Int8Float> bytes_a = { 0, 1, 2, 4, 8, 16, 32, 64, -128,  0, 1, 2, 4, 8, 16, 32, 64, -128, 0, 1, 2 };
    std::vector<Int8Float> bytes_b = { 1, 2, 2, 4, 8, 16, 32, 65, -128,  0, 1, 0, 4, 8, 16, 32, 64, -128, 0, 1, -1 };
    // expect diff:                    1  2                    1               1                                7
    HammingDistanceFunctionFactory<Int8Float> factory_int8;
    auto dist_fun = factory_int8.for_query_vector(TypedCells(bytes_a));
    EXPECT_EQ(dist_fun->calc(TypedCells(bytes_b)), 12.0);
}

TEST(GeoDegreesTest, gives_expected_score)
{
    std::vector<double> g1_sfo{37.61, -122.38};
    std::vector<double> g2_lhr{51.47, -0.46};
    std::vector<double> g3_osl{60.20, 11.08};
    std::vector<double> g4_gig{-22.8, -43.25};
    std::vector<double> g5_hkg{22.31, 113.91};
    std::vector<double> g6_trd{63.45, 10.92};
    std::vector<double> g7_syd{-33.95, 151.17};
    std::vector<double> g8_lax{33.94, -118.41};
    std::vector<double> g9_jfk{40.64, -73.78};

    auto geodeg = GeoDistanceFunctionFactory().for_query_vector(t(g6_trd));
    double g63_a = geodeg->calc(t(g3_osl));
    double g63_r = geodeg->to_rawscore(g63_a);
    double g63_km = ((1.0/g63_r)-1.0);
    EXPECT_GT(g63_km, 350);
    EXPECT_LT(g63_km, 375);

    // all distances from gcmap.com, the
    // Great Circle Mapper for airports using
    // a more accurate formula - we should agree
    // with < 1.0% deviation
    verify_geo_miles(g1_sfo, g1_sfo, 0);
    verify_geo_miles(g1_sfo, g2_lhr, 5367);
    verify_geo_miles(g1_sfo, g3_osl, 5196);
    verify_geo_miles(g1_sfo, g4_gig, 6604);
    verify_geo_miles(g1_sfo, g5_hkg, 6927);
    verify_geo_miles(g1_sfo, g6_trd, 5012);
    verify_geo_miles(g1_sfo, g7_syd, 7417);
    verify_geo_miles(g1_sfo, g8_lax, 337);
    verify_geo_miles(g1_sfo, g9_jfk, 2586);

    verify_geo_miles(g2_lhr, g1_sfo, 5367);
    verify_geo_miles(g2_lhr, g2_lhr, 0);
    verify_geo_miles(g2_lhr, g3_osl, 750);
    verify_geo_miles(g2_lhr, g4_gig, 5734);
    verify_geo_miles(g2_lhr, g5_hkg, 5994);
    verify_geo_miles(g2_lhr, g6_trd, 928);
    verify_geo_miles(g2_lhr, g7_syd, 10573);
    verify_geo_miles(g2_lhr, g8_lax, 5456);
    verify_geo_miles(g2_lhr, g9_jfk, 3451);

    verify_geo_miles(g3_osl, g1_sfo, 5196);
    verify_geo_miles(g3_osl, g2_lhr, 750);
    verify_geo_miles(g3_osl, g3_osl, 0);
    verify_geo_miles(g3_osl, g4_gig, 6479);
    verify_geo_miles(g3_osl, g5_hkg, 5319);
    verify_geo_miles(g3_osl, g6_trd, 226);
    verify_geo_miles(g3_osl, g7_syd, 9888);
    verify_geo_miles(g3_osl, g8_lax, 5345);
    verify_geo_miles(g3_osl, g9_jfk, 3687);

    verify_geo_miles(g4_gig, g1_sfo, 6604);
    verify_geo_miles(g4_gig, g2_lhr, 5734);
    verify_geo_miles(g4_gig, g3_osl, 6479);
    verify_geo_miles(g4_gig, g4_gig, 0);
    verify_geo_miles(g4_gig, g5_hkg, 10989);
    verify_geo_miles(g4_gig, g6_trd, 6623);
    verify_geo_miles(g4_gig, g7_syd, 8414);
    verify_geo_miles(g4_gig, g8_lax, 6294);
    verify_geo_miles(g4_gig, g9_jfk, 4786);

    verify_geo_miles(g5_hkg, g1_sfo, 6927);
    verify_geo_miles(g5_hkg, g2_lhr, 5994);
    verify_geo_miles(g5_hkg, g3_osl, 5319);
    verify_geo_miles(g5_hkg, g4_gig, 10989);
    verify_geo_miles(g5_hkg, g5_hkg, 0);
    verify_geo_miles(g5_hkg, g6_trd, 5240);
    verify_geo_miles(g5_hkg, g7_syd, 4581);
    verify_geo_miles(g5_hkg, g8_lax, 7260);
    verify_geo_miles(g5_hkg, g9_jfk, 8072);

    verify_geo_miles(g6_trd, g1_sfo, 5012);
    verify_geo_miles(g6_trd, g2_lhr, 928);
    verify_geo_miles(g6_trd, g3_osl, 226);
    verify_geo_miles(g6_trd, g4_gig, 6623);
    verify_geo_miles(g6_trd, g5_hkg, 5240);
    verify_geo_miles(g6_trd, g6_trd, 0);
    verify_geo_miles(g6_trd, g7_syd, 9782);
    verify_geo_miles(g6_trd, g8_lax, 5171);
    verify_geo_miles(g6_trd, g9_jfk, 3611);

    verify_geo_miles(g7_syd, g1_sfo, 7417);
    verify_geo_miles(g7_syd, g2_lhr, 10573);
    verify_geo_miles(g7_syd, g3_osl, 9888);
    verify_geo_miles(g7_syd, g4_gig, 8414);
    verify_geo_miles(g7_syd, g5_hkg, 4581);
    verify_geo_miles(g7_syd, g6_trd, 9782);
    verify_geo_miles(g7_syd, g7_syd, 0);
    verify_geo_miles(g7_syd, g8_lax, 7488);
    verify_geo_miles(g7_syd, g9_jfk, 9950);

    verify_geo_miles(g8_lax, g1_sfo, 337);
    verify_geo_miles(g8_lax, g2_lhr, 5456);
    verify_geo_miles(g8_lax, g3_osl, 5345);
    verify_geo_miles(g8_lax, g4_gig, 6294);
    verify_geo_miles(g8_lax, g5_hkg, 7260);
    verify_geo_miles(g8_lax, g6_trd, 5171);
    verify_geo_miles(g8_lax, g7_syd, 7488);
    verify_geo_miles(g8_lax, g8_lax, 0);
    verify_geo_miles(g8_lax, g9_jfk, 2475);

    verify_geo_miles(g9_jfk, g1_sfo, 2586);
    verify_geo_miles(g9_jfk, g2_lhr, 3451);
    verify_geo_miles(g9_jfk, g3_osl, 3687);
    verify_geo_miles(g9_jfk, g4_gig, 4786);
    verify_geo_miles(g9_jfk, g5_hkg, 8072);
    verify_geo_miles(g9_jfk, g6_trd, 3611);
    verify_geo_miles(g9_jfk, g7_syd, 9950);
    verify_geo_miles(g9_jfk, g8_lax, 2475);
    verify_geo_miles(g9_jfk, g9_jfk, 0);
}


double computeTransformedMipsChecked(TypedCells a, TypedCells b, bool check_insert = true) {
    MipsDistanceFunctionFactory<float> flt_dff;
    MipsDistanceFunctionFactory<double> dbl_dff;

    auto d_n = dbl_dff.for_query_vector(a);
    auto d_f = flt_dff.for_query_vector(a);
    auto d_r = dbl_dff.for_query_vector(b);
    // normal:
    double result = d_n->calc(b);
    // reverse:
    EXPECT_DOUBLE_EQ(d_r->calc(a), result);
    // float factory:
    EXPECT_FLOAT_EQ(d_f->calc(b), result);
    double closeness_n = d_n->to_rawscore(result);
    double closeness_f = d_f->to_rawscore(result);
    double closeness_r = d_r->to_rawscore(result);
    EXPECT_DOUBLE_EQ(closeness_n, closeness_f);
    EXPECT_DOUBLE_EQ(closeness_n, closeness_r);
    EXPECT_DOUBLE_EQ(closeness_n, -result);
    EXPECT_DOUBLE_EQ(result, d_n->to_distance(closeness_n));
    EXPECT_DOUBLE_EQ(result, d_f->to_distance(closeness_f));
    EXPECT_DOUBLE_EQ(result, d_r->to_distance(closeness_r));
    if (check_insert) {
        auto d_i = dbl_dff.for_insertion_vector(a);
        EXPECT_DOUBLE_EQ(d_i->calc(b), result);
    }
    return result;
}

TEST(DistanceFunctionsTest, transformed_mips_basic_scores)
{
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p4{0.5, 0.5, sq_root_half};
    std::vector<double> p5{0.0,-1.0, 0.0};

    double i12 = computeTransformedMipsChecked(t(p1), t(p2));
    double i13 = computeTransformedMipsChecked(t(p1), t(p3));
    double i23 = computeTransformedMipsChecked(t(p2), t(p3));
    EXPECT_DOUBLE_EQ(i12, 0.0);
    EXPECT_DOUBLE_EQ(i13, 0.0);
    EXPECT_DOUBLE_EQ(i23, 0.0);

    double i14 = computeTransformedMipsChecked(t(p1), t(p4));
    double i24 = computeTransformedMipsChecked(t(p2), t(p4));
    EXPECT_DOUBLE_EQ(i14, -0.5);
    EXPECT_DOUBLE_EQ(i24, -0.5);

    double i34 = computeTransformedMipsChecked(t(p3), t(p4));
    EXPECT_FLOAT_EQ(i34, -sq_root_half);

    double i25 = computeTransformedMipsChecked(t(p2), t(p5));
    EXPECT_DOUBLE_EQ(i25, 1.0);

    double i44 = computeTransformedMipsChecked(t(p4), t(p4));
    EXPECT_DOUBLE_EQ(i44, -1.0);

    std::vector<double> p6{ 0.0,  4.0, -4.0};
    std::vector<double> p7{-4.0,  0.0,  4.0};
    std::vector<double> p8{ 4.0, -4.0,  0.0};

    double i66 = computeTransformedMipsChecked(t(p6), t(p6));
    EXPECT_DOUBLE_EQ(i66, -32.0);

    double i67 = computeTransformedMipsChecked(t(p6), t(p7));
    EXPECT_DOUBLE_EQ(i67, 16.0);

    double i68 = computeTransformedMipsChecked(t(p6), t(p8));
    EXPECT_DOUBLE_EQ(i68, 16.0);

    double i78 = computeTransformedMipsChecked(t(p7), t(p8));
    EXPECT_DOUBLE_EQ(i78, 16.0);
}

TEST(DistanceFunctionsTest, transformed_mips_growing_norm)
{
    std::vector<double> p1{1.0, 0.0, 0.0};
    std::vector<double> p2{0.0, 1.0, 0.0};
    std::vector<double> p3{0.0, 0.0, 1.0};
    std::vector<double> p6{ 0.0,  4.0, -4.0};
    std::vector<double> p7{-4.0,  0.0,  4.0};
    std::vector<double> p8{ 4.0, -4.0,  0.0};

    MipsDistanceFunctionFactory<double> dff;
    auto f = dff.for_insertion_vector(t(p1));
    EXPECT_DOUBLE_EQ(-1.0, f->calc(t(p1)));
    EXPECT_DOUBLE_EQ(0.0, f->calc(t(p2)));
    EXPECT_DOUBLE_EQ(0.0, f->calc(t(p3)));
    EXPECT_DOUBLE_EQ(0.0, f->calc(t(p6)));
    EXPECT_DOUBLE_EQ(4.0, f->calc(t(p7)));
    EXPECT_DOUBLE_EQ(-4.0, f->calc(t(p8)));

    // now "insert" a bigger vector
    f = dff.for_insertion_vector(t(p6));
    EXPECT_DOUBLE_EQ(0.0, f->calc(t(p1)));
    EXPECT_DOUBLE_EQ(-4.0, f->calc(t(p2)));
    EXPECT_DOUBLE_EQ(4.0, f->calc(t(p3)));
    EXPECT_DOUBLE_EQ(-32.0, f->calc(t(p6)));
    EXPECT_DOUBLE_EQ(16.0, f->calc(t(p7)));
    EXPECT_DOUBLE_EQ(16.0, f->calc(t(p8)));

    // now max squared norm is 32, so p1 is "closer" to itself
    f = dff.for_insertion_vector(t(p1));
    EXPECT_DOUBLE_EQ(-32.0, f->calc(t(p1)));

    // also closer to other small vectors
    EXPECT_DOUBLE_EQ(-31.0, f->calc(t(p2)));
    EXPECT_DOUBLE_EQ(-31.0, f->calc(t(p3)));
    std::vector<double> p9a{-5.0, 0.0, 0.0};
    // 32 - (-5)^2 = 32 - 25 = 7
    EXPECT_DOUBLE_EQ(5.0 - std::sqrt(31.0 * 7), f->calc(t(p9a)));
    std::vector<double> p9b{-3.0, 4.0, 0.0};
    std::vector<double> p9c{0.0, -3.0, 4.0};
    std::vector<double> p9d{-4.0, 0.0, 3.0};
    EXPECT_DOUBLE_EQ(3.0 - std::sqrt(31.0 * 7), f->calc(t(p9b)));
    EXPECT_DOUBLE_EQ(0.0 - std::sqrt(31.0 * 7), f->calc(t(p9c)));
    EXPECT_DOUBLE_EQ(4.0 - std::sqrt(31.0 * 7), f->calc(t(p9d)));

    // but only for insert:
    f = dff.for_query_vector(t(p1));
    EXPECT_DOUBLE_EQ(-1.0, f->calc(t(p1)));

    std::vector<double> big{-100, 100, -100};
    f = dff.for_insertion_vector(t(big));
    EXPECT_DOUBLE_EQ(100.0, f->calc(t(p1)));

    // much bigger numbers expected:
    f = dff.for_insertion_vector(t(p1));
    EXPECT_DOUBLE_EQ(-30000.0, f->calc(t(p1)));
    EXPECT_DOUBLE_EQ(-29999.0, f->calc(t(p2)));
    EXPECT_DOUBLE_EQ(-29999.0, f->calc(t(p3)));
    // all these have larger distance:
    EXPECT_LT(-29999.0, f->calc(t(p6)));
    EXPECT_LT(-29999.0, f->calc(t(p7)));
    EXPECT_LT(-29999.0, f->calc(t(p8)));
    EXPECT_LT(-29999.0, f->calc(t(p9a)));
    EXPECT_LT(-29999.0, f->calc(t(p9b)));
    EXPECT_LT(-29999.0, f->calc(t(p9c)));
    EXPECT_LT(-29999.0, f->calc(t(p9d)));
    // but not by much:
    EXPECT_GT(-29900.0, f->calc(t(p6)));
    EXPECT_GT(-29900.0, f->calc(t(p7)));
    EXPECT_GT(-29900.0, f->calc(t(p8)));
    EXPECT_GT(-29900.0, f->calc(t(p9a)));
    EXPECT_GT(-29900.0, f->calc(t(p9b)));
    EXPECT_GT(-29900.0, f->calc(t(p9c)));
    EXPECT_GT(-29900.0, f->calc(t(p9d)));
}


GTEST_MAIN_RUN_ALL_TESTS()

