// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("reference_attribute_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/document/base/documentid.h>

using search::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using search::attribute::ReferenceAttribute;
using search::attribute::Config;
using search::attribute::BasicType;
using search::AttributeVector;
using search::AttributeGuard;
using document::GlobalId;
using document::DocumentId;

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");
vespalib::string doc2("id:test:music::2");

}


struct Fixture
{
    std::shared_ptr<ReferenceAttribute> _attr;

    Fixture()
        : _attr()
    {
        resetAttr();
    }

    AttributeVector &attr() {
        return *_attr;
    }

    void resetAttr() {
        _attr.reset();
        _attr = std::make_shared<ReferenceAttribute>("test",
                                                     Config(BasicType::REFERENCE));
    }

    void ensureDocIdLimit(uint32_t docIdLimit) {
        while (attr().getNumDocs() < docIdLimit) {
            uint32_t newDocId = 0u;
            _attr->addDoc(newDocId);
            _attr->commit();
        }
    }

    search::attribute::Status getStatus() {
        attr().commit(true);
        return attr().getStatus();
    }

    const GlobalId *get(uint32_t doc) {
        const ReferenceAttribute::Reference *ref = _attr->getReference(doc);
        if (ref != nullptr) {
            return &ref->gid();
        } else {
            return nullptr;
        }
    }

    void set(uint32_t doc, const GlobalId &gid) {
        _attr->update(doc, gid);
    }

    void clear(uint32_t doc) {
        _attr->clearDoc(doc);
    }

    void commit() { attr().commit(); }

    void assertNoRef(uint32_t doc)
    {
        EXPECT_TRUE(get(doc) == nullptr);
    }

    void assertRef(vespalib::stringref str, uint32_t doc) {
        const GlobalId *gid = get(doc);
        EXPECT_TRUE(gid != nullptr);
        EXPECT_EQUAL(toGid(str), *gid);
    }

    void save() {
        attr().save();
    }

    void load() {
        resetAttr();
        attr().load();
    }

    void triggerCompaction(uint64_t iterLimit) {
        search::attribute::Status oldStatus = getStatus();
        search::attribute::Status newStatus = oldStatus;
        uint64_t iter = 0;
        for (; iter < iterLimit; ++iter) {
            clear(2);
            set(2, toGid(doc2));
            newStatus = getStatus();
            if (newStatus.getUsed() < oldStatus.getUsed()) {
                break;
            }
            oldStatus = newStatus;
        }
        EXPECT_GREATER(iterLimit, iter);
        LOG(info,
            "iter = %" PRIu64 ", memory usage %" PRIu64 ", -> %" PRIu64,
            iter, oldStatus.getUsed(), newStatus.getUsed());
    }
};

TEST_F("require that we can instantiate reference attribute", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.commit();

    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
}

TEST_F("require that we can set new reference for a document", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(3, toGid(doc2));
    f.commit();
    TEST_DO(f.assertNoRef(4));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
    TEST_DO(f.assertRef(doc2, 3));
    f.set(2, toGid(doc1));
    f.commit();
    TEST_DO(f.assertNoRef(4));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc1, 2));
    TEST_DO(f.assertRef(doc2, 3));
}

TEST_F("require that we can clear reference for a document", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(2, toGid(doc2));
    f.commit();
    TEST_DO(f.assertRef(doc2, 2));
    f.clear(2);
    f.commit();
    TEST_DO(f.assertNoRef(2));
    f.clear(2);
    f.commit();
    TEST_DO(f.assertNoRef(2));
}

TEST_F("require that read guard protects reference", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(2, toGid(doc2));
    f.commit();
    const GlobalId *gid = f.get(2);
    EXPECT_TRUE(gid != nullptr);
    EXPECT_EQUAL(toGid(doc2), *gid);
    {
        AttributeGuard guard(f._attr);
        f.clear(2);
        f.commit();
        EXPECT_EQUAL(toGid(doc2), *gid);
    }
    f.commit();
    EXPECT_NOT_EQUAL(toGid(doc2), *gid);
}

TEST_F("require that we can compact attribute", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.commit();
    TEST_DO(f.triggerCompaction(100000));
    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
}

TEST_F("require that we can save and load attribute", Fixture)
{
    f.ensureDocIdLimit(5);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(4, toGid(doc1));
    f.commit();
    f.save();
    f.load();
    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
    TEST_DO(f.assertRef(doc1, 4));
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
