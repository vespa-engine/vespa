// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <map>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_handler_test");

using document::GlobalId;
using document::DocumentId;
using vespalib::makeLambdaTask;
using search::SerialNum;

namespace proton {

namespace {

GlobalId toGid(std::string_view docId) {
    return DocumentId(docId).getGlobalId();
}

std::string doc1("id:test:music::1");

}

TEST(GidToLidChangeHandlerTest, control_sizeof_PendingGidToLidChange) {
    EXPECT_EQ(48u, sizeof(PendingGidToLidChange));
}

class ListenerStats {
    using lock_guard = std::lock_guard<std::mutex>;
    std::mutex _lock;
    uint32_t  _putChanges;
    uint32_t  _removeChanges;
    uint32_t  _createdListeners;
    uint32_t  _registeredListeners;
    uint32_t  _destroyedListeners;
    std::vector<GlobalId> _initial_removes;

public:
    ListenerStats() noexcept
        : _lock(),
          _putChanges(0u),
          _removeChanges(0u),
          _createdListeners(0u),
          _registeredListeners(0u),
          _destroyedListeners(0u),
          _initial_removes()
    {
    }

    ~ListenerStats()
    {
        EXPECT_EQ(_createdListeners, _destroyedListeners);
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
    void markRegisteredListener(const std::vector<GlobalId>& removes) { lock_guard guard(_lock); ++_registeredListeners; _initial_removes = removes; }
    void markDestroyedListener()  { lock_guard guard(_lock); ++_destroyedListeners; }

    uint32_t getCreatedListeners() const { return _createdListeners; }
    uint32_t getRegisteredListeners() const { return _registeredListeners; }
    uint32_t getDestroyedListeners() const { return _destroyedListeners; }

    void assertListeners(uint32_t expCreatedListeners, uint32_t expRegisteredListeners, uint32_t expDestroyedListeners,
                         const std::string &label)
    {
        SCOPED_TRACE(label);
        EXPECT_EQ(expCreatedListeners, getCreatedListeners());
        EXPECT_EQ(expRegisteredListeners, getRegisteredListeners());
        EXPECT_EQ(expDestroyedListeners, getDestroyedListeners());
    }
    void assertChanges(uint32_t expPutChanges, uint32_t expRemoveChanges, const std::string& label)
    {
        SCOPED_TRACE(label);
        EXPECT_EQ(expPutChanges, _putChanges);
        EXPECT_EQ(expRemoveChanges, _removeChanges);
    }
    const std::vector<GlobalId>& get_initial_removes() const noexcept { return _initial_removes; }
};

class MyListener : public IGidToLidChangeListener
{
    ListenerStats         &_stats;
    std::string  _name;
    std::string  _docTypeName;
public:
    MyListener(ListenerStats &stats,
               const std::string &name,
               const std::string &docTypeName)
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
    void notifyRegistered(const std::vector<GlobalId>& removes) override { _stats.markRegisteredListener(removes); }
    const std::string &getName() const override { return _name; }
    const std::string &getDocTypeName() const override { return _docTypeName; }
};

struct Fixture
{
    std::vector<std::shared_ptr<ListenerStats>> _statss;
    std::shared_ptr<GidToLidChangeHandler>  _real_handler;
    std::shared_ptr<IGidToLidChangeHandler> _handler;

    Fixture()
        : _statss(),
          _real_handler(std::make_shared<GidToLidChangeHandler>()),
          _handler(_real_handler)
    {
    }

    ~Fixture()
    {
        close();
    }

    void close()
    {
        _real_handler->close();
    }

    ListenerStats &addStats() {
        _statss.push_back(std::make_shared<ListenerStats>());
        return *_statss.back();
    }

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) {
        _handler->addListener(std::move(listener));
    }

    void commit() {
        auto pending = _handler->grab_pending_changes();
        if (pending) {
            pending->notify_done();
        }
    }

    void notifyPut(GlobalId gid, uint32_t lid, SerialNum serial_num) {
        _handler->notifyPut(std::shared_ptr<vespalib::IDestructorCallback>(), gid, lid, serial_num);
    }

    void notifyRemove(GlobalId gid, SerialNum serialNum) {
        vespalib::Gate gate;
        _handler->notifyRemove(std::make_shared<vespalib::GateCallback>(gate), gid, serialNum);
        gate.await();
    }

