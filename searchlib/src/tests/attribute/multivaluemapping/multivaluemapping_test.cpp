// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("multivaluemapping_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/multivaluemapping.h>
#include <algorithm>
#include <limits>

namespace search {

namespace
{

uint32_t dummyCommittedDocIdLimit = std::numeric_limits<uint32_t>::max();

}

class MvMapping : public MultiValueMappingT<uint32_t>
{
    using ArrayRef = vespalib::ConstArrayRef<uint32_t>;
public:
    using MultiValueMappingT<uint32_t>::MultiValueMappingT;
    using MultiValueMappingT<uint32_t>::get;

    uint32_t getValueCount(uint32_t key) {
        ArrayRef values = get(key);
        return values.size();
    }
    uint32_t get(uint32_t key, const uint32_t *&handle) {
        ArrayRef values = get(key);
        handle = &values[0];
        return values.size();
    }
    uint32_t get(uint32_t key, uint32_t *buffer, uint32_t bufferSize)
    {
        ArrayRef values = get(key);
        uint32_t valueCount = values.size();
        for (uint32_t i = 0, m(std::min(valueCount,bufferSize)); i < m; ++i) {
            buffer[i] = values[i];
        }
        return valueCount;
    }
    uint32_t get(uint32_t key, std::vector<uint32_t> &buffer) {
        return get(key, &buffer[0], buffer.size());
    }
    bool get(uint32_t key, uint32_t index, uint32_t &value) const {
        ArrayRef values = get(key);
        if (values.size() <= index) {
            return false;
        } else {
            value = values[0];
            return true;
        }
    }
};

typedef MvMapping::Index Index;
typedef multivalue::Index64 Index64;
typedef multivalue::Index32 Index32;
typedef MvMapping::Histogram Histogram;

class MultiValueMappingTest : public vespalib::TestApp
{
private:
    typedef std::vector<Index> IndexVector;
    typedef std::vector<std::vector<uint32_t> > ExpectedVector;
    typedef vespalib::GenerationHandler::generation_t generation_t;

    class Reader {
    public:
        uint32_t _startGen;
        uint32_t _endGen;
        IndexVector _indices;
        ExpectedVector _expected;
        uint32_t numKeys() { return _indices.size(); }
        Reader(uint32_t startGen, uint32_t endGen, const IndexVector & indices,
               const ExpectedVector & expected) :
            _startGen(startGen), _endGen(endGen), _indices(indices), _expected(expected) {}
    };

    typedef std::vector<Reader> ReaderVector;

    void testIndex32();
    void testIndex64();
    void testSimpleSetAndGet();
    void testChangingValueCount();

    void
    checkReaders(MvMapping &mvm,
                 generation_t mvmGen,
                 ReaderVector &readers);

