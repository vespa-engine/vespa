// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("reference_attribute_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/document/base/documentid.h>

using search::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using search::attribute::ReferenceAttribute;
using search::attribute::Config;
using search::attribute::BasicType;
using search::AttributeVector;
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
    std::unique_ptr<ReferenceAttribute> _attr;

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
        _attr = std::make_unique<ReferenceAttribute>("test",
                                                     Config(BasicType::REFERENCE));
    }

    void ensureSpace(uint32_t docId) {
        while (attr().getNumDocs() <= docId) {
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
        return _attr->getReference(doc);
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
};

TEST_F("require that we can instantiate reference attribute", Fixture)
{
    f.ensureSpace(4);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.commit();

    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
}


TEST_F("require that we can compact attribute", Fixture)
{
    f.ensureSpace(4);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.commit();
    search::attribute::Status oldStatus = f.getStatus();
    search::attribute::Status newStatus = oldStatus;
    uint64_t iter = 0;
    uint64_t iterLimit = 100000;
    for (; iter < iterLimit; ++iter) {
        f.clear(2);
        f.set(2, toGid(doc2));
        newStatus = f.getStatus();
        if (newStatus.getUsed() < oldStatus.getUsed()) {
            break;
        }
        oldStatus = newStatus;
    }
    EXPECT_GREATER(iterLimit, iter);
    LOG(info,
        "iter = %" PRIu64 ", memory usage %" PRIu64 ", -> %" PRIu64,
        iter, oldStatus.getUsed(), newStatus.getUsed());
    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
}

TEST_F("require that we can save and load attribute", Fixture)
{
    f.ensureSpace(4);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.commit();
    f.save();
    f.load();
    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
