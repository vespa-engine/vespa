// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_listener.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <map>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_listener_test");

using document::GlobalId;
using document::BucketId;
using document::DocumentId;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::Reference;
using search::attribute::ReferenceAttribute;
using search::attribute::test::MockGidToLidMapperFactory;
using vespalib::MonitoredRefCount;
using vespalib::GenerationHandler;

namespace proton {

namespace {

GlobalId toGid(std::string_view docId) {
    return DocumentId(docId).getGlobalId();
}

std::string doc1("id:test:music::1");
std::string doc2("id:test:music::2");
std::string doc3("id:test:music::3");
VESPA_THREAD_STACK_TAG(test_executor)

struct MyGidToLidMapperFactory : public MockGidToLidMapperFactory
{
    MyGidToLidMapperFactory()
        : MockGidToLidMapperFactory()
    {
        _map.insert({toGid(doc1), 10});
        _map.insert({toGid(doc2), 17});
    }
};

}

struct GidToLidChangeListenerTest : public ::testing::Test
{
    std::shared_ptr<ReferenceAttribute> _attr;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _writer;
    MonitoredRefCount _refCount;
    std::unique_ptr<GidToLidChangeListener>  _listener;

    GidToLidChangeListenerTest();
    ~GidToLidChangeListenerTest() override;

    void ensureDocIdLimit(uint32_t docIdLimit) {
        while (_attr->getNumDocs() < docIdLimit) {
            uint32_t newDocId = 0u;
            _attr->addDoc(newDocId);
            _attr->commit();
        }
    }

    void set(uint32_t doc, const GlobalId &gid) {
        _attr->update(doc, gid);
    }

    void commit() { _attr->commit(); }

    const Reference *getRef(uint32_t doc) {
        return _attr->getReference(doc);
    }

    void assertTargetLid(uint32_t expLid, uint32_t doc, const std::string& label) {
        SCOPED_TRACE(label);
        auto ref = getRef(doc);
        EXPECT_TRUE(ref != nullptr);
        EXPECT_EQ(expLid, ref->lid());
    }

    void assertNoTargetLid(uint32_t doc, const std::string& label) {
        SCOPED_TRACE(label);
        auto ref = getRef(doc);
        EXPECT_TRUE(ref == nullptr);
    }

    void allocListener() {
        _listener = std::make_unique<GidToLidChangeListener>(*_writer, _attr, _refCount, "test", "testdoc");
    }

    void notifyPutDone(const GlobalId &gid, uint32_t referencedDoc) {
        vespalib::Gate gate;
        _listener->notifyPutDone(std::make_shared<vespalib::GateCallback>(gate), gid, referencedDoc);
        gate.await();
    }

    void notifyListenerRegistered(const std::vector<GlobalId>& removes) {
        _listener->notifyRegistered(removes);
    }
};

GidToLidChangeListenerTest::GidToLidChangeListenerTest()
     : _attr(std::make_shared<ReferenceAttribute>("test")),
       _writer(vespalib::SequencedTaskExecutor::create(test_executor, 1)),
       _refCount(),
       _listener()
{
}

GidToLidChangeListenerTest::~GidToLidChangeListenerTest() = default;

TEST_F(GidToLidChangeListenerTest, Test_that_we_can_use_gid_to_lid_change_listener)
{
    ensureDocIdLimit(4);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(3, toGid(doc1));
    commit();
    assertTargetLid(0, 1, "initial 1");
    assertTargetLid(0, 2, "initial 2");
    assertTargetLid(0, 3, "initial 3");
    allocListener();
    notifyPutDone(toGid(doc1), 10);
    notifyPutDone(toGid(doc2), 20);
    notifyPutDone(toGid(doc3), 30);
    assertTargetLid(10, 1, "later 1");
    assertTargetLid(20, 2, "later 2");
    assertTargetLid(10, 3, "later 3");
}

TEST_F(GidToLidChangeListenerTest, Test_that_target_lids_are_populated_when_listener_is_registered)
{
    ensureDocIdLimit(6);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(3, toGid(doc1));
    set(4, toGid(doc3));
    commit();
    assertTargetLid(0, 1, "initial 1");
    assertTargetLid(0, 2, "initial 2");
    assertTargetLid(0, 3, "initial 3");
    assertTargetLid(0, 4, "initial 4");
    assertNoTargetLid(5, "initial 5");
    std::shared_ptr<search::IGidToLidMapperFactory> factory =
        std::make_shared<MyGidToLidMapperFactory>();
    _attr->setGidToLidMapperFactory(factory);
    allocListener();
    notifyListenerRegistered({});
    assertTargetLid(10, 1, "later 1");
    assertTargetLid(17, 2, "later 2");
    assertTargetLid(10, 3, "later 3");
    assertTargetLid(0, 4, "later 4");
    assertNoTargetLid(5, "later 5");
}

TEST_F(GidToLidChangeListenerTest, Test_that_removed_target_lids_are_pruned_when_listener_is_registered)
{
    ensureDocIdLimit(6);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(3, toGid(doc1));
    set(4, toGid(doc3));
    commit();
    assertTargetLid(0, 1, "initial 1");
    assertTargetLid(0, 2, "initial 2");
    assertTargetLid(0, 3, "initial 3");
    assertTargetLid(0, 4, "initial 4");
    assertNoTargetLid(5, "initial 5");
    std::shared_ptr<search::IGidToLidMapperFactory> factory =
        std::make_shared<MyGidToLidMapperFactory>();
    _attr->setGidToLidMapperFactory(factory);
    allocListener();
    notifyListenerRegistered({ toGid(doc1) });
    assertTargetLid(0, 1, "later 1");
    assertTargetLid(17, 2, "later 2");
    assertTargetLid(0, 3, "later 3");
    assertTargetLid(0, 4, "later 4");
    assertNoTargetLid(5, "later 5");
}

}

GTEST_MAIN_RUN_ALL_TESTS()