    void testHoldListAndGeneration();
    void testManualCompaction();
    void testVariousGets();
    void testReplace();
    void testMemoryUsage();
    void testShrink();
    void testHoldElem();
    void requireThatAddressSpaceUsageIsReported();
    void requireThatDeadIsNotAccountedInAddressSpaceUsage();

public:
    int Main();
};

void
MultiValueMappingTest::testIndex32()
{
    {
        Index32 idx;
        EXPECT_EQUAL(idx.values(), 0u);
        EXPECT_EQUAL(idx.alternative(), 0u);
        EXPECT_EQUAL(idx.vectorIdx(), 0u);
        EXPECT_EQUAL(idx.offset(), 0u);
    }
    {
        Index32 idx(3, 0, 1000);
        EXPECT_EQUAL(idx.values(), 3u);
        EXPECT_EQUAL(idx.alternative(), 0u);
        EXPECT_EQUAL(idx.vectorIdx(), 6u);
        EXPECT_EQUAL(idx.offset(), 1000u);
        EXPECT_EQUAL(idx.idx(), 0x300003e8u);
    }
    {
        Index32 idx(15, 1, 134217727);
        EXPECT_EQUAL(idx.values(), 15u);
        EXPECT_EQUAL(idx.alternative(), 1u);
        EXPECT_EQUAL(idx.vectorIdx(), 31u);
        EXPECT_EQUAL(idx.offset(), 134217727u);
        EXPECT_EQUAL(idx.idx(), 0xffffffffu);
    }
    {
        EXPECT_EQUAL(Index32::maxValues(), 15u);
        EXPECT_EQUAL(Index32::alternativeSize(), 2u);
    }
}

void
MultiValueMappingTest::testIndex64()
{
    {
        Index64 idx;
        EXPECT_EQUAL(idx.values(), 0u);
        EXPECT_EQUAL(idx.alternative(), 0u);
        EXPECT_EQUAL(idx.vectorIdx(), 0u);
        EXPECT_EQUAL(idx.offset(), 0u);
    }
    {
        Index64 idx(3, 0, 1000);
        EXPECT_EQUAL(idx.values(), 3u);
        EXPECT_EQUAL(idx.alternative(), 0u);
        EXPECT_EQUAL(idx.vectorIdx(), 6u);
        EXPECT_EQUAL(idx.offset(), 1000u);
        EXPECT_EQUAL(idx.idx(), 0x6000003e8ul);
    }
    {
        Index64 idx(15, 1, 134217727);
        EXPECT_EQUAL(idx.values(), 15u);
        EXPECT_EQUAL(idx.alternative(), 1u);
        EXPECT_EQUAL(idx.vectorIdx(), 31u);
        EXPECT_EQUAL(idx.offset(), 134217727u);
        EXPECT_EQUAL(idx.idx(), 0x1f07fffffful);
    }
    {
        Index64 idx(3087, 1, 0xfffffffful);
        EXPECT_EQUAL(idx.values(), 3087u);
        EXPECT_EQUAL(idx.alternative(), 1u);
        EXPECT_EQUAL(idx.vectorIdx(), (3087u << 1) + 1);
        EXPECT_EQUAL(idx.offset(), 0xfffffffful);
        EXPECT_EQUAL(idx.idx(), 0x181ffffffffful);
    }
    {
        EXPECT_EQUAL(Index64::maxValues(), 4095u);
        EXPECT_EQUAL(Index64::alternativeSize(), 2u);
        EXPECT_EQUAL(Index64::offsetSize(), 0x1ul << 32);
    }
}

void
MultiValueMappingTest::testSimpleSetAndGet()
{
    uint32_t maxValueCount = Index::maxValues() * 2;
    uint32_t numKeys = maxValueCount * 2;
    MvMapping mvm(dummyCommittedDocIdLimit, numKeys);
    EXPECT_EQUAL(mvm.getNumKeys(), numKeys);
    Index idx;

    // insert values
    for (uint32_t key = 0; key < numKeys; ++key) {
        uint32_t valueCount = key / maxValueCount;
        std::vector<uint32_t> values(valueCount, key);
        Histogram needed(Index::maxValues());
        needed[valueCount] = 1;
        if (!mvm.enoughCapacity(needed)) {
            mvm.trimHoldLists(1);
            mvm.performCompaction(needed);
        }
        mvm.set(key, values);
        EXPECT_EQUAL(mvm.getValueCount(key), valueCount);
        idx = mvm._indices[key];
        if (valueCount < Index::maxValues()) {
            EXPECT_EQUAL(idx.values(), valueCount);
        } else {
            EXPECT_EQUAL(idx.values(), Index::maxValues());
        }
    }
    EXPECT_TRUE(!mvm.hasKey(numKeys));

    // check for expected values
    for (uint32_t key = 0; key < numKeys; ++key) {
        uint32_t valueCount = key / maxValueCount;
        EXPECT_EQUAL(mvm.getValueCount(key), valueCount);
        std::vector<uint32_t> buffer(valueCount);
        EXPECT_EQUAL(mvm.get(key, buffer), valueCount);
        EXPECT_TRUE(buffer.size() == valueCount);
        EXPECT_EQUAL(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), key)), valueCount);
        uint32_t value;
        const uint32_t * handle = NULL;
        EXPECT_EQUAL(mvm.get(key, handle), valueCount);
        EXPECT_TRUE(valueCount == 0 ? handle == NULL : handle != NULL);
        for (uint32_t i = 0; i < valueCount; ++i) {
            EXPECT_TRUE(mvm.get(key, i, value));
            EXPECT_EQUAL(value, key);
            EXPECT_TRUE(handle[i] == key);
        }
        EXPECT_TRUE(!mvm.get(key, valueCount, value));
    }

    // reset
    mvm.reset(10);
    EXPECT_TRUE(mvm.getNumKeys() == 10);
    EXPECT_TRUE(!mvm.hasKey(10));
    EXPECT_TRUE(mvm._genHolder.getHeldBytes() == 0);
    for (uint32_t key = 0; key < 10; ++key) {
        EXPECT_TRUE(mvm.getValueCount(key) == 0);
        std::vector<uint32_t> buffer;
        EXPECT_TRUE(mvm.get(key, buffer) == 0);
        EXPECT_TRUE(buffer.size() == 0);
    }

    // add more keys
    for (uint32_t i = 0; i < 5; ++i) {
        uint32_t key;
        mvm.addKey(key);
        EXPECT_TRUE(key == 10 + i);
        EXPECT_TRUE(mvm.getNumKeys() == 11 + i);
    }
}

