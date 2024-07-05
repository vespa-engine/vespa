// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_mapper.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_mapper_factory.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/test_master.hpp>

#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_mapper_test");

using document::GlobalId;
using document::BucketId;
using document::DocumentId;
using storage::spi::Timestamp;
using vespalib::GenerationHandler;

namespace proton {

namespace {

GlobalId toGid(std::string_view docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");
vespalib::string doc2("id:test:music::2");
vespalib::string doc3("id:test:music::3");

static constexpr uint32_t numBucketBits = UINT32_C(20);

BucketId toBucketId(const GlobalId &gid) {
    BucketId bucketId(gid.convertToBucketId());
    bucketId.setUsedBits(numBucketBits);
    return bucketId;
}

using GidMap = std::map<GlobalId, uint32_t>;

struct GidCollector : public search::IGidToLidMapperVisitor
{
    GidMap &_map;
    GidCollector(GidMap &map)
        : IGidToLidMapperVisitor(),
          _map(map)
    {
    }
    virtual void visit(const document::GlobalId &gid, uint32_t lid) const override { _map.insert(std::make_pair(gid, lid)); }
};

GidMap collectGids(const std::unique_ptr<search::IGidToLidMapper> &mapper)
{
    GidMap result;
    mapper->foreach(GidCollector(result));
    return result;
}

void assertGids(const GidMap &expGids, const GidMap &gids)
{
    EXPECT_EQUAL(expGids, gids);
}

void assertLid(const std::unique_ptr<search::IGidToLidMapper> &mapper, const vespalib::string &docId, uint32_t lid) {
    auto gids = collectGids(mapper);
    auto itr = gids.find(toGid(docId));
    uint32_t foundLid = (itr != gids.end()) ? itr->second : 0u;
    EXPECT_EQUAL(lid, foundLid);
}

}

struct Fixture
{

    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    std::shared_ptr<DocumentMetaStore> _dms;
    std::shared_ptr<const DocumentMetaStoreContext> _dmsContext;
    Timestamp _timestamp;
    using generation_t = GenerationHandler::generation_t;

    Fixture()
        : _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
          _dms(std::make_shared<DocumentMetaStore>(_bucketDB)),
          _dmsContext(std::make_shared<const DocumentMetaStoreContext>(_dms))
    {
        populate();
    }

    void bumpTimeStamp() {
        _timestamp = Timestamp(_timestamp.getValue() + 1);
    }

    void put(std::string_view docId, uint32_t lid) {
        bumpTimeStamp();
        const GlobalId gid(toGid(docId));
        uint32_t docSize = 1;
        _dms->put(gid, toBucketId(gid), _timestamp, docSize, lid, 0u);
        _dms->commit();
    }

    uint32_t put(std::string_view docId) {
        auto inspectRes = _dms->inspect(toGid(docId), 0u);
        uint32_t lid = inspectRes.getLid();
        put(docId, lid);
        return lid;
    }

    void remove(uint32_t lid) {
        if (_dms->remove(lid, 0u)) {
            _dms->removes_complete({ lid });
        }
        _dms->commit();
    }


    void populate() {
        put(doc1, 4);
        put(doc2, 7);
        _dms->constructFreeList();
    }

    std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() {
        return std::make_shared<GidToLidMapperFactory>(_dmsContext);
    }

    void assertGenerations(generation_t currentGeneration, generation_t oldest_used_generation)
    {
        const GenerationHandler &handler = _dms->getGenerationHandler();
        EXPECT_EQUAL(currentGeneration, handler.getCurrentGeneration());
        EXPECT_EQUAL(oldest_used_generation, handler.get_oldest_used_generation());
    }

    template <typename Function>
    void assertPut(std::string_view docId, uint32_t expLid,
                   generation_t currentGeneration, generation_t firstUsedGeneration,
                   Function &&func)
    {
        uint32_t lid = put(docId);
        EXPECT_EQUAL(expLid, lid);
        TEST_DO(assertLid(func(), docId, expLid));
        TEST_DO(assertGenerations(currentGeneration, firstUsedGeneration));
    }
};

TEST_F("Test that mapper holds read guard", Fixture)
{
    TEST_DO(f.assertGenerations(3, 3));
    auto factory = f.getGidToLidMapperFactory();
    TEST_DO(f.assertPut(doc3, 1, 4, 4, [&]() { return factory->getMapper(); }));
    // Remove and readd withoug guard, old docid can be reused
    f.remove(1);
    TEST_DO(f.assertPut(doc3, 1, 7, 7, [&]() { return factory->getMapper(); }));
    // Remove and readd withoug guard, old docid cannot be reused
    auto mapper = factory->getMapper();
    f.remove(1);
    TEST_DO(f.assertPut(doc3, 2, 10, 7, [&]() -> auto & { return mapper; }));
}

TEST_F("Test that gid mapper can iterate over known gids", Fixture)
{
    auto factory = f.getGidToLidMapperFactory();
    auto mapper = factory->getMapper();
    TEST_DO(assertGids({{toGid(doc1), 4}, {toGid(doc2), 7}}, collectGids(mapper)));
    f.put(doc3);
    TEST_DO(assertGids({{toGid(doc1), 4}, {toGid(doc2), 7}, {toGid(doc3), 1}}, collectGids(mapper)));
    f.remove(4);
    TEST_DO(assertGids({{toGid(doc2), 7}, {toGid(doc3), 1}}, collectGids(mapper)));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
