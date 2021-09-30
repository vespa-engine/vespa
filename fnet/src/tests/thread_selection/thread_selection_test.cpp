// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>
#include <chrono>
#include <mutex>
#include <map>

struct Fixture {
    std::mutex lock;
    FNET_Transport transport;
    std::map<FNET_TransportThread *, size_t> counts;
    Fixture(size_t num_threads) : transport(num_threads) {}
    void count_selected_thread(const void *key, size_t key_len) {
        std::lock_guard<std::mutex> guard(lock);
        FNET_TransportThread *thread = transport.select_thread(key, key_len);
        ++counts[thread];
    }
    std::vector<size_t> get_counts() {
        std::vector<size_t> result;
        for (const auto &entry: counts) {
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

TEST_F("require that selection is time sensistive", Fixture(8))
{
    using namespace std::literals;
    vespalib::string key("my random key");
    for (size_t i = 0; i < 256; ++i) {
        f1.count_selected_thread(key.data(), key.size());
        std::this_thread::sleep_for(10ms);
    }
    EXPECT_EQUAL(f1.counts.size(), 8u);
    f1.dump_counts();
}

TEST_F("require that selection is key sensistive", Fixture(8))
{
    for (size_t i = 0; i < 256; ++i) {
        vespalib::string key = vespalib::make_string("my random key %zu", i);
        f1.count_selected_thread(key.data(), key.size());
    }
    EXPECT_EQUAL(f1.counts.size(), 8u);
    f1.dump_counts();
}

TEST_MT_F("require that selection is thread sensitive", 256, Fixture(8))
{
    f1.count_selected_thread(nullptr, 0);
    TEST_BARRIER();
    if (thread_id == 0) {
        std::vector<size_t> counts = f1.get_counts();
        EXPECT_EQUAL(f1.counts.size(), 8u);
        f1.dump_counts();
    }
}

void recursive_select(Fixture &f, size_t n) {
    char dummy[32];
    if (n > 0) {
        recursive_select(f, n - 1);
        f.count_selected_thread(nullptr, 0);
        (void) dummy;
    }
}

TEST_F("require that selection is stack location sensistive", Fixture(8))
{
    recursive_select(f, 256);
    EXPECT_EQUAL(f1.counts.size(), 8u);
    f1.dump_counts();
}

TEST_MAIN() { TEST_RUN_ALL(); }