void
MultiValueMappingTest::testChangingValueCount()
{
    uint32_t numKeys = 10;
    uint32_t maxCount = Index::maxValues() + 1;
    Histogram initCapacity(Index::maxValues());
    for (uint32_t i = 0; i < Index::maxValues(); ++i) {
        initCapacity[i] = numKeys;
    }
    initCapacity[Index::maxValues()] = numKeys * 2;
    MvMapping mvm(dummyCommittedDocIdLimit, numKeys, initCapacity);

    // Increasing the value count for some keys
    for (uint32_t valueCount = 1; valueCount <= maxCount; ++valueCount) {
        uint32_t lastValueCount = valueCount - 1;
        // set values
        for (uint32_t key = 0; key < numKeys; ++key) {
            std::vector<uint32_t> buffer(valueCount, key);
            mvm.set(key, buffer);
        }

        Histogram remaining = mvm.getRemaining();
        if (valueCount < Index::maxValues()) {
            EXPECT_TRUE(remaining[valueCount] == 0);
        } else {
            EXPECT_TRUE(remaining[Index::maxValues()] == numKeys * (maxCount - valueCount));
        }

        if (valueCount < Index::maxValues()) {
            MvMapping::SingleVectorPtr current = mvm.getSingleVector(valueCount, MvMapping::ACTIVE);
            EXPECT_TRUE(current.first->used() == numKeys * (valueCount));
            EXPECT_TRUE(current.first->dead() == 0);

            if (lastValueCount != 0) {
                MvMapping::SingleVectorPtr last = mvm.getSingleVector(lastValueCount, MvMapping::ACTIVE);
                EXPECT_TRUE(last.first->used() == numKeys * (lastValueCount));
                EXPECT_TRUE(last.first->dead() == numKeys * (lastValueCount));
            }
        } else {
            MvMapping::VectorVectorPtr current = mvm.getVectorVector(MvMapping::ACTIVE);
            EXPECT_TRUE(current.first->used() == numKeys * (valueCount - Index::maxValues() + 1));
            EXPECT_TRUE(current.first->dead() == numKeys * (valueCount - Index::maxValues()));
        }

        // check values
        for (uint32_t key = 0; key < numKeys; ++key) {
            std::vector<uint32_t> buffer(valueCount);
            EXPECT_TRUE(mvm.get(key, buffer) == valueCount);
            EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), key)) == valueCount);
        }
    }
}

