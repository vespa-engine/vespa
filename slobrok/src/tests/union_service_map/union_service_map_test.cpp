// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/slobrok/server/mock_map_listener.h>
#include <vespa/slobrok/server/union_service_map.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace slobrok;
using vespalib::make_string_short::fmt;

TEST(UnionServiceMapTest, forwards_simple_requests) {
    ProxyMapSource source;
    UnionServiceMap unionizer;
    MockMapListener observer;
    auto subscription1 = MapSubscription::subscribe(unionizer, observer);
    auto subscription2 = MapSubscription::subscribe(source, unionizer);

    EXPECT_EQ(observer.last_event, MockEvent::NONE);

    ServiceMapping one{"foo/1", "bar/1"};
    source.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, one);
    ServiceMapping two{"foo/2", "bar/2"};
    source.add(two);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, two);

    source.remove(one);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, one);

    ServiceMapping two_q{"foo/2", "qux/2"};
    source.update(two, two_q);
    // update implemented ass remove+add:
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_remove, two);
    EXPECT_EQ(observer.last_add, two_q);
}

TEST(UnionServiceMapTest, handles_refcount) {
    ProxyMapSource source1;
    ProxyMapSource source2;
    ProxyMapSource source3;
    UnionServiceMap unionizer;
    MockMapListener observer;
    auto subscription1 = MapSubscription::subscribe(unionizer, observer);
    auto subscription2 = MapSubscription::subscribe(source1, unionizer);
    auto subscription3 = MapSubscription::subscribe(source2, unionizer);
    auto subscription4 = MapSubscription::subscribe(source3, unionizer);

    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    ServiceMapping one{"foo/1", "bar/1"};
    source1.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, one);
    observer.clear();
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source2.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source3.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    ServiceMapping two{"foo/2", "bar/2"};
    source1.add(two);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, two);
    observer.clear();
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source2.add(two);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);

    source1.remove(one);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source2.remove(one);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);

    source1.remove(two);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source2.remove(two);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, two);

    observer.clear();
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source3.remove(one);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, one);
}

TEST(UnionServiceMapTest, handles_conflicts) {
    ProxyMapSource source1;
    ProxyMapSource source2;
    ProxyMapSource source3;
    UnionServiceMap unionizer;
    MockMapListener observer;
    auto subscription1 = MapSubscription::subscribe(unionizer, observer);
    auto subscription2 = MapSubscription::subscribe(source1, unionizer);
    auto subscription3 = MapSubscription::subscribe(source2, unionizer);
    auto subscription4 = MapSubscription::subscribe(source3, unionizer);

    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    ServiceMapping one{"foo/1", "bar/1"};
    source1.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, one);
    observer.clear();
    source2.add(one);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);

    ServiceMapping two{"foo/2", "bar/2"};
    source1.add(two);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, two);
    observer.clear();
    source2.add(two);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);

    ServiceMapping one_q{"foo/1", "qux/1"};
    source3.add(one_q);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, one);

    ServiceMapping two_q{"foo/2", "qux/2"};
    source3.add(two_q);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, two);

    source3.remove(one_q);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, one);

    observer.clear();
    source1.remove(two);
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
    source2.remove(two);
    EXPECT_EQ(observer.last_event, MockEvent::ADD);
    EXPECT_EQ(observer.last_add, two_q);
}


GTEST_MAIN_RUN_ALL_TESTS()

