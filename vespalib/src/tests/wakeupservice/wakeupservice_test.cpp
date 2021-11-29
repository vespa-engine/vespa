// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/wakeupservice.h>

using namespace vespalib;

struct WakeupCounter : public IWakeup {
    WakeupCounter() : _count(0) {}
    void wakeup() override { _count++; }
    std::atomic<uint64_t> _count;
};

TEST("require that wakeup is called") {
    WakeupCounter a;
    WakeupService service(1ms);
    EXPECT_EQUAL(0u, a._count);
    auto ra = service.registerForInvoke([&a](){ a.wakeup(); });
    EXPECT_TRUE(ra);
    while (a._count == 0) {
        std::this_thread::sleep_for(1ms);
    }
    ra.reset();
    uint64_t countAtStop = a._count;
    std::this_thread::sleep_for(1s);
    EXPECT_EQUAL(countAtStop, a._count);
}

TEST("require that same wakeup can be registered multiple times.") {
    WakeupCounter a;
    WakeupService service(1ms);
    EXPECT_EQUAL(0u, a._count);
    auto ra1 = service.registerForInvoke([&a](){ a.wakeup(); });
    EXPECT_TRUE(ra1);
    auto ra2 = service.registerForInvoke([&a](){ a.wakeup(); });
    while (a._count == 0) {
        std::this_thread::sleep_for(1ms);
    }
    ra1.reset();
    uint64_t countAtStop = a._count;
    ra2 = service.registerForInvoke([&a](){ a.wakeup(); });
    EXPECT_TRUE(ra2);
    std::this_thread::sleep_for(1s);
    EXPECT_LESS(countAtStop, a._count);
}


TEST_MAIN() { TEST_RUN_ALL(); }
