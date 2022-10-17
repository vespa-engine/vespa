// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/mapped_lookup.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

struct FunInfo {
    using LookFor = MappedLookup;
    bool expect_mutable;
    FunInfo(bool expect_mutable_in)
      : expect_mutable(expect_mutable_in) {}
    void verify(const LookFor &fun) const {
        EXPECT_EQ(fun.result_is_mutable(), expect_mutable);
    }
};

void verify_optimized_cell_types(const vespalib::string &expr) {
    auto same_stable_types = CellTypeSpace(CellTypeUtils::list_stable_types(), 2).same();
    auto same_unstable_types = CellTypeSpace(CellTypeUtils::list_unstable_types(), 2).same();
    auto different_types = CellTypeSpace(CellTypeUtils::list_types(), 2).different();
    EvalFixture::verify<FunInfo>(expr, {FunInfo(false)}, same_stable_types);
    EvalFixture::verify<FunInfo>(expr, {}, same_unstable_types);
    EvalFixture::verify<FunInfo>(expr, {}, different_types);
}

void verify_optimized(const vespalib::string &expr, bool expect_mutable = false) {
    CellTypeSpace just_float({CellType::FLOAT}, 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo(expect_mutable)}, just_float);
}

void verify_not_optimized(const vespalib::string &expr) {
    CellTypeSpace just_float({CellType::FLOAT}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_float);
}

//-----------------------------------------------------------------------------

TEST(MappedLookup, expression_can_be_optimized) {
    verify_optimized_cell_types("reduce(x1_1*x5_1y5,sum,x)");
}

TEST(MappedLookup, key_and_map_can_be_swapped) {
    verify_optimized("reduce(x5_1y5*x1_1,sum,x)");
}

TEST(MappedLookup, trivial_indexed_dimensions_are_ignored) {
    verify_optimized("reduce(c1d1x1_1*a1b1x5_1y5,sum,x,c,d,a,b)");
    verify_optimized("reduce(c1d1x1_1*a1b1x5_1y5,sum,x,c,a)");
    verify_optimized("reduce(c1d1x1_1*a1b1x5_1y5,sum,x)");
}

TEST(MappedLookup, mutable_map_gives_mutable_result) {
    verify_optimized("reduce(@x1_1*x5_1y5,sum,x)", false);
    verify_optimized("reduce(x1_1*@x5_1y5,sum,x)", true);
    verify_optimized("reduce(@x5_1y5*x1_1,sum,x)", true);
    verify_optimized("reduce(x5_1y5*@x1_1,sum,x)", false);
    verify_optimized("reduce(@x5_1y5*@x1_1,sum,x)", true);
}

TEST(MappedLookup, similar_expressions_are_not_optimized) {
    verify_not_optimized("reduce(x1_1*x5_1,sum,x)");
    verify_not_optimized("reduce(x1_1*x5_1y5,sum,y)");
    verify_not_optimized("reduce(x1_1*x5_1y5,sum)");
    verify_not_optimized("reduce(x1_1*x5_1y5z8,sum,x,y)");
    verify_not_optimized("reduce(x1_1*x5_1y5,prod,x)");
    verify_not_optimized("reduce(x1_1y3_3*x5_1y3_2z5,sum,x)");
    verify_not_optimized("reduce(x1_1y3_3*x5_1y3_2z5,sum,x,y)");
    verify_not_optimized("reduce(x1_1y5*x5_1z5,sum,x)");
}

enum class KeyType { EMPTY, UNIT, SCALING, MULTI };
GenSpec make_key(KeyType type) {
    switch (type) {
    case KeyType::EMPTY:   return GenSpec().cells_float().map("x", {});
    case KeyType::UNIT:    return GenSpec().cells_float().map("x", {"1"}).seq({1.0});
    case KeyType::SCALING: return GenSpec().cells_float().map("x", {"1"}).seq({5.0});
    case KeyType::MULTI:   return GenSpec().cells_float().map("x", {"1", "2", "3"}).seq({1.0});
    }
    abort();
}

enum class MapType { EMPTY, SMALL, MEDIUM, LARGE1, LARGE2, LARGE3 };
GenSpec make_map(MapType type) {
    switch (type) {
    case MapType::EMPTY:  return GenSpec().cells_float().idx("y", 5).map("x", {});
    case MapType::SMALL:  return GenSpec().cells_float().idx("y", 5).map("x", {"1"}).seq(N(10));
    case MapType::MEDIUM: return GenSpec().cells_float().idx("y", 5).map("x", {"1", "2"}).seq(N(10));
    case MapType::LARGE1:  return GenSpec().cells_float().idx("y", 5).map("x", 5, 100).seq(N(10));
    case MapType::LARGE2:  return GenSpec().cells_float().idx("y", 5).map("x", 5, 2).seq(N(10));
    case MapType::LARGE3:  return GenSpec().cells_float().idx("y", 5).map("x", 5, 1).seq(N(10));
    }
    abort();
}

std::vector<MapType> map_types_for(KeyType key_type) {
    if (key_type == KeyType::MULTI) {
        return {MapType::EMPTY, MapType::SMALL, MapType::MEDIUM, MapType::LARGE1, MapType::LARGE2, MapType::LARGE3};
    } else {
        return {MapType::EMPTY, MapType::SMALL, MapType::MEDIUM};
    }
}

TEST(MappedLookup, test_case_interactions) {
    for (bool mutable_map: {false, true}) {
        vespalib::string expr = mutable_map ? "reduce(a*@b,sum,x)" : "reduce(a*b,sum,x)";
        for (KeyType key_type: {KeyType::EMPTY, KeyType::UNIT, KeyType::SCALING, KeyType::MULTI}) {
            auto key = make_key(key_type);
            for (MapType map_type: map_types_for(key_type)) {
                auto map = make_map(map_type);
                EvalFixture::verify<FunInfo>(expr, {FunInfo(mutable_map)}, {key,map});
            }
        }
    }
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
