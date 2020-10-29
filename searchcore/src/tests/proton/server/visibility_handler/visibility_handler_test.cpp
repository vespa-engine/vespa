// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/visibilityhandler.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchcore/proton/test/threading_service_observer.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/log/log.h>
LOG_SETUP("visibility_handler_test");

using search::SerialNum;
using proton::IGetSerialNum;
using proton::test::DummyFeedView;
using proton::ExecutorThreadingService;
using proton::test::ThreadingServiceObserver;
using proton::IFeedView;
using proton::VisibilityHandler;
using vespalib::makeLambdaTask;

namespace {

class MyGetSerialNum : public IGetSerialNum
{
    SerialNum _serialNum;
public:
    MyGetSerialNum()
        : _serialNum(0u)
    {}
    SerialNum getSerialNum() const override { return _serialNum; }
    void setSerialNum(SerialNum serialNum) { _serialNum = serialNum; }
};



class MyFeedView : public DummyFeedView
{
    uint32_t _forceCommitCount;
    SerialNum _committedSerialNum;
public:
    std::unique_ptr<proton::PendingLidTrackerBase> _tracker;


    MyFeedView()
        : _forceCommitCount(0u),
          _committedSerialNum(0u)
    {}

    void setTracker(vespalib::duration visibilityDelay) {
        if (visibilityDelay == vespalib::duration::zero()) {
            _tracker = std::make_unique<proton::PendingLidTracker>();
        } else {
            _tracker = std::make_unique<proton::TwoPhasePendingLidTracker>();
        }
    }

    void forceCommit(SerialNum serialNum, DoneCallback) override
    {
        EXPECT_TRUE(serialNum >= _committedSerialNum);
        _committedSerialNum = serialNum;
        ++_forceCommitCount;
        _tracker->produceSnapshot();
    }

    uint32_t getForceCommitCount() const { return _forceCommitCount; }
    SerialNum getCommittedSerialNum() const { return _committedSerialNum; }
};


class Fixture
{
public:
    MyGetSerialNum _getSerialNum;
    vespalib::ThreadStackExecutor _sharedExecutor;
    ExecutorThreadingService _writeServiceReal;
    ThreadingServiceObserver _writeService;
    std::shared_ptr<MyFeedView> _feedViewReal;
    vespalib::VarHolder<IFeedView::SP> _feedView;
    VisibilityHandler _visibilityHandler;


    Fixture()
        : _getSerialNum(),
          _sharedExecutor(1, 0x10000),
          _writeServiceReal(_sharedExecutor),
          _writeService(_writeServiceReal),
          _feedViewReal(std::make_shared<MyFeedView>()),
          _feedView(_feedViewReal),
          _visibilityHandler(_getSerialNum, _writeService, _feedView)
    {}

    void
    checkCommitPostCondition(uint32_t expForceCommitCount,
                             SerialNum expCommittedSerialNum,
                             uint32_t expMasterExecuteCnt)
    {
        EXPECT_EQUAL(expForceCommitCount, _feedViewReal->getForceCommitCount());
        EXPECT_EQUAL(expCommittedSerialNum,
                     _feedViewReal->getCommittedSerialNum());
        EXPECT_EQUAL(expMasterExecuteCnt,
                     _writeService.masterObserver().getExecuteCnt());
    }

    void
    testCommit(vespalib::duration visibilityDelay, bool internal,
               uint32_t expForceCommitCount, SerialNum expCommittedSerialNum,
               uint32_t expMasterExecuteCnt,
               SerialNum currSerialNum = 10u)
    {
        _feedViewReal->setTracker(visibilityDelay);
        _getSerialNum.setSerialNum(currSerialNum);
        if (internal) {
            VisibilityHandler *visibilityHandler = &_visibilityHandler;
            auto task = makeLambdaTask([=]() { visibilityHandler->commit(); });
            _writeService.master().execute(std::move(task));
        } else {
            _visibilityHandler.commit();
        }
        _writeService.master().sync();
        checkCommitPostCondition(expForceCommitCount,
                                 expCommittedSerialNum,
                                 expMasterExecuteCnt);
    }

    proton::PendingLidTracker::Token
    createToken(proton::PendingLidTrackerBase & tracker, SerialNum serialNum, uint32_t lid) {
        if (serialNum == 0) {
            return proton::PendingLidTracker::Token();
        } else {
            return tracker.produce(lid);;
        }
    }

    void
    testCommitAndWait(vespalib::duration visibilityDelay, bool internal,
                      uint32_t expForceCommitCount,
                      SerialNum expCommittedSerialNum,
                      uint32_t expMasterExecuteCnt,
                      SerialNum currSerialNum = 10u)
    {
        _feedViewReal->setTracker(visibilityDelay);
        _getSerialNum.setSerialNum(currSerialNum);
        constexpr uint32_t MY_LID=13;
        proton::PendingLidTrackerBase * lidTracker = _feedViewReal->_tracker.get();
        {
            proton::PendingLidTracker::Token token = createToken(*lidTracker, currSerialNum, MY_LID);
        }
        if (internal) {
            VisibilityHandler *visibilityHandler = &_visibilityHandler;
            auto task = makeLambdaTask([=]() { visibilityHandler->commitAndWait(*lidTracker, MY_LID); });
            _writeService.master().execute(std::move(task));
            _writeService.master().sync();
        } else {
            _visibilityHandler.commitAndWait(*lidTracker, MY_LID);
        }
        checkCommitPostCondition(expForceCommitCount,
                                 expCommittedSerialNum,
                                 expMasterExecuteCnt);
    }
};

}

TEST_F("Check external commit with zero visibility delay", Fixture)
{
    f.testCommit(0s, false, 0u, 0u, 0u);
}

TEST_F("Check internal commit with zero visibility delay", Fixture)
{
    f.testCommit(0s, true, 0u, 0u, 1u);
}

TEST_F("Check external commitAndWait with zero visibility delay", Fixture)
{
    f.testCommitAndWait(0s, false, 0u, 0u, 0u);
}

TEST_F("Check external commitAndWait with nonzero visibility delay", Fixture)
{
    f.testCommitAndWait(1s, false, 1u, 10u, 1u);
}

TEST_F("Check external commitAndWait with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommitAndWait(1s, false, 0u, 0u, 0u, 0u);
}

TEST_F("Check internal commitAndWait with zero visibility delay", Fixture)
{
    f.testCommitAndWait(0s, true, 0u, 0u, 1u);
}

TEST_F("Check internal commitAndWait with nonzero visibility delay", Fixture)
{
    f.testCommitAndWait(1s, true, 1u, 10u, 1u);
}

TEST_F("Check internal commitAndWait with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommitAndWait(1s, true, 0u, 0u, 1u, 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
