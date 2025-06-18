// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/test/thread_meets.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <format>
#include <vector>
#include <map>
#include <xxhash.h>

using namespace vespalib;
using make_string_short::fmt;
using Handle = SharedStringRepo::Handle;
using Handles = SharedStringRepo::Handles;
using Stats = SharedStringRepo::Stats;
using vespalib::test::Nexus;

bool verbose = false;
double budget = 0.10;
size_t work_size = 4_Ki;

size_t active_enums() {
    return SharedStringRepo::stats().active_entries;
}

bool will_reclaim() {
    return SharedStringRepo::will_reclaim();
}

//-----------------------------------------------------------------------------

std::vector<std::string> make_strings(size_t cnt) {
    std::vector<std::string> strings;
    strings.reserve(cnt);
    for (size_t i = 0; i < cnt; ++i) {
        strings.push_back(fmt("str_%zu", i));
    }
    return strings;
}

std::vector<std::string> make_direct_strings(size_t cnt) {
    std::vector<std::string> strings;
    strings.reserve(cnt);
    for (size_t i = 0; i < cnt; ++i) {
        strings.push_back(fmt("%zu", (i % 100000)));
    }
    return strings;
}

std::vector<std::string> copy_strings(const std::vector<std::string> &strings) {
    return strings;
}

std::vector<std::pair<std::string, uint64_t>> copy_and_hash(const std::vector<std::string> &strings) {
    std::vector<std::pair<std::string, uint64_t>> result;
    result.reserve(strings.size());
    for (const auto &str: strings) {
        result.emplace_back(str, XXH3_64bits(str.data(), str.size()));
    }
    return result;
}

std::vector<uint32_t> local_enum(const std::vector<std::string> &strings) {
    hash_map<std::string, uint32_t> map(strings.size() * 2);
    std::vector<uint32_t> result;
    result.reserve(strings.size());
    for (const auto &str: strings) {
        result.push_back(map.insert(std::make_pair(str, map.size())).first->second);
    }
    return result;
}

std::vector<Handle> resolve_strings(const std::vector<std::string> &strings) {
    std::vector<Handle> handles;
    handles.reserve(strings.size());
    for (const auto & string : strings) {
        handles.emplace_back(string);
    }
    return handles;
}

std::vector<std::string> get_strings(const std::vector<Handle> &handles) {
    std::vector<std::string> strings;
    strings.reserve(handles.size());
    for (const auto & handle : handles) {
        strings.push_back(handle.as_string());
    }
    return strings;
}

std::unique_ptr<Handles> make_strong_handles(const std::vector<std::string> &strings) {
    auto result = std::make_unique<Handles>();
    result->reserve(strings.size());
    for (const auto &str: strings) {
        result->add(str);
    }
    return result;
}

std::unique_ptr<Handles> copy_strong_handles(const Handles &handles) {
    const auto &view = handles.view();
    auto result = std::make_unique<Handles>();
    result->reserve(view.size());
    for (const auto &handle: view) {
        result->push_back(handle);
    }
    return result;
}

std::unique_ptr<StringIdVector> make_weak_handles(const Handles &handles) {
    return std::make_unique<StringIdVector>(handles.view());
}

//-----------------------------------------------------------------------------

using Avg = vespalib::test::ThreadMeets::Avg;
using Vote = vespalib::test::ThreadMeets::Vote;

//-----------------------------------------------------------------------------

template <typename T>
void verify_equal(const std::vector<T> &a, const std::vector<T> &b) {
    ASSERT_EQ(a.size(), b.size());
    for (size_t i = 0; i < a.size(); ++i) {
        EXPECT_TRUE(a[i] == b[i]);
    }
}

//-----------------------------------------------------------------------------

