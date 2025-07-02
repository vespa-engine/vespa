// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/flush_history.h>
#include <vespa/searchcore/proton/flushengine/flush_history_view.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::flushengine::FlushHistory;
using proton::flushengine::FlushHistoryEntry;
using proton::flushengine::FlushHistoryView;

using namespace std::literals::chrono_literals;

namespace {

const std::string NORMAL_STRATEGY("normal");
const std::string ALL_STRATEGY("all");
const std::string HANDLER1("handler1");
const std::string HANDLER2("handler2");

template <typename Entry>
std::vector<std::string>
make_names(const std::vector<Entry>& entries) {
    std::vector<std::string> result;
    result.reserve(entries.size());
    for (auto& entry : entries) {
        result.emplace_back(entry.name());
    }
    return result;
}

}

class FlushHistoryTest : public ::testing::Test
{
protected:

    using SV = std::vector<std::string>;

    FlushHistory _flush_history;

    FlushHistoryTest();
    ~FlushHistoryTest() override;
};

FlushHistoryTest::FlushHistoryTest()
    : ::testing::Test(),
      _flush_history(NORMAL_STRATEGY, 42, 3)
{
}

FlushHistoryTest::~FlushHistoryTest() = default;

TEST_F(FlushHistoryTest, empty_history)
{
    auto view = _flush_history.make_view();
    EXPECT_EQ(NORMAL_STRATEGY, view->strategy());
    EXPECT_EQ(42, view->strategy_id());
    EXPECT_FALSE(view->priority_strategy());
    EXPECT_EQ(3, view->max_concurrent_normal());
    EXPECT_TRUE(view->finished().empty());
    EXPECT_TRUE(view->active().empty());
    EXPECT_TRUE(view->pending().empty());
    EXPECT_TRUE(view->finished_strategies().empty());
    EXPECT_TRUE(view->last_strategies().empty());
}

TEST_F(FlushHistoryTest, track_flushes)
{
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.start_flush(HANDLER1, "a3", 4s, 7);
    _flush_history.flush_done(6);
    _flush_history.flush_done(5);
    _flush_history.prune_done(6);
    _flush_history.prune_done(5);
    auto view = _flush_history.make_view();
    EXPECT_EQ((SV{"handler2.a2", "handler1.a1"}), make_names(view->finished()));
    EXPECT_EQ(SV{"handler1.a3"}, make_names(view->active()));
}

TEST_F(FlushHistoryTest, trackes_pending_flushes)
{
    _flush_history.add_pending_flush(HANDLER1, "a1", 3s);
    _flush_history.add_pending_flush(HANDLER2, "a2", 1s);
    _flush_history.add_pending_flush(HANDLER2, "a3", 4s);
    _flush_history.add_pending_flush(HANDLER1, "a4", 7s);
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.flush_done(6);
    _flush_history.prune_done(6);
    auto view = _flush_history.make_view();
    EXPECT_EQ(SV{"handler2.a2"}, make_names(view->finished()));
    EXPECT_EQ(SV{"handler1.a1"}, make_names(view->active()));
    EXPECT_EQ((SV{"handler2.a3", "handler1.a4"}), make_names(view->pending()));
}

TEST_F(FlushHistoryTest, pending_flushes_can_be_cleared)
{
    _flush_history.add_pending_flush(HANDLER1, "a1", 3s);
    _flush_history.clear_pending_flushes();
    auto view = _flush_history.make_view();
    EXPECT_TRUE(view->pending().empty());
}

TEST_F(FlushHistoryTest, active_priority_flush_strategy_can_be_detected)
{
    _flush_history.set_strategy(ALL_STRATEGY, 43, true);
    auto view = _flush_history.make_view();
    EXPECT_EQ(ALL_STRATEGY, view->strategy());
    EXPECT_EQ(43, view->strategy_id());
    EXPECT_TRUE(view->priority_strategy());
}

TEST_F(FlushHistoryTest, flush_strategy_can_be_changed)
{
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.set_strategy(ALL_STRATEGY, 43, true);
    _flush_history.add_pending_flush(HANDLER2, "a2", 1s);
    _flush_history.add_pending_flush(HANDLER1, "a3", 4s);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.start_flush(HANDLER1, "a3", 4s, 7);
    _flush_history.set_strategy(NORMAL_STRATEGY, 44, false);
    auto view = _flush_history.make_view();
    EXPECT_EQ(NORMAL_STRATEGY, view->strategy());
    EXPECT_EQ(44, view->strategy_id());
    EXPECT_FALSE(view->priority_strategy());
    EXPECT_TRUE(view->finished().empty());
    ASSERT_EQ((SV{"handler1.a1", "handler2.a2", "handler1.a3"}), make_names(view->active()));
    EXPECT_EQ(NORMAL_STRATEGY, view->active()[0].strategy());
    EXPECT_EQ(ALL_STRATEGY, view->active()[1].strategy());
    EXPECT_EQ(ALL_STRATEGY, view->active()[2].strategy());
    EXPECT_EQ((SV{NORMAL_STRATEGY, ALL_STRATEGY}), make_names(view->finished_strategies()));
    EXPECT_FALSE(view->finished_strategies()[0].priority_strategy());
    EXPECT_TRUE(view->finished_strategies()[1].priority_strategy());
    EXPECT_EQ(42, view->finished_strategies()[0].id());
    EXPECT_EQ(43, view->finished_strategies()[1].id());
    EXPECT_EQ((SV{ALL_STRATEGY, NORMAL_STRATEGY}), make_names(view->last_strategies()));
}

GTEST_MAIN_RUN_ALL_TESTS()
