// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_handler.h>
#include <vespa/searchlib/common/gatecallback.h>
#include <map>
#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_handler_test");

using document::GlobalId;
using document::DocumentId;
using vespalib::makeLambdaTask;
using search::SerialNum;

namespace proton {

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");

}

class ListenerStats {
    using lock_guard = std::lock_guard<std::mutex>;
    std::mutex _lock;
    uint32_t  _putChanges;
    uint32_t  _removeChanges;
    uint32_t  _createdListeners;
    uint32_t  _registeredListeners;
    uint32_t  _destroyedListeners;

public:
    ListenerStats() noexcept
        : _lock(),
          _putChanges(0u),
          _removeChanges(0u),
          _createdListeners(0u),
          _registeredListeners(0u),
          _destroyedListeners(0u)
    {
    }

    ~ListenerStats()
    {
        EXPECT_EQUAL(_createdListeners, _destroyedListeners);
    }

    void notifyPutDone() {
        lock_guard guard(_lock);
        ++_putChanges;
    }
    void notifyRemove() {
        lock_guard guard(_lock);
        ++_removeChanges;
    }
    void markCreatedListener()    { lock_guard guard(_lock); ++_createdListeners; }
    void markRegisteredListener() { lock_guard guard(_lock); ++_registeredListeners; }
    void markDestroyedListener()  { lock_guard guard(_lock); ++_destroyedListeners; }

    uint32_t getCreatedListeners() const { return _createdListeners; }
    uint32_t getRegisteredListeners() const { return _registeredListeners; }
    uint32_t getDestroyedListeners() const { return _destroyedListeners; }

    void assertListeners(uint32_t expCreatedListeners,
                         uint32_t expRegisteredListeners,
                         uint32_t expDestroyedListeners)
    {
        EXPECT_EQUAL(expCreatedListeners, getCreatedListeners());
        EXPECT_EQUAL(expRegisteredListeners, getRegisteredListeners());
        EXPECT_EQUAL(expDestroyedListeners, getDestroyedListeners());
    }
    void assertChanges(uint32_t expPutChanges, uint32_t expRemoveChanges)
    {
        EXPECT_EQUAL(expPutChanges, _putChanges);
        EXPECT_EQUAL(expRemoveChanges, _removeChanges);
    }
};

class MyListener : public IGidToLidChangeListener
{
    ListenerStats         &_stats;
    vespalib::string  _name;
    vespalib::string  _docTypeName;
public:
    MyListener(ListenerStats &stats,
               const vespalib::string &name,
               const vespalib::string &docTypeName)
        : IGidToLidChangeListener(),
          _stats(stats),
          _name(name),
          _docTypeName(docTypeName)
    {
        _stats.markCreatedListener();
    }
    ~MyListener() override { _stats.markDestroyedListener(); }
    void notifyPutDone(IDestructorCallbackSP, GlobalId, uint32_t) override { _stats.notifyPutDone(); }
    void notifyRemove(IDestructorCallbackSP, GlobalId) override { _stats.notifyRemove(); }
    void notifyRegistered() override { _stats.markRegisteredListener(); }
    const vespalib::string &getName() const override { return _name; }
    const vespalib::string &getDocTypeName() const override { return _docTypeName; }
};

struct Fixture
{
    std::vector<std::shared_ptr<ListenerStats>> _statss;
    std::shared_ptr<GidToLidChangeHandler> _handler;

    Fixture()
        : _statss(),
          _handler(std::make_shared<GidToLidChangeHandler>())
    {
    }

    ~Fixture()
    {
        close();
    }

    void close()
    {
        _handler->close();
    }

    ListenerStats &addStats() {
        _statss.push_back(std::make_shared<ListenerStats>());
        return *_statss.back();
    }

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) {
        _handler->addListener(std::move(listener));
    }

    void notifyPutDone(GlobalId gid, uint32_t lid, SerialNum serialNum) {
        vespalib::Gate gate;
        _handler->notifyPutDone(std::make_shared<search::GateCallback>(gate), gid, lid, serialNum);
        gate.await();
    }

    void notifyRemove(GlobalId gid, SerialNum serialNum) {
        vespalib::Gate gate;
        _handler->notifyRemove(std::make_shared<search::GateCallback>(gate), gid, serialNum);
        gate.await();
    }

    void notifyRemoveDone(GlobalId gid, SerialNum serialNum) {
        _handler->notifyRemoveDone(gid, serialNum);
    }

    void removeListeners(const vespalib::string &docTypeName,
                         const std::set<vespalib::string> &keepNames) {
        _handler->removeListeners(docTypeName, keepNames);
    }

};

TEST_F("Test that we can register a listener", Fixture)
{
    auto &stats = f.addStats();
    auto listener = std::make_unique<MyListener>(stats, "test", "testdoc");
    TEST_DO(stats.assertListeners(1, 0, 0));
    f.addListener(std::move(listener));
    TEST_DO(stats.assertListeners(1, 1, 0));
    f.notifyPutDone(toGid(doc1), 10, 10);
    TEST_DO(stats.assertChanges(1, 0));
    f.removeListeners("testdoc", {});
    TEST_DO(stats.assertListeners(1, 1, 1));
}

