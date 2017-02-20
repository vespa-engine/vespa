// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/base/documentid.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_listener.h>
#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_listener_test");

using document::GlobalId;
using document::BucketId;
using document::DocumentId;
using vespalib::GenerationHandler;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::ReferenceAttribute;

namespace proton {

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");
vespalib::string doc2("id:test:music::2");
vespalib::string doc3("id:test:music::3");

}

struct Fixture
{
    std::shared_ptr<ReferenceAttribute> _attr;
    search::SequencedTaskExecutor _writer;
    MonitoredRefCount _refCount;
    std::unique_ptr<GidToLidChangeListener>  _listener;

    Fixture()
        : _attr(std::make_shared<ReferenceAttribute>("test", Config(BasicType::REFERENCE))),
          _writer(1),
          _refCount(),
          _listener()
    {
    }

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

    const ReferenceAttribute::Reference *getRef(uint32_t doc) {
        return _attr->getReference(doc);
    }

    void assertRefLid(uint32_t expLid, uint32_t doc) {
        auto ref = getRef(doc);
        EXPECT_TRUE(ref != nullptr);
        EXPECT_EQUAL(expLid, ref->lid());
    }

    void allocListener() {
        _listener = std::make_unique<GidToLidChangeListener>(_writer, _attr, _refCount);
    }

    void notifyGidToLidChange(const GlobalId &gid, uint32_t referencedDoc) {
        _listener->notifyGidToLidChange(gid, referencedDoc);
    }
};

TEST_F("Test that we can use gid to lid change listener", Fixture)
{
    f.ensureDocIdLimit(4);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(3, toGid(doc1));
    f.commit();
    TEST_DO(f.assertRefLid(0, 1));
    TEST_DO(f.assertRefLid(0, 2));
    TEST_DO(f.assertRefLid(0, 3));
    f.allocListener();
    f.notifyGidToLidChange(toGid(doc1), 10);
    f.notifyGidToLidChange(toGid(doc2), 20);
    f.notifyGidToLidChange(toGid(doc3), 30);
    TEST_DO(f.assertRefLid(10, 1));
    TEST_DO(f.assertRefLid(20, 2));
    TEST_DO(f.assertRefLid(10, 3));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
