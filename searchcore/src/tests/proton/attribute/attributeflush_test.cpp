// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/file.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/flushableattribute.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <filesystem>
#include <thread>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP("attributeflush_test");

using namespace document;
using namespace search;
using namespace vespalib;

using search::index::DummyFileHeaderContext;

using AVConfig = search::attribute::Config;
using AVBasicType = search::attribute::BasicType;
using AVCollectionType = search::attribute::CollectionType;
using searchcorespi::IFlushTarget;
using searchcorespi::FlushStats;
using std::chrono::duration_cast;
using vespalib::HwInfo;
using namespace std::literals;

using GateSP = std::shared_ptr<Gate>;

namespace fs = std::filesystem;

namespace proton {

namespace {

const uint64_t createSerialNum = 42u;

constexpr auto zero_flush_duration = std::chrono::steady_clock::duration::zero();

}

class TaskWrapper : public Executor::Task
{
private:
    Executor::Task::UP _task;
    GateSP             _gate;
public:
    TaskWrapper(Executor::Task::UP task, const GateSP &gate)
        : _task(std::move(task)),
          _gate(gate)
    {
    }

    virtual void
    run() override
    {
        _task->run();
        _gate->countDown();
        LOG(info, "doneFlushing");
    }
};


class FlushHandler
{
private:
    ThreadStackExecutor _executor;
public:
    GateSP gate;

    FlushHandler()
        : _executor(1),
          gate()
    { }
    ~FlushHandler();

    void
    doFlushing(Executor::Task::UP task)
    {
        if (!task) {
            return;
        }
        Executor::Task::UP wrapper(new TaskWrapper(std::move(task), gate));
        Executor::Task::UP ok = _executor.execute(std::move(wrapper));
        assert(ok.get() == nullptr);
    }
};


class UpdaterTask
{
private:
    proton::AttributeManager & _am;
public:
    UpdaterTask(proton::AttributeManager & am)
        :
        _am(am)
    {
    }

    void
    startFlushing(uint64_t syncToken, FlushHandler & handler);

    void
    run();
};

FlushHandler::~FlushHandler() = default;

void
UpdaterTask::startFlushing(uint64_t syncToken, FlushHandler & handler)
{
    handler.gate.reset(new Gate());
    IFlushTarget::SP flushable = _am.getFlushable("a1");
    LOG(info, "startFlushing(%" PRIu64 ")", syncToken);
    handler.doFlushing(flushable->initFlush(syncToken, std::make_shared<search::FlushToken>()));
}


void
UpdaterTask::run()
{
    LOG(info, "UpdaterTask::run(begin)");
    uint32_t totalDocs = 2000000;
    uint32_t totalDocsMax = 125000000; // XXX: Timing dependent.
    uint32_t slowdownUpdateLim = 4000000;
    bool slowedDown = false;
    uint32_t incDocs = 1000;
    uint64_t commits = 0;
    uint32_t flushCount = 0;
    uint64_t flushedToken = 0;
    uint64_t needFlushToken = 0;
    FlushHandler flushHandler;
    for (uint32_t i = incDocs;
         i <= totalDocs || (flushCount + (flushedToken <
                                          needFlushToken) <= 2 &&
                            i <= totalDocsMax);
         i += incDocs) {
        uint32_t startDoc = 0;
        uint32_t lastDoc = 0;
        AttributeGuard::UP agap = _am.getAttribute("a1");
        AttributeGuard &ag(*agap);
        IntegerAttribute & ia = static_cast<IntegerAttribute &>(*ag);
        for (uint32_t j = i - incDocs; j < i; ++j) {
            if (j >= ag->getNumDocs()) {
                ag->addDocs(startDoc, lastDoc, incDocs);
                if (i % (totalDocs / 20) == 0) {
                    LOG(info,
                        "addDocs(%u, %u, %u)",
                        startDoc, lastDoc, ag->getNumDocs());
                }
            }
            ia.update(j, i);
        }
        ia.commit(CommitParam(i-1, i, CommitParam::UpdateStats::SKIP)); // save i as last sync token
        needFlushToken = i;
        assert(i + 1 == ag->getNumDocs());
        if ((commits++ % 20 == 0) &&
            ( ! flushHandler.gate ||
             flushHandler.gate->getCount() == 0)) {
            startFlushing(i, flushHandler);
            ++flushCount;
            flushedToken = i;
            slowedDown = false;
        }
        if (needFlushToken > flushedToken + slowdownUpdateLim) {
            std::this_thread::sleep_for(100ms);
            if (!slowedDown) {
                LOG(warning,
                    "Slowing down updates due to slow flushing (slow disk ?)");
            }
            slowedDown = true;
        }
    }
    if (flushHandler.gate) {
        flushHandler.gate->await();
    }
    if (flushedToken < needFlushToken) {
        startFlushing(needFlushToken, flushHandler);
        flushHandler.gate->await();
    }
    LOG(info, "UpdaterTask::run(end)");
}


AVConfig
getInt32Config()
{
    return AVConfig(AVBasicType::INT32);
}

AVConfig getInt32ArrayConfig()
{
    return AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY);
}

