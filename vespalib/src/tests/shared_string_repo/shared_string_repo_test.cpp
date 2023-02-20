// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vector>
#include <map>
#include <xxhash.h>

using namespace vespalib;
using make_string_short::fmt;
using Handle = SharedStringRepo::Handle;
using Handles = SharedStringRepo::Handles;
using Stats = SharedStringRepo::Stats;

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

std::vector<vespalib::string> make_strings(size_t cnt) {
    std::vector<vespalib::string> strings;
    strings.reserve(cnt);
    for (size_t i = 0; i < cnt; ++i) {
        strings.push_back(fmt("str_%zu", i));
    }
    return strings;
}

std::vector<vespalib::string> make_direct_strings(size_t cnt) {
    std::vector<vespalib::string> strings;
    strings.reserve(cnt);
    for (size_t i = 0; i < cnt; ++i) {
        strings.push_back(fmt("%zu", (i % 100000)));
    }
    return strings;
}

std::vector<vespalib::string> copy_strings(const std::vector<vespalib::string> &strings) {
    return strings;
}

std::vector<std::pair<vespalib::string, uint64_t>> copy_and_hash(const std::vector<vespalib::string> &strings) {
    std::vector<std::pair<vespalib::string, uint64_t>> result;
    result.reserve(strings.size());
    for (const auto &str: strings) {
        result.emplace_back(str, XXH3_64bits(str.data(), str.size()));
    }
    return result;
}

std::vector<uint32_t> local_enum(const std::vector<vespalib::string> &strings) {
    hash_map<vespalib::string, uint32_t> map(strings.size() * 2);
    std::vector<uint32_t> result;
    result.reserve(strings.size());
    for (const auto &str: strings) {
        result.push_back(map.insert(std::make_pair(str, map.size())).first->second);
    }
    return result;
}

std::vector<Handle> resolve_strings(const std::vector<vespalib::string> &strings) {
    std::vector<Handle> handles;
    handles.reserve(strings.size());
    for (const auto & string : strings) {
        handles.emplace_back(string);
    }
    return handles;
}

std::vector<vespalib::string> get_strings(const std::vector<Handle> &handles) {
    std::vector<vespalib::string> strings;
    strings.reserve(handles.size());
    for (const auto & handle : handles) {
        strings.push_back(handle.as_string());
    }
    return strings;
}