struct Fixture {
    Avg avg;
    Vote vote;
    std::vector<std::string> work;
    std::vector<std::string> direct_work;
    steady_time start_time;
    std::map<std::string,double> time_ms;
    explicit Fixture(size_t num_threads)
        : avg(num_threads), vote(num_threads), work(make_strings(work_size)), direct_work(make_direct_strings(work_size)), start_time(steady_clock::now()) {}
    ~Fixture() {
        if (verbose) {
            fprintf(stderr, "benchmark results for %zu threads:\n", vote.size());
            for (const auto &[tag, ms_cost]: time_ms) {
                fprintf(stderr, "    %s: %g ms\n", tag.c_str(), ms_cost);
            }
        }
    }
    [[nodiscard]] bool has_budget() const {
        return to_s(steady_clock::now() - start_time) < budget;
    }
    template <typename F>
    void measure_task(const std::string &tag, bool is_master, F &&task) {
        auto before = steady_clock::now();
        task();
        double ms_cost = to_s(steady_clock::now() - before) * 1000.0;
        double avg_ms = avg(ms_cost);
        if (is_master) {
            if (time_ms.count(tag) > 0) {
                time_ms[tag] = std::min(time_ms[tag], avg_ms);
            } else {
                time_ms[tag] = avg_ms;
            }
        }
    }
    void benchmark(bool is_master) {
        for (bool once_more = true; vote(once_more); once_more = has_budget()) {
            std::vector<std::string> copy_strings_result;
            std::vector<std::pair<std::string,uint64_t>> copy_and_hash_result;
            std::vector<uint32_t> local_enum_result;
            std::vector<Handle> resolve_result;
            std::vector<Handle> resolve_direct_result;
            std::vector<Handle> copy_handles_result;
            std::vector<Handle> resolve_again_result;
            std::vector<std::string> get_result;
            std::vector<std::string> get_direct_result;
            std::unique_ptr<Handles> strong;
            std::unique_ptr<Handles> strong_copy;
            std::unique_ptr<StringIdVector> weak;
            auto copy_strings_task = [&](){ copy_strings_result = copy_strings(work); };
            auto copy_and_hash_task = [&](){ copy_and_hash_result = copy_and_hash(work); };
            auto local_enum_task = [&](){ local_enum_result = local_enum(work); };
            auto resolve_task = [&](){ resolve_result = resolve_strings(work); };
            auto resolve_direct_task = [&](){ resolve_direct_result = resolve_strings(direct_work); };
            auto copy_handles_task = [&](){ copy_handles_result = resolve_result; };
            auto resolve_again_task = [&](){ resolve_again_result = resolve_strings(work); };
            auto get_task = [&](){ get_result = get_strings(resolve_result); };
            auto get_direct_task = [&](){ get_direct_result = get_strings(resolve_direct_result); };
            auto reclaim_task = [&]() { resolve_again_result.clear(); };
            auto reclaim_last_task = [&]() { resolve_result.clear(); };
            auto make_strong_task = [&]() { strong = make_strong_handles(work); };
            auto copy_strong_task = [&]() { strong_copy = copy_strong_handles(*strong); };
            auto make_weak_task = [&]() { weak = make_weak_handles(*strong); };
            auto free_weak_task = [&]() { weak.reset(); };
            auto free_strong_copy_task = [&]() { strong_copy.reset(); };
            auto free_strong_task = [&]() { strong.reset(); };
            measure_task("[01] copy strings", is_master, copy_strings_task);
            measure_task("[02] copy and hash", is_master, copy_and_hash_task);
            measure_task("[03] local enum", is_master, local_enum_task);
            measure_task("[04] resolve", is_master, resolve_task);
            measure_task("[05] resolve direct", is_master, resolve_direct_task);
            measure_task("[06] copy handles", is_master, copy_handles_task);
            measure_task("[07] resolve again", is_master, resolve_again_task);
            verify_equal(resolve_result, resolve_again_result);
            measure_task("[08] as_string", is_master, get_task);
            measure_task("[09] as_string direct", is_master, get_direct_task);
            verify_equal(get_result, work);
            verify_equal(get_direct_result, direct_work);
            measure_task("[10] reclaim", is_master, reclaim_task);
            copy_handles_result.clear();
            measure_task("[11] reclaim last", is_master, reclaim_last_task);
            measure_task("[12] make strong handles", is_master, make_strong_task);
            measure_task("[13] copy strong handles", is_master, copy_strong_task);
            measure_task("[14] make weak handles", is_master, make_weak_task);
            measure_task("[15] free weak handles", is_master, free_weak_task);
            measure_task("[16] free strong handles copy", is_master, free_strong_copy_task);
            measure_task("[17] free strong handles", is_master, free_strong_task);
        }
    }
};

//-----------------------------------------------------------------------------