TEST_F("Test that we can register multiple listeners", Fixture)
{
    auto &stats1 = f.addStats();
    auto &stats2 = f.addStats();
    auto &stats3 = f.addStats();
    auto listener1 = std::make_unique<MyListener>(stats1, "test1", "testdoc");
    auto listener2 = std::make_unique<MyListener>(stats2, "test2", "testdoc");
    auto listener3 = std::make_unique<MyListener>(stats3, "test3", "testdoc2");
    TEST_DO(stats1.assertListeners(1, 0, 0));
    TEST_DO(stats2.assertListeners(1, 0, 0));
    TEST_DO(stats3.assertListeners(1, 0, 0));
    f.addListener(std::move(listener1));
    f.addListener(std::move(listener2));
    f.addListener(std::move(listener3));
    TEST_DO(stats1.assertListeners(1, 1, 0));
    TEST_DO(stats2.assertListeners(1, 1, 0));
    TEST_DO(stats3.assertListeners(1, 1, 0));
    f.notifyPutDone(toGid(doc1), 10, 10);
    TEST_DO(stats1.assertChanges(1, 0));
    TEST_DO(stats2.assertChanges(1, 0));
    TEST_DO(stats3.assertChanges(1, 0));
    f.removeListeners("testdoc", {"test1"});
    TEST_DO(stats1.assertListeners(1, 1, 0));
    TEST_DO(stats2.assertListeners(1, 1, 1));
    TEST_DO(stats3.assertListeners(1, 1, 0));
    f.removeListeners("testdoc", {});
    TEST_DO(stats1.assertListeners(1, 1, 1));
    TEST_DO(stats2.assertListeners(1, 1, 1));
    TEST_DO(stats3.assertListeners(1, 1, 0));
    f.removeListeners("testdoc2", {"test3"});
    TEST_DO(stats1.assertListeners(1, 1, 1));
    TEST_DO(stats2.assertListeners(1, 1, 1));
    TEST_DO(stats3.assertListeners(1, 1, 0));
    f.removeListeners("testdoc2", {"foo"});
    TEST_DO(stats1.assertListeners(1, 1, 1));
    TEST_DO(stats2.assertListeners(1, 1, 1));
    TEST_DO(stats3.assertListeners(1, 1, 1));
}

TEST_F("Test that we keep old listener when registering duplicate", Fixture)
{
    auto &stats = f.addStats();
    auto listener = std::make_unique<MyListener>(stats, "test1", "testdoc");
    TEST_DO(stats.assertListeners(1, 0, 0));
    f.addListener(std::move(listener));
    TEST_DO(stats.assertListeners(1, 1, 0));
    listener = std::make_unique<MyListener>(stats, "test1", "testdoc");
    TEST_DO(stats.assertListeners(2, 1, 0));
    f.addListener(std::move(listener));
    TEST_DO(stats.assertListeners(2, 1, 1));
}

class StatsFixture : public Fixture
{
    ListenerStats &_stats;

public:
    StatsFixture()
        : Fixture(),
          _stats(addStats())
    {
        addListener(std::make_unique<MyListener>(_stats, "test", "testdoc"));
    }

    ~StatsFixture()
    {
        removeListeners("testdoc", {});
    }

    void assertChanges(uint32_t expPutChanges, uint32_t expRemoveChanges)
    {
        TEST_DO(_stats.assertChanges(expPutChanges, expRemoveChanges));
    }
};

TEST_F("Test that put is ignored if we have a pending remove", StatsFixture)
{
    f.notifyRemove(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 10, 10);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemoveDone(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 11, 30);
    TEST_DO(f.assertChanges(1, 1));
}

TEST_F("Test that pending removes are merged", StatsFixture)
{
    f.notifyRemove(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemove(toGid(doc1), 40);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 10, 10);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemoveDone(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 11, 30);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemoveDone(toGid(doc1), 40);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 12, 50);
    TEST_DO(f.assertChanges(1, 1));
}

TEST_F("Test that out of order notifyRemoveDone is handled", StatsFixture)
{
     f.notifyRemove(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemove(toGid(doc1), 40);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemoveDone(toGid(doc1), 40);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyRemoveDone(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 12, 50);
    TEST_DO(f.assertChanges(1, 1));
}

TEST_F("Test that out of order notifyPutDone is partially handled", StatsFixture)
{
     f.notifyRemove(toGid(doc1), 20);
    TEST_DO(f.assertChanges(0, 1));
    f.notifyPutDone(toGid(doc1), 12, 50);
    TEST_DO(f.assertChanges(1, 1));
    f.notifyPutDone(toGid(doc1), 11, 40);
    TEST_DO(f.assertChanges(1, 1));
    f.notifyPutDone(toGid(doc1), 13, 55);
    TEST_DO(f.assertChanges(2, 1));
    f.notifyRemoveDone(toGid(doc1), 20);
    TEST_DO(f.assertChanges(2, 1));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
