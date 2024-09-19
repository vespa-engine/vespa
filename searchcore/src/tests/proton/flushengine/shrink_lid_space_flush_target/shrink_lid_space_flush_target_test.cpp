// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;
using search::SerialNum;
using searchcorespi::IFlushTarget;

bool validTask(const std::unique_ptr<searchcorespi::FlushTask> &task)
{
    return static_cast<bool>(task);
}

class MyLidSpace : public search::common::ICompactableLidSpace
{
    bool _canShrink;
    size_t _canFree;
public:
    MyLidSpace() noexcept
        : _canShrink(true),
          _canFree(16)
    {
    }
    ~MyLidSpace() override = default;

    void compactLidSpace(uint32_t wantedDocLidLimit) override {
        (void) wantedDocLidLimit;
    }

    bool canShrinkLidSpace() const override {
        return _canShrink;
    }

    size_t getEstimatedShrinkLidSpaceGain() const override {
        return _canShrink ? _canFree : 0;
    }

    void shrinkLidSpace() override {
        if (_canShrink) {
            _canFree = 0;
            _canShrink = false;
        }
    }
    void setCanShrink(bool canShrink) { _canShrink = canShrink; }
    void setCanFree(size_t canFree) { _canFree = canFree; }
};

class ShrinkLidSpaceFlushTargetTest : public ::testing::Test
{
protected:
    std::shared_ptr<MyLidSpace> _lidSpace;
    std::shared_ptr<ShrinkLidSpaceFlushTarget> _ft;
    ShrinkLidSpaceFlushTargetTest();
    ~ShrinkLidSpaceFlushTargetTest() override;
};

ShrinkLidSpaceFlushTargetTest::ShrinkLidSpaceFlushTargetTest()
    : ::testing::Test(),
      _lidSpace(std::make_shared<MyLidSpace>()),
      _ft(std::make_shared<ShrinkLidSpaceFlushTarget>("name",
                                                      IFlushTarget::Type::GC,
                                                      IFlushTarget::Component::ATTRIBUTE,
                                                      10,
                                                      vespalib::system_time(),
                                                      _lidSpace))
{
}

ShrinkLidSpaceFlushTargetTest::~ShrinkLidSpaceFlushTargetTest() = default;

TEST_F(ShrinkLidSpaceFlushTargetTest, require_that_flush_target_returns_estimated_memory_gain)
{
    auto memoryGain = _ft->getApproxMemoryGain();
    EXPECT_EQ(16, memoryGain.gain());
    EXPECT_EQ(10u, _ft->getFlushedSerialNum());
    EXPECT_EQ(IFlushTarget::Time(), _ft->getLastFlushTime());
}

TEST_F(ShrinkLidSpaceFlushTargetTest, require_that_flush_target_returns_no_estimated_memory_gain_when_not_able_to_flush)
{
    _lidSpace->setCanShrink(false);
    auto memoryGain = _ft->getApproxMemoryGain();
    EXPECT_EQ(0, memoryGain.gain());
}

TEST_F(ShrinkLidSpaceFlushTargetTest, require_that_flush_target_returns_no_estimated_memory_gain_right_after_shrink)
{
    auto task = _ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_TRUE(validTask(task));
    task->run();
    auto memoryGain = _ft->getApproxMemoryGain();
    EXPECT_EQ(0, memoryGain.gain());
    EXPECT_EQ(20u, _ft->getFlushedSerialNum());
    EXPECT_NE(IFlushTarget::Time(), _ft->getLastFlushTime());
}

TEST_F(ShrinkLidSpaceFlushTargetTest, require_that_flush_target_returns_no_task_when_not_able_to_flush)
{
    _lidSpace->setCanShrink(false);
    auto task = _ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_FALSE(validTask(task));
    EXPECT_EQ(20u, _ft->getFlushedSerialNum());
    EXPECT_NE(IFlushTarget::Time(), _ft->getLastFlushTime());
}

TEST_F(ShrinkLidSpaceFlushTargetTest, require_that_flush_target_returns_valid_task_when_able_to_flush_again)
{
    _lidSpace->setCanShrink(false);
    auto task = _ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_FALSE(validTask(task));
    EXPECT_EQ(20u, _ft->getFlushedSerialNum());
    EXPECT_NE(IFlushTarget::Time(), _ft->getLastFlushTime());
    _lidSpace->setCanShrink(true);
    auto memoryGain = _ft->getApproxMemoryGain();
    EXPECT_EQ(16, memoryGain.gain());
    task = _ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_TRUE(validTask(task));
    task->run();
    EXPECT_EQ(20u, _ft->getFlushedSerialNum());
}

GTEST_MAIN_RUN_ALL_TESTS()