void
MultiValueMappingTest::checkReaders(MvMapping &mvm,
                                    generation_t mvmGen,
                                    ReaderVector &readers)
{
    for (ReaderVector::iterator iter = readers.begin();
         iter != readers.end(); ) {
        if (iter->_endGen <= mvmGen) {
            for (uint32_t key = 0; key < iter->numKeys(); ++key) {
                Index idx = iter->_indices[key];
                uint32_t valueCount = iter->_expected[key].size();
                if (valueCount < Index::maxValues()) {
                    EXPECT_TRUE(idx.values() == valueCount);
                    for (uint32_t i = idx.offset() * idx.values(), j = 0;
                         i < (idx.offset() + 1) * idx.values() && j < iter->_expected[key].size();
                         ++i, ++j)
                    {
                        EXPECT_TRUE(mvm._singleVectors[idx.vectorIdx()][i] == iter->_expected[key][j]);
                    }
                } else {
                    EXPECT_TRUE(mvm._vectorVectors[idx.alternative()][idx.offset()].size() ==
                               valueCount);
                    EXPECT_TRUE(std::equal(mvm._vectorVectors[idx.alternative()][idx.offset()].begin(),
                                          mvm._vectorVectors[idx.alternative()][idx.offset()].end(),
                                          iter->_expected[key].begin()));
                }
            }
            iter = readers.erase(iter);
        } else {
            ++iter;
        }
    }
}

void
MultiValueMappingTest::testHoldListAndGeneration()
{
    uint32_t numKeys = 10;
    uint32_t maxCount = Index::maxValues() + 1;
    uint32_t maxKeys = numKeys * 2;

    Histogram initCapacity(Index::maxValues());
    for (uint32_t i = 1; i < maxCount; ++i) {
        initCapacity[i] = numKeys; // make enough capacity for 1/2 of the keys
    }
    MvMapping mvm(dummyCommittedDocIdLimit, maxKeys, initCapacity);
    EXPECT_TRUE(mvm.enoughCapacity(initCapacity));

    ReaderVector readers;
    uint32_t safeGen = std::numeric_limits<uint32_t>::max();
    uint32_t readDuration = 2;
    generation_t mvmGen = 0u;

    for (uint32_t valueCount = 1; valueCount < maxCount; ++valueCount) {
        // check and remove readers
        checkReaders(mvm, mvmGen, readers);

        // update safe generation and removeOldGenerations
        safeGen = std::numeric_limits<uint32_t>::max();
        for (ReaderVector::iterator iter = readers.begin(); iter != readers.end(); ++iter) {
            if ((*iter)._startGen < safeGen) {
                safeGen= (*iter)._startGen;
            }
        }
        mvm.trimHoldLists(safeGen);

        // set new values for 1/2 of the keys
        for (uint32_t key = 0; key < numKeys; ++key) {
            std::vector<uint32_t> values(valueCount, valueCount * numKeys + key);
            mvm.set(key, values);
        }
        // check new values
        for (uint32_t key = 0; key < numKeys; ++key) {
            EXPECT_TRUE(mvm.getValueCount(key) == valueCount);
            std::vector<uint32_t> buffer(valueCount);
            EXPECT_TRUE(mvm.get(key, buffer) == valueCount);
            EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), valueCount * numKeys + key)) == valueCount);
        }
        mvm.transferHoldLists(mvmGen);
        ++mvmGen;

        // associate reader with current generation
        IndexVector indices;
        ExpectedVector expected;
        for (uint32_t key = 0; key < numKeys; ++key) {
            indices.push_back(mvm._indices[key]);
            expected.push_back(std::vector<uint32_t>(valueCount, valueCount * numKeys + key));
        }
        readers.push_back(Reader(mvmGen, mvmGen + readDuration,
                                 indices, expected));
        readDuration = (readDuration % 4) + 2;

        // perform compaction
        Histogram needed(Index::maxValues());
        needed[valueCount] = maxKeys;
        EXPECT_TRUE(!mvm.enoughCapacity(needed));
        mvm.performCompaction(needed);

        // set new value for all keys (the associated reader should see the old values)
        for (uint32_t key = 0; key < maxKeys; ++key) {
            std::vector<uint32_t> values(valueCount, valueCount * maxKeys + key);
            mvm.set(key, values);
        }
        // check new values
        for (uint32_t key = 0; key < maxKeys; ++key) {
            EXPECT_TRUE(mvm.getValueCount(key) == valueCount);
            std::vector<uint32_t> buffer(valueCount);
            EXPECT_TRUE(mvm.get(key, buffer) == valueCount);
            EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), valueCount * maxKeys + key)) == valueCount);
        }

        mvm.transferHoldLists(mvmGen);
        ++mvmGen;
    }
    while (!readers.empty()) {
        checkReaders(mvm, mvmGen, readers);
        mvm.transferHoldLists(mvmGen);
        ++mvmGen;
    }
}

