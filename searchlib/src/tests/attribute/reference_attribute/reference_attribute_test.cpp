// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP("reference_attribute_test");

using document::DocumentId;
using document::GlobalId;
using generation_t = vespalib::GenerationHandler::generation_t;
using search::AttributeGuard;
using search::AttributeVector;
using search::QueryTermSimple;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::Reference;
using search::attribute::ReferenceAttribute;
using search::attribute::SearchContextParams;
using search::fef::TermFieldMatchData;
using search::queryeval::FakeResult;
using search::queryeval::SearchIterator;
using vespalib::ArrayRef;
using vespalib::MemoryUsage;

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");
vespalib::string doc2("id:test:music::2");
vespalib::string doc3("id:test:music::3");

}

struct MyGidToLidMapperFactory : public search::attribute::test::MockGidToLidMapperFactory {
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

class LidCollector {
    std::vector<uint32_t> &_lids;
public:
    LidCollector(std::vector<uint32_t> &lids)
        : _lids(lids)
    {
    }
    void operator()(uint32_t lid) { _lids.push_back(lid); }
};

struct ReferenceAttributeTest : public ::testing::Test {
    std::shared_ptr<ReferenceAttribute> _attr;

    ReferenceAttributeTest()
        : _attr()
    {
        resetAttr();
    }

    ~ReferenceAttributeTest() {}

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

    void assertNoRef(uint32_t doc) {
        EXPECT_TRUE(get(doc) == nullptr);
    }

    void assertRef(vespalib::stringref str, uint32_t doc) {
        const GlobalId *gid = get(doc);
        ASSERT_TRUE(gid != nullptr);
        EXPECT_EQ(toGid(str), *gid);
    }

    void assertTargetLid(uint32_t doc, uint32_t expTargetLid) {
        auto ref = getRef(doc);
        ASSERT_TRUE(ref != nullptr);
        EXPECT_EQ(expTargetLid, ref->lid());
        EXPECT_EQ(expTargetLid, _attr->getTargetLid(doc));
    }

    void assertNoTargetLid(uint32_t doc) {
        auto ref = getRef(doc);
        EXPECT_TRUE(ref == nullptr);
        EXPECT_EQ(0u, _attr->getTargetLid(doc));
    }

    void assertLids(uint32_t targetLid, std::vector<uint32_t> expLids) {
        std::vector<uint32_t> lids;
        LidCollector collector(lids);
        _attr->foreach_lid(targetLid, collector);
        EXPECT_EQ(expLids, lids);
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
        AttributeGuard guard(_attr);
        uint64_t dropCount = vespalib::datastore::CompactionStrategy::DEAD_BYTES_SLACK / sizeof(Reference);
        for (; iter < iterLimit; ++iter) {
            clear(2);
            set(2, toGid(doc2));
            if (iter == dropCount) {
                guard = AttributeGuard();
            }
            newStatus = getStatus();
            if (newStatus.getUsed() < oldStatus.getUsed()) {
                break;
            }
            oldStatus = newStatus;
        }
        EXPECT_GT(iterLimit, iter);
        LOG(info, "iter = %" PRIu64 ", memory usage %" PRIu64 ", -> %" PRIu64,
            iter, oldStatus.getUsed(), newStatus.getUsed());
    }

