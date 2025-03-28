// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for simple_index.

#include <vespa/searchlib/predicate/simple_index.hpp>
#include <vespa/searchlib/predicate/simple_index_saver.hpp>
#include <vespa/searchlib/predicate/nbo_write.h>
#include <vespa/searchlib/util/data_buffer_writer.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <map>

using namespace search;
using namespace search::predicate;
using vespalib::GenerationHolder;

namespace {

struct MyData {
    uint32_t data;
    MyData() : data(0) {}
    MyData(uint32_t d) : data(d) {}
    bool valid() const {
        return data != 0;
    }
};

struct MyDataSaver : PostingSaver<MyData> {
    void save(const MyData &data, BufferWriter& writer) const override {
        nbo_write<uint32_t>(writer, data.data);
    }
};

struct MyDataDeserializer : PostingDeserializer<MyData> {
    MyData deserialize(vespalib::DataBuffer& buffer) override {
        return {buffer.readInt32()};
    }
};

struct SimpleDocIdLimitProvider : public DocIdLimitProvider {
    uint32_t _doc_id_limit = 1;
    uint32_t _committed_doc_id_limit = 1;
    uint32_t getDocIdLimit() const override { return _doc_id_limit; }
    uint32_t getCommittedDocIdLimit() const override { return _committed_doc_id_limit; }
};

constexpr uint64_t key = 0x123456;
constexpr uint32_t doc_id = 42;
const MyData data{100};

constexpr double UPPER_DOCID_FREQ_THRESHOLD = 0.5;
constexpr double LOWER_DOCID_FREQ_THRESHOLD = 0.25;
constexpr size_t UPPER_VECTOR_SIZE_THRESHOLD = 10;
constexpr size_t LOWER_VECTOR_SIZE_THRESHOLD = 8;
constexpr size_t VECTOR_PRUNE_FREQUENCY = 1;
constexpr double FOREACH_VECTOR_THRESHOLD = 0.0;
const auto config = SimpleIndexConfig(UPPER_DOCID_FREQ_THRESHOLD,
                                      LOWER_DOCID_FREQ_THRESHOLD,
                                      UPPER_VECTOR_SIZE_THRESHOLD,
                                      LOWER_VECTOR_SIZE_THRESHOLD,
                                      VECTOR_PRUNE_FREQUENCY,
                                      FOREACH_VECTOR_THRESHOLD,
                                      vespalib::GrowStrategy());
struct Fixture {
    GenerationHolder _generation_holder;
    SimpleDocIdLimitProvider _limit_provider;
    SimpleIndex<MyData> _index;
    Fixture() : _generation_holder(), _limit_provider(),
                _index(_generation_holder, _limit_provider, config) {}
    ~Fixture() {
        _generation_holder.reclaim_all();
    }
    SimpleIndex<MyData> &index() {
        return _index;
    }
    void addPosting(uint64_t k, uint32_t id, const MyData &d) {
        if (id >= _limit_provider._doc_id_limit) {
            _limit_provider._doc_id_limit = id + 1;
        }
        _index.addPosting(k, id, d);
    }
    SimpleIndex<MyData>::DictionaryIterator lookup(uint64_t k) {
        return _index.lookup(k);
    }
    bool hasKey(uint64_t k) {
        return lookup(k).valid();
    }
    std::pair<MyData, bool> removeFromPostingList(uint64_t k, uint32_t id) {
        return _index.removeFromPostingList(k, id);
    }
    bool hasVectorPostingList(uint64_t k) {
        return _index.getVectorPostingList(k).operator bool();
    }
    SimpleIndex<MyData>::VectorIterator getVectorPostingList(uint64_t k) {
        return *_index.getVectorPostingList(k);
    }
    SimpleIndex<MyData>::BTreeIterator getBTreePostingList(vespalib::datastore::EntryRef ref) {
        return _index.getBTreePostingList(ref);
    }
    void commit() {
        _index.commit();
        _limit_provider._committed_doc_id_limit = _limit_provider._doc_id_limit;
    }
};

TEST(SimpleIndexTest, require_that_SimpleIndex_can_insert_and_remove_a_value) {
    Fixture f;
    f.addPosting(key, doc_id, data);
    f.commit();
    auto it = f.lookup(key);
    ASSERT_TRUE(it.valid());
    vespalib::datastore::EntryRef ref = it.getData();
    auto posting_it = f.getBTreePostingList(ref);
    ASSERT_TRUE(posting_it.valid());
    EXPECT_EQ(doc_id, posting_it.getKey());
    EXPECT_EQ(data.data, posting_it.getData().data);

    auto result = f.removeFromPostingList(key, doc_id);
    EXPECT_TRUE(result.second);
    EXPECT_EQ(data.data, result.first.data);
    f.commit();

    result = f.removeFromPostingList(key, doc_id);
    EXPECT_FALSE(result.second);
    EXPECT_FALSE(result.first.valid());

    ASSERT_FALSE(f.hasKey(key));
}

TEST(SimpleIndexTest, require_that_SimpleIndex_can_insert_and_remove_many_values) {
    Fixture f;
    for (uint32_t id = 1; id < 100; ++id) {
        f.addPosting(key, id, {id});
    }
    f.commit();
    auto it = f.lookup(key);
    ASSERT_TRUE(it.valid());
    vespalib::datastore::EntryRef ref = it.getData();
    auto posting_it = f.getBTreePostingList(ref);
    for (size_t id = 1; id < 100; ++id) {
        ASSERT_TRUE(posting_it.valid());
        EXPECT_EQ(id, posting_it.getKey());
        EXPECT_EQ(id, posting_it.getData().data);
        ++posting_it;
    }
    ASSERT_FALSE(posting_it.valid());
    for (uint32_t id = 1; id < 100; ++id) {
        it = f.lookup(key);
        ASSERT_TRUE(it.valid());
        ref = it.getData();
        auto result = f.removeFromPostingList(key, id);
        EXPECT_TRUE(result.second);
        EXPECT_EQ(id, result.first.data);
    }
    f.commit();
    ASSERT_FALSE(f.hasKey(key));
}

struct MyObserver : SimpleIndexDeserializeObserver<> {
    std::map<uint32_t, uint64_t> features;
    void notifyInsert(uint64_t my_key, uint32_t my_doc_id, uint32_t) override {
        features[my_doc_id] = my_key;
    }
    bool hasSeenDoc(uint32_t doc) {
        return features.find(doc) != features.end();
    }
};

TEST(SimpleIndexTest, require_that_SimpleIndex_can_be_serialized_and_deserialized) {
    Fixture f1;
    Fixture f2;
    for (uint32_t id = 1; id < 100; ++id) {
        f1.addPosting(key, id, {id});
    }
    f1.commit();
    vespalib::DataBuffer buffer;
    {
        DataBufferWriter writer(buffer);
        f1.index().make_saver(std::make_unique<MyDataSaver>())->save(writer);
        writer.flush();
    }
    MyObserver observer;
    MyDataDeserializer deserializer;
    f2.index().deserialize(buffer, deserializer, observer, PredicateAttribute::PREDICATE_ATTRIBUTE_VERSION);

    auto it = f2.lookup(key);
    ASSERT_TRUE(it.valid());
    vespalib::datastore::EntryRef ref = it.getData();
    auto posting_it = f1.getBTreePostingList(ref);
    for (uint32_t id = 1; id < 100; ++id) {
        ASSERT_TRUE(posting_it.valid());
        EXPECT_EQ(id, posting_it.getKey());
        EXPECT_EQ(id, posting_it.getData().data);
        EXPECT_TRUE(observer.hasSeenDoc(id));
        ++posting_it;
    }
    EXPECT_FALSE(posting_it.valid());
}

TEST(SimpleIndexTest, require_that_SimpleIndex_can_update_by_inserting_the_same_key_twice) {
    Fixture f;
    f.addPosting(key, doc_id, data);

    MyData new_data{42};
    f.addPosting(key, doc_id, new_data);
    f.commit();

    auto it = f.lookup(key);
    ASSERT_TRUE(it.valid());
    vespalib::datastore::EntryRef ref = it.getData();
    auto posting_it = f.getBTreePostingList(ref);
    ASSERT_TRUE(posting_it.valid());
    EXPECT_EQ(doc_id, posting_it.getKey());
    EXPECT_EQ(new_data.data, posting_it.getData().data);
}

TEST(SimpleIndexTest, require_that_only_that_btrees_exceeding_size_threshold_is_promoted_to_vector) {
    Fixture f;
    for (uint32_t i = 1; i < 10; ++i) {
        f.addPosting(key, i, {i});
    }
    f.commit();
    ASSERT_TRUE(f.hasKey(key));
    EXPECT_FALSE(f.hasVectorPostingList(key));
    f.addPosting(key, 10, {10});
    f.commit();
    ASSERT_TRUE(f.hasVectorPostingList(key));
}

TEST(SimpleIndexTest, require_that_vectors_below_size_threshold_is_pruned) {
    Fixture f;
    for (uint32_t i = 1; i <= 10; ++i) {
        f.addPosting(key, i, {i});
    }
    f.commit();
    auto it = f.lookup(key);
    ASSERT_TRUE(it.valid());
    for (uint32_t i = 10; i > 8; --i) {
        f.removeFromPostingList(key, i);
    }
    f.commit();
    EXPECT_TRUE(f.hasVectorPostingList(key));
    f.removeFromPostingList(key, 8);
    f.commit();
    EXPECT_FALSE(f.hasVectorPostingList(key));
}

TEST(SimpleIndexTest, require_that_only_btrees_with_high_enough_doc_frequency_is_promoted_to_vector) {
    Fixture f;
    for (uint32_t i = 100; i > 51; --i) {
        f.addPosting(key, i, {i});
    }
    f.commit();
    auto it = f.lookup(key);
    ASSERT_TRUE(it.valid());
    EXPECT_FALSE(f.hasVectorPostingList(key));
    f.addPosting(key, 51, {51});
    f.commit();
    ASSERT_TRUE(f.hasVectorPostingList(key));
}

TEST(SimpleIndexTest, require_that_vectors_below_doc_frequency_is_pruned_by_removeFromPostingList) {
    Fixture f;
    for (uint32_t i = 1; i <= 100; ++i) {
        f.addPosting(key, i, {i});
    }
    f.commit();
    ASSERT_TRUE(f.hasKey(key));
    EXPECT_TRUE(f.hasVectorPostingList(key));
    for (uint32_t i = 100; i > 25; --i) {
        f.removeFromPostingList(key, i);
    }
    f.commit();
    EXPECT_TRUE(f.hasVectorPostingList(key));
    f.removeFromPostingList(key, 25);
    f.commit();
    EXPECT_FALSE(f.hasVectorPostingList(key));
}

TEST(SimpleIndexTest, require_that_vectors_below_doc_frequency_is_pruned_by_addPosting) {
    Fixture f;
    for (uint32_t i = 1; i <= 10; ++i) {
        f.addPosting(key, i, {i});
    }
    f.commit();
    ASSERT_TRUE(f.hasKey(key));
    EXPECT_TRUE(f.hasVectorPostingList(key));
    for (uint32_t i = 1; i <= 100; ++i) {
        f.addPosting(key + 1, i, {i});
    }
    f.commit();
    EXPECT_FALSE(f.hasVectorPostingList(key));
}

TEST(SimpleIndexTest, require_that_promoteOverThresholdVectors_promotes_posting_lists_over_threshold_to_vectors) {
    Fixture f;
    f._limit_provider._doc_id_limit = 100;
    for (uint32_t i = 1; i <= 20; ++i) {
        f.addPosting(key + 0, i, {i});
        f.addPosting(key + 1, i, {i});
        f.addPosting(key + 2, i, {i});
    }
    for (uint32_t i = 21; i <= 40; ++i) {
        f.addPosting(key + 0, i, {i});
        f.addPosting(key + 2, i, {i});
    }
    f.commit();
    EXPECT_FALSE(f.hasVectorPostingList(key + 0));
    EXPECT_FALSE(f.hasVectorPostingList(key + 1));
    EXPECT_FALSE(f.hasVectorPostingList(key + 2));
    f._limit_provider._doc_id_limit = 50;
    f.index().promoteOverThresholdVectors();
    f.commit();
    EXPECT_TRUE(f.hasVectorPostingList(key + 0));
    EXPECT_FALSE(f.hasVectorPostingList(key + 1));
    EXPECT_TRUE(f.hasVectorPostingList(key + 2));
}

TEST(SimpleIndexTest, require_that_vector_contains_correct_postings) {
    Fixture f;
    for (uint32_t i = 1; i <= 100; ++i) {
        f.addPosting(key, i, i % 5 > 0 ? MyData{i * 2} : MyData{0});
    }
    f.commit();
    ASSERT_TRUE(f.hasKey(key));
    ASSERT_TRUE(f.hasVectorPostingList(key));
    auto v = f.getVectorPostingList(key);

    EXPECT_EQ(1u, v.getKey());
    EXPECT_EQ(2u, v.getData().data);

    for (uint32_t i = 1; i < 100; ++i) {
        v.linearSeek(i);
        ASSERT_TRUE(v.valid());
        if (i % 5 == 0) {
            EXPECT_EQ(i + 1, v.getKey());
            EXPECT_EQ((i + 1) * 2, v.getData().data);
        } else {
            EXPECT_EQ(i, v.getKey());
            EXPECT_EQ(i * 2, v.getData().data);
        }
    }
    v.linearSeek(100);
    EXPECT_FALSE(v.valid());
}

}  // namespace