void
MultiValueMappingTest::testManualCompaction()
{
    Histogram initCapacity(Index::maxValues());
    uint32_t maxCount = Index::maxValues() + 1;
    for (uint32_t i = 1; i < maxCount; ++i) {
        initCapacity[i] = 1;
    }
    MvMapping mvm(dummyCommittedDocIdLimit, maxCount * 2, initCapacity);
    EXPECT_TRUE(mvm.enoughCapacity(initCapacity));

    // first update pass. use all capacity
    for (uint32_t key = 1; key < maxCount; ++key) {
        std::vector<uint32_t> values(key, key);
        Histogram needed(Index::maxValues());
        needed[key] = 1;
        EXPECT_TRUE(mvm.enoughCapacity(needed));
        mvm.set(key, values);
        EXPECT_TRUE(!mvm.enoughCapacity(needed));
    }
    // second update pass. must perform compaction
    for (uint32_t key = maxCount + 1; key < maxCount * 2; ++key) {
        uint32_t valueCount = key % maxCount;
        std::vector<uint32_t> values(valueCount, key);
        Histogram needed(Index::maxValues());
        needed[valueCount] = 1;
        EXPECT_TRUE(!mvm.enoughCapacity(needed));
        mvm.performCompaction(needed);
        EXPECT_TRUE(mvm.enoughCapacity(needed));
        mvm.set(key, values);
    }
    // check for correct buffer values
    for (uint32_t key = 0; key < maxCount * 2; ++key) {
        uint32_t valueCount = key % maxCount;
        EXPECT_TRUE(mvm.getValueCount(key) == valueCount);
        std::vector<uint32_t> buffer(valueCount);
        EXPECT_TRUE(mvm.get(key, buffer) == valueCount);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), key)) == valueCount);
    }

    // reset
    mvm.reset(maxCount, initCapacity);
    EXPECT_TRUE(mvm.getNumKeys() == maxCount);
    EXPECT_TRUE(mvm.enoughCapacity(initCapacity));

    // new update pass. use all capacity
    for (uint32_t key = 1; key < maxCount; ++key) {
        std::vector<uint32_t> values(key, key);
        Histogram needed(Index::maxValues());
        needed[key] = 1;
        EXPECT_EQUAL(mvm.getValueCount(key), 0u);
        EXPECT_TRUE(mvm.enoughCapacity(needed));
        mvm.set(key, values);
        EXPECT_TRUE(!mvm.enoughCapacity(needed));
    }
}

void
MultiValueMappingTest::testVariousGets()
{
    MvMapping::Histogram initCapacity(Index::maxValues());
    initCapacity[5] = 1;
    initCapacity[Index::maxValues()] = 1;
    MvMapping mvm(dummyCommittedDocIdLimit, 3, initCapacity);
    Index idx;

    mvm.set(1, std::vector<uint32_t>(5, 50));
    mvm.set(2, std::vector<uint32_t>(25, 250));
    EXPECT_TRUE(25 >= Index::maxValues());

    {
        std::vector<uint32_t> buffer(5);
        EXPECT_TRUE(mvm.get(0, &buffer[0], 0) == 0);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)5)) == 0);
    }
    {
        std::vector<uint32_t> buffer(5);
        EXPECT_TRUE(mvm.get(0, &buffer[0], 5) == 0);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)5)) == 0);
    }
    {
        std::vector<uint32_t> buffer(10);
        EXPECT_TRUE(mvm.get(1, &buffer[0], 3) == 5);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)50)) == 3);
    }
    {
        std::vector<uint32_t> buffer(10);
        EXPECT_TRUE(mvm.get(1, &buffer[0], 10) == 5);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)50)) == 5);
    }
    {
        std::vector<uint32_t> buffer(30);
        EXPECT_TRUE(mvm.get(2, &buffer[0], 23) == 25);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)250)) == 23);
    }
    {
        std::vector<uint32_t> buffer(30);
        EXPECT_TRUE(mvm.get(2, &buffer[0], 30) == 25);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)250)) == 25);
    }
}