void verify_eq(const Handle &a, const Handle &b) {
    EXPECT_TRUE(a == b);
    EXPECT_TRUE(a.id() == b.id());
    EXPECT_FALSE(a != b);
    EXPECT_FALSE(a.id() != b.id());
    EXPECT_FALSE(a < b);
    EXPECT_FALSE(a.id() < b.id());
    EXPECT_FALSE(b < a);
    EXPECT_FALSE(b.id() < a.id());
}

void verify_not_eq(const Handle &a, const Handle &b) {
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(a.id() == b.id());
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(a.id() != b.id());
    EXPECT_NE((a < b), (b < a));
    EXPECT_NE((a.id() < b.id()), (b.id() < a.id()));
}

//-----------------------------------------------------------------------------

TEST(SharedStringRepoTest, require_that_empty_stats_object_has_expected_values) {
    Stats empty;
    EXPECT_EQ(empty.active_entries, 0u);
    EXPECT_EQ(empty.total_entries, 0u);
    EXPECT_EQ(empty.max_part_usage, 0u);
    EXPECT_EQ(empty.memory_usage.allocatedBytes(), 0u);
    EXPECT_EQ(empty.memory_usage.usedBytes(), 0u);
    EXPECT_EQ(empty.memory_usage.deadBytes(), 0u);
    EXPECT_EQ(empty.memory_usage.allocatedBytesOnHold(), 0u);
}

TEST(SharedStringRepoTest, require_that_stats_can_be_merged) {
    Stats a;
    Stats b;
    a.active_entries = 1;
    a.total_entries = 10;
    a.max_part_usage = 100;
    a.memory_usage.incAllocatedBytes(10);
    a.memory_usage.incUsedBytes(5);
    b.active_entries = 3;
    b.total_entries = 20;
    b.max_part_usage = 50;
    b.memory_usage.incAllocatedBytes(20);
    b.memory_usage.incUsedBytes(10);
    a.merge(b);
    EXPECT_EQ(a.active_entries, 4u);
    EXPECT_EQ(a.total_entries, 30u);
    EXPECT_EQ(a.max_part_usage, 100u);
    EXPECT_EQ(a.memory_usage.allocatedBytes(), 30u);
    EXPECT_EQ(a.memory_usage.usedBytes(), 15u);
    EXPECT_EQ(a.memory_usage.deadBytes(), 0u);
    EXPECT_EQ(a.memory_usage.allocatedBytesOnHold(), 0u);
}

TEST(SharedStringRepoTest, require_that_id_space_usage_is_sane) {
    Stats stats;
    stats.max_part_usage = 0;
    EXPECT_EQ(stats.id_space_usage(), 0.0);
    stats.max_part_usage = Stats::part_limit() / 4;
    EXPECT_FLOAT_EQ(stats.id_space_usage(), 0.25);
    stats.max_part_usage = Stats::part_limit() / 2;
    EXPECT_FLOAT_EQ(stats.id_space_usage(), 0.5);
    stats.max_part_usage = Stats::part_limit();
    EXPECT_EQ(stats.id_space_usage(), 1.0);
}

TEST(SharedStringRepoTest, require_that_initial_stats_are_as_expected) {
    size_t num_parts = 256;
    size_t part_size = 128;
    size_t hash_node_size = 12;
    size_t entry_size = 8 + sizeof(std::string);
    size_t initial_entries = roundUp2inN(16 * entry_size) / entry_size;
    size_t initial_hash_used = 16;
    size_t initial_hash_allocated = 32;
    size_t part_limit = (uint32_t(-1) - 10000001) / num_parts;
    auto stats = SharedStringRepo::stats();
    EXPECT_EQ(stats.active_entries, 0u);
    EXPECT_EQ(stats.total_entries, num_parts * initial_entries);
    EXPECT_EQ(stats.max_part_usage, 0u);
    EXPECT_EQ(stats.id_space_usage(), 0.0);
    EXPECT_EQ(stats.memory_usage.allocatedBytes(),
                 num_parts * (part_size + hash_node_size * initial_hash_allocated + entry_size * initial_entries));
    EXPECT_EQ(stats.memory_usage.usedBytes(),
                 num_parts * (part_size + hash_node_size * initial_hash_used + entry_size * initial_entries));
    EXPECT_EQ(stats.memory_usage.deadBytes(), 0u);
    EXPECT_EQ(stats.memory_usage.allocatedBytesOnHold(), 0u);
    EXPECT_EQ(Stats::part_limit(), part_limit);
    if (verbose) {
        fprintf(stderr, "max entries per part: %zu\n", Stats::part_limit());
        fprintf(stderr, "initial memory usage: %zu\n", stats.memory_usage.allocatedBytes());
    }
}

