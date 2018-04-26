// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("reference_attribute_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/document/base/documentid.h>

using search::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using search::attribute::Reference;
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
vespalib::string doc3("id:test:music::3");

}

struct MyGidToLidMapperFactory : public search::attribute::test::MockGidToLidMapperFactory
{
    MyGidToLidMapperFactory() {
        _map.insert({toGid(doc1), 10});
        _map.insert({toGid(doc2), 17});
    }

    void add(vespalib::stringref docId, uint32_t lid) {
        auto insres = _map.insert({ toGid(docId), lid });
        if (!insres.second) {
            insres.first->second = lid;
        }
    }

    void remove(vespalib::stringref docId) {
        _map.erase(toGid(docId));
    }
};

class LidCollector
{
    std::vector<uint32_t> &_lids;
public:
    LidCollector(std::vector<uint32_t> &lids)
        : _lids(lids)
    {
    }
    void operator()(uint32_t lid) { _lids.push_back(lid); }
};

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
        const Reference *ref = _attr->getReference(doc);
        if (ref != nullptr) {
            return &ref->gid();
        } else {
            return nullptr;
        }
    }

    const Reference *getRef(uint32_t doc) {
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

    void assertTargetLid(uint32_t doc, uint32_t expTargetLid) {
        auto ref = getRef(doc);
        EXPECT_TRUE(ref != nullptr);
        EXPECT_EQUAL(expTargetLid, ref->lid());
        EXPECT_EQUAL(expTargetLid, _attr->getTargetLid(doc));
    }

    void assertNoTargetLid(uint32_t doc) {
        auto ref = getRef(doc);
        EXPECT_TRUE(ref == nullptr);
        EXPECT_EQUAL(0u, _attr->getTargetLid(doc));
    }

    void assertLids(uint32_t targetLid, std::vector<uint32_t> expLids)
    {
        std::vector<uint32_t> lids;
        LidCollector collector(lids);
        _attr->foreach_lid(targetLid, collector);
        EXPECT_EQUAL(expLids, lids);
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

    void notifyReferencedPut(const GlobalId &gid, uint32_t referencedDoc) {
        _attr->notifyReferencedPut(gid, referencedDoc);
    }
    void notifyReferencedRemove(const GlobalId &gid) {
        _attr->notifyReferencedRemove(gid);
    }
    void setGidToLidMapperFactory(std::shared_ptr<MyGidToLidMapperFactory> factory) {
        _attr->setGidToLidMapperFactory(factory);
        _attr->populateTargetLids();
    }
    uint32_t getUniqueGids() {
        return getStatus().getNumUniqueValues();
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
    EXPECT_EQUAL(5u, f.attr().getNumDocs());
    TEST_DO(f.assertNoRef(3));
    TEST_DO(f.assertRef(doc1, 1));
    TEST_DO(f.assertRef(doc2, 2));
    TEST_DO(f.assertRef(doc1, 4));
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_F("require that update() uses gid-mapper to set target lid", Fixture)
{
    f.ensureDocIdLimit(6);
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    f.setGidToLidMapperFactory(factory);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(4, toGid(doc1));
    f.set(5, toGid(doc3));
    f.commit();
    TEST_DO(f.assertTargetLid(1, 10));
    TEST_DO(f.assertTargetLid(2, 17));
    TEST_DO(f.assertNoTargetLid(3));
    TEST_DO(f.assertTargetLid(4, 10));
    TEST_DO(f.assertTargetLid(5, 0));
}

TEST_F("require that notifyReferencedPut() updates lid-2-lid mapping", Fixture)
{
    f.ensureDocIdLimit(4);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(3, toGid(doc1));
    f.commit();
    TEST_DO(f.assertTargetLid(1, 0));
    TEST_DO(f.assertTargetLid(2, 0));
    TEST_DO(f.assertTargetLid(3, 0));
    f.notifyReferencedPut(toGid(doc1), 10);
    f.notifyReferencedPut(toGid(doc2), 20);
    f.notifyReferencedPut(toGid(doc3), 30);
    TEST_DO(f.assertTargetLid(1, 10));
    TEST_DO(f.assertTargetLid(2, 20));
    TEST_DO(f.assertTargetLid(3, 10));
}

namespace {

void preparePopulateTargetLids(Fixture &f)
{
    f.ensureDocIdLimit(6);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(3, toGid(doc1));
    f.set(4, toGid(doc3));
    f.commit();
    TEST_DO(f.assertTargetLid(1, 0));
    TEST_DO(f.assertTargetLid(2, 0));
    TEST_DO(f.assertTargetLid(3, 0));
    TEST_DO(f.assertTargetLid(4, 0));
    TEST_DO(f.assertNoTargetLid(5));
}

void checkPopulateTargetLids(Fixture &f)
{
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    f.setGidToLidMapperFactory(factory);
    TEST_DO(f.assertTargetLid(1, 10));
    TEST_DO(f.assertTargetLid(2, 17));
    TEST_DO(f.assertTargetLid(3, 10));
    TEST_DO(f.assertTargetLid(4, 0));
    TEST_DO(f.assertNoTargetLid(5));
    TEST_DO(f.assertLids(0, { }));
    TEST_DO(f.assertLids(10, { 1, 3}));
    TEST_DO(f.assertLids(17, { 2 }));
    TEST_DO(f.assertLids(18, { }));
}

}

TEST_F("require that populateTargetLids() uses gid-mapper to update lid-2-lid mapping", Fixture)
{
    TEST_DO(preparePopulateTargetLids(f));
    TEST_DO(checkPopulateTargetLids(f));
}

TEST_F("require that populateTargetLids() uses gid-mapper to update lid-2-lid mapping after load", Fixture)
{
    TEST_DO(preparePopulateTargetLids(f));
    f.save();
    f.load();
    TEST_DO(checkPopulateTargetLids(f));
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_F("Require that notifyReferencedPut and notifyReferencedRemove changes reverse mapping", Fixture)
{
    TEST_DO(preparePopulateTargetLids(f));
    TEST_DO(f.assertLids(10, { }));
    TEST_DO(f.assertLids(11, { }));
    f.notifyReferencedPut(toGid(doc1), 10);
    TEST_DO(f.assertLids(10, { 1, 3}));
    TEST_DO(f.assertLids(11, { }));
    f.notifyReferencedPut(toGid(doc1), 11);
    TEST_DO(f.assertLids(10, { }));
    TEST_DO(f.assertLids(11, { 1, 3}));
    f.notifyReferencedRemove(toGid(doc1));
    TEST_DO(f.assertLids(10, { }));
    TEST_DO(f.assertLids(11, { }));
}

TEST_F("Require that we track unique gids", Fixture)
{
    EXPECT_EQUAL(0u, f.getUniqueGids());
    f.notifyReferencedPut(toGid(doc1), 10);
    EXPECT_EQUAL(1u, f.getUniqueGids());
    f.ensureDocIdLimit(3);
    f.set(1, toGid(doc1));
    f.commit();
    EXPECT_EQUAL(1u, f.getUniqueGids());
    TEST_DO(f.assertTargetLid(1, 10));
    TEST_DO(f.assertLids(10, { 1 }));
    f.set(2, toGid(doc2));
    f.commit();
    EXPECT_EQUAL(2u, f.getUniqueGids());
    TEST_DO(f.assertTargetLid(2, 0));
    f.notifyReferencedPut(toGid(doc2), 17);
    EXPECT_EQUAL(2u, f.getUniqueGids());
    TEST_DO(f.assertTargetLid(2, 17));
    TEST_DO(f.assertLids(17, { 2 }));
    f.clear(1);
    f.notifyReferencedRemove(toGid(doc2));
    EXPECT_EQUAL(2u, f.getUniqueGids());
    TEST_DO(f.assertNoTargetLid(1));
    TEST_DO(f.assertTargetLid(2, 0));
    TEST_DO(f.assertLids(10, { }));
    TEST_DO(f.assertLids(17, { }));
    f.clear(2);
    f.notifyReferencedRemove(toGid(doc1));
    EXPECT_EQUAL(0u, f.getUniqueGids());
}

TEST_MAIN() { TEST_RUN_ALL(); }
