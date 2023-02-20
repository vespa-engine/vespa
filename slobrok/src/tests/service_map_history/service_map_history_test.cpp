// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/slobrok/server/service_map_history.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <map>

using namespace vespalib;
using namespace slobrok;
using vespalib::make_string_short::fmt;

using Map = std::map<vespalib::string, vespalib::string>;

struct Dumper : ServiceMapHistory::DiffCompletionHandler {
    std::unique_ptr<MapDiff> got = {};
    void handle(MapDiff diff) override {
        got = std::make_unique<MapDiff>(std::move(diff));
    }
};

MapDiff diffGen(ServiceMapHistory &history, uint32_t gen) {
    Dumper dumper;
    history.asyncGenerationDiff(&dumper, GenCnt(gen));
    EXPECT_TRUE(dumper.got);
    return std::move(*dumper.got);
}

Map dump(ServiceMapHistory &history) {
    MapDiff full = diffGen(history, 0);
    EXPECT_TRUE(full.is_full_dump());
    Map result;
    for (const auto & [ k, v ] : full.updated) {
        result[k] = v;
    }
    return result;
}


vespalib::string lookup(ServiceMapHistory &history, const vespalib::string &name) {
    auto map = dump(history);
    auto iter = map.find(name);
    if (iter == map.end()) {
        return {};
    } else {
        return iter->second;
    }
}

TEST(ServiceMapHistoryTest, empty_inspection) {
    ServiceMapHistory p;
    auto bar = dump(p);
    EXPECT_TRUE(bar.empty());

    auto gen = p.currentGen();
    EXPECT_EQ(gen, GenCnt(1));

    Dumper dumper;
    {
        ServiceMapHistory empty2;
        empty2.asyncGenerationDiff(&dumper, gen);
    }
    EXPECT_TRUE(dumper.got);
    auto diff1 = std::move(*dumper.got);
    EXPECT_FALSE(diff1.is_full_dump());
    EXPECT_EQ(diff1.fromGen, gen);
    EXPECT_TRUE(diff1.removed.empty());
    EXPECT_TRUE(diff1.updated.empty());
    EXPECT_EQ(diff1.toGen, gen);

    auto diff2 = diffGen(p, 42);
    EXPECT_TRUE(diff2.is_full_dump());
    EXPECT_EQ(diff2.fromGen, GenCnt(0));
    EXPECT_TRUE(diff2.removed.empty());
    EXPECT_TRUE(diff2.updated.empty());
    EXPECT_EQ(diff2.toGen, gen);

    auto diff3 = diffGen(p, 0);
    EXPECT_TRUE(diff3.is_full_dump());
    EXPECT_EQ(diff3.fromGen, GenCnt(0));
    EXPECT_TRUE(diff3.removed.empty());
    EXPECT_TRUE(diff3.updated.empty());
    EXPECT_EQ(diff3.toGen, gen);
}

TEST(ServiceMapHistoryTest, full_inspection) {
    Dumper dumper;
    {
        ServiceMapHistory p;
        for (int i = 0; i < 1984; ++i) {
            auto name = fmt("key/%d/name", i);
            auto spec = fmt("tcp/host%d.domain.tld:19099", 10000+i);
            p.add(ServiceMapping{name, spec});
        }
        EXPECT_EQ(p.currentGen(), GenCnt(1985));
        p.remove(ServiceMapping{"key/666/name", "tcp/host10666.domain.tld:19099"});
        EXPECT_EQ(p.currentGen(), GenCnt(1986));
        p.add(ServiceMapping{"key/1969/name", "tcp/woodstock:19069"});
        EXPECT_EQ(p.currentGen(), GenCnt(1987));

        auto map = dump(p);
        
        EXPECT_FALSE(map.contains("foo"));
        EXPECT_TRUE(map.contains("key/0/name"));
        EXPECT_FALSE(map.contains("key/666/name"));
        EXPECT_TRUE(map.contains("key/1983/name"));
        EXPECT_FALSE(map.contains("key/1984/name"));
        EXPECT_TRUE(map.contains("key/1969/name"));

        auto foo = map["key/0/name"];
        EXPECT_EQ(foo, "tcp/host10000.domain.tld:19099");

        foo = map["key/123/name"];
        EXPECT_EQ(foo, "tcp/host10123.domain.tld:19099");

        foo = map["key/1983/name"];
        EXPECT_EQ(foo, "tcp/host11983.domain.tld:19099");

        foo = map["key/1969/name"];
        EXPECT_EQ(foo, "tcp/woodstock:19069");
        
        EXPECT_EQ(map.size(), 1983ul);

        auto gen = p.currentGen();
        
        auto diff2 = diffGen(p, 42);
        EXPECT_TRUE(diff2.is_full_dump());
        EXPECT_EQ(diff2.fromGen, GenCnt(0));
        EXPECT_TRUE(diff2.removed.empty());
        EXPECT_EQ(diff2.updated.size(), 1983ul);
        EXPECT_EQ(diff2.toGen, gen);
        
        auto diff3 = diffGen(p, 1984);
        EXPECT_FALSE(diff3.is_full_dump());
        EXPECT_EQ(diff3.fromGen, GenCnt(1984));
        EXPECT_EQ(diff3.removed.size(), 1ul);
        EXPECT_EQ(diff3.updated.size(), 2ul);
        EXPECT_EQ(diff3.toGen, gen);
        
        p.asyncGenerationDiff(&dumper, gen);
        EXPECT_FALSE(dumper.got);
    }
    EXPECT_TRUE(dumper.got);
    auto diff1 = std::move(*dumper.got);
    EXPECT_EQ(diff1.fromGen, GenCnt(1987));
    EXPECT_TRUE(diff1.removed.empty());
    EXPECT_TRUE(diff1.updated.empty());
    EXPECT_EQ(diff1.toGen, GenCnt(1987));
    EXPECT_FALSE(diff1.is_full_dump());
}

