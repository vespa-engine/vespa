// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("feature_store_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/memoryindex/feature_store.h>

using namespace search::btree;
using namespace search::datastore;
using namespace search::index;

using search::index::schema::CollectionType;
using search::index::schema::DataType;

namespace search
{


namespace memoryindex
{


class Test : public vespalib::TestApp
{
private:
    Schema _schema;

    const Schema & getSchema() const { return _schema; }
    bool assertFeatures(const DocIdAndFeatures &exp, const DocIdAndFeatures &act);
    void requireThatFeaturesCanBeAddedAndRetrieved();
    void requireThatNextWordsAreWorking();
    void requireThatAddFeaturesTriggersChangeOfBuffer();

public:
    Test();
    int Main() override;
};


bool
Test::assertFeatures(const DocIdAndFeatures &exp,
                     const DocIdAndFeatures &act)
{
    // docid is not encoded as part of features
    if (!EXPECT_EQUAL(exp._elements.size(),
                      act._elements.size()))
        return false;
    for (size_t i = 0; i < exp._elements.size(); ++i) {
        if (!EXPECT_EQUAL(exp._elements[i]._elementId,
                          act._elements[i]._elementId))
            return false;
        if (!EXPECT_EQUAL(exp._elements[i]._numOccs,
                          act._elements[i]._numOccs))
            return false;
        if (!EXPECT_EQUAL(exp._elements[i]._weight, act._elements[i]._weight))
            return false;
        if (!EXPECT_EQUAL(exp._elements[i]._elementLen,
                          act._elements[i]._elementLen))
            return false;
    }
    if (!EXPECT_EQUAL(exp._wordPositions.size(), act._wordPositions.size()))
        return false;
    for (size_t i = 0; i < exp._wordPositions.size(); ++i) {
        if (!EXPECT_EQUAL(exp._wordPositions[i]._wordPos,
                          act._wordPositions[i]._wordPos)) return false;
    }
    return true;
}


DocIdAndFeatures
getFeatures(uint32_t numOccs,
            int32_t weight,
            uint32_t elemLen)
{
    DocIdAndFeatures f;
    f._docId = 0;
    f._elements.push_back(WordDocElementFeatures(0));
    f._elements.back().setNumOccs(numOccs);
    f._elements.back().setWeight(weight);
    f._elements.back().setElementLen(elemLen);
    for (uint32_t i = 0; i < numOccs; ++i) {
        f._wordPositions.push_back(WordDocElementWordPosFeatures(i));
    }
    return f;
}


void
Test::requireThatFeaturesCanBeAddedAndRetrieved()
{
    FeatureStore fs(getSchema());
    DocIdAndFeatures act;
    EntryRef r1;
    EntryRef r2;
    std::pair<EntryRef, uint64_t> r;
    {
        DocIdAndFeatures f = getFeatures(2, 4, 8);
        r = fs.addFeatures(0, f);
        r1 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_EQUAL(FeatureStore::RefType::align(1u),
                     FeatureStore::RefType(r1).offset());
        EXPECT_EQUAL(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r1).offset(),
            FeatureStore::RefType(r1).bufferId());
        fs.getFeatures(0, r1, act);
        // weight not encoded for single value
        EXPECT_TRUE(assertFeatures(getFeatures(2, 1, 8), act));
    }
    {
        DocIdAndFeatures f = getFeatures(4, 8, 16);
        r = fs.addFeatures(1, f);
        r2 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_TRUE(FeatureStore::RefType(r2).offset() >
                    FeatureStore::RefType(r1).offset());
        EXPECT_EQUAL(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r2).offset(),
            FeatureStore::RefType(r2).bufferId());
        fs.getFeatures(1, r2, act);
        EXPECT_TRUE(assertFeatures(f, act));
    }
}


void
Test::requireThatNextWordsAreWorking()
{
    FeatureStore fs(getSchema());
    DocIdAndFeatures act;
    EntryRef r1;
    EntryRef r2;
    std::pair<EntryRef, uint64_t> r;
    {
        DocIdAndFeatures f = getFeatures(2, 4, 8);
        r = fs.addFeatures(0, f);
        r1 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_EQUAL(FeatureStore::RefType::align(1u),
                     FeatureStore::RefType(r1).offset());
        EXPECT_EQUAL(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r1).offset(),
            FeatureStore::RefType(r1).bufferId());
        fs.getFeatures(0, r1, act);
        // weight not encoded for single value
        EXPECT_TRUE(assertFeatures(getFeatures(2, 1, 8), act));
    }
    {
        DocIdAndFeatures f = getFeatures(4, 8, 16);
        r = fs.addFeatures(1, f);
        r2 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_TRUE(FeatureStore::RefType(r2).offset() >
                    FeatureStore::RefType(r1).offset());
        EXPECT_EQUAL(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r2).offset(),
            FeatureStore::RefType(r2).bufferId());
        fs.getFeatures(1, r2, act);
        EXPECT_TRUE(assertFeatures(f, act));
    }
}


void
Test::requireThatAddFeaturesTriggersChangeOfBuffer()
{
    FeatureStore fs(getSchema());
    size_t cnt = 1;
    DocIdAndFeatures act;
    uint32_t lastId = 0;
    for (;;++cnt) {
        uint32_t numOccs = (cnt % 100) + 1;
        DocIdAndFeatures f = getFeatures(numOccs, 1, numOccs + 1);
        std::pair<EntryRef, uint64_t> r = fs.addFeatures(0, f);
        fs.getFeatures(0, r.first, act);
        EXPECT_TRUE(assertFeatures(f, act));
        uint32_t bufferId = FeatureStore::RefType(r.first).bufferId();
        if (bufferId > lastId) {
            LOG(info,
                "Changed to bufferId %u after %zu feature sets",
                bufferId, cnt);
            lastId = bufferId;
        }
        if (bufferId == 1) {
            break;
        }
    }
    EXPECT_EQUAL(1u, lastId);
    LOG(info, "Added %zu feature sets in 1 buffer", cnt);
}


Test::Test()
    : _schema()
{
    _schema.addIndexField(Schema::IndexField("f0", DataType::STRING));
    _schema.addIndexField(Schema::IndexField("f1", DataType::STRING, CollectionType::WEIGHTEDSET));
}


int
Test::Main()
{
    TEST_INIT("feature_store_test");

    requireThatFeaturesCanBeAddedAndRetrieved();
    requireThatNextWordsAreWorking();
    requireThatAddFeaturesTriggersChangeOfBuffer();

    TEST_DONE();
}


}


}


TEST_APPHOOK(search::memoryindex::Test);
