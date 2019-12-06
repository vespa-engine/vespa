// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("visibility_handler_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/visibilityhandler.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchcore/proton/test/threading_service_observer.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/vespalib/util/lambdatask.h>

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
    {
    }
    SerialNum getSerialNum() const override { return _serialNum; }
    void setSerialNum(SerialNum serialNum) { _serialNum = serialNum; }
};



class MyFeedView : public DummyFeedView
{
    uint32_t _forceCommitCount;
    SerialNum _committedSerialNum;


public:
    MyFeedView()
        : _forceCommitCount(0u),
          _committedSerialNum(0u)
    {
    }

    void forceCommit(SerialNum serialNum) override
    {
        EXPECT_TRUE(serialNum >= _committedSerialNum);
        _committedSerialNum = serialNum;
        ++_forceCommitCount;
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
    {
    }

    void
    checkCommitPostCondition(uint32_t expForceCommitCount,
                             SerialNum expCommittedSerialNum,
                             uint32_t expMasterExecuteCnt,
                             uint32_t expAttributeFieldWriterSyncCnt)
    {
        EXPECT_EQUAL(expForceCommitCount, _feedViewReal->getForceCommitCount());
        EXPECT_EQUAL(expCommittedSerialNum,
                     _feedViewReal->getCommittedSerialNum());
        EXPECT_EQUAL(expMasterExecuteCnt,
                     _writeService.masterObserver().getExecuteCnt());
        EXPECT_EQUAL(expAttributeFieldWriterSyncCnt,
                     _writeService.attributeFieldWriterObserver().getSyncCnt());
    }

    void
    testCommit(vespalib::duration visibilityDelay, bool internal,
               uint32_t expForceCommitCount, SerialNum expCommittedSerialNum,
               uint32_t expMasterExecuteCnt,
               uint32_t expAttributeFieldWriterSyncCnt,
               SerialNum currSerialNum = 10u)
    {
        _getSerialNum.setSerialNum(currSerialNum);
        _visibilityHandler.setVisibilityDelay(visibilityDelay);
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
                                 expMasterExecuteCnt,
                                 expAttributeFieldWriterSyncCnt);
    }

    void
    testCommitAndWait(vespalib::duration visibilityDelay, bool internal,
                      uint32_t expForceCommitCount,
                      SerialNum expCommittedSerialNum,
                      uint32_t expMasterExecuteCnt,
                      uint32_t expAttributeFieldWriterSyncCnt,
                      SerialNum currSerialNum = 10u)
    {
        _getSerialNum.setSerialNum(currSerialNum);
        _visibilityHandler.setVisibilityDelay(visibilityDelay);
        if (internal) {
            VisibilityHandler *visibilityHandler = &_visibilityHandler;
            auto task = makeLambdaTask([=]() { visibilityHandler->commitAndWait(); });
            _writeService.master().execute(std::move(task));
            _writeService.master().sync();
        } else {
            _visibilityHandler.commitAndWait();
        }
        checkCommitPostCondition(expForceCommitCount,
                                 expCommittedSerialNum,
                                 expMasterExecuteCnt,
                                 expAttributeFieldWriterSyncCnt);
    }
};

}

TEST_F("Check external commit with zero visibility delay", Fixture)
{
    f.testCommit(0s, false, 0u, 0u, 0u, 0u);
}

TEST_F("Check external commit with nonzero visibility delay", Fixture)
{
    f.testCommit(1s, false, 1u, 10u, 1u, 0u);
}

TEST_F("Check external commit with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommit(1s, false, 1u, 0u, 1u, 0u, 0u);
}

TEST_F("Check internal commit with zero visibility delay", Fixture)
{
    f.testCommit(0s, true, 0u, 0u, 1u, 0u);
}

TEST_F("Check internal commit with nonzero visibility delay", Fixture)
{
    f.testCommit(1s, true, 1u, 10u, 1u, 0u);
}

TEST_F("Check internal commit with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommit(1s, true, 1u, 0u, 1u, 0u, 0u);
}

TEST_F("Check external commitAndWait with zero visibility delay", Fixture)
{
    f.testCommitAndWait(0s, false, 0u, 0u, 0u, 1u);
}

TEST_F("Check external commitAndWait with nonzero visibility delay", Fixture)
{
    f.testCommitAndWait(1s, false, 1u, 10u, 1u, 1u);
}

TEST_F("Check external commitAndWait with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommitAndWait(1s, false, 0u, 0u, 0u, 1u, 0u);
}

TEST_F("Check internal commitAndWait with zero visibility delay", Fixture)
{
    f.testCommitAndWait(0s, true, 0u, 0u, 1u, 1u);
}

TEST_F("Check internal commitAndWait with nonzero visibility delay", Fixture)
{
    f.testCommitAndWait(1s, true, 1u, 10u, 1u, 1u);
}

TEST_F("Check internal commitAndWait with nonzero visibility delay and no new feed operation", Fixture)
{
    f.testCommitAndWait(1s, true, 0u, 0u, 1u, 1u, 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
