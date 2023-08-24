// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/sparse_join_reduce_plan.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::instruction;

using Handle = vespalib::SharedStringRepo::Handle;

Value::UP val(const vespalib::string &value_desc) {
    return value_from_spec(GenSpec::from_desc(value_desc), FastValueBuilderFactory::get());
}

Handle make_handle(string_id id) {
    return Handle::handle_from_id(id);
}

Handle make_handle(const vespalib::string &str) {
    return Handle(str);
}

struct Event {
    size_t lhs_idx;
    size_t rhs_idx;
    std::vector<Handle> res_addr;
    template <typename ADDR>
    Event(size_t a, size_t b, ADDR addr)
      : lhs_idx(a), rhs_idx(b), res_addr()
    {
        for (auto label: addr) {
            res_addr.push_back(make_handle(label));
        }
    }
    auto operator<=>(const Event &rhs) const = default;
};

struct Trace {
    size_t estimate;
    std::vector<Event> events;
    Trace(size_t estimate_in)
      : estimate(estimate_in), events() {}
    void add_raw(size_t lhs_idx, size_t rhs_idx, ConstArrayRef<string_id> res_addr) {
        events.emplace_back(lhs_idx, rhs_idx, res_addr);
    }
    Trace &add(size_t lhs_idx, size_t rhs_idx, std::vector<vespalib::string> res_addr) {
        events.emplace_back(lhs_idx, rhs_idx, res_addr);
        return *this;
    }
    auto operator<=>(const Trace &rhs) const = default;
};

std::ostream &
operator<<(std::ostream &os, const Event &event) {
    os << "{ lhs: " << event.lhs_idx << ", rhs: " << event.rhs_idx << ", addr: [";
    for (size_t i = 0; i < event.res_addr.size(); ++i) {
        if (i > 0) {
            os << ", ";
        }
        os << event.res_addr[i].as_string();
    }
    os << "] }";
    return os;
}

std::ostream &
operator<<(std::ostream &os, const Trace &trace) {
    os << "estimate: " << trace.estimate << "\n";
    for (const Event &event: trace.events) {
        os << "      " << event << "\n";
    }
    return os;
}

Trace trace(size_t est) { return Trace(est); }

Trace trace(const vespalib::string &a_desc, const vespalib::string &b_desc,
            const std::vector<vespalib::string> &reduce_dims)
{
    auto a = val(a_desc);
    auto b = val(b_desc);
    auto res_type = ValueType::join(a->type(), b->type());
    if (!reduce_dims.empty()) {
        res_type = res_type.reduce(reduce_dims);
    }
    SparseJoinReducePlan plan(a->type(), b->type(), res_type);
    Trace trace(plan.estimate_result_size(a->index(), b->index()));
    plan.execute(a->index(), b->index(),
                 [&trace](size_t lhs_idx, size_t rhs_idx, ConstArrayRef<string_id> res_addr) {
                     trace.add_raw(lhs_idx, rhs_idx, res_addr);
                 });
    return trace;
}

//-----------------------------------------------------------------------------

TEST(SparseJoinReducePlanTest, simple_dense) {
    EXPECT_EQ(trace("x10", "x10", {}), trace(1).add(0, 0, {}));
    EXPECT_EQ(trace("x10", "x10", {"x"}), trace(1).add(0, 0, {}));
}

TEST(SparseJoinReducePlanTest, many_dimensions) {
    EXPECT_EQ(trace("a1_1b1_2c1_3d1_4", "c1_3d1_4e1_5f1_6", {"b","d","f"}), trace(1).add(0, 0,  {"1", "3", "5"}));
    EXPECT_EQ(trace("c1_3d1_4e1_5f1_6", "a1_1b1_2c1_3d1_4", {"b","d","f"}), trace(1).add(0, 0,  {"1", "3", "5"}));
}

TEST(SparseJoinReducePlanTest, traverse_order_can_be_swapped) {
    EXPECT_EQ(trace("x2_4", "y3_1", {}), trace(6).add(0, 0, {"4", "1"}).add(0, 1, {"4", "2"}).add(0, 2, {"4", "3"})
                                                 .add(1, 0, {"8", "1"}).add(1, 1, {"8", "2"}).add(1, 2, {"8", "3"}));
    EXPECT_EQ(trace("y3_1", "x2_4", {}), trace(6).add(0, 0, {"4", "1"}).add(1, 0, {"4", "2"}).add(2, 0, {"4", "3"})
                                                 .add(0, 1, {"8", "1"}).add(1, 1, {"8", "2"}).add(2, 1, {"8", "3"}));
}

//-----------------------------------------------------------------------------

TEST(SparseJoinReducePlanTest, full_overlap_no_reduce) {
    EXPECT_EQ(trace("x4_1", "x2_2", {}), trace(2).add(1, 0, {"2"}).add(3, 1, {"4"}));
    EXPECT_EQ(trace("x1_1", "x0_0", {}), trace(0));
    EXPECT_EQ(trace("x0_0", "x1_1", {}), trace(0));
}

TEST(SparseJoinReducePlanTest, full_overlap_reduce_all) {
    EXPECT_EQ(trace("x4_1", "x2_2", {"x"}), trace(1).add(1, 0, {}).add(3, 1, {}));
    EXPECT_EQ(trace("x1_1", "x0_0", {"x"}), trace(1));
    EXPECT_EQ(trace("x0_0", "x1_1", {"x"}), trace(1));
}