std::unique_ptr<Handles> make_strong_handles(const std::vector<vespalib::string> &strings) {
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

struct Avg : Rendezvous<double, double> {
    explicit Avg(size_t n) : Rendezvous<double, double>(n) {}
    void mingle() override {
        double sum = 0;
        for (size_t i = 0; i < size(); ++i) {
            sum += in(i);
        }
        double result = sum / size();
        for (size_t i = 0; i < size(); ++i) {
            out(i) = result;
        }
    }
    double operator()(double value) { return rendezvous(value); }
};

struct Vote : Rendezvous<bool, bool> {
    explicit Vote(size_t n) : Rendezvous<bool, bool>(n) {}
    void mingle() override {
        size_t true_cnt = 0;
        size_t false_cnt = 0;
        for (size_t i = 0; i < size(); ++i) {
            if (in(i)) {
                ++true_cnt;
            } else {
                ++false_cnt;
            }
        }
        bool result = (true_cnt > false_cnt);
        for (size_t i = 0; i < size(); ++i) {
            out(i) = result;
        }
    }
    [[nodiscard]] size_t num_threads() const { return size(); }
    bool operator()(bool flag) { return rendezvous(flag); }
};

//-----------------------------------------------------------------------------

template <typename T>
void verify_equal(const std::vector<T> &a, const std::vector<T> &b) {
    ASSERT_EQUAL(a.size(), b.size());
    for (size_t i = 0; i < a.size(); ++i) {
        EXPECT_TRUE(a[i] == b[i]);
    }
}

//-----------------------------------------------------------------------------

struct Fixture {
    Avg avg;
    Vote vote;
    std::vector<vespalib::string> work;
    std::vector<vespalib::string> direct_work;
    steady_time start_time;
    std::map<vespalib::string,double> time_ms;
    explicit Fixture(size_t num_threads)
        : avg(num_threads), vote(num_threads), work(make_strings(work_size)), direct_work(make_direct_strings(work_size)), start_time(steady_clock::now()) {}
    ~Fixture() {
        if (verbose) {
            fprintf(stderr, "benchmark results for %zu threads:\n", vote.num_threads());
            for (const auto &[tag, ms_cost]: time_ms) {
                fprintf(stderr, "    %s: %g ms\n", tag.c_str(), ms_cost);
            }
        }
    }
    [[nodiscard]] bool has_budget() const {
        return to_s(steady_clock::now() - start_time) < budget;
    }
    template <typename F>
    void measure_task(const vespalib::string &tag, bool is_master, F &&task) {
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
            std::vector<vespalib::string> copy_strings_result;
            std::vector<std::pair<vespalib::string,uint64_t>> copy_and_hash_result;
            std::vector<uint32_t> local_enum_result;
            std::vector<Handle> resolve_result;
            std::vector<Handle> resolve_direct_result;
            std::vector<Handle> copy_handles_result;
            std::vector<Handle> resolve_again_result;
            std::vector<vespalib::string> get_result;
            std::vector<vespalib::string> get_direct_result;
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
    EXPECT_NOT_EQUAL((a < b), (b < a));
    EXPECT_NOT_EQUAL((a.id() < b.id()), (b.id() < a.id()));
}

//-----------------------------------------------------------------------------

TEST("require that empty stats object has expected values") {
    Stats empty;
    EXPECT_EQUAL(empty.active_entries, 0u);
    EXPECT_EQUAL(empty.total_entries, 0u);
    EXPECT_EQUAL(empty.max_part_usage, 0u);
    EXPECT_EQUAL(empty.memory_usage.allocatedBytes(), 0u);
    EXPECT_EQUAL(empty.memory_usage.usedBytes(), 0u);
    EXPECT_EQUAL(empty.memory_usage.deadBytes(), 0u);
    EXPECT_EQUAL(empty.memory_usage.allocatedBytesOnHold(), 0u);
}

TEST("require that stats can be merged") {
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
    EXPECT_EQUAL(a.active_entries, 4u);
    EXPECT_EQUAL(a.total_entries, 30u);
    EXPECT_EQUAL(a.max_part_usage, 100u);
    EXPECT_EQUAL(a.memory_usage.allocatedBytes(), 30u);
    EXPECT_EQUAL(a.memory_usage.usedBytes(), 15u);
    EXPECT_EQUAL(a.memory_usage.deadBytes(), 0u);
    EXPECT_EQUAL(a.memory_usage.allocatedBytesOnHold(), 0u);
}

TEST("require that id_space_usage is sane") {
    Stats stats;
    stats.max_part_usage = 0;
    EXPECT_EQUAL(stats.id_space_usage(), 0.0);
    stats.max_part_usage = Stats::part_limit() / 4;
    EXPECT_EQUAL(stats.id_space_usage(), 0.25);
    stats.max_part_usage = Stats::part_limit() / 2;
    EXPECT_EQUAL(stats.id_space_usage(), 0.5);
    stats.max_part_usage = Stats::part_limit();
    EXPECT_EQUAL(stats.id_space_usage(), 1.0);
}

TEST("require that initial stats are as expected") {
    size_t num_parts = 256;
    size_t part_size = 128;
    size_t hash_node_size = 12;
    size_t entry_size = 72;
    size_t initial_entries = 28;
    size_t initial_hash_used = 16;
    size_t initial_hash_allocated = 32;
    size_t part_limit = (uint32_t(-1) - 10000001) / num_parts;
    auto stats = SharedStringRepo::stats();
    EXPECT_EQUAL(stats.active_entries, 0u);
    EXPECT_EQUAL(stats.total_entries, num_parts * initial_entries);
    EXPECT_EQUAL(stats.max_part_usage, 0u);
    EXPECT_EQUAL(stats.id_space_usage(), 0.0);
    EXPECT_EQUAL(stats.memory_usage.allocatedBytes(),
                 num_parts * (part_size + hash_node_size * initial_hash_allocated + entry_size * initial_entries));
    EXPECT_EQUAL(stats.memory_usage.usedBytes(),
                 num_parts * (part_size + hash_node_size * initial_hash_used + entry_size * initial_entries));
    EXPECT_EQUAL(stats.memory_usage.deadBytes(), 0u);
    EXPECT_EQUAL(stats.memory_usage.allocatedBytesOnHold(), 0u);
    EXPECT_EQUAL(Stats::part_limit(), part_limit);
    if (verbose) {
        fprintf(stderr, "max entries per part: %zu\n", Stats::part_limit());
        fprintf(stderr, "initial memory usage: %zu\n", stats.memory_usage.allocatedBytes());
    }
}

//-----------------------------------------------------------------------------

TEST("require that basic handle usage works") {
    Handle empty;
    Handle foo("foo");
    Handle bar("bar");
    Handle empty2("");
    Handle foo2("foo");
    Handle bar2("bar");

    EXPECT_EQUAL(active_enums(), 2u);

    TEST_DO(verify_eq(empty, empty2));
    TEST_DO(verify_eq(foo, foo2));
    TEST_DO(verify_eq(bar, bar2));

    TEST_DO(verify_not_eq(empty, foo));
    TEST_DO(verify_not_eq(empty, bar));
    TEST_DO(verify_not_eq(foo, bar));

    EXPECT_EQUAL(empty.id().hash(), 0u);
    EXPECT_EQUAL(empty.id().value(), 0u);
    EXPECT_TRUE(empty.id() == string_id());
    EXPECT_TRUE(empty2.id() == string_id());
    EXPECT_EQUAL(empty.as_string(), vespalib::string(""));
    EXPECT_EQUAL(empty2.as_string(), vespalib::string(""));
    EXPECT_EQUAL(foo.as_string(), vespalib::string("foo"));
    EXPECT_EQUAL(bar.as_string(), vespalib::string("bar"));
    EXPECT_EQUAL(foo2.as_string(), vespalib::string("foo"));
    EXPECT_EQUAL(bar2.as_string(), vespalib::string("bar"));
}

TEST("require that handles can be copied") {
    size_t before = active_enums();
    Handle a("copied");
    EXPECT_EQUAL(active_enums(), before + 1);
    Handle b(a);
    Handle c;
    c = b;
    EXPECT_EQUAL(active_enums(), before + 1);
    EXPECT_TRUE(a.id() == b.id());
    EXPECT_TRUE(b.id() == c.id());
    EXPECT_EQUAL(c.as_string(), vespalib::string("copied"));
}

TEST("require that handles can be moved") {
    size_t before = active_enums();
    Handle a("moved");
    EXPECT_EQUAL(active_enums(), before + 1);
    Handle b(std::move(a));
    Handle c;
    c = std::move(b);
    EXPECT_EQUAL(active_enums(), before + 1);
    EXPECT_TRUE(a.id() == string_id());
    EXPECT_TRUE(b.id() == string_id());
    EXPECT_EQUAL(c.as_string(), vespalib::string("moved"));
}

TEST("require that handle/string can be obtained from string_id") {
    size_t before = active_enums();
    Handle a("str");
    EXPECT_EQUAL(active_enums(), before + 1);
    Handle b = Handle::handle_from_id(a.id());
    EXPECT_EQUAL(active_enums(), before + 1);
    EXPECT_EQUAL(Handle::string_from_id(b.id()), vespalib::string("str"));
}

void verifySelfAssignment(Handle & a, const Handle &b) {
    a = b;
}

TEST("require that handle can be self-assigned") {
    Handle a("foo");
    verifySelfAssignment(a, a);
    EXPECT_EQUAL(a.as_string(), vespalib::string("foo"));
}

//-----------------------------------------------------------------------------

void verify_direct(const vespalib::string &str, size_t value) {
    size_t before = active_enums();
    Handle handle(str);
    EXPECT_EQUAL(handle.id().hash(), value + 1);
    EXPECT_EQUAL(handle.id().value(), value + 1);
    EXPECT_EQUAL(active_enums(), before);
    EXPECT_EQUAL(handle.as_string(), str);
}

void verify_not_direct(const vespalib::string &str) {
    size_t before = active_enums();
    Handle handle(str);
    EXPECT_EQUAL(handle.id().hash(), handle.id().value());
    EXPECT_EQUAL(active_enums(), before + 1);
    EXPECT_EQUAL(handle.as_string(), str);
}

TEST("require that direct handles work as expected") {
    TEST_DO(verify_direct("", -1));
    TEST_DO(verify_direct("0", 0));
    TEST_DO(verify_direct("1", 1));
    TEST_DO(verify_direct("123", 123));
    TEST_DO(verify_direct("456", 456));
    TEST_DO(verify_direct("789", 789));
    TEST_DO(verify_direct("9999999", 9999999));
    TEST_DO(verify_not_direct(" "));
    TEST_DO(verify_not_direct(" 5"));
    TEST_DO(verify_not_direct("5 "));
    TEST_DO(verify_not_direct("10000000"));
    TEST_DO(verify_not_direct("00"));
    TEST_DO(verify_not_direct("01"));
    TEST_DO(verify_not_direct("001"));
    TEST_DO(verify_not_direct("-0"));
    TEST_DO(verify_not_direct("-1"));
    TEST_DO(verify_not_direct("a1"));
}

//-----------------------------------------------------------------------------

TEST("require that basic multi-handle usage works") {
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
        EXPECT_EQUAL(before, 0u);
        EXPECT_EQUAL(active_enums(), 2u);
    } else {
        EXPECT_EQUAL(active_enums(), before);
    }
    EXPECT_EQUAL(a.view().size(), 0u);
    EXPECT_EQUAL(b.view().size(), 4u);
    EXPECT_TRUE(b.view()[0] == foo.id());
    EXPECT_TRUE(b.view()[1] == bar.id());
    EXPECT_TRUE(b.view()[2] == foo.id());
    EXPECT_TRUE(b.view()[3] == bar.id());
}

//-----------------------------------------------------------------------------

void verify_same_enum(int64_t num, const vespalib::string &str) {
    Handle n = Handle::handle_from_number(num);
    Handle s(str);
    EXPECT_EQUAL(n.id().value(), s.id().value());
}

TEST("require that numeric label resolving works as expected") {
    TEST_DO(verify_same_enum(-123, "-123"););
    TEST_DO(verify_same_enum(-1, "-1"););
    TEST_DO(verify_same_enum(0, "0"););
    TEST_DO(verify_same_enum(123, "123"););
    TEST_DO(verify_same_enum(9999999, "9999999"););
    TEST_DO(verify_same_enum(10000000, "10000000"););
    TEST_DO(verify_same_enum(999999999999, "999999999999"););
}

//-----------------------------------------------------------------------------

#if 0
// needs a lot of memory or tweaking of PART_LIMIT
TEST("allocate handles until we run out") {
    size_t cnt = 0;
    std::vector<Handle> handles;
    for (;;) {
        auto stats = SharedStringRepo::stats();
        size_t min_free = Stats::part_limit() - stats.max_part_usage;
        fprintf(stderr, "cnt: %zu, used: %zu/%zu, min free: %zu, usage: %g\n",
                cnt, stats.active_entries, stats.total_entries, min_free,
                stats.id_space_usage());
        size_t n = std::max(size_t(1), min_free);
        for (size_t i = 0; i < n; ++i) {
            handles.emplace_back(fmt("my_id_%zu", cnt++));
        }
    }
}
#endif

//-----------------------------------------------------------------------------

TEST_MT_F("test shared string repo operations with 1 threads", 1, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 2 threads", 2, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 4 threads", 4, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 8 threads", 8, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 16 threads", 16, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 32 threads", 32, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

TEST_MT_F("test shared string repo operations with 64 threads", 64, Fixture(num_threads)) {
    f1.benchmark(thread_id == 0);
}

//-----------------------------------------------------------------------------

#if 0
// verify leak-detection and reporting
TEST("leak some handles on purpose") {
    new Handle("leaked string");
    new Handle("also leaked");
    new Handle("even more leak");
}
#endif

TEST("require that no handles have leaked during testing") {
    if (will_reclaim()) {
        EXPECT_EQUAL(active_enums(), 0u);
    } else {
        auto stats = SharedStringRepo::stats();
        fprintf(stderr, "enum stats after testing (no reclaim):\n");
        fprintf(stderr, "  active enums:   %zu\n", stats.active_entries);
        fprintf(stderr, "  id space usage: %g\n", stats.id_space_usage());
        fprintf(stderr, "  memory usage:   %zu\n", stats.memory_usage.usedBytes());
    }
}

//-----------------------------------------------------------------------------

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 30.0;
        work_size = 128000;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