const std::string test_dir = "flush";

struct BaseFixture
{
    test::DirectoryHandler   _dirHandler;
    DummyFileHeaderContext   _fileHeaderContext;
    ForegroundTaskExecutor   _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo                   _hwInfo;
    BaseFixture();
    BaseFixture(const HwInfo &hwInfo);
    ~BaseFixture();
};

BaseFixture::BaseFixture()
        : _dirHandler(test_dir),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo()
{ }
BaseFixture::BaseFixture(const HwInfo &hwInfo)
        : _dirHandler(test_dir),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo(hwInfo)
{}
BaseFixture::~BaseFixture() = default;

struct AttributeManagerFixture
{
    AttributeManager::SP _msp;
    AttributeManager &_m;
    AttributeManagerFixture(BaseFixture &bf);
    ~AttributeManagerFixture();
    AttributeVector::SP addAttribute(const std::string &name) {
        return _m.addAttribute({name, getInt32Config()}, createSerialNum);
    }
    AttributeVector::SP addPostingAttribute(const std::string &name) {
        AVConfig cfg(getInt32Config());
        cfg.setFastSearch(true);
        return _m.addAttribute({name, cfg}, createSerialNum);
    }
    AttributeVector::SP addIntArrayPostingAttribute(const std::string &name) {
        AVConfig cfg(getInt32ArrayConfig());
        cfg.setFastSearch(true);
        return _m.addAttribute({name, cfg}, createSerialNum);
    }
};

AttributeManagerFixture::AttributeManagerFixture(BaseFixture &bf)
    : _msp(std::make_shared<AttributeManager>(test_dir, "test.subdb", TuneFileAttributes(),
                                              bf._fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                              bf._attributeFieldWriter, bf._shared, bf._hwInfo)),
      _m(*_msp)
{}
AttributeManagerFixture::~AttributeManagerFixture() = default;

struct Fixture : public BaseFixture, public AttributeManagerFixture
{
    Fixture()
        : BaseFixture(),
          AttributeManagerFixture(*static_cast<BaseFixture *>(this))
    {
    }
    Fixture(const HwInfo &hwInfo)
        : BaseFixture(hwInfo),
          AttributeManagerFixture(*static_cast<BaseFixture *>(this))
    {
    }
    ~Fixture();
};

Fixture::~Fixture() = default;

TEST(AttributeFlushTest, require_that_updater_and_flusher_can_run_concurrently)
{
    Fixture f;
    AttributeManager &am = f._m;
    EXPECT_TRUE(f.addAttribute("a1").get() != nullptr);
    IFlushTarget::SP ft = am.getFlushable("a1");
    (static_cast<FlushableAttribute *>(ft.get()))->setCleanUpAfterFlush(false);
    UpdaterTask updaterTask(am);
    updaterTask.run();

    IndexMetaInfo info("flush/a1");
    EXPECT_TRUE(info.load());
    EXPECT_TRUE(info.snapshots().size() > 2);
    for (size_t i = 0; i < info.snapshots().size(); ++i) {
        const IndexMetaInfo::Snapshot & snap = info.snapshots()[i];
        LOG(info,
            "Snapshot(%" PRIu64 ", %s)",
            snap.syncToken, snap.dirName.c_str());
        if (snap.syncToken > 0) {
            EXPECT_TRUE(snap.valid);
            std::string baseFileName = "flush/a1/" + snap.dirName + "/a1";
            AttributeVector::SP attr =
                AttributeFactory::createAttribute(baseFileName,
                        getInt32Config());
            EXPECT_TRUE(attr->load());
            EXPECT_EQ((uint32_t)snap.syncToken + 1, attr->getNumDocs());
        }
    }
}

TEST(AttributeFlushTest, require_that_flushable_attribute_reports_memory_usage)
{
    Fixture f;
    AttributeManager &am = f._m;
    AttributeVector::SP av = f.addAttribute("a2");
    av->addDocs(100);
    av->commit();
    IFlushTarget::SP fa = am.getFlushable("a2");
    EXPECT_TRUE(av->getStatus().getAllocated() >= 100u * sizeof(int32_t));
    EXPECT_EQ(av->getStatus().getUsed(),
              fa->getApproxMemoryGain().getBefore()+0lu);
    // attributes stay in memory
    EXPECT_EQ(fa->getApproxMemoryGain().getBefore(),
              fa->getApproxMemoryGain().getAfter());
}