//-----------------------------------------------------------------------------

TEST(SparseJoinReducePlanTest, no_overlap_no_reduce) {
    EXPECT_EQ(trace("x2_1", "y3_1", {}), trace(6).add(0, 0, {"1", "1"}).add(0, 1, {"1", "2"}).add(0, 2, {"1", "3"})
                                                 .add(1, 0, {"2", "1"}).add(1, 1, {"2", "2"}).add(1, 2, {"2", "3"}));
    EXPECT_EQ(trace("x1_1", "y0_0", {}), trace(0));
    EXPECT_EQ(trace("y0_0", "x1_1", {}), trace(0));
}

TEST(SparseJoinReducePlanTest, no_overlap_reduce_last) {
    EXPECT_EQ(trace("x2_1", "y3_1", {"y"}), trace(2).add(0, 0, {"1"}).add(0, 1, {"1"}).add(0, 2, {"1"})
                                                    .add(1, 0, {"2"}).add(1, 1, {"2"}).add(1, 2, {"2"}));
    EXPECT_EQ(trace("x1_1", "y0_0", {"y"}), trace(0));
    EXPECT_EQ(trace("y0_0", "x1_1", {"y"}), trace(0));
}

TEST(SparseJoinReducePlanTest, no_overlap_reduce_first) {
    EXPECT_EQ(trace("x2_1", "y3_1", {"x"}), trace(3).add(0, 0, {"1"}).add(0, 1, {"2"}).add(0, 2, {"3"})
                                                    .add(1, 0, {"1"}).add(1, 1, {"2"}).add(1, 2, {"3"}));
    EXPECT_EQ(trace("x0_0", "y1_1", {"x"}), trace(0));
    EXPECT_EQ(trace("y1_1", "x0_0", {"x"}), trace(0));
}

TEST(SparseJoinReducePlanTest, no_overlap_reduce_all) {
    EXPECT_EQ(trace("x2_1", "y3_1", {"x", "y"}), trace(1).add(0, 0, {}).add(0, 1, {}).add(0, 2, {})
                                                         .add(1, 0, {}).add(1, 1, {}).add(1, 2, {}));
    EXPECT_EQ(trace("x0_0", "y1_1", {"x", "y"}), trace(1));
    EXPECT_EQ(trace("y1_1", "x0_0", {"x", "y"}), trace(1));
}

//-----------------------------------------------------------------------------

TEST(SparseJoinReducePlanTest, partial_overlap_no_reduce) {
    EXPECT_EQ(trace("x2_1y1_1", "y1_1z2_3", {}), trace(2).add(0, 0, {"1", "1", "3"}).add(0, 1, {"1", "1", "6"})
                                                         .add(1, 0, {"2", "1", "3"}).add(1, 1, {"2", "1", "6"}));
    EXPECT_EQ(trace("x2_1y1_1", "y1_2z3_1", {}), trace(2));
    EXPECT_EQ(trace("x2_1y1_1", "y0_0z2_3", {}), trace(0));
}

TEST(SparseJoinReducePlanTest, partial_overlap_reduce_first) {
    EXPECT_EQ(trace("x2_1y1_1", "y1_1z2_3", {"x"}), trace(2).add(0, 0, {"1", "3"}).add(0, 1, {"1", "6"})
                                                            .add(1, 0, {"1", "3"}).add(1, 1, {"1", "6"}));
    EXPECT_EQ(trace("x2_1y1_1", "y1_2z3_1", {"x"}), trace(2));
    EXPECT_EQ(trace("x2_1y1_1", "y0_0z2_3", {"x"}), trace(0));
}

TEST(SparseJoinReducePlanTest, partial_overlap_reduce_middle) {
    EXPECT_EQ(trace("x2_1y1_1", "y1_1z2_3", {"y"}), trace(2).add(0, 0, {"1", "3"}).add(0, 1, {"1", "6"})
                                                            .add(1, 0, {"2", "3"}).add(1, 1, {"2", "6"}));
    EXPECT_EQ(trace("x2_1y1_1", "y1_2z3_1", {"y"}), trace(2));
    EXPECT_EQ(trace("x2_1y1_1", "y0_0z2_3", {"y"}), trace(0));
}

TEST(SparseJoinReducePlanTest, partial_overlap_reduce_last) {
    EXPECT_EQ(trace("x2_1y1_1", "y1_1z2_3", {"z"}), trace(2).add(0, 0, {"1", "1"}).add(0, 1, {"1", "1"})
                                                            .add(1, 0, {"2", "1"}).add(1, 1, {"2", "1"}));
    EXPECT_EQ(trace("x2_1y1_1", "y1_2z3_1", {"z"}), trace(2));
    EXPECT_EQ(trace("x2_1y1_1", "y0_0z2_3", {"z"}), trace(0));
}

TEST(SparseJoinReducePlanTest, partial_overlap_reduce_all) {
    EXPECT_EQ(trace("x2_1y1_1", "y1_1z2_3", {"x", "y", "z"}), trace(1).add(0, 0, {}).add(0, 1, {})
                                                                      .add(1, 0, {}).add(1, 1, {}));
    EXPECT_EQ(trace("x2_1y1_1", "y1_2z3_1", {"x", "y", "z"}), trace(1));
    EXPECT_EQ(trace("x2_1y1_1", "y0_0z2_3", {"x", "y", "z"}), trace(1));
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
