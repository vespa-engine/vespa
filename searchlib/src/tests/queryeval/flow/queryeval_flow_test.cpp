// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <random>

using search::queryeval::AndFlow;
using search::queryeval::OrFlow;

struct Item {
    double rel_est;
    double cost;
    Item(double rel_est_in, double cost_in) noexcept
      : rel_est(rel_est_in), cost(cost_in) {}
    static void sort_for_and(std::vector<Item> &data) {
        std::sort(data.begin(), data.end(), [](const Item &a, const Item &b) noexcept {
                                                return (1.0 - a.rel_est) / a.cost > (1.0 - b.rel_est) / b.cost;
                                            });
    }
    static void sort_for_or(std::vector<Item> &data) {
        std::sort(data.begin(), data.end(), [](const Item &a, const Item &b) noexcept {
                                                return a.rel_est / a.cost > b.rel_est / b.cost;
                                            });
    }
    static double cost_of(const std::vector<Item> &data, auto flow) {
        double cost = 0.0;
        for (const Item &item: data) {
            cost += flow.flow() * item.cost;
            flow.add(item.rel_est);
        }
        return cost;
    }
    static double cost_of_and(const std::vector<Item> &data) { return cost_of(data, AndFlow()); }
    static double cost_of_or(const std::vector<Item> &data) { return cost_of(data, OrFlow()); }
};

std::vector<Item> gen_data(size_t size) {
    static std::mt19937 gen;
    static std::uniform_real_distribution<double> rel_est(0.1, 0.9);
    static std::uniform_real_distribution<double> cost(1.0, 10.0);
    std::vector<Item> result;
    result.reserve(size);
    for (size_t i = 0; i < size; ++i) {
        result.emplace_back(rel_est(gen), cost(gen));
    }
    return result;
}

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

TEST(FlowTest, optimal_and_flow) {
    for (size_t i = 0; i < 256; ++i) {
        auto data = gen_data(7);
        Item::sort_for_and(data);
        double min_cost = Item::cost_of_and(data);
        double max_cost = 0.0;
        auto check = [min_cost,&max_cost](const std::vector<Item> &my_data) noexcept {
                         double my_cost = Item::cost_of_and(my_data);
                         EXPECT_LE(min_cost, my_cost);
                         max_cost = std::max(max_cost, my_cost);
                     };
        each_perm(data, check);
        fprintf(stderr, "  and cost(%zu): min: %g, max: %g, factor: %g\n",
                i, min_cost, max_cost, max_cost / min_cost);
    }
}

TEST(FlowTest, optimal_or_flow) {
    for (size_t i = 0; i < 256; ++i) {
        auto data = gen_data(7);
        Item::sort_for_or(data);
        double min_cost = Item::cost_of_or(data);
        double max_cost = 0.0;
        auto check = [min_cost,&max_cost](const std::vector<Item> &my_data) noexcept {
                         double my_cost = Item::cost_of_or(my_data);
                         EXPECT_LE(min_cost, my_cost);
                         max_cost = std::max(max_cost, my_cost);
                     };
        each_perm(data, check);
        fprintf(stderr, "  or cost(%zu): min: %g, max: %g, factor: %g\n",
                i, min_cost, max_cost, max_cost / min_cost);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
