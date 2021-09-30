// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/searchlib/common/flush_token.h>

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

struct Fixture
{
    std::shared_ptr<MyLidSpace> _lidSpace;
    std::shared_ptr<ShrinkLidSpaceFlushTarget> _ft;
    Fixture()
        : _lidSpace(std::make_shared<MyLidSpace>()),
          _ft(std::make_shared<ShrinkLidSpaceFlushTarget>("name",
                                                          IFlushTarget::Type::GC,
                                                          IFlushTarget::Component::ATTRIBUTE,
                                                          10,
                                                          vespalib::system_time(),
                                                          _lidSpace))
    {
    }

    ~Fixture();
};

Fixture::~Fixture() = default;

TEST_F("require that flush target returns estimated memory gain", Fixture)
{
    auto memoryGain = f._ft->getApproxMemoryGain();
    EXPECT_EQUAL(16, memoryGain.gain());
    EXPECT_EQUAL(10u, f._ft->getFlushedSerialNum());
    EXPECT_EQUAL(IFlushTarget::Time(), f._ft->getLastFlushTime());
}

TEST_F("require that flush target returns no estimated memory gain when not able to flush", Fixture)
{
    f._lidSpace->setCanShrink(false);
    auto memoryGain = f._ft->getApproxMemoryGain();
    EXPECT_EQUAL(0, memoryGain.gain());
}

TEST_F("require that flush target returns no estimated memory gain right after shrink", Fixture)
{
    auto task = f._ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_TRUE(validTask(task));
    task->run();
    auto memoryGain = f._ft->getApproxMemoryGain();
    EXPECT_EQUAL(0, memoryGain.gain());
    EXPECT_EQUAL(20u, f._ft->getFlushedSerialNum());
    EXPECT_NOT_EQUAL(IFlushTarget::Time(), f._ft->getLastFlushTime());
}

TEST_F("require that flush target returns no task when not able to flush", Fixture)
{
    f._lidSpace->setCanShrink(false);
    auto task = f._ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_FALSE(validTask(task));
    EXPECT_EQUAL(20u, f._ft->getFlushedSerialNum());
    EXPECT_NOT_EQUAL(IFlushTarget::Time(), f._ft->getLastFlushTime());
}

TEST_F("require that flush target returns valid task when able to flush again", Fixture)
{
    f._lidSpace->setCanShrink(false);
    auto task = f._ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_FALSE(validTask(task));
    EXPECT_EQUAL(20u, f._ft->getFlushedSerialNum());
    EXPECT_NOT_EQUAL(IFlushTarget::Time(), f._ft->getLastFlushTime());
    f._lidSpace->setCanShrink(true);
    auto memoryGain = f._ft->getApproxMemoryGain();
    EXPECT_EQUAL(16, memoryGain.gain());
    task = f._ft->initFlush(20, std::make_shared<search::FlushToken>());
    EXPECT_TRUE(validTask(task));
    task->run();
    EXPECT_EQUAL(20u, f._ft->getFlushedSerialNum());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