TEST(AttributeFlushTest, require_that_flushable_attribute_manages_sync_token_info)
{
    Fixture f;
    AttributeManager &am = f._m;
    AttributeVector::SP av = f.addAttribute("a3");
    av->addDocs(1);
    IFlushTarget::SP fa = am.getFlushable("a3");

    IndexMetaInfo info("flush/a3");
    EXPECT_EQ(0u, fa->getFlushedSerialNum());
    EXPECT_TRUE(fa->initFlush(0, std::make_shared<search::FlushToken>()).get() == nullptr);
    EXPECT_TRUE(!info.load());

    av->commit(CommitParam(10)); // last sync token = 10
    EXPECT_EQ(0u, fa->getFlushedSerialNum());
    EXPECT_TRUE(fa->initFlush(10, std::make_shared<search::FlushToken>()).get() != nullptr);
    fa->initFlush(10, std::make_shared<search::FlushToken>())->run();
    EXPECT_EQ(10u, fa->getFlushedSerialNum());
    EXPECT_TRUE(info.load());
    EXPECT_EQ(1u, info.snapshots().size());
    EXPECT_TRUE(info.snapshots()[0].valid);
    EXPECT_EQ(10u, info.snapshots()[0].syncToken);

    av->commit(CommitParam(20)); // last sync token = 20
    EXPECT_EQ(10u, fa->getFlushedSerialNum());
    fa->initFlush(20, std::make_shared<search::FlushToken>())->run();
    EXPECT_EQ(20u, fa->getFlushedSerialNum());
    EXPECT_TRUE(info.load());
    EXPECT_EQ(1u, info.snapshots().size()); // snapshot 10 removed
    EXPECT_TRUE(info.snapshots()[0].valid);
    EXPECT_EQ(20u, info.snapshots()[0].syncToken);
}

TEST(AttributeFlushTest, require_that_flush_targets_can_be_retrieved)
{
    Fixture f;
    AttributeManager &am = f._m;
    f.addAttribute("a4");
    f.addAttribute("a5");
    std::vector<IFlushTarget::SP> ftl = am.getFlushTargets();
    EXPECT_EQ(4u, ftl.size());
    EXPECT_EQ(am.getFlushable("a4").get(), ftl[0].get());
    EXPECT_EQ(am.getShrinker("a4").get(),  ftl[1].get());
    EXPECT_EQ(am.getFlushable("a5").get(), ftl[2].get());
    EXPECT_EQ(am.getShrinker("a5").get(),  ftl[3].get());
}

TEST(AttributeFlushTest, require_that_cleanup_is_performed_after_flush)
{
    Fixture f;
    AttributeVector::SP av = f.addAttribute("a6");
    av->addDocs(1);
    av->commit(CommitParam(30));

    // fake up some snapshots
    std::string base = "flush/a6";
    std::string snap10 = "flush/a6/snapshot-10";
    std::string snap20 = "flush/a6/snapshot-20";
    fs::create_directory(fs::path(base));
    fs::create_directory(fs::path(snap10));
    fs::create_directory(fs::path(snap20));
    IndexMetaInfo info("flush/a6");
    info.addSnapshot(IndexMetaInfo::Snapshot(true, 10, "snapshot-10"));
    info.addSnapshot(IndexMetaInfo::Snapshot(false, 20, "snapshot-20"));
    EXPECT_TRUE(info.save());
    auto diskLayout = AttributeDiskLayout::create("flush");

    FlushableAttribute fa(av, diskLayout->getAttributeDir("a6"), TuneFileAttributes(),
                          f._fileHeaderContext, f._attributeFieldWriter,
                          f._hwInfo);
    fa.initFlush(30, std::make_shared<search::FlushToken>())->run();

    EXPECT_TRUE(info.load());
    EXPECT_EQ(1u, info.snapshots().size()); // snapshots 10 & 20 removed
    EXPECT_TRUE(info.snapshots()[0].valid);
    EXPECT_EQ(30u, info.snapshots()[0].syncToken);
    EXPECT_FALSE(fs::exists(fs::path(snap10)));
    EXPECT_FALSE(fs::exists(fs::path(snap20)));
}