    void notifyReferencedPut(const GlobalId &gid, uint32_t referencedDoc) {
        _attr->notifyReferencedPut(gid, referencedDoc);
    }
    void notifyReferencedRemove(const GlobalId &gid) {
        _attr->notifyReferencedRemove(gid);
    }
    void setGidToLidMapperFactory(std::shared_ptr<MyGidToLidMapperFactory> factory, const std::vector<GlobalId>& removes) {
        _attr->setGidToLidMapperFactory(factory);
        _attr->populateTargetLids(removes);
    }
    uint32_t getUniqueGids() {
        return getStatus().getNumUniqueValues();
    }
};

TEST_F(ReferenceAttributeTest, reference_attribute_can_be_instantiated)
{
    ensureDocIdLimit(5);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    commit();

    assertNoRef(3);
    assertRef(doc1, 1);
    assertRef(doc2, 2);
}

TEST_F(ReferenceAttributeTest, new_reference_for_a_document_can_be_set)
{
    ensureDocIdLimit(5);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(3, toGid(doc2));
    commit();
    assertNoRef(4);
    assertRef(doc1, 1);
    assertRef(doc2, 2);
    assertRef(doc2, 3);
    set(2, toGid(doc1));
    commit();
    assertNoRef(4);
    assertRef(doc1, 1);
    assertRef(doc1, 2);
    assertRef(doc2, 3);
}

TEST_F(ReferenceAttributeTest, reference_for_a_document_can_be_cleared)
{
    ensureDocIdLimit(5);
    set(2, toGid(doc2));
    commit();
    assertRef(doc2, 2);
    clear(2);
    commit();
    assertNoRef(2);
    clear(2);
    commit();
    assertNoRef(2);
}

TEST_F(ReferenceAttributeTest, lid_beyond_range_is_mapped_to_zero)
{
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    setGidToLidMapperFactory(factory, {});
    ensureDocIdLimit(5);
    _attr->addDocs(1);
    set(5, toGid(doc2));
    EXPECT_EQ(0, _attr->getTargetLid(5));
    _attr->commit();
    EXPECT_EQ(17, _attr->getTargetLid(5));
}

TEST_F(ReferenceAttributeTest, read_guard_protects_references)
{
    ensureDocIdLimit(5);
    set(2, toGid(doc2));
    commit();
    const GlobalId *gid = get(2);
    ASSERT_TRUE(gid != nullptr);
    EXPECT_EQ(toGid(doc2), *gid);
    {
        AttributeGuard guard(_attr);
        clear(2);
        commit();
        EXPECT_EQ(toGid(doc2), *gid);
    }
    commit();
    EXPECT_NE(toGid(doc2), *gid);
}

TEST_F(ReferenceAttributeTest, attribute_can_be_compacted)
{
    ensureDocIdLimit(5);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    commit();
    triggerCompaction(100000);
    assertNoRef(3);
    assertRef(doc1, 1);
    assertRef(doc2, 2);
}

TEST_F(ReferenceAttributeTest, attribute_can_be_saved_and_loaded)
{
    ensureDocIdLimit(5);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(4, toGid(doc1));
    commit();
    save();
    load();
    EXPECT_EQ(5u, attr().getNumDocs());
    assertNoRef(3);
    assertRef(doc1, 1);
    assertRef(doc2, 2);
    assertRef(doc1, 4);
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_F(ReferenceAttributeTest, update_uses_gid_mapper_to_set_target_lid)
{
    ensureDocIdLimit(6);
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    setGidToLidMapperFactory(factory, {});
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(4, toGid(doc1));
    set(5, toGid(doc3));
    commit();
    assertTargetLid(1, 10);
    assertTargetLid(2, 17);
    assertNoTargetLid(3);
    assertTargetLid(4, 10);
    assertTargetLid(5, 0);
}

TEST_F(ReferenceAttributeTest, notifyReferencedPut_updates_lid_2_lid_mapping)
{
    ensureDocIdLimit(4);
    set(1, toGid(doc1));
    set(2, toGid(doc2));
    set(3, toGid(doc1));
    commit();
    assertTargetLid(1, 0);
    assertTargetLid(2, 0);
    assertTargetLid(3, 0);
    notifyReferencedPut(toGid(doc1), 10);
    notifyReferencedPut(toGid(doc2), 20);
    notifyReferencedPut(toGid(doc3), 30);
    assertTargetLid(1, 10);
    assertTargetLid(2, 20);
    assertTargetLid(3, 10);
}

namespace {

void
preparePopulateTargetLids(ReferenceAttributeTest &f)
{
    f.ensureDocIdLimit(6);
    f.set(1, toGid(doc1));
    f.set(2, toGid(doc2));
    f.set(3, toGid(doc1));
    f.set(4, toGid(doc3));
    f.commit();
    f.assertTargetLid(1, 0);
    f.assertTargetLid(2, 0);
    f.assertTargetLid(3, 0);
    f.assertTargetLid(4, 0);
    f.assertNoTargetLid(5);
}

void
checkPopulateTargetLids(ReferenceAttributeTest &f)
{
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    f.setGidToLidMapperFactory(factory, {});
    f.assertTargetLid(1, 10);
    f.assertTargetLid(2, 17);
    f.assertTargetLid(3, 10);
    f.assertTargetLid(4, 0);
    f.assertNoTargetLid(5);
    f.assertLids(0, { });
    f.assertLids(10, { 1, 3});
    f.assertLids(17, { 2 });
    f.assertLids(18, { });
}

}

TEST_F(ReferenceAttributeTest, populateTargetLids_uses_gid_mapper_to_update_lid_2_lid_mapping)
{
    preparePopulateTargetLids(*this);
    checkPopulateTargetLids(*this);
}

TEST_F(ReferenceAttributeTest, populateTargetLids_uses_gid_mapper_to_update_lid_2_lid_mapping_after_load)
{
    preparePopulateTargetLids(*this);
    save();
    load();
    checkPopulateTargetLids(*this);
    EXPECT_TRUE(vespalib::unlink("test.dat"));
    EXPECT_TRUE(vespalib::unlink("test.udat"));
}

TEST_F(ReferenceAttributeTest, populateTargetLids_handles_removes)
{
    preparePopulateTargetLids(*this);
    auto factory = std::make_shared<MyGidToLidMapperFactory>();
    setGidToLidMapperFactory(factory, { toGid(doc1) });
    assertTargetLid(1, 0);
    assertTargetLid(2, 17);
    assertTargetLid(3, 0);
    assertTargetLid(4, 0);
    assertNoTargetLid(5);
    assertLids(0, { });
    assertLids(10, { });
    assertLids(17, { 2 });
    assertLids(18, { });
}

TEST_F(ReferenceAttributeTest, notifyReferencedPut_and_notifyReferencedRemove_changes_reverse_mapping)
{
    preparePopulateTargetLids(*this);
    assertLids(10, { });
    assertLids(11, { });
    notifyReferencedPut(toGid(doc1), 10);
    assertLids(10, { 1, 3});
    assertLids(11, { });
    notifyReferencedPut(toGid(doc1), 11);
    assertLids(10, { });
    assertLids(11, { 1, 3});
    notifyReferencedRemove(toGid(doc1));
    assertLids(10, { });
    assertLids(11, { });
}

TEST_F(ReferenceAttributeTest, unique_gids_are_tracked)
{
    EXPECT_EQ(0u, getUniqueGids());
    notifyReferencedPut(toGid(doc1), 10);
    EXPECT_EQ(1u, getUniqueGids());
    ensureDocIdLimit(3);
    set(1, toGid(doc1));
    commit();
    EXPECT_EQ(1u, getUniqueGids());
    assertTargetLid(1, 10);
    assertLids(10, { 1 });
    set(2, toGid(doc2));
    commit();
    EXPECT_EQ(2u, getUniqueGids());
    assertTargetLid(2, 0);
    notifyReferencedPut(toGid(doc2), 17);
    EXPECT_EQ(2u, getUniqueGids());
    assertTargetLid(2, 17);
    assertLids(17, { 2 });
    clear(1);
    notifyReferencedRemove(toGid(doc2));
    EXPECT_EQ(2u, getUniqueGids());
    assertNoTargetLid(1);
    assertTargetLid(2, 0);
    assertLids(10, { });
    assertLids(17, { });
    clear(2);
    notifyReferencedRemove(toGid(doc1));
    EXPECT_EQ(0u, getUniqueGids());
}

struct ReferenceAttributeSearchTest : public ReferenceAttributeTest {

    constexpr static uint32_t doc_id_limit = 6;

    ReferenceAttributeSearchTest()
        : ReferenceAttributeTest()
    {
        ensureDocIdLimit(doc_id_limit);
        set(1, toGid(doc1));
        set(3, toGid(doc2));
        set(4, toGid(doc1));
        commit();
    }

    FakeResult perform_search(SearchIterator& itr) {
        FakeResult result;
        itr.initFullRange();
        for (uint32_t doc_id = 1; doc_id < doc_id_limit; ++doc_id) {
            if (itr.seek(doc_id)) {
                result.doc(doc_id);
            }
        }
        return result;
    }

    void expect_search_result(const std::string& term, const FakeResult& expected) {
        auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>(term, QueryTermSimple::Type::WORD),
                                    SearchContextParams());
        TermFieldMatchData tfmd;
        auto itr = ctx->createIterator(&tfmd, false);
        FakeResult actual = perform_search(*itr);
        EXPECT_EQ(expected, actual);
    }

};

TEST_F(ReferenceAttributeSearchTest, can_be_searched_by_document_id)
{
    expect_search_result(doc1, FakeResult().doc(1).doc(4));
    expect_search_result(doc2, FakeResult().doc(3));
    expect_search_result(doc3, FakeResult());
    expect_search_result("invalid document id", FakeResult());
}

GTEST_MAIN_RUN_ALL_TESTS()
