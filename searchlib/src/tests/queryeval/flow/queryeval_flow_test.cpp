// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <random>

constexpr size_t loop_cnt = 64;
constexpr size_t max_work = 1; // 500'000'000;
constexpr bool dump_unexpected = false;
constexpr bool verbose = false;

using namespace search::queryeval;

// at what in-flow (non-strict) rate is it equally cheap to be (forced) strict and non-strict
double strict_crossover(const FlowStats &stats) {
    return (stats.strict_cost - 0.2 * stats.estimate) / (stats.cost - 0.2);
}

// how much cost do we save by having an iterator strict vs non-strict with the given in-flow
double strict_gain(const FlowStats &stats, InFlow in_flow) {
    if (in_flow.strict()) {
        return stats.cost - stats.strict_cost;
    } else {
        return (in_flow.rate() * stats.cost) - flow::forced_strict_cost(stats, in_flow.rate());
    }
}

template <typename FLOW>
double ordered_cost_of(const std::vector<FlowStats> &data, InFlow in_flow, bool allow_force_strict) {
    return flow::ordered_cost_of(flow::DirectAdapter(), data, FLOW(in_flow), allow_force_strict);
}

template <typename FLOW>
double dual_ordered_cost_of(const std::vector<FlowStats> &data, InFlow in_flow, bool allow_force_strict) {
    double result = flow::ordered_cost_of(flow::DirectAdapter(), data, FLOW(in_flow), allow_force_strict);
    AnyFlow any_flow = AnyFlow::create<FLOW>(in_flow);
    double total_cost = 0.0;
    for (const auto &item: data) {
        double child_cost = flow::min_child_cost(InFlow(any_flow.strict(), any_flow.flow()), item, allow_force_strict);
        any_flow.update_cost(total_cost, child_cost);
        any_flow.add(item.estimate);
    }
    EXPECT_DOUBLE_EQ(total_cost, result);
    return result;
}

std::vector<FlowStats> gen_data(size_t size) {
    static std::mt19937 gen;
    static std::uniform_real_distribution<double>    estimate(0.0,  1.0);
    static std::uniform_real_distribution<double>        cost(1.0, 10.0);
    std::vector<FlowStats> result;
    result.reserve(size);
    for (size_t i = 0; i < size; ++i) {
        double est = estimate(gen);
        std::uniform_real_distribution<double> strict_cost(est,  5.0);
        result.emplace_back(est, cost(gen), strict_cost(gen));
    }
    if (size == 0) {
        gen.seed(gen.default_seed);
    }
    return result;
}
void re_seed() { gen_data(0); }

size_t count_perms(size_t n) {
    return (n <= 1) ? 1 : count_perms(n-1) * n;
};

template <typename T, typename F>
void each_perm(std::vector<T> &data, size_t k, F fun) {
    if (k <= 1) {
        fun(const_cast<const std::vector<T> &>(data));
    } else {
        each_perm(data, k-1, fun);
        for (size_t i = 0; i < k-1; ++i) {
            if (k & 1) {
                std::swap(data[0], data[k-1]);
            } else {
                std::swap(data[i], data[k-1]);
            }
            each_perm(data, k-1, fun);
        }
    }
}

template <typename T, typename F>
void each_perm(std::vector<T> &data, F fun) {
    each_perm(data, data.size(), fun);
}

TEST(FlowTest, strict_crossover_and_gain) {
    auto list = gen_data(64);
    for (const auto &item: list) {
        double limit = strict_crossover(item);
        double gain = strict_gain(item, limit);
        EXPECT_NEAR(gain, 0.0, 1e-9);
    }
}

TEST(FlowTest, perm_test) {
    std::set<std::vector<int>> seen;
    std::vector<int> data = {1,2,3,4,5};
    auto hook = [&](const std::vector<int> &perm) {
                    EXPECT_EQ(perm.size(), 5);
                    seen.insert(perm);
                };
    each_perm(data, hook);
    EXPECT_EQ(seen.size(), 120);
}