TEST(AttributeFlushTest, require_that_flush_stats_are_updated)
{
    Fixture f;
    AttributeManager &am = f._m;
    AttributeVector::SP av = f.addAttribute("a7");
    av->addDocs(1);
    av->commit(CommitParam(100));
    IFlushTarget::SP ft = am.getFlushable("a7");
    ft->initFlush(101, std::make_shared<search::FlushToken>())->run();
    FlushStats stats = ft->getLastFlushStats();
    EXPECT_EQ("flush/a7/snapshot-101", stats.getPath());
    EXPECT_EQ(8u, stats.getPathElementsToLog());
}

TEST(AttributeFlushTest, require_that_only_one_flusher_can_run_at_the_same_time)
{
    Fixture f;
    AttributeManager &am = f._m;
    AttributeVector::SP av = f.addAttribute("a8");
    av->addDocs(10000);
    av->commit(CommitParam(9));
    IFlushTarget::SP ft = am.getFlushable("a8");
    (static_cast<FlushableAttribute *>(ft.get()))->setCleanUpAfterFlush(false);
    vespalib::ThreadStackExecutor exec(16);

    for (size_t i = 10; i < 100; ++i) {
        av->commit(CommitParam(i));
        vespalib::Executor::Task::UP task = ft->initFlush(i, std::make_shared<search::FlushToken>());
        if (task) {
            exec.execute(std::move(task));
        }
    }
    exec.sync();
    exec.shutdown();

    IndexMetaInfo info("flush/a8");
    ASSERT_TRUE(info.load());
    LOG(info, "Found %zu snapshots", info.snapshots().size());
    for (size_t i = 0; i < info.snapshots().size(); ++i) {
        EXPECT_EQ(true, info.snapshots()[i].valid);
    }
    IndexMetaInfo::Snapshot best = info.getBestSnapshot();
    EXPECT_EQ(true, best.valid);
}

TEST(AttributeFlushTest, require_that_last_flush_time_is_reported)
{
    using seconds = std::chrono::seconds;
    BaseFixture f;
    FastOS_StatInfo stat;
    { // no meta info file yet
        AttributeManagerFixture amf(f);
        AttributeManager &am = amf._m;
        AttributeVector::SP av = amf.addAttribute("a9");
        EXPECT_EQ(vespalib::system_time(), am.getFlushable("a9")->getLastFlushTime());
        EXPECT_EQ(zero_flush_duration, am.getFlushable("a9")->last_flush_duration());
    }
    { // no snapshot flushed yet
        AttributeManagerFixture amf(f);
        AttributeManager &am = amf._m;
        AttributeVector::SP av = amf.addAttribute("a9");
        IFlushTarget::SP ft = am.getFlushable("a9");
        EXPECT_EQ(vespalib::system_time(), ft->getLastFlushTime());
        EXPECT_EQ(zero_flush_duration, ft->last_flush_duration());
        ft->initFlush(200, std::make_shared<search::FlushToken>())->run();
        EXPECT_TRUE(FastOS_File::Stat("flush/a9/snapshot-200", &stat));
        EXPECT_EQ(stat._modifiedTime, ft->getLastFlushTime());
        EXPECT_NE(zero_flush_duration, ft->last_flush_duration());
    }
    { // snapshot flushed
        AttributeManagerFixture amf(f);
        AttributeManager &am = amf._m;
        amf.addAttribute("a9");
        IFlushTarget::SP ft = am.getFlushable("a9");
        EXPECT_EQ(stat._modifiedTime, ft->getLastFlushTime());
        EXPECT_NE(zero_flush_duration, ft->last_flush_duration());
        { // updated flush time after nothing to flush
            std::this_thread::sleep_for(1100ms);
            std::chrono::seconds now = duration_cast<seconds>(vespalib::system_clock::now().time_since_epoch());
            Executor::Task::UP task = ft->initFlush(200, std::make_shared<search::FlushToken>());
            EXPECT_FALSE(task);
            EXPECT_LT(stat._modifiedTime, ft->getLastFlushTime());
            EXPECT_NEAR(now.count(), duration_cast<seconds>(ft->getLastFlushTime().time_since_epoch()).count(), 3);
            EXPECT_NE(zero_flush_duration, ft->last_flush_duration());
        }
    }
}