void
MultiValueMappingTest::testReplace()
{
    MvMapping::Histogram initCapacity(Index::maxValues());
    initCapacity[5] = 1;
    initCapacity[Index::maxValues()] = 1;
    MvMapping mvm(dummyCommittedDocIdLimit, 3, initCapacity);
    Index idx;

    mvm.set(1, std::vector<uint32_t>(5, 50));
    mvm.set(2, std::vector<uint32_t>(25, 100));
    EXPECT_TRUE(25 >= Index::maxValues());

    {
        EXPECT_TRUE(mvm.getValueCount(0) == 0);
        std::vector<uint32_t> replace(5, 50);
        mvm.replace(0, replace);
        EXPECT_TRUE(mvm.getValueCount(0) == 0);
    }
    {
        EXPECT_TRUE(mvm.getValueCount(1) == 5);
        std::vector<uint32_t> buffer(5);
        EXPECT_TRUE(mvm.get(1, buffer) == 5);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)50)) == 5);

        std::vector<uint32_t> replace(5, 55);
        mvm.replace(1, replace);
        EXPECT_TRUE(mvm.getValueCount(1) == 5);
        EXPECT_TRUE(mvm.get(1, buffer) == 5);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)55)) == 5);
    }
    {
        EXPECT_TRUE(mvm.getValueCount(2) == 25);
        std::vector<uint32_t> buffer(25);
        EXPECT_TRUE(mvm.get(2, buffer) == 25);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)100)) == 25);

        std::vector<uint32_t> replace(25, 200);
        mvm.replace(2, replace);
        EXPECT_TRUE(mvm.getValueCount(2) == 25);
        EXPECT_TRUE(mvm.get(2, buffer) == 25);
        EXPECT_TRUE(static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), (uint32_t)200)) == 25);
    }
}

void
MultiValueMappingTest::testMemoryUsage()
{
    uint32_t numKeys = Index::maxValues() + 4;
    MemoryUsage exp;
    exp.incAllocatedBytes(numKeys * sizeof(Index));
    exp.incUsedBytes(numKeys * sizeof(Index));
    uint32_t totalCnt = 0;

    Histogram initCapacity(Index::maxValues());
    for (uint32_t i = 0; i < Index::maxValues(); ++i) {
        initCapacity[i] = 2;
        exp.incAllocatedBytes(i * 2 * sizeof(uint32_t));
    }
    initCapacity[Index::maxValues()] = 12;
    exp.incAllocatedBytes(12 * sizeof(vespalib::Array<uint32_t>)); // due to vector vector

    MvMapping mvm(dummyCommittedDocIdLimit,
                  numKeys, initCapacity, GrowStrategy(numKeys));

    // usage before inserting values
    MemoryUsage usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), totalCnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), uint32_t(0));
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), uint32_t(0));

    // insert values for all keys
    for (uint32_t key = 0; key < numKeys; ++key) {
        uint32_t cnt = key + 1;
        std::vector<uint32_t> values(cnt, key);
        mvm.set(key, values);
        EXPECT_EQUAL(mvm.getValueCount(key), cnt);
        totalCnt += cnt;
        exp.incUsedBytes(cnt * sizeof(uint32_t));
        if (cnt >= Index::maxValues()) {
            exp.incAllocatedBytes(cnt * sizeof(uint32_t));
            exp.incUsedBytes(sizeof(vespalib::Array<uint32_t>)); // due to vector vector
        }
    }

    // usage after inserting values
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), totalCnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), uint32_t(0));
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), uint32_t(0));

    totalCnt = 0;
    // insert new values for all keys making dead bytes
    for (uint32_t key = 0; key < numKeys; ++key) {
        uint32_t cnt = key + 2;
        std::vector<uint32_t> values(cnt, key);
        mvm.set(key, values);
        EXPECT_EQUAL(mvm.getValueCount(key), cnt);
        totalCnt += cnt;
        exp.incUsedBytes(cnt * sizeof(uint32_t));
        if ((cnt - 1) < Index::maxValues()) {
            exp.incDeadBytes((cnt - 1) * sizeof(uint32_t)); // the previous values are marked dead
        } else {
            exp.incAllocatedBytesOnHold((cnt - 1) * sizeof(uint32_t) +
                                        sizeof(vespalib::Array<uint32_t>));
        }
        if (cnt >= Index::maxValues()) {
            exp.incAllocatedBytes(cnt * sizeof(uint32_t));
            exp.incUsedBytes(sizeof(vespalib::Array<uint32_t>)); // due to vector vector
        }
    }

    // usage after inserting new values making dead bytes
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), totalCnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), exp.deadBytes());
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), exp.allocatedBytesOnHold());

    // make sure all internal vectors are put on hold list
    mvm.performCompaction(initCapacity);
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), totalCnt);
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes() - exp.deadBytes() - exp.allocatedBytesOnHold());
    EXPECT_EQUAL(usage.deadBytes(), uint32_t(0));
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), exp.allocatedBytes() - numKeys * sizeof(Index) + exp.allocatedBytesOnHold());
    mvm.transferHoldLists(0);
    mvm.trimHoldLists(1);
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), totalCnt);
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes() - exp.deadBytes() - exp.allocatedBytesOnHold());
    EXPECT_EQUAL(usage.deadBytes(), uint32_t(0));
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), 0u);
}