//-----------------------------------------------------------------------------

TEST(SharedStringRepoTest, require_that_basic_handle_usage_works) {
    Handle empty;
    Handle foo("foo");
    Handle bar("bar");
    Handle empty2("");
    Handle foo2("foo");
    Handle bar2("bar");

    EXPECT_EQ(active_enums(), 2u);

    GTEST_DO(verify_eq(empty, empty2));
    GTEST_DO(verify_eq(foo, foo2));
    GTEST_DO(verify_eq(bar, bar2));

    GTEST_DO(verify_not_eq(empty, foo));
    GTEST_DO(verify_not_eq(empty, bar));
    GTEST_DO(verify_not_eq(foo, bar));

    EXPECT_EQ(empty.id().hash(), 0u);
    EXPECT_EQ(empty.id().value(), 0u);
    EXPECT_TRUE(empty.id() == string_id());
    EXPECT_TRUE(empty2.id() == string_id());
    EXPECT_EQ(empty.as_string(), std::string(""));
    EXPECT_EQ(empty2.as_string(), std::string(""));
    EXPECT_EQ(foo.as_string(), std::string("foo"));
    EXPECT_EQ(bar.as_string(), std::string("bar"));
    EXPECT_EQ(foo2.as_string(), std::string("foo"));
    EXPECT_EQ(bar2.as_string(), std::string("bar"));
}

TEST(SharedStringRepoTest, require_that_handles_can_be_copied) {
    size_t before = active_enums();
    Handle a("copied");
    EXPECT_EQ(active_enums(), before + 1);
    Handle b(a);
    Handle c;
    c = b;
    EXPECT_EQ(active_enums(), before + 1);
    EXPECT_TRUE(a.id() == b.id());
    EXPECT_TRUE(b.id() == c.id());
    EXPECT_EQ(c.as_string(), std::string("copied"));
}

TEST(SharedStringRepoTest, require_that_handles_can_be_moved) {
    size_t before = active_enums();
    Handle a("moved");
    EXPECT_EQ(active_enums(), before + 1);
    Handle b(std::move(a));
    Handle c;
    c = std::move(b);
    EXPECT_EQ(active_enums(), before + 1);
    EXPECT_TRUE(a.id() == string_id());
    EXPECT_TRUE(b.id() == string_id());
    EXPECT_EQ(c.as_string(), std::string("moved"));
}

TEST(SharedStringRepoTest, require_that_handle_string_can_be_obtained_from_string_id) {
    size_t before = active_enums();
    Handle a("str");
    EXPECT_EQ(active_enums(), before + 1);
    Handle b = Handle::handle_from_id(a.id());
    EXPECT_EQ(active_enums(), before + 1);
    EXPECT_EQ(Handle::string_from_id(b.id()), std::string("str"));
}

void verifySelfAssignment(Handle & a, const Handle &b) {
    a = b;
}

TEST(SharedStringRepoTest, require_that_handle_can_be_self_assigned) {
    Handle a("foo");
    verifySelfAssignment(a, a);
    EXPECT_EQ(a.as_string(), std::string("foo"));
}

//-----------------------------------------------------------------------------

void verify_direct(const std::string &str, size_t value) {
    size_t before = active_enums();
    Handle handle(str);
    EXPECT_EQ(handle.id().hash(), value + 1);
    EXPECT_EQ(handle.id().value(), value + 1);
    EXPECT_EQ(active_enums(), before);
    EXPECT_EQ(handle.as_string(), str);
}

void verify_not_direct(const std::string &str) {
    size_t before = active_enums();
    Handle handle(str);
    EXPECT_EQ(handle.id().hash(), handle.id().value());
    EXPECT_EQ(active_enums(), before + 1);
    EXPECT_EQ(handle.as_string(), str);
}