template <template <typename> typename ORDER>
void verify_ordering_is_strict_weak() {
    auto cmp = ORDER(flow::DirectAdapter());
    auto input = gen_data(7);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.0, 1.5, 0.5);
    input.emplace_back(0.0, 1.5, 0.5);
    input.emplace_back(0.5, 0.0, 0.5);
    input.emplace_back(0.5, 0.0, 0.5);
    input.emplace_back(0.5, 1.5, 0.0);
    input.emplace_back(0.5, 1.5, 0.0);
    input.emplace_back(0.0, 0.0, 0.0);
    input.emplace_back(0.0, 0.0, 0.0);
    std::vector<FlowStats> output;
    for (const FlowStats &in: input) {
        EXPECT_FALSE(cmp(in, in)); // Irreflexivity
        size_t out_idx = 0;
        bool lower = false;
        bool upper = false;
        for (const FlowStats &out: output) {
            if (cmp(out, in)) {
                EXPECT_FALSE(cmp(in, out)); // Antisymmetry
                EXPECT_FALSE(lower); // Transitivity
                EXPECT_FALSE(upper); // Transitivity
                ++out_idx;
            } else {
                lower = true;
                if (cmp(in, out)) {
                    upper = true;
                } else {
                    EXPECT_FALSE(upper); // Transitivity
                }
            }
        }
        output.insert(output.begin() + out_idx, in);
    }
}

TEST(FlowTest, and_ordering_is_strict_weak) {
    verify_ordering_is_strict_weak<flow::MinAndCost>();
}

TEST(FlowTest, or_ordering_is_strict_weak) {
    verify_ordering_is_strict_weak<flow::MinOrCost>();
}

struct ExpectFlow {
    double flow;
    double est;
    bool strict;
};

std::vector<FlowStats> make_flow_stats(const std::vector<double> &est_list, size_t n) {
    std::vector<FlowStats> result;
    for (size_t i = 0; i < n; ++i) {
        result.emplace_back(est_list[i], 123.0, 456.0);
    }
    return result;
}

void verify_flow(auto flow, const std::vector<double> &est_list, const std::vector<ExpectFlow> &expect) {
    AnyFlow any_flow = AnyFlow::create<decltype(flow)>(InFlow(flow.strict(), flow.flow()));
    ASSERT_EQ(est_list.size() + 1, expect.size());
    for (size_t i = 0; i < est_list.size(); ++i) {
        EXPECT_DOUBLE_EQ(any_flow.flow(), flow.flow());
        EXPECT_EQ(any_flow.strict(), flow.strict());
        EXPECT_DOUBLE_EQ(flow.flow(), expect[i].flow);
        EXPECT_EQ(flow.strict(), expect[i].strict);
        EXPECT_DOUBLE_EQ(flow.estimate_of(make_flow_stats(est_list, i)), expect[i].est);
        any_flow.add(est_list[i]);
        flow.add(est_list[i]);
    }
    EXPECT_DOUBLE_EQ(any_flow.flow(), flow.flow());
    EXPECT_EQ(any_flow.strict(), flow.strict());
    EXPECT_DOUBLE_EQ(flow.flow(), expect.back().flow);
    EXPECT_EQ(flow.strict(), expect.back().strict);
    EXPECT_DOUBLE_EQ(flow.estimate_of(make_flow_stats(est_list, est_list.size())), expect.back().est);
}

TEST(FlowTest, full_and_flow) {
    for (bool strict: {false, true}) {
        verify_flow(AndFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.4, 0.4, false},
                     {0.4*0.7, 0.4*0.7, false},
                     {0.4*0.7*0.2, 0.4*0.7*0.2, false}});
    }
}

TEST(FlowTest, partial_and_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(AndFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.4, 0.4, false},
                     {in*0.4*0.7, 0.4*0.7, false},
                     {in*0.4*0.7*0.2, 0.4*0.7*0.2, false}});
    }
}

TEST(FlowTest, full_or_flow) {
    verify_flow(OrFlow(false), {0.4, 0.7, 0.2},
                {{1.0, 0.0, false},
                 {0.6, 1.0-0.6, false},
                 {0.6*0.3, 1.0-0.6*0.3, false},
                 {0.6*0.3*0.8, 1.0-0.6*0.3*0.8, false}});
    verify_flow(OrFlow(true), {0.4, 0.7, 0.2},
                {{1.0, 0.0, true},
                 {1.0, 1.0-0.6, true},
                 {1.0, 1.0-0.6*0.3, true},
                 {1.0, 1.0-0.6*0.3*0.8, true}});
}

