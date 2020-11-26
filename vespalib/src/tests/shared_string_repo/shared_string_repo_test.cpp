// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vector>
#include <map>

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
            std::vector<Handle> resolve_result;
            std::vector<Handle> copy_handles_result;
            std::vector<Handle> resolve_again_result;
            std::vector<vespalib::string> get_result;
            auto copy_strings_task = [&](){ copy_strings_result = work; };
            auto resolve_task = [&](){ resolve_result = resolve_strings(work); };
            auto copy_handles_task = [&](){ copy_handles_result = resolve_result; };
            auto resolve_again_task = [&](){ resolve_again_result = resolve_strings(work); };
            auto get_task = [&](){ get_result = get_strings(resolve_result); };
            auto reclaim_task = [&]() { resolve_again_result.clear(); };
            auto reclaim_last_task = [&]() { resolve_result.clear(); };
            measure_task("[0] copy strings", is_master, copy_strings_task);
            measure_task("[1] resolve", is_master, resolve_task);
            measure_task("[2] copy handles", is_master, copy_handles_task);
            measure_task("[3] resolve again", is_master, resolve_again_task);
            verify_equal(resolve_result, resolve_again_result);
            measure_task("[4] as_string", is_master, get_task);
            verify_equal(get_result, work);
            measure_task("[5] reclaim", is_master, reclaim_task);
            copy_handles_result.clear();
            measure_task("[6] reclaim last", is_master, reclaim_last_task);
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
        budget = 10.0;
        work_size = 128000;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
