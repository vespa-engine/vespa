// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <chrono>
#include <map>
#include <mutex>
#include <thread>

using vespalib::test::Nexus;

struct Fixture {
    std::mutex                              lock;
    FNET_Transport                          transport;
    std::map<FNET_TransportThread*, size_t> counts;
    Fixture(size_t num_threads) : transport(num_threads) {}
    void count_selected_thread(const void* key, size_t key_len) {
        std::lock_guard<std::mutex> guard(lock);
        FNET_TransportThread*       thread = transport.select_thread(key, key_len);
        ++counts[thread];
    }
    std::vector<size_t> get_counts() {
        std::vector<size_t> result;
        for (const auto& entry : counts) {
            result.push_back(entry.second);
        }
        return result;
    }
    void dump_counts() {
        std::vector<size_t> list = get_counts();
        fprintf(stderr, "thread selection counts: [");
        for (size_t i = 0; i < list.size(); ++i) {
            if (i > 0) {
                fprintf(stderr, ", ");
            }
            fprintf(stderr, "%zu", list[i]);
        }
        fprintf(stderr, "]\n");
    }
};

TEST(ThreadSelectionTest, require_that_selection_is_time_sensistive) {
    Fixture f1(8);
    using namespace std::literals;
    std::string key("my random key");
    for (size_t i = 0; i < 256; ++i) {
        f1.count_selected_thread(key.data(), key.size());
        std::this_thread::sleep_for(10ms);
    }
    EXPECT_EQ(f1.counts.size(), 8u);
    f1.dump_counts();
}

TEST(ThreadSelectionTest, require_that_selection_is_key_sensistive) {
    Fixture f1(8);
    for (size_t i = 0; i < 256; ++i) {
        std::string key = vespalib::make_string("my random key %zu", i);
        f1.count_selected_thread(key.data(), key.size());
    }
    EXPECT_EQ(f1.counts.size(), 8u);
    f1.dump_counts();
}

TEST(ThreadSelectionTest, require_that_selection_is_thread_sensitive) {
    Fixture f1(8);
    size_t  num_threads = 256;
    auto    task = [&](Nexus& ctx) {
        f1.count_selected_thread(nullptr, 0);
        ctx.barrier(); // #1
        if (ctx.thread_id() == 0) {
            std::vector<size_t> counts = f1.get_counts();
            EXPECT_EQ(f1.counts.size(), 8u);
            f1.dump_counts();
        }
    };
    Nexus::run(num_threads, task);
}

void recursive_select(Fixture& f, size_t n) {
    char dummy[32];
    if (n > 0) {
        recursive_select(f, n - 1);
        f.count_selected_thread(nullptr, 0);
        (void)dummy;
    }
}

TEST(ThreadSelectionTest, require_that_selection_is_stack_location_sensistive) {
    Fixture f1(8);
    recursive_select(f1, 256);
    EXPECT_EQ(f1.counts.size(), 8u);
    f1.dump_counts();
}

GTEST_MAIN_RUN_ALL_TESTS()