class MockListener : public ServiceMapHistory::DiffCompletionHandler {
public:
    bool got_update = false;
    GenCnt got_gen = GenCnt(0);
    size_t got_removes = 0;
    size_t got_updates = 0;

    void handle(MapDiff diff) override {
        got_update = true;
        got_gen = diff.toGen;
        got_removes = diff.removed.size();
        got_updates = diff.updated.size();
    }

    ~MockListener() override;
};

MockListener::~MockListener() = default;

TEST(ServiceMapHistoryTest, handlers_test) {
    MockListener handler1;
    MockListener handler2;
    MockListener handler3;
    MockListener handler4;
    MockListener handler5;
    {
        ServiceMapHistory p;
        p.asyncGenerationDiff(&handler1, GenCnt(0));
        p.asyncGenerationDiff(&handler2, GenCnt(1));
        EXPECT_TRUE(handler1.got_update);
        EXPECT_FALSE(handler2.got_update);
        EXPECT_FALSE(handler3.got_update);
        EXPECT_EQ(handler1.got_gen, GenCnt(1));
        EXPECT_EQ(handler1.got_removes, 0ul);
        EXPECT_EQ(handler1.got_updates, 0ul);
        bool cantCancel = p.cancel(&handler1);
        EXPECT_FALSE(cantCancel);

        handler1.got_update = false;
        p.add(ServiceMapping{"foo", "bar"});
        EXPECT_FALSE(handler1.got_update);
        EXPECT_TRUE(handler2.got_update);
        EXPECT_FALSE(handler3.got_update);
        EXPECT_EQ(handler2.got_removes, 0ul);
        EXPECT_EQ(handler2.got_updates, 1ul);

        handler2.got_update = false;
        p.asyncGenerationDiff(&handler3, GenCnt(2));
        EXPECT_FALSE(handler3.got_update);
        p.remove(ServiceMapping{"foo", "bar"});
        EXPECT_FALSE(handler1.got_update);
        EXPECT_FALSE(handler2.got_update);
        EXPECT_TRUE(handler3.got_update);
        EXPECT_EQ(handler3.got_removes, 1ul);
        EXPECT_EQ(handler3.got_updates, 0ul);

        p.asyncGenerationDiff(&handler4, GenCnt(3));
        EXPECT_FALSE(handler4.got_update);
        p.asyncGenerationDiff(&handler5, GenCnt(3));
        EXPECT_FALSE(handler5.got_update);
        bool couldCancel = p.cancel(&handler4);
        EXPECT_TRUE(couldCancel);

        handler1.got_update = false;
        handler2.got_update = false;
        handler3.got_update = false;
    }
    EXPECT_FALSE(handler1.got_update);
    EXPECT_FALSE(handler2.got_update);
    EXPECT_FALSE(handler3.got_update);
    EXPECT_FALSE(handler4.got_update);
    EXPECT_TRUE(handler5.got_update);
    EXPECT_EQ(handler5.got_removes, 0ul);
    EXPECT_EQ(handler5.got_updates, 0ul);
}

GTEST_MAIN_RUN_ALL_TESTS()

