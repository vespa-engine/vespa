// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/slobrok/server/mock_map_listener.h>
#include <vespa/slobrok/server/service_map_mirror.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <map>

using namespace vespalib;
using namespace slobrok;
using vespalib::make_string_short::fmt;

using Map = std::map<vespalib::string, vespalib::string>;

Map dump(const ServiceMapMirror &mirror) {
    Map result;
    for (const auto & entry : mirror.allMappings()) {
        result[entry.name] = entry.spec;
    }
    return result;
}


void addTo(ServiceMapMirror &target, const ServiceMapping &mapping) {
    auto cur = target.currentGeneration();
    std::vector<vespalib::string> removes = {};
    ServiceMappingList updates = { mapping };
    auto nxt = cur;
    nxt.add();
    MapDiff diff{cur, removes, updates, nxt};
    target.apply(diff);
}

void removeFrom(ServiceMapMirror &target, const ServiceMapping &mapping) {
    auto cur = target.currentGeneration();
    std::vector<vespalib::string> removes = { mapping.name };
    ServiceMappingList updates = { };
    auto nxt = cur;
    nxt.add();
    MapDiff diff{cur, removes, updates, nxt};
    target.apply(diff);
}

TEST(ServiceMapMirrorTest, empty_inspection) {
    ServiceMapMirror mirror;
    auto bar = dump(mirror);
    EXPECT_TRUE(bar.empty());

    MockMapListener observer;
    auto subscription = MapSubscription::subscribe(mirror, observer);
    subscription.reset();
    EXPECT_EQ(observer.last_event, MockEvent::NONE);
}

TEST(ServiceMapMirrorTest, full_inspection) {
    ServiceMapMirror mirror;
    MockMapListener observer;
    auto subscription = MapSubscription::subscribe(mirror, observer);
    for (int i = 0; i < 1984; ++i) {
        EXPECT_EQ(mirror.currentGeneration(), GenCnt(i));
        auto name = fmt("key/%d/name", i);
        auto spec = fmt("tcp/host%d.domain.tld:19099", 10000+i);
        ServiceMapping toAdd{name, spec};
        addTo(mirror, toAdd);
        EXPECT_EQ(observer.last_event, MockEvent::ADD);
        EXPECT_EQ(observer.last_add, toAdd);
    }
    EXPECT_EQ(mirror.currentGeneration(), GenCnt(1984));
    ServiceMapping toRemove{"key/666/name", "tcp/host10666.domain.tld:19099"};
    removeFrom(mirror, toRemove);
    EXPECT_EQ(observer.last_event, MockEvent::REMOVE);
    EXPECT_EQ(observer.last_remove, toRemove);
    EXPECT_EQ(mirror.currentGeneration(), GenCnt(1985));

    ServiceMapping oldMapping{"key/1969/name", "tcp/host11969.domain.tld:19099"};
    ServiceMapping toUpdate{"key/1969/name", "tcp/woodstock:19069"};
    addTo(mirror, toUpdate);
    EXPECT_EQ(observer.last_event, MockEvent::UPDATE);
    EXPECT_EQ(observer.last_remove, oldMapping);
    EXPECT_EQ(observer.last_add, toUpdate);
    EXPECT_EQ(mirror.currentGeneration(), GenCnt(1986));

    auto map = dump(mirror);
    EXPECT_FALSE(map.contains("foo"));
    EXPECT_TRUE(map.contains("key/0/name"));
    EXPECT_FALSE(map.contains("key/666/name"));
    EXPECT_TRUE(map.contains("key/1983/name"));
    EXPECT_FALSE(map.contains("key/1984/name"));
    EXPECT_TRUE(map.contains("key/1969/name"));
    EXPECT_EQ(map["key/0/name"], "tcp/host10000.domain.tld:19099");
    EXPECT_EQ(map["key/123/name"], "tcp/host10123.domain.tld:19099");
    EXPECT_EQ(map["key/1983/name"], "tcp/host11983.domain.tld:19099");
    EXPECT_EQ(map["key/1969/name"], "tcp/woodstock:19069");
    EXPECT_EQ(map.size(), 1983ul);

    auto cur = mirror.currentGeneration();
    std::vector<vespalib::string> removes = {
        "key/123/name",
        "key/1983/name",
        "key/234/name",
        "key/345/name",
        "key/123/name",
        "key/456/name"
    };
    ServiceMappingList updates = {
        ServiceMapping{ "key/567/name", "bar/1/foo" },
        ServiceMapping{ "key/678/name", "bar/2/foo" },
        ServiceMapping{ "key/234/name", "bar/3/foo" },
        ServiceMapping{ "key/345/name", "bar/4/foo" },
        ServiceMapping{ "key/789/name", "bar/5/foo" },
        ServiceMapping{ "key/666/name", "bar/6/foo" },
        ServiceMapping{ "key/567/name", "bar/7/foo" }
    };
    auto nxt = cur;
    nxt.add();
    nxt.add();
    mirror.apply(MapDiff{cur, removes, updates, nxt});
    EXPECT_EQ(mirror.currentGeneration(), GenCnt(1988));
    map = dump(mirror);
    EXPECT_FALSE(map.contains("key/123/name"));
    EXPECT_FALSE(map.contains("key/1983/name"));
    EXPECT_FALSE(map.contains("key/456/name"));
    EXPECT_TRUE(map.contains("key/0/name"));
    EXPECT_TRUE(map.contains("key/234/name"));
    EXPECT_TRUE(map.contains("key/345/name"));
    EXPECT_TRUE(map.contains("key/567/name"));
    EXPECT_TRUE(map.contains("key/666/name"));
    EXPECT_TRUE(map.contains("key/678/name"));
    EXPECT_TRUE(map.contains("key/789/name"));
    EXPECT_EQ(map["key/234/name"], "bar/3/foo");
    EXPECT_EQ(map["key/345/name"], "bar/4/foo");
    EXPECT_EQ(map["key/567/name"], "bar/7/foo");
    EXPECT_EQ(map["key/666/name"], "bar/6/foo");
    EXPECT_EQ(map["key/678/name"], "bar/2/foo");
    EXPECT_EQ(map["key/789/name"], "bar/5/foo");
    EXPECT_EQ(map.size(), 1981ul);
}


GTEST_MAIN_RUN_ALL_TESTS()

