// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vector>
#include <map>
#include <xxhash.h>

using namespace vespalib;
using make_string_short::fmt;
using Handle = SharedStringRepo::Handle;

bool verbose = false;
double budget = 0.10;
size_t work_size = 4096;

//-----------------------------------------------------------------------------

std::vector<vespalib::string> make_strings(size_t cnt) {
    std::vector<vespalib::string> strings;
    strings.reserve(cnt);
    for (size_t i = 0; i < cnt; ++i) {
        strings.push_back(fmt("str_%zu", i));
    }
    return strings;
}

std::vector<vespalib::string> copy_strings(const std::vector<vespalib::string> &strings) {
    std::vector<vespalib::string> result;
    result.reserve(strings.size());
    for (const auto &str: strings) {
        result.push_back(str);
    }
    return result;
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
    for (size_t i = 0; i < strings.size(); ++i) {
        handles.emplace_back(strings[i]);
    }
    return handles;
}

std::vector<vespalib::string> get_strings(const std::vector<Handle> &handles) {
    std::vector<vespalib::string> strings;
    strings.reserve(handles.size());
    for (size_t i = 0; i < handles.size(); ++i) {
        strings.push_back(handles[i].as_string());
    }
    return strings;
}

std::unique_ptr<SharedStringRepo::StrongHandles> make_strong_handles(const std::vector<vespalib::string> &strings) {
    auto result = std::make_unique<SharedStringRepo::StrongHandles>(strings.size());
    for (const auto &str: strings) {
        result->add(str);
    }
    return result;
}

std::unique_ptr<SharedStringRepo::WeakHandles> make_weak_handles(const SharedStringRepo::HandleView &view) {
    auto result = std::make_unique<SharedStringRepo::WeakHandles>(view.handles().size());
    for (uint32_t handle: view.handles()) {
        result->add(handle);
    }
    return result;    
}

//-----------------------------------------------------------------------------

struct Avg : Rendezvous<double, double> {
    Avg(size_t n) : Rendezvous<double, double>(n) {}
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
    Vote(size_t n) : Rendezvous<bool, bool>(n) {}
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
    size_t num_threads() const { return size(); }
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
    steady_time start_time;
    std::map<vespalib::string,double> time_ms;
    Fixture(size_t num_threads)
        : avg(num_threads), vote(num_threads), work(make_strings(work_size)), start_time(steady_clock::now()) {}
    ~Fixture() {
        if (verbose) {
            fprintf(stderr, "benchmark results for %zu threads:\n", vote.num_threads());
            for (const auto &[tag, ms_cost]: time_ms) {
                fprintf(stderr, "    %s: %g ms\n", tag.c_str(), ms_cost);
            }
        }
    }
    bool has_budget() {
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
            std::vector<Handle> copy_handles_result;
            std::vector<Handle> resolve_again_result;
            std::vector<vespalib::string> get_result;
            std::unique_ptr<SharedStringRepo::StrongHandles> strong;
            std::unique_ptr<SharedStringRepo::WeakHandles> weak;
            auto copy_strings_task = [&](){ copy_strings_result = copy_strings(work); };
            auto copy_and_hash_task = [&](){ copy_and_hash_result = copy_and_hash(work); };
            auto local_enum_task = [&](){ local_enum_result = local_enum(work); };
            auto resolve_task = [&](){ resolve_result = resolve_strings(work); };
            auto copy_handles_task = [&](){ copy_handles_result = resolve_result; };
            auto resolve_again_task = [&](){ resolve_again_result = resolve_strings(work); };
            auto get_task = [&](){ get_result = get_strings(resolve_result); };
            auto reclaim_task = [&]() { resolve_again_result.clear(); };
            auto reclaim_last_task = [&]() { resolve_result.clear(); };
            auto make_strong_task = [&]() { strong = make_strong_handles(work); };
            auto make_weak_task = [&]() { weak = make_weak_handles(strong->view()); };
            auto free_weak_task = [&]() { weak.reset(); };
            auto free_strong_task = [&]() { strong.reset(); };
            measure_task("[01] copy strings", is_master, copy_strings_task);
            measure_task("[02] copy and hash", is_master, copy_and_hash_task);
            measure_task("[03] local enum", is_master, local_enum_task);
            measure_task("[04] resolve", is_master, resolve_task);
            measure_task("[05] copy handles", is_master, copy_handles_task);
            measure_task("[06] resolve again", is_master, resolve_again_task);
            verify_equal(resolve_result, resolve_again_result);
            measure_task("[07] as_string", is_master, get_task);
            verify_equal(get_result, work);
            measure_task("[08] reclaim", is_master, reclaim_task);
            copy_handles_result.clear();
            measure_task("[09] reclaim last", is_master, reclaim_last_task);
            measure_task("[10] make strong handles", is_master, make_strong_task);
            measure_task("[11] make weak handles", is_master, make_weak_task);
            measure_task("[12] free weak handles", is_master, free_weak_task);
            measure_task("[13] free strong handles", is_master, free_strong_task);
        }
    }
};

//-----------------------------------------------------------------------------

TEST("require that basic usage works") {
    Handle empty;
    Handle foo("foo");
    Handle bar("bar");
    Handle empty2;
    Handle foo2("foo");
    Handle bar2(bar);
    EXPECT_EQUAL(empty.id(), 0u);
    EXPECT_TRUE(empty.id() != foo.id());
    EXPECT_TRUE(empty.id() != bar.id());
    EXPECT_TRUE(foo.id() != bar.id());
    EXPECT_EQUAL(empty.id(), empty2.id());
    EXPECT_EQUAL(foo.id(), foo2.id());
    EXPECT_EQUAL(bar.id(), bar2.id());
    EXPECT_EQUAL(empty.as_string(), vespalib::string(""));
    EXPECT_EQUAL(foo.as_string(), vespalib::string("foo"));
    EXPECT_EQUAL(bar.as_string(), vespalib::string("bar"));
    EXPECT_EQUAL(foo2.as_string(), vespalib::string("foo"));
    EXPECT_EQUAL(bar2.as_string(), vespalib::string("bar"));
}

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