TEST(SharedStringRepoTest, require_that_direct_handles_work_as_expected) {
    GTEST_DO(verify_direct("", -1));
    GTEST_DO(verify_direct("0", 0));
    GTEST_DO(verify_direct("1", 1));
    GTEST_DO(verify_direct("123", 123));
    GTEST_DO(verify_direct("456", 456));
    GTEST_DO(verify_direct("789", 789));
    GTEST_DO(verify_direct("9999999", 9999999));
    GTEST_DO(verify_not_direct(" "));
    GTEST_DO(verify_not_direct(" 5"));
    GTEST_DO(verify_not_direct("5 "));
    GTEST_DO(verify_not_direct("10000000"));
    GTEST_DO(verify_not_direct("00"));
    GTEST_DO(verify_not_direct("01"));
    GTEST_DO(verify_not_direct("001"));
    GTEST_DO(verify_not_direct("-0"));
    GTEST_DO(verify_not_direct("-1"));
    GTEST_DO(verify_not_direct("a1"));
}

//-----------------------------------------------------------------------------

TEST(SharedStringRepoTest, require_that_basic_multi_handle_usage_works) {
    size_t before = active_enums();
    Handles a;
    a.reserve(4);
    Handle foo("foo");
    Handle bar("bar");
    EXPECT_TRUE(a.add("foo") == foo.id());
    EXPECT_TRUE(a.add("bar") == bar.id());
    a.push_back(foo.id());
    a.push_back(bar.id());
    Handles b(std::move(a));
    if (will_reclaim()) {
        EXPECT_EQ(before, 0u);
        EXPECT_EQ(active_enums(), 2u);
    } else {
        EXPECT_EQ(active_enums(), before);
    }
    EXPECT_EQ(a.view().size(), 0u);
    EXPECT_EQ(b.view().size(), 4u);
    EXPECT_TRUE(b.view()[0] == foo.id());
    EXPECT_TRUE(b.view()[1] == bar.id());
    EXPECT_TRUE(b.view()[2] == foo.id());
    EXPECT_TRUE(b.view()[3] == bar.id());
}

//-----------------------------------------------------------------------------

void verify_same_enum(int64_t num, const std::string &str) {
    Handle n = Handle::handle_from_number(num);
    Handle s(str);
    EXPECT_EQ(n.id().value(), s.id().value());
}

TEST(SharedStringRepoTest, require_that_numeric_label_resolving_works_as_expected) {
    GTEST_DO(verify_same_enum(-123, "-123"););
    GTEST_DO(verify_same_enum(-1, "-1"););
    GTEST_DO(verify_same_enum(0, "0"););
    GTEST_DO(verify_same_enum(123, "123"););
    GTEST_DO(verify_same_enum(9999999, "9999999"););
    GTEST_DO(verify_same_enum(10000000, "10000000"););
    GTEST_DO(verify_same_enum(999999999999, "999999999999"););
}

//-----------------------------------------------------------------------------

struct SharedStringRepoBenchmarkTest : ::testing::TestWithParam<size_t> {};

TEST_P(SharedStringRepoBenchmarkTest, benchmark_with_threads) {
    size_t num_threads = GetParam();
    Fixture f1(num_threads);
    auto task = [&](Nexus &ctx){
                    f1.benchmark(ctx.thread_id() == 0);
                };
    Nexus::run(num_threads, task);
}

INSTANTIATE_TEST_SUITE_P(, SharedStringRepoBenchmarkTest,
                         ::testing::Values(1, 2, 4, 8, 16, 32, 64),
                         [](const ::testing::TestParamInfo<size_t> &pi) {
                             return std::format("{}", pi.param);
                         });

//-----------------------------------------------------------------------------

#if 0
// verify leak-detection and reporting
TEST(SharedStringRepoTest, leak_some_handles_on_purpose) {
    new Handle("leaked string");
    new Handle("also leaked");
    new Handle("even more leak");
}
#endif

//-----------------------------------------------------------------------------

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 30.0;
        work_size = 128000;
    }
    auto res = RUN_ALL_TESTS();
    if (will_reclaim()) {
        if (active_enums() != 0u) {
            fprintf(stderr, "test failed: enum leak detected\n");
            res = 1;
        }
    } else {
        auto stats = SharedStringRepo::stats();
        fprintf(stderr, "enum stats after testing (no reclaim):\n");
        fprintf(stderr, "  active enums:   %zu\n", stats.active_entries);
        fprintf(stderr, "  id space usage: %g\n", stats.id_space_usage());
        fprintf(stderr, "  memory usage:   %zu\n", stats.memory_usage.usedBytes());
    }
    return res;
}
