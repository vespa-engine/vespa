// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/memoryindex/compact_words_store.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <iostream>
#include <map>

using namespace search;
using namespace search::datastore;
using namespace search::memoryindex;

typedef CompactWordsStore::Builder Builder;
typedef CompactWordsStore::Iterator Iterator;
typedef Builder::WordRefVector WordRefVector;

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

struct SingleFixture
{
    CompactWordsStore _store;
    SingleFixture() : _store() {
        _store.insert(Builder(d1).insert(w1).insert(w2).insert(w3));
    }
};

struct MultiFixture
{
    CompactWordsStore _store;
    MultiFixture() : _store() {
        _store.insert(Builder(d1).insert(w1));
        _store.insert(Builder(d2).insert(w2));
        _store.insert(Builder(d3).insert(w3));
    }
};


TEST_F("require that fields and words can be added for a document", SingleFixture)
{
    EXPECT_EQUAL("[1,2,3]", toStr(f._store.get(d1)));
}

TEST_F("require that multiple documents can be added", MultiFixture)
{
    EXPECT_EQUAL("[1]", toStr(f._store.get(d1)));
    EXPECT_EQUAL("[2]", toStr(f._store.get(d2)));
    EXPECT_EQUAL("[3]", toStr(f._store.get(d3)));
    EXPECT_FALSE(f._store.get(d4).valid());
}

TEST_F("require that documents can be removed", MultiFixture)
{
    f._store.remove(d2);
    EXPECT_TRUE(f._store.get(d1).valid());
    EXPECT_FALSE(f._store.get(d2).valid());
    EXPECT_TRUE(f._store.get(d3).valid());
}

TEST_F("require that documents can be removed and re-inserted", MultiFixture)
{
    f._store.remove(d2);
    f._store.insert(Builder(d2).insert(w4));
    EXPECT_EQUAL("[4]", toStr(f._store.get(d2)));
}

TEST("require that a lot of words can be inserted, retrieved and removed")
{
    CompactWordsStore store;
    for (uint32_t docId = 0; docId < 50; ++docId) {
        Builder b(docId);
        for (uint32_t wordRef = 0; wordRef < 20000; ++wordRef) {
            b.insert(EntryRef(wordRef));
        }
        store.insert(b);
        MemoryUsage usage = store.getMemoryUsage();
        std::cout << "memory usage (insert): docId=" << docId << ", alloc=" << usage.allocatedBytes() << ", used=" << usage.usedBytes() << std::endl;
    }
    for (uint32_t docId = 0; docId < 50; ++docId) {
        WordRefVector words = build(store.get(docId));
        EXPECT_EQUAL(20000u, words.size());
        uint32_t wordRef = 0;
        for (auto word : words) {
            EXPECT_EQUAL(wordRef++, word.ref());
        }
        store.remove(docId);
        MemoryUsage usage = store.getMemoryUsage();
        std::cout << "memory usage (remove): docId=" << docId << ", alloc=" << usage.allocatedBytes() << ", used=" << usage.usedBytes() << std::endl;
    }
}

TEST("require that initial memory usage is reported")
{
    CompactWordsStore store;
    CompactWordsStore::DocumentWordsMap docs;
    CompactWordsStore::Store internalStore;
    MemoryUsage initExp;
    initExp.incAllocatedBytes(docs.getMemoryConsumption());
    initExp.incUsedBytes(docs.getMemoryUsed());
    initExp.merge(internalStore.getMemoryUsage());
    MemoryUsage init = store.getMemoryUsage();
    EXPECT_EQUAL(initExp.allocatedBytes(), init.allocatedBytes());
    EXPECT_EQUAL(initExp.usedBytes(), init.usedBytes());
    EXPECT_GREATER(init.allocatedBytes(), init.usedBytes());
    EXPECT_GREATER(init.allocatedBytes(), 0u);
    EXPECT_GREATER(init.usedBytes(), 0u);
}

TEST("require that memory usage is updated after insert")
{
    CompactWordsStore store;
    MemoryUsage init = store.getMemoryUsage();

    store.insert(Builder(d1).insert(w1));
    MemoryUsage after = store.getMemoryUsage();
    EXPECT_GREATER_EQUAL(after.allocatedBytes(), init.allocatedBytes());
    EXPECT_GREATER(after.usedBytes(), init.usedBytes());
}


TEST_MAIN() { TEST_RUN_ALL(); }

