// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/metrics/simple_metrics.h>
#include <vespa/vespalib/metrics/simple_metrics_manager.h>
#include <vespa/vespalib/metrics/stable_store.h>
#include <vespa/vespalib/metrics/json_formatter.h>
#include <stdio.h>
#include <unistd.h>

using namespace vespalib;
using namespace vespalib::metrics;

struct Foo {
    int a;
    char *p;
    explicit Foo(int v) : a(v), p(nullptr) {}
    bool operator==(const Foo &other) const {
        return a == other.a;
    }
};

TEST(StableStoreTest, require_that_stable_store_works)
{
    vespalib::StableStore<Foo> bunch;
    bunch.add(Foo(1));
    bunch.add(Foo(2));
    bunch.add(Foo(3));
    bunch.add(Foo(5));
    bunch.add(Foo(8));
    bunch.add(Foo(13));
    bunch.add(Foo(21));
    bunch.add(Foo(34));
    bunch.add(Foo(55));
    bunch.add(Foo(89));

    EXPECT_EQ(bunch.size(), 10u);

    int sum = 0;

    bunch.for_each([&sum](const Foo& value) { sum += value.a; });
    EXPECT_EQ(231, sum);

    std::vector<const Foo *> pointers;
    bunch.for_each([&pointers](const Foo& value)
                   { pointers.push_back(&value); });
    EXPECT_EQ(1, pointers[0]->a);
    EXPECT_EQ(2, pointers[1]->a);
    EXPECT_EQ(55, pointers[8]->a);
    EXPECT_EQ(89, pointers[9]->a);

    for (int i = 0; i < 20000; ++i) {
        bunch.add(Foo(i));
    }
    bunch.for_each([&sum](const Foo& value) { sum -= value.a; });
    EXPECT_EQ(-199990000, sum);

    std::vector<const Foo *> after;
    bunch.for_each([&after](const Foo& value)
                   { if (after.size() < 10) after.push_back(&value); });

    EXPECT_EQ(pointers[0], after[0]);
    EXPECT_EQ(pointers[9], after[9]);
}

GTEST_MAIN_RUN_ALL_TESTS()
