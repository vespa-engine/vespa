// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/feature_store.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("feature_store_test");

using namespace vespalib::datastore;
using namespace search::index;

using search::index::schema::CollectionType;
using search::index::schema::DataType;

namespace search::memoryindex {

class FeatureStoreTest : public ::testing::Test {
public:
    Schema schema;
    FeatureStore fs;

    Schema make_schema() const;
    FeatureStoreTest();
};

Schema
FeatureStoreTest::make_schema() const
{
    Schema result;
    result.addIndexField(Schema::IndexField("f0", DataType::STRING));
    result.addIndexField(Schema::IndexField("f1", DataType::STRING, CollectionType::WEIGHTEDSET));
    return result;
}

FeatureStoreTest::FeatureStoreTest()
    : schema(make_schema()),
      fs(schema)
{
}

void
assertFeatures(const DocIdAndFeatures& exp,
               const DocIdAndFeatures& act)
{
    // docid is not encoded as part of features
    ASSERT_EQ(exp.elements().size(),
              act.elements().size());
    for (size_t i = 0; i < exp.elements().size(); ++i) {
        EXPECT_EQ(exp.elements()[i].getElementId(),
                  act.elements()[i].getElementId());
        EXPECT_EQ(exp.elements()[i].getNumOccs(),
                  act.elements()[i].getNumOccs());
        EXPECT_EQ(exp.elements()[i].getWeight(), act.elements()[i].getWeight());
        EXPECT_EQ(exp.elements()[i].getElementLen(),
                  act.elements()[i].getElementLen());
    }
    ASSERT_EQ(exp.word_positions().size(), act.word_positions().size());
    for (size_t i = 0; i < exp.word_positions().size(); ++i) {
        EXPECT_EQ(exp.word_positions()[i].getWordPos(),
                  act.word_positions()[i].getWordPos());
    }
}

DocIdAndFeatures
getFeatures(uint32_t numOccs,
            int32_t weight,
            uint32_t elemLen)
{
    DocIdAndFeatures f;
    f.set_doc_id(0);
    f.elements().push_back(WordDocElementFeatures(0));
    f.elements().back().setNumOccs(numOccs);
    f.elements().back().setWeight(weight);
    f.elements().back().setElementLen(elemLen);
    for (uint32_t i = 0; i < numOccs; ++i) {
        f.word_positions().push_back(WordDocElementWordPosFeatures(i));
    }
    return f;
}

TEST_F(FeatureStoreTest, features_can_be_added_and_retrieved)
{
    DocIdAndFeatures act;
    EntryRef r1;
    EntryRef r2;
    std::pair<EntryRef, uint64_t> r;
    {
        DocIdAndFeatures f = getFeatures(2, 4, 8);
        r = fs.addFeatures(0, f);
        r1 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_EQ(1u, FeatureStore::RefType(r1).offset());
        EXPECT_EQ(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r1).offset(),
            FeatureStore::RefType(r1).bufferId());
        fs.getFeatures(0, r1, act);
        // weight not encoded for single value
        ASSERT_NO_FATAL_FAILURE(assertFeatures(getFeatures(2, 1, 8), act));
    }
    {
        DocIdAndFeatures f = getFeatures(4, 8, 16);
        r = fs.addFeatures(1, f);
        r2 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_TRUE(FeatureStore::RefType(r2).offset() >
                    FeatureStore::RefType(r1).offset());
        EXPECT_EQ(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r2).offset(),
            FeatureStore::RefType(r2).bufferId());
        fs.getFeatures(1, r2, act);
        ASSERT_NO_FATAL_FAILURE(assertFeatures(f, act));
    }
}

TEST_F(FeatureStoreTest, next_words_are_working)
{
    DocIdAndFeatures act;
    EntryRef r1;
    EntryRef r2;
    std::pair<EntryRef, uint64_t> r;
    {
        DocIdAndFeatures f = getFeatures(2, 4, 8);
        r = fs.addFeatures(0, f);
        r1 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_EQ(1u, FeatureStore::RefType(r1).offset());
        EXPECT_EQ(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r1).offset(),
            FeatureStore::RefType(r1).bufferId());
        fs.getFeatures(0, r1, act);
        // weight not encoded for single value
        ASSERT_NO_FATAL_FAILURE(assertFeatures(getFeatures(2, 1, 8), act));
    }
    {
        DocIdAndFeatures f = getFeatures(4, 8, 16);
        r = fs.addFeatures(1, f);
        r2 = r.first;
        EXPECT_TRUE(r.second > 0);
        EXPECT_TRUE(FeatureStore::RefType(r2).offset() >
                    FeatureStore::RefType(r1).offset());
        EXPECT_EQ(0u, FeatureStore::RefType(r1).bufferId());
        LOG(info,
            "bits(%" PRIu64 "), ref.offset(%zu), ref.bufferId(%u)",
            r.second,
            FeatureStore::RefType(r2).offset(),
            FeatureStore::RefType(r2).bufferId());
        fs.getFeatures(1, r2, act);
        ASSERT_NO_FATAL_FAILURE(assertFeatures(f, act));
    }
}

TEST_F(FeatureStoreTest, add_features_triggers_change_of_buffer)
{
    size_t cnt = 1;
    DocIdAndFeatures act;
    uint32_t lastId = 0;
    for (;;++cnt) {
        uint32_t numOccs = (cnt % 100) + 1;
        DocIdAndFeatures f = getFeatures(numOccs, 1, numOccs + 1);
        std::pair<EntryRef, uint64_t> r = fs.addFeatures(0, f);
        fs.getFeatures(0, r.first, act);
        ASSERT_NO_FATAL_FAILURE(assertFeatures(f, act));
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
    EXPECT_EQ(1u, lastId);
    LOG(info, "Added %zu feature sets in 1 buffer", cnt);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