TEST(FlowTest, partial_or_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(OrFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.6, 1.0-0.6, false},
                     {in*0.6*0.3, 1.0-0.6*0.3, false},
                     {in*0.6*0.3*0.8, 1.0-0.6*0.3*0.8, false}});
    }
}

TEST(FlowTest, full_and_not_flow) {
    for (bool strict: {false, true}) {
        verify_flow(AndNotFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.4, 0.4, false},
                     {0.4*0.3, 0.4*0.3, false},
                     {0.4*0.3*0.8, 0.4*0.3*0.8, false}});
    }
}

TEST(FlowTest, partial_and_not_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(AndNotFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.4, 0.4, false},
                     {in*0.4*0.3, 0.4*0.3, false},
                     {in*0.4*0.3*0.8, 0.4*0.3*0.8, false}});
    }
}

TEST(FlowTest, full_rank_flow) {
    for (bool strict: {false, true}) {
        verify_flow(RankFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false}});
    }
}

TEST(FlowTest, partial_rank_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(RankFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false}});
    }
}

TEST(FlowTest, full_blender_flow) {
    for (bool strict: {false, true}) {
        verify_flow(BlenderFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {1.0, 1.0-0.6, strict},
                     {1.0, 1.0-0.6*0.3, strict},
                     {1.0, 1.0-0.6*0.3*0.8, strict}});
    }
}

TEST(FlowTest, partial_blender_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(BlenderFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in, 1.0-0.6, false},
                     {in, 1.0-0.6*0.3, false},
                     {in, 1.0-0.6*0.3*0.8, false}});
    }
}

TEST(FlowTest, in_flow_strict_vs_rate_interaction) {
    EXPECT_EQ(InFlow(true).strict(), true);
    EXPECT_EQ(InFlow(true).rate(), 1.0);
    EXPECT_EQ(InFlow(false).strict(), false);
    EXPECT_EQ(InFlow(false).rate(), 1.0);
    EXPECT_EQ(InFlow(0.5).strict(), false);
    EXPECT_EQ(InFlow(0.5).rate(), 0.5);
    EXPECT_EQ(InFlow(true, 0.5).strict(), true);
    EXPECT_EQ(InFlow(true, 0.5).rate(), 1.0);
    EXPECT_EQ(InFlow(false, 0.5).strict(), false);
    EXPECT_EQ(InFlow(false, 0.5).rate(), 0.5);
    EXPECT_EQ(InFlow(-1.0).strict(), false);
    EXPECT_EQ(InFlow(-1.0).rate(), 0.0);
}

TEST(FlowTest, flow_cost) {
    std::vector<FlowStats> data = {{0.4, 1.1, 0.6}, {0.7, 1.2, 0.5}, {0.2, 1.3, 0.4}};
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndFlow>(data, false, false), 1.1 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndFlow>(data, true, false), 0.6 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<OrFlow>(data, false, false), 1.1 + 0.6*1.2 + 0.6*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<OrFlow>(data, true, false), 0.6 + 0.5 + 0.4);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndNotFlow>(data, false, false), 1.1 + 0.4*1.2 + 0.4*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndNotFlow>(data, true, false), 0.6 + 0.4*1.2 + 0.4*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<RankFlow>(data, false, false), 1.1);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<RankFlow>(data, true, false), 0.6);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<BlenderFlow>(data, false, false), 1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<BlenderFlow>(data, true, false), 0.6);
}

TEST(FlowTest, rank_flow_cost_accumulation_is_first) {
    for (bool strict: {false, true}) {
        auto flow = AnyFlow::create<RankFlow>(strict);
        double cost = 0.0;
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
    }
}

TEST(FlowTest, blender_flow_cost_accumulation_is_max) {
    for (bool strict: {false, true}) {
        auto flow = AnyFlow::create<BlenderFlow>(strict);
        double cost = 0.0;
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 3.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 7.0);
        EXPECT_EQ(cost, 7.0);
    }
}