void
MultiValueMappingTest::testShrink()
{
    uint32_t committedDocIdLimit = dummyCommittedDocIdLimit;
    MvMapping mvm(committedDocIdLimit);
    for (uint32_t i = 0; i < 10; ++i) {
        uint32_t k;
        mvm.addKey(k);
        EXPECT_EQUAL(i, k);
    }
    mvm.transferHoldLists(0);
    mvm.trimHoldLists(1);
    uint32_t shrinkTarget = 4;
    committedDocIdLimit = shrinkTarget;
    mvm.shrinkKeys(shrinkTarget);
    mvm.transferHoldLists(1);
    mvm.trimHoldLists(2);
    EXPECT_EQUAL(shrinkTarget, mvm.getNumKeys());
    EXPECT_EQUAL(shrinkTarget, mvm.getCapacityKeys());
}


void
MultiValueMappingTest::testHoldElem()
{
    uint32_t numKeys = 1;
    MemoryUsage exp;
    exp.incAllocatedBytes(numKeys * sizeof(Index));
    exp.incUsedBytes(numKeys * sizeof(Index));

    Histogram initCapacity(Index::maxValues());
    initCapacity[Index::maxValues()] = 3;
    exp.incAllocatedBytes(3 * sizeof(vespalib::Array<uint32_t>)); // due to vector vector

    MvMapping mvm(dummyCommittedDocIdLimit,
                  numKeys, initCapacity, GrowStrategy(numKeys));

    // usage before inserting values
    MemoryUsage usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), 0u);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), exp.deadBytes());
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), exp.allocatedBytesOnHold());

    uint32_t key = 0;
    uint32_t cnt = Index::maxValues() + 3;
    {
        std::vector<uint32_t> values(cnt, key);
        mvm.set(key, values);
        exp.incAllocatedBytes(cnt * sizeof(uint32_t));
        exp.incUsedBytes(cnt * sizeof(uint32_t) +
                         sizeof(vespalib::Array<uint32_t>));
    }
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), cnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), exp.deadBytes());
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), exp.allocatedBytesOnHold());
    ++cnt;
    {
        std::vector<uint32_t> values(cnt, key);
        mvm.set(key, values);
        exp.incAllocatedBytes(cnt * sizeof(uint32_t));
        exp.incUsedBytes(cnt * sizeof(uint32_t) +
                         sizeof(vespalib::Array<uint32_t>));
        exp.incAllocatedBytesOnHold((cnt - 1) * sizeof(uint32_t) +
                                    sizeof(vespalib::Array<uint32_t>));
    }
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), cnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), exp.deadBytes());
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), exp.allocatedBytesOnHold());
    mvm.transferHoldLists(0);
    mvm.trimHoldLists(1);
    exp.incDeadBytes(sizeof(vespalib::Array<uint32_t>));
    exp.decAllocatedBytes((cnt - 1) * sizeof(uint32_t));
    usage = mvm.getMemoryUsage();
    EXPECT_EQUAL(mvm.getTotalValueCnt(), cnt);
    EXPECT_EQUAL(usage.allocatedBytes(), exp.allocatedBytes());
    EXPECT_EQUAL(usage.usedBytes(), exp.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), exp.deadBytes());
    EXPECT_EQUAL(usage.allocatedBytesOnHold(), 0u);
}

