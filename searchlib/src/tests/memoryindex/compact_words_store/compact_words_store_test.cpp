// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/compact_words_store.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/string.h>
#include <iostream>
#include <map>

using namespace search;
using namespace vespalib::datastore;
using namespace search::memoryindex;
using vespalib::MemoryUsage;

using Builder = CompactWordsStore::Builder;
using Iterator = CompactWordsStore::Iterator;
using WordRefVector = Builder::WordRefVector;

const EntryRef w1(1);
const EntryRef w2(2);
const EntryRef w3(3);
const EntryRef w4(4);
const uint32_t d1(111);
const uint32_t d2(222);
const uint32_t d3(333);
const uint32_t d4(444);

WordRefVector
build(Iterator itr)
{
    WordRefVector words;
    for (; itr.valid(); ++itr) {
        words.push_back(itr.wordRef());
    }
    return words;
}

vespalib::string
toStr(Iterator itr)
{
    WordRefVector words = build(itr);
    std::ostringstream oss;
    oss << "[";
    bool firstWord = true;
    for (auto word : words) {
        if (!firstWord) oss << ",";
        oss << word.ref();
        firstWord = false;
    }
    oss << "]";
    return oss.str();
}

struct SingleDocumentTest : public ::testing::Test {
    CompactWordsStore store;
    SingleDocumentTest() : store() {
        store.insert(Builder(d1).insert(w1).insert(w2).insert(w3));
    }
};

struct MultiDocumentTest : public ::testing::Test {
    CompactWordsStore store;
    MultiDocumentTest() : store() {
        store.insert(Builder(d1).insert(w1));
        store.insert(Builder(d2).insert(w2));
        store.insert(Builder(d3).insert(w3));
    }
};


TEST_F(SingleDocumentTest, fields_and_words_can_be_added_for_a_document)
{
    EXPECT_EQ("[1,2,3]", toStr(store.get(d1)));
}

TEST_F(MultiDocumentTest, multiple_documents_can_be_added)
{
    EXPECT_EQ("[1]", toStr(store.get(d1)));
    EXPECT_EQ("[2]", toStr(store.get(d2)));
    EXPECT_EQ("[3]", toStr(store.get(d3)));
    EXPECT_FALSE(store.get(d4).valid());
}

TEST_F(MultiDocumentTest, documents_can_be_removed)
{
    store.remove(d2);
    EXPECT_TRUE(store.get(d1).valid());
    EXPECT_FALSE(store.get(d2).valid());
    EXPECT_TRUE(store.get(d3).valid());
}

TEST_F(MultiDocumentTest, documents_can_be_removed_and_reinserted)
{
    store.remove(d2);
    store.insert(Builder(d2).insert(w4));
    EXPECT_EQ("[4]", toStr(store.get(d2)));
}

TEST(CompactWordStoreTest, multiple_words_can_be_inserted_retrieved_and_removed)
{
    CompactWordsStore store;
    for (uint32_t docId = 0; docId < 50; ++docId) {
        Builder b(docId);
        for (uint32_t wordRef = 0; wordRef < 20000; ++wordRef) {
            b.insert(EntryRef(wordRef));
        }
        store.insert(b);
        store.commit();
        MemoryUsage usage = store.getMemoryUsage();
        std::cout << "memory usage (insert): docId=" << docId << ", alloc=" << usage.allocatedBytes() << ", used=" << usage.usedBytes() << std::endl;
    }
    for (uint32_t docId = 0; docId < 50; ++docId) {
        WordRefVector words = build(store.get(docId));
        EXPECT_EQ(20000u, words.size());
        uint32_t wordRef = 0;
        for (auto word : words) {
            EXPECT_EQ(wordRef++, word.ref());
        }
        store.remove(docId);
        store.commit();
        MemoryUsage usage = store.getMemoryUsage();
        std::cout << "memory usage (remove): docId=" << docId << ", alloc=" << usage.allocatedBytes() << ", used=" << usage.usedBytes() << std::endl;
    }
}

TEST(CompactWordStoreTest, initial_memory_usage_is_reported)
{
    CompactWordsStore store;
    CompactWordsStore::DocumentWordsMap docs;
    CompactWordsStore::Store internalStore;
    MemoryUsage initExp;
    initExp.incAllocatedBytes(docs.getMemoryConsumption());
    initExp.incUsedBytes(docs.getMemoryUsed());
    initExp.merge(internalStore.getMemoryUsage());
    MemoryUsage init = store.getMemoryUsage();
    EXPECT_EQ(initExp.allocatedBytes(), init.allocatedBytes());
    EXPECT_EQ(initExp.usedBytes(), init.usedBytes());
    EXPECT_GT(init.allocatedBytes(), init.usedBytes());
    EXPECT_GT(init.allocatedBytes(), 0u);
    EXPECT_GT(init.usedBytes(), 0u);
}

TEST(CompactWordStoreTest, memory_usage_is_updated_after_insert)
{
    CompactWordsStore store;
    MemoryUsage init = store.getMemoryUsage();

    store.insert(Builder(d1).insert(w1));
    store.commit();
    MemoryUsage after = store.getMemoryUsage();
    EXPECT_GE(after.allocatedBytes(), init.allocatedBytes());
    EXPECT_GT(after.usedBytes(), init.usedBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()