TEST(AttributeFlushTest, require_that_shrink_works)
{
    Fixture f;
    AttributeManager &am = f._m;
    AttributeVector::SP av = f.addAttribute("a10");
    AttributeWriter aw(f._msp);
    
    av->addDocs(1000 - av->getNumDocs());
    av->commit(CommitParam(50));
    IFlushTarget::SP ft = am.getShrinker("a10");
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());
    AttributeGuard::UP g = am.getAttribute("a10");
    EXPECT_FALSE(av->wantShrinkLidSpace());
    EXPECT_FALSE(av->canShrinkLidSpace());
    EXPECT_EQ(1000u, av->getNumDocs());
    EXPECT_EQ(1000u, av->getCommittedDocIdLimit());
    av->compactLidSpace(100);
    EXPECT_TRUE(av->wantShrinkLidSpace());
    EXPECT_FALSE(av->canShrinkLidSpace());
    EXPECT_EQ(1000u, av->getNumDocs());
    EXPECT_EQ(100u, av->getCommittedDocIdLimit());
    aw.heartBeat(51, IDestructorCallback::SP());
    EXPECT_TRUE(av->wantShrinkLidSpace());
    EXPECT_FALSE(av->canShrinkLidSpace());
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());
    g.reset();
    aw.heartBeat(52, IDestructorCallback::SP());
    EXPECT_TRUE(av->wantShrinkLidSpace());
    EXPECT_TRUE(av->canShrinkLidSpace());
    EXPECT_TRUE(ft->getApproxMemoryGain().getBefore() >
                ft->getApproxMemoryGain().getAfter());
    EXPECT_EQ(1000u, av->getNumDocs());
    EXPECT_EQ(100u, av->getCommittedDocIdLimit());
    EXPECT_EQ(createSerialNum - 1, ft->getFlushedSerialNum());
    vespalib::ThreadStackExecutor exec(1);
    vespalib::Executor::Task::UP task = ft->initFlush(53, std::make_shared<search::FlushToken>());
    exec.execute(std::move(task));
    exec.sync();
    exec.shutdown();
    EXPECT_FALSE(av->wantShrinkLidSpace());
    EXPECT_FALSE(av->canShrinkLidSpace());
    EXPECT_EQ(ft->getApproxMemoryGain().getBefore(),
              ft->getApproxMemoryGain().getAfter());
    EXPECT_EQ(100u, av->getNumDocs());
    EXPECT_EQ(100u, av->getCommittedDocIdLimit());
}

void
require_that_flushed_attribute_can_be_loaded(const HwInfo &hwInfo, const std::string& label)
{
    SCOPED_TRACE(label);
    constexpr uint32_t numDocs = 100;
    BaseFixture f(hwInfo);
    std::string attrName(hwInfo.disk().slow() ? "a11slow" : "a11fast");
    {
        AttributeManagerFixture amf(f);
        AttributeManager &am = amf._m;
        AttributeVector::SP av = amf.addPostingAttribute(attrName);
        IntegerAttribute & ia = static_cast<IntegerAttribute &>(*av);
        EXPECT_EQ(1u, av->getNumDocs());
        av->addDocs(numDocs);
        EXPECT_EQ(numDocs + 1, av->getNumDocs());
        for (uint32_t i = 0; i < numDocs; ++i) {
            ia.update(i + 1, i + 43);
        }
        av->commit();
        IFlushTarget::SP ft = am.getFlushable(attrName);
        ft->initFlush(200, std::make_shared<search::FlushToken>())->run();
    }
    {
        AttributeManagerFixture amf(f);
        AttributeVector::SP av = amf.addPostingAttribute(attrName);
        EXPECT_EQ(numDocs + 1, av->getNumDocs());
        for (uint32_t i = 0; i < numDocs; ++i) {
            EXPECT_EQ(i + 43, av->getInt(i + 1));
        }
    }
}

TEST(AttributeFlushTest, require_that_flushed_attribute_can_be_loaded)
{
    require_that_flushed_attribute_can_be_loaded(HwInfo(HwInfo::Disk(0, false, false), HwInfo::Memory(0), HwInfo::Cpu(0)), "fast-disk");
    require_that_flushed_attribute_can_be_loaded(HwInfo(HwInfo::Disk(0, true, false), HwInfo::Memory(0), HwInfo::Cpu(0)), "slow-disk");
}

TEST(AttributeFlushTest, require_that_flush_failure_prevents_sync_token_update)
{
    BaseFixture f;
    AttributeManagerFixture amf(f);
    auto &am = amf._m;
    auto av = amf.addIntArrayPostingAttribute("a12");
    EXPECT_EQ(1u, av->getNumDocs());
    auto flush_target = am.getFlushable("a12");
    EXPECT_EQ(0u, flush_target->getFlushedSerialNum());
    auto flush_task = flush_target->initFlush(200, std::make_shared<search::FlushToken>());
    // Trigger flush failure
    av->getEnumStoreBase()->inc_compaction_count();
    flush_task->run();
    EXPECT_EQ(0u, flush_target->getFlushedSerialNum());
}

}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    fs::remove_all(fs::path(proton::test_dir));
    return RUN_ALL_TESTS();
}
