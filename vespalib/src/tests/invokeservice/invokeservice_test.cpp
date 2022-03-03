// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>
#include <atomic>

using namespace vespalib;

struct InvokeCounter {
    InvokeCounter() : _count(0) {}
    void inc() noexcept { _count++; }
    void wait_for_atleast(uint64_t n) {
        while (_count <= n) {
            std::this_thread::sleep_for(1ms);
        }
    }
    std::atomic<uint64_t> _count;
};

TEST("require that wakeup is called") {
    InvokeCounter a;
    InvokeServiceImpl service(1ms);
    EXPECT_EQUAL(0u, a._count);
    auto ra = service.registerInvoke([&a]() noexcept { a.inc(); });
    EXPECT_TRUE(ra);
    a.wait_for_atleast(1);
    ra.reset();
    uint64_t countAtStop = a._count;
    std::this_thread::sleep_for(1s);
    EXPECT_EQUAL(countAtStop, a._count);
}

TEST("require that now is moving forward") {
    steady_time prev = steady_clock::now();
    InvokeCounter a;
    InvokeServiceImpl service(1ms);
    EXPECT_EQUAL(0u, a._count);
    auto ra = service.registerInvoke([&prev, &a, &now= service.nowRef() ]() noexcept {
        EXPECT_GREATER(now.load(), prev);
        prev = now.load();
        a.inc();
    });
    EXPECT_TRUE(ra);
    a.wait_for_atleast(100);
    ra.reset();
    EXPECT_GREATER_EQUAL(a._count, 100u);
    steady_time now = steady_clock::now();
    EXPECT_GREATER(now, prev);
    EXPECT_LESS(now - prev, 5s);
}

TEST("require that same wakeup can be registered multiple times.") {
    InvokeCounter a;
    InvokeCounter b;
    InvokeCounter c;
    InvokeServiceImpl service(1ms);
    EXPECT_EQUAL(0u, a._count);
    auto ra1 = service.registerInvoke([&a]() noexcept { a.inc(); });
    EXPECT_TRUE(ra1);
    auto rb = service.registerInvoke([&b]() noexcept { b.inc(); });
    EXPECT_TRUE(rb);
    auto rc = service.registerInvoke([&c]() noexcept { c.inc(); });
    EXPECT_TRUE(rc);
    a.wait_for_atleast(1);
    b.wait_for_atleast(1);
    c.wait_for_atleast(1);
    auto ra2 = service.registerInvoke([&a]() noexcept { a.inc(); });
    EXPECT_TRUE(ra2);

    rb.reset();
    uint64_t countAtStop = b._count;
    uint64_t a_count = a._count;
    uint64_t c_count = c._count;
    std::this_thread::sleep_for(1s);
    EXPECT_EQUAL(countAtStop, b._count);

    uint64_t diff_c = c._count - c_count;
    uint64_t diff_a = a._count - a_count;
    EXPECT_LESS((diff_c*3)/2, diff_a); // diff_c*3/2 should still be smaller than diff_a(2x)
}


TEST_MAIN() { TEST_RUN_ALL(); }
