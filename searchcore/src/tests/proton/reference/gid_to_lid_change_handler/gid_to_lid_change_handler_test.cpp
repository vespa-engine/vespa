// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/searchlib/common/lambdatask.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_handler.h>
#include <map>
#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_handler_test");

using document::GlobalId;
using document::DocumentId;
using search::makeLambdaTask;

namespace proton {

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");

}

class MyTarget {
    using lock_guard = std::lock_guard<std::mutex>;
    std::mutex _lock;
    std::vector<std::pair<GlobalId, uint32_t>> _changes;
    uint32_t  _createdListeners;
    uint32_t  _registeredListeners;
    uint32_t  _destroyedListeners;

public:
    MyTarget()
        : _lock(),
          _changes(),
          _createdListeners(0u),
          _registeredListeners(0u),
          _destroyedListeners(0u)
    {
    }

    ~MyTarget()
    {
        EXPECT_EQUAL(_createdListeners, _destroyedListeners);
    }

    void notifyGidToLidChange(GlobalId gid, uint32_t lid) {
        lock_guard guard(_lock);
        _changes.emplace_back(gid, lid);
    }
    void markCreatedListener()    { lock_guard guard(_lock); ++_createdListeners; }
    void markRegisteredListener() { lock_guard guard(_lock); ++_registeredListeners; }
    void markDestroyedListener()  { lock_guard guard(_lock); ++_destroyedListeners; }

    uint32_t getCreatedListeners() const { return _createdListeners; }
    uint32_t getRegisteredListeners() const { return _registeredListeners; }
    uint32_t getDestroyedListeners() const { return _destroyedListeners; }
    const std::vector<std::pair<GlobalId, uint32_t>> &getChanges() const { return _changes; }

    void assertCounts(uint32_t expCreatedListeners,
                      uint32_t expRegisteredListeners,
                      uint32_t expDestroyedListeners,
                      uint32_t expChanges)
    {
        EXPECT_EQUAL(expCreatedListeners, getCreatedListeners());
        EXPECT_EQUAL(expRegisteredListeners, getRegisteredListeners());
        EXPECT_EQUAL(expDestroyedListeners, getDestroyedListeners());
        EXPECT_EQUAL(expChanges, _changes.size());
    }
};

class MyListener : public IGidToLidChangeListener
{
    MyTarget         &_target;
    vespalib::string  _name;
    vespalib::string  _docTypeName;
public:
    MyListener(MyTarget &target,
               const vespalib::string &name,
               const vespalib::string &docTypeName)
        : IGidToLidChangeListener(),
          _target(target),
          _name(name),
          _docTypeName(docTypeName)
    {
        _target.markCreatedListener();
    }
    virtual ~MyListener() { _target.markDestroyedListener(); }
    virtual void notifyGidToLidChange(GlobalId gid, uint32_t lid) override { _target.notifyGidToLidChange(gid, lid); }
    virtual void notifyRegistered() override { _target.markRegisteredListener(); }
    virtual const vespalib::string &getName() const override { return _name; }
    virtual const vespalib::string &getDocTypeName() const override { return _docTypeName; }
};

struct Fixture
{
    vespalib::ThreadStackExecutor _masterExecutor;
    ExecutorThreadService _master;
    std::vector<std::shared_ptr<MyTarget>> _targets;
    std::shared_ptr<GidToLidChangeHandler> _handler;

    Fixture()
        : _masterExecutor(1, 128 * 1024),
          _master(_masterExecutor),
          _targets(),
          _handler(std::make_shared<GidToLidChangeHandler>(&_master))
    {
    }

    ~Fixture()
    {
        close();
    }

    void close()
    {
        _master.execute(makeLambdaTask([this]() { _handler->close(); }));
        _master.sync();
    }

    MyTarget &addTarget() {
        _targets.push_back(std::make_shared<MyTarget>());
        return *_targets.back();
    }

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) {
        _handler->addListener(std::move(listener));
        _master.sync();
    }

    void notifyGidToLidChange(GlobalId gid, uint32_t lid) {
        _master.execute(makeLambdaTask([this, gid, lid]() { _handler->notifyGidToLidChange(gid, lid); }));
        _master.sync();
    }

    void removeListeners(const vespalib::string &docTypeName,
                         const std::set<vespalib::string> &keepNames) {
        _handler->removeListeners(docTypeName, keepNames);
        _master.sync();
    }

};

TEST_F("Test that we can register a listener", Fixture)
{
    auto &target = f.addTarget();
    auto listener = std::make_unique<MyListener>(target, "test", "testdoc");
    TEST_DO(target.assertCounts(1, 0, 0, 0));
    f.addListener(std::move(listener));
    TEST_DO(target.assertCounts(1, 1, 0, 0));
    f.notifyGidToLidChange(toGid(doc1), 10);
    TEST_DO(target.assertCounts(1, 1, 0, 1));
    f.removeListeners("testdoc", {});
    TEST_DO(target.assertCounts(1, 1, 1, 1));
}

TEST_F("Test that we can register multiple listeners", Fixture)
{
    auto &target1 = f.addTarget();
    auto &target2 = f.addTarget();
    auto &target3 = f.addTarget();
    auto listener1 = std::make_unique<MyListener>(target1, "test1", "testdoc");
    auto listener2 = std::make_unique<MyListener>(target2, "test2", "testdoc");
    auto listener3 = std::make_unique<MyListener>(target3, "test3", "testdoc2");
    TEST_DO(target1.assertCounts(1, 0, 0, 0));
    TEST_DO(target2.assertCounts(1, 0, 0, 0));
    TEST_DO(target3.assertCounts(1, 0, 0, 0));
    f.addListener(std::move(listener1));
    f.addListener(std::move(listener2));
    f.addListener(std::move(listener3));
    TEST_DO(target1.assertCounts(1, 1, 0, 0));
    TEST_DO(target2.assertCounts(1, 1, 0, 0));
    TEST_DO(target3.assertCounts(1, 1, 0, 0));
    f.notifyGidToLidChange(toGid(doc1), 10);
    TEST_DO(target1.assertCounts(1, 1, 0, 1));
    TEST_DO(target2.assertCounts(1, 1, 0, 1));
    TEST_DO(target3.assertCounts(1, 1, 0, 1));
    f.removeListeners("testdoc", {"test1"});
    TEST_DO(target1.assertCounts(1, 1, 0, 1));
    TEST_DO(target2.assertCounts(1, 1, 1, 1));
    TEST_DO(target3.assertCounts(1, 1, 0, 1));
    f.removeListeners("testdoc", {});
    TEST_DO(target1.assertCounts(1, 1, 1, 1));
    TEST_DO(target2.assertCounts(1, 1, 1, 1));
    TEST_DO(target3.assertCounts(1, 1, 0, 1));
    f.removeListeners("testdoc2", {"test3"});
    TEST_DO(target1.assertCounts(1, 1, 1, 1));
    TEST_DO(target2.assertCounts(1, 1, 1, 1));
    TEST_DO(target3.assertCounts(1, 1, 0, 1));
    f.removeListeners("testdoc2", {"foo"});
    TEST_DO(target1.assertCounts(1, 1, 1, 1));
    TEST_DO(target2.assertCounts(1, 1, 1, 1));
    TEST_DO(target3.assertCounts(1, 1, 1, 1));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