TEST(FlowTest, optimal_and_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            double ref_est = AndFlow::estimate_of(data);
            double min_cost = AndFlow::cost_of(data, strict);
            double max_cost = 0.0;
            AndFlow::sort(data, strict);
            EXPECT_DOUBLE_EQ(ordered_cost_of<AndFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<AndFlow>(my_data, strict, false);
                             EXPECT_LE(min_cost, my_cost + 1e-9);
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            if (verbose && (loop_cnt < 1024 || i % 1024 == 0)) {
                fprintf(stderr, "  AND cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
            EXPECT_NEAR(ref_est, AndFlow::estimate_of(data), 1e-9);
        }
    }
}

TEST(FlowTest, optimal_or_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            double min_cost = OrFlow::cost_of(data, strict);
            double max_cost = 0.0;
            OrFlow::sort(data, strict);
            EXPECT_DOUBLE_EQ(ordered_cost_of<OrFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<OrFlow>(my_data, strict, false);
                             EXPECT_LE(min_cost, my_cost + 1e-9);
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            if (verbose && (loop_cnt < 1024 || i % 1024 == 0)) {
                fprintf(stderr, "  OR cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
        }
    }
}

TEST(FlowTest, optimal_and_not_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            FlowStats first = data[0];
            double min_cost = AndNotFlow::cost_of(data, strict);
            double max_cost = 0.0;
            AndNotFlow::sort(data, strict);
            EXPECT_EQ(data[0], first);
            EXPECT_DOUBLE_EQ(ordered_cost_of<AndNotFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             if (my_data[0] == first) {
                                 double my_cost = ordered_cost_of<AndNotFlow>(my_data, strict, false);
                                 EXPECT_LE(min_cost, my_cost + 1e-9);
                                 max_cost = std::max(max_cost, my_cost);
                             }
                         };
            each_perm(data, check);
            if (verbose && (loop_cnt < 1024 || i % 1024 == 0)) {
                fprintf(stderr, "  ANDNOT cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
        }
    }
}

void test_strict_AND_sort_strategy(auto my_sort) {
    re_seed();
    const char *tags = "ABCDEFGHI";
    for (size_t child_cnt: {2, 3, 5, 7, 9}) {
        size_t cnt = std::max(size_t(10), std::min(size_t(128'000), (max_work / count_perms(child_cnt))));
        if (verbose) {
            fprintf(stderr, "AND/%zu: checking all permutations for %zu random cases\n", child_cnt, cnt);
        }
        std::vector<FlowStats> my_worst_order;
        std::vector<FlowStats> best_worst_order;
        auto get_tag = [&](const FlowStats &stats, const std::vector<FlowStats> &ref)noexcept->char{
                           for (size_t i = 0; i < ref.size(); ++i) {
                               if (stats == ref[i]) {
                                   return tags[i];
                               }
                           }
                           return 'X';
                       };
        auto dump_flow = [&](const std::vector<FlowStats> &list, const std::vector<FlowStats> &ref){
                             double total_cost = 0.0;
                             auto flow = AndFlow(true);
                             for (const auto &item: list) {
                                 auto in_flow = InFlow(flow.strict(), flow.flow());
                                 bool strict = flow.strict() || flow::should_force_strict(item, flow.flow());
                                 double child_cost = flow::min_child_cost(in_flow, item, true);
                                 fprintf(stderr, "    %10f -> %c (estimate: %10f, cost: %10f, strict_cost: %10f, cross: %10f, gain: %10f, gain@est: %10f) cost: %10f%s\n",
                                         flow.flow(), get_tag(item, ref), item.estimate, item.cost, item.strict_cost, strict_crossover(item), strict_gain(item, in_flow), strict_gain(item, item.estimate),
                                         child_cost, strict ? " STRICT" : "");
                                 flow.add(item.estimate);
                                 total_cost += child_cost;
                             }
                             EXPECT_DOUBLE_EQ(total_cost, ordered_cost_of<AndFlow>(list, true, true));
                             fprintf(stderr, "    total cost: %10f\n", total_cost);
                         };
        auto verify_order = [&](const std::vector<FlowStats> &list){
                                // check the following constraints for the given order:
                                //
                                // (1) never strict after non-strict
                                // (2) strict items are sorted by estimate
                                // (3) non-strict items are sorted by max(reduction/cost)
                                auto flow = AndFlow(true);
                                size_t strict_limit = list.size();
                                auto my_cmp = flow::MinAndCost(flow::DirectAdapter());
                                for (size_t i = 0; i < list.size(); ++i) {
                                    const auto &item = list[i];
                                    if (i > 0) {
                                        const auto &prev = list[i-1];
                                        bool strict = flow::should_force_strict(item, flow.flow());
                                        if (strict) {
                                            if (i > strict_limit) {
                                                return false; // (1)
                                            } else if (item.estimate < prev.estimate) {
                                                return false; // (2)
                                            }
                                        } else {
                                            strict_limit = std::min(i, strict_limit);
                                            if ((strict_limit < i) && my_cmp(item, prev)) {
                                                return false; // (3)
                                            }
                                        }
                                    }
                                    flow.add(item.estimate);
                                }
                                return true;
                            };
        double max_rel_err = 0.0;
        double sum_rel_err = 0.0;
        std::vector<double> errs;
        errs.reserve(cnt);
        auto p = [&](double arg){
                     size_t idx = std::lround(arg * (errs.size() - 1));
                     if (idx < errs.size()) {
                         return errs[idx];
                     }
                     return errs.back();
                 };
        for (size_t i = 0; i < cnt; ++i) {
            auto data = gen_data(child_cnt);
            double ref_est = AndFlow::estimate_of(data);
            my_sort(data);
            auto my_order = data;
            auto best_order = my_order;
            double est_cost = ordered_cost_of<AndFlow>(data, true, true);
            double min_cost = est_cost;
            double max_cost = est_cost;
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<AndFlow>(my_data, true, true);
                             if (my_cost < min_cost) {
                                 min_cost = my_cost;
                                 best_order = my_data;
                             }
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            double rel_err = 0.0;
            rel_err = (est_cost - min_cost) / min_cost;
            if (rel_err > max_rel_err) {
                max_rel_err = rel_err;
                my_worst_order = my_order;
                best_worst_order = best_order;
            }
            sum_rel_err += rel_err;
            errs.push_back(rel_err);
            if (dump_unexpected && !verify_order(best_order)) {
                fprintf(stderr, "  BEST ORDER IS UNEXPECTED:\n");
                dump_flow(best_order, best_order);
                fprintf(stderr, "  UNEXPECTED case, my_order:\n");
                dump_flow(my_order, best_order);
            }
            EXPECT_NEAR(ref_est, AndFlow::estimate_of(data), 1e-9);
        }
        std::sort(errs.begin(), errs.end());
        if (verbose && !my_worst_order.empty()) {
            fprintf(stderr, "  worst case, best order:\n");
            dump_flow(best_worst_order, best_worst_order);
            fprintf(stderr, "  worst case, my order:\n");
            dump_flow(my_worst_order, best_worst_order);
        }
        fprintf(stderr, "AND/%zu: avg: %10f, p90: %10f, p99: %10f, p99.9: %10f, max: %10f\n",
                child_cnt, (sum_rel_err / cnt), p(0.9), p(0.99), p(0.999), max_rel_err);
    }
}

TEST(FlowTest, strict_and_with_allow_force_strict_basic_order) {
    auto my_sort = [](auto &data){ AndFlow::sort(data, true); };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_incremental_strict_selection_destructive_order_max_3_extra_strict) {
    auto my_sort = [](auto &data) {
                       AndFlow::sort(data, true);
                       for (size_t next = 1; next <= 3 && next < data.size(); ++next) {
                           auto [idx, target, diff] = flow::select_forced_strict_and_child(flow::DirectAdapter(), data, next);
                           if (diff >= 0.0) {
                               break;
                           }
                           auto pos = data.begin() + idx;
                           std::rotate(data.begin() + target, pos, pos + 1);
                       }
                   };
    test_strict_AND_sort_strategy(my_sort);
}

GTEST_MAIN_RUN_ALL_TESTS()