namespace {

void
insertValues(MvMapping &mvm, uint32_t key, uint32_t count)
{
    std::vector<uint32_t> values(count, 13);
    mvm.set(key, values);
}

Histogram
createHistogram(uint32_t numValuesPerValueClass)
{
    Histogram result(Index32::maxValues());
    for (uint32_t i = 0; i <= Index32::maxValues(); ++i) {
        result[i] = numValuesPerValueClass;
    }
    return result;
}

const size_t ADDRESS_LIMIT = 134217728; // Index32::offsetSize()

struct AddressSpaceFixture
{
    MvMapping mvm;
    AddressSpaceFixture()
        : mvm(dummyCommittedDocIdLimit, 20, createHistogram(4), GrowStrategy(20))
    {}
};

}

void
MultiValueMappingTest::requireThatAddressSpaceUsageIsReported()
{
    AddressSpaceFixture f;
    MvMapping &mvm = f.mvm;

    EXPECT_EQUAL(AddressSpace(0, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 1, 1);
    EXPECT_EQUAL(AddressSpace(1, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 2, 2);
    insertValues(mvm, 3, 2);
    EXPECT_EQUAL(AddressSpace(2, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 4, 13);
    insertValues(mvm, 5, 13);
    insertValues(mvm, 6, 13);
    EXPECT_EQUAL(AddressSpace(3, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 7, 14);
    insertValues(mvm, 8, 14);
    insertValues(mvm, 9, 14);
    EXPECT_EQUAL(AddressSpace(3, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 10, 15);
    insertValues(mvm, 11, 16);
    insertValues(mvm, 12, 17);
    insertValues(mvm, 13, 18);
    EXPECT_EQUAL(AddressSpace(4, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
}

void
MultiValueMappingTest::requireThatDeadIsNotAccountedInAddressSpaceUsage()
{
    AddressSpaceFixture f;
    MvMapping &mvm = f.mvm;

    EXPECT_EQUAL(AddressSpace(0, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 1, 3);
    insertValues(mvm, 2, 3);
    insertValues(mvm, 3, 3);
    insertValues(mvm, 4, 3);
    EXPECT_EQUAL(AddressSpace(4, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 1, 4);
    EXPECT_EQUAL(AddressSpace(3, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 2, 5);
    EXPECT_EQUAL(AddressSpace(2, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 10, 15);
    insertValues(mvm, 11, 15);
    insertValues(mvm, 12, 15);
    insertValues(mvm, 13, 15);
    EXPECT_EQUAL(AddressSpace(4, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 10, 14);
    EXPECT_EQUAL(AddressSpace(3, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
    insertValues(mvm, 11, 14);
    EXPECT_EQUAL(AddressSpace(2, ADDRESS_LIMIT), mvm.getAddressSpaceUsage());
}

int
MultiValueMappingTest::Main()
{
    TEST_INIT("multivaluemapping_test");

    testIndex32();
    testIndex64();
    testSimpleSetAndGet();
    testChangingValueCount();
    testHoldListAndGeneration();
    testManualCompaction();
    testVariousGets();
    testReplace();
    testMemoryUsage();
    testShrink();
    testHoldElem();
    TEST_DO(requireThatAddressSpaceUsageIsReported());
    TEST_DO(requireThatDeadIsNotAccountedInAddressSpaceUsage());

    TEST_DONE();
}

}

TEST_APPHOOK(search::MultiValueMappingTest);