    void removeListeners(const std::string &docTypeName,
                         const std::set<std::string> &keepNames) {
        _handler->removeListeners(docTypeName, keepNames);
    }

};

TEST(GidToLidChangeHandlerTest, Test_that_we_can_register_a_listener)
{
    Fixture f;
    auto &stats = f.addStats();
    auto listener = std::make_unique<MyListener>(stats, "test", "testdoc");
    stats.assertListeners(1, 0, 0, "created");
    f.addListener(std::move(listener));
    stats.assertListeners(1, 1, 0, "registered");
    f.notifyPut(toGid(doc1), 10, 10);
    f.commit();
    stats.assertChanges(1, 0, "put");
    f.removeListeners("testdoc", {});
    stats.assertListeners(1, 1, 1, "destroyed");
}

TEST(GidToLidChangeHandlerTest, Test_that_we_can_register_multiple_listeners)
{
    Fixture f;
    auto &stats1 = f.addStats();
    auto &stats2 = f.addStats();
    auto &stats3 = f.addStats();
    auto listener1 = std::make_unique<MyListener>(stats1, "test1", "testdoc");
    auto listener2 = std::make_unique<MyListener>(stats2, "test2", "testdoc");
    auto listener3 = std::make_unique<MyListener>(stats3, "test3", "testdoc2");
    stats1.assertListeners(1, 0, 0, "created 1");
    stats2.assertListeners(1, 0, 0, "created 2");
    stats3.assertListeners(1, 0, 0, "created 3");
    f.addListener(std::move(listener1));
    f.addListener(std::move(listener2));
    f.addListener(std::move(listener3));
    stats1.assertListeners(1, 1, 0, "registered 1");
    stats2.assertListeners(1, 1, 0, "registered 2");
    stats3.assertListeners(1, 1, 0, "registered 3");
    f.notifyPut(toGid(doc1), 10, 10);
    f.commit();
    stats1.assertChanges(1, 0, "put 1");
    stats2.assertChanges(1, 0, "put 2");
    stats3.assertChanges(1, 0, "put 3");
    f.removeListeners("testdoc", {"test1"});
    stats1.assertListeners(1, 1, 0, "destroyed 1");
    stats2.assertListeners(1, 1, 1, "destroyed 2");
    stats3.assertListeners(1, 1, 0, "destroyed 3");
    f.removeListeners("testdoc", {});
    stats1.assertListeners(1, 1, 1, "destroyed 4");
    stats2.assertListeners(1, 1, 1, "destroyed 5");
    stats3.assertListeners(1, 1, 0, "destroyed 6");
    f.removeListeners("testdoc2", {"test3"});
    stats1.assertListeners(1, 1, 1, "destroyed 7");
    stats2.assertListeners(1, 1, 1, "destroyed 8");
    stats3.assertListeners(1, 1, 0, "destroyed 9");
    f.removeListeners("testdoc2", {"foo"});
    stats1.assertListeners(1, 1, 1, "destroyed 10");
    stats2.assertListeners(1, 1, 1, "destroyed 11");
    stats3.assertListeners(1, 1, 1, "destroyed 12");
}

TEST(GidToLidChangeHandlerTest, Test_that_we_keep_old_listener_when_registering_duplicate)
{
    Fixture f;
    auto &stats = f.addStats();
    auto listener = std::make_unique<MyListener>(stats, "test1", "testdoc");
    stats.assertListeners(1, 0, 0, "created");
    f.addListener(std::move(listener));
    stats.assertListeners(1, 1, 0, "registered");
    listener = std::make_unique<MyListener>(stats, "test1", "testdoc");
    stats.assertListeners(2, 1, 0, "created dup");
    f.addListener(std::move(listener));
    stats.assertListeners(2, 1, 1, "destroyed dup");
}

TEST(GidToLidChangeHandlerTest, Test_that_pending_removes_are_passed_on_to_new_listener)
{
    Fixture f;
    auto& stats = f.addStats();
    auto listener = std::make_unique<MyListener>(stats, "test1", "testdoc");
    f.notifyRemove(toGid(doc1), 20);
    f.addListener(std::move(listener));
    EXPECT_TRUE((std::vector<GlobalId>{ toGid(doc1) }) == stats.get_initial_removes());
    f.commit();
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

    void assertChanges(uint32_t expPutChanges, uint32_t expRemoveChanges, const std::string& label)
    {
        _stats.assertChanges(expPutChanges, expRemoveChanges, label);
    }
};

TEST(GidToLidChangeHandlerTest, Test_that_multiple_puts_are_processed)
{
    StatsFixture f;
    f.notifyPut(toGid(doc1), 10, 10);
    f.assertChanges(0, 0, "put 1");
    f.notifyPut(toGid(doc1), 11, 20);
    f.assertChanges(0, 0, "put 2");
    f.commit();
    f.assertChanges(2, 0, "commit");
}

TEST(GidToLidChangeHandlerTest, Test_that_put_is_ignored_if_we_have_a_pending_remove)
{
    StatsFixture f;
    f.notifyPut(toGid(doc1), 10, 10);
    f.assertChanges(0, 0, "put 1");
    f.notifyRemove(toGid(doc1), 20);
    f.assertChanges(0, 1, "remove");
    f.commit();
    f.assertChanges(0, 1, "commit 1");
    f.notifyPut(toGid(doc1), 11, 30);
    f.commit();
    f.assertChanges(1, 1, "new put and commit");
}

TEST(GidToLidChangeHandlerTest, Test_that_pending_removes_are_merged)
{
    StatsFixture f;
    f.notifyPut(toGid(doc1), 10, 10);
    f.assertChanges(0, 0, "put 1");
    f.notifyRemove(toGid(doc1), 20);
    f.assertChanges(0, 1, "remove");
    f.notifyRemove(toGid(doc1), 40);
    f.assertChanges(0, 1, "remove again");
    f.commit();
    f.assertChanges(0, 1, "commit");
}

}

GTEST_MAIN_RUN_ALL_TESTS()
