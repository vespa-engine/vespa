// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("enumstore_test");
#include <vespa/vespalib/testkit/testapp.h>
//#define LOG_ENUM_STORE
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <limits>
#include <string>
#include <iostream>

namespace search {

size_t enumStoreAlign(size_t size)
{
    return (size + 15) & -UINT64_C(16);
}

// EnumStoreBase::Index(0,0) is reserved thus 16 bytes are reserved in buffer 0
const uint32_t RESERVED_BYTES = 16u;
typedef EnumStoreT<NumericEntryType<uint32_t> > NumericEnumStore;

class EnumStoreTest : public vespalib::TestApp
{
private:
    typedef EnumStoreT<StringEntryType> StringEnumStore;
    typedef EnumStoreT<NumericEntryType<float> > FloatEnumStore;
    typedef EnumStoreT<NumericEntryType<double> > DoubleEnumStore;

    typedef EnumStoreBase::Index EnumIndex;
    typedef vespalib::GenerationHandler::generation_t generation_t;

    void testIndex();
    void fillDataBuffer(char * data, uint32_t enumValue, uint32_t refCount,
                        const std::string & string);
    void fillDataBuffer(char * data, uint32_t enumValue, uint32_t refCount,
                        uint32_t value);
    void testStringEntry();
    void testNumericEntry();

    template <typename EnumStoreType, typename T>
    void testFloatEnumStore(EnumStoreType & es);
    void testFloatEnumStore();

    void testAddEnum();
    template <typename EnumStoreType>
    void testAddEnum(bool hasPostings);

    template <typename EnumStoreType, typename Dictionary>
    void
    testUniques(const EnumStoreType &ses,
                const std::vector<std::string> &unique);


    void testCompaction();
    template <typename EnumStoreType>
    void testCompaction(bool hasPostings, bool disableReEnumerate);

    void testReset();
    template <typename EnumStoreType>
    void testReset(bool hasPostings);

    void testHoldListAndGeneration();
    void testMemoryUsage();
    void requireThatAddressSpaceUsageIsReported();
    void testBufferLimit();

    // helper methods
    typedef std::vector<std::string> StringVector;
    template <typename T>
    T random(T low, T high);
    std::string getRandomString(uint32_t minLen, uint32_t maxLen);
    StringVector fillRandomStrings(uint32_t numStrings, uint32_t minLen, uint32_t maxLen);
    StringVector sortRandomStrings(StringVector & strings);

    struct StringEntry {
        StringEntry(uint32_t e, uint32_t r, const std::string & s) :
            _enum(e), _refCount(r), _string(s) {}
        uint32_t _enum;
        uint32_t _refCount;
        std::string _string;
    };

    struct Reader {
        typedef StringEnumStore::Index Index;
        typedef std::vector<Index> IndexVector;
        typedef std::vector<StringEntry> ExpectedVector;
        uint32_t _generation;
        IndexVector _indices;
        ExpectedVector _expected;
        Reader(uint32_t generation, const IndexVector & indices,
               const ExpectedVector & expected);
        ~Reader();
    };

    void
    checkReaders(const StringEnumStore &ses,
                 generation_t sesGen,
                 const std::vector<Reader> &readers);

public:
    EnumStoreTest() {}
    int Main() override;
};

EnumStoreTest::Reader::Reader(uint32_t generation, const IndexVector & indices, const ExpectedVector & expected)
    : _generation(generation), _indices(indices), _expected(expected)
{}
EnumStoreTest::Reader::~Reader() { }

void
EnumStoreTest::testIndex()
{
    {
        StringEnumStore::Index idx;
        EXPECT_TRUE( ! idx.valid());
        EXPECT_EQUAL(idx.offset(), 0u);
        EXPECT_TRUE(idx.bufferId() == 0);
    }
    {
        StringEnumStore::Index idx(enumStoreAlign(1000), 0);
        EXPECT_TRUE(idx.offset() == enumStoreAlign(1000));
        EXPECT_TRUE(idx.bufferId() == 0);
    }
    {
        StringEnumStore::Index idx((UINT64_C(1) << 31)- RESERVED_BYTES, 1);
        EXPECT_TRUE(idx.offset() == (UINT64_C(1) << 31) - RESERVED_BYTES);
        EXPECT_TRUE(idx.bufferId() == 1);
    }
    {
        StringEnumStore::Index idx((UINT64_C(1) << 33) - RESERVED_BYTES, 1);
        EXPECT_TRUE(idx.offset() == (UINT64_C(1) << 33) - RESERVED_BYTES);
        EXPECT_TRUE(idx.bufferId() == 1);
    }
    {
        StringEnumStore::Index idx((UINT64_C(1) << 35) - RESERVED_BYTES, 1);
        EXPECT_TRUE(idx.offset() == (UINT64_C(1) << 35) - RESERVED_BYTES);
        EXPECT_TRUE(idx.bufferId() == 1);
    }
    {
        // Change offsets when alignment changes.
        StringEnumStore::Index idx1(48, 0);
        StringEnumStore::Index idx2(80, 0);
        StringEnumStore::Index idx3(48, 0);
        EXPECT_TRUE(!(idx1 == idx2));
        EXPECT_TRUE(idx1 == idx3);
    }
    {
        EXPECT_TRUE(StringEnumStore::Index::numBuffers() == 2);
    }
}

void
EnumStoreTest::fillDataBuffer(char * data, uint32_t enumValue, uint32_t refCount,
                              const std::string & string)
{
    StringEnumStore::insertEntry(data, enumValue, refCount, string.c_str());
}

void
EnumStoreTest::fillDataBuffer(char * data, uint32_t enumValue, uint32_t refCount,
                              uint32_t value)
{
    NumericEnumStore::insertEntry(data, enumValue, refCount, value);
}

void
EnumStoreTest::testStringEntry()
{
    {
        char data[9];
        fillDataBuffer(data, 0, 0, "");
        StringEnumStore::Entry e(data);
        EXPECT_TRUE(StringEnumStore::getEntrySize("") ==
                   StringEnumStore::alignEntrySize(8 + 1));

        EXPECT_TRUE(e.getEnum() == 0);
        EXPECT_TRUE(e.getRefCount() == 0);
        EXPECT_TRUE(strcmp(e.getValue(), "") == 0);

        e.incRefCount();
        EXPECT_TRUE(e.getEnum() == 0);
        EXPECT_TRUE(e.getRefCount() == 1);
        EXPECT_TRUE(strcmp(e.getValue(), "") == 0);
        e.decRefCount();
        EXPECT_TRUE(e.getEnum() == 0);
        EXPECT_TRUE(e.getRefCount() == 0);
        EXPECT_TRUE(strcmp(e.getValue(), "") == 0);
    }
    {
        char data[18];
        fillDataBuffer(data, 10, 5, "enumstore");
        StringEnumStore::Entry e(data);
        EXPECT_TRUE(StringEnumStore::getEntrySize("enumstore") ==
                   StringEnumStore::alignEntrySize(8 + 1 + 9));

        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 5);
        EXPECT_TRUE(strcmp(e.getValue(), "enumstore") == 0);

        e.incRefCount();
        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 6);
        EXPECT_TRUE(strcmp(e.getValue(), "enumstore") == 0);
        e.decRefCount();
        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 5);
        EXPECT_TRUE(strcmp(e.getValue(), "enumstore") == 0);
    }
}

void
EnumStoreTest::testNumericEntry()
{
    {
        char data[12];
        fillDataBuffer(data, 10, 20, 30);
        NumericEnumStore::Entry e(data);
        EXPECT_TRUE(NumericEnumStore::getEntrySize(30) ==
                   NumericEnumStore::alignEntrySize(8 + 4));

        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 20);
        EXPECT_TRUE(e.getValue() == 30);

        e.incRefCount();
        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 21);
        EXPECT_TRUE(e.getValue() == 30);
        e.decRefCount();
        EXPECT_TRUE(e.getEnum() == 10);
        EXPECT_TRUE(e.getRefCount() == 20);
        EXPECT_TRUE(e.getValue() == 30);
    }
}

template <typename EnumStoreType, typename T>
void
EnumStoreTest::testFloatEnumStore(EnumStoreType & es)
{
    EnumIndex idx;

    T a[5] = {-20.5f, -10.5f, -0.5f, 9.5f, 19.5f};
    T b[5] = {-25.5f, -15.5f, -5.5f, 4.5f, 14.5f};

    for (uint32_t i = 0; i < 5; ++i) {
        es.addEnum(a[i], idx);
    }

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(es.findIndex(a[i], idx));
        EXPECT_TRUE(!es.findIndex(b[i], idx));
    }

    es.addEnum(std::numeric_limits<T>::quiet_NaN(), idx);
    EXPECT_TRUE(es.findIndex(std::numeric_limits<T>::quiet_NaN(), idx));
    EXPECT_TRUE(es.findIndex(std::numeric_limits<T>::quiet_NaN(), idx));

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(es.findIndex(a[i], idx));
        EXPECT_TRUE(!es.findIndex(b[i], idx));
    }
}

void
EnumStoreTest::testFloatEnumStore()
{
    {
        FloatEnumStore fes(1000, false);
        testFloatEnumStore<FloatEnumStore, float>(fes);
    }
    {
        DoubleEnumStore des(1000, false);
        testFloatEnumStore<DoubleEnumStore, double>(des);
    }
}

void
EnumStoreTest::testAddEnum()
{
    testAddEnum<StringEnumStore>(false);

    testAddEnum<StringEnumStore>(true);
}

template <typename EnumStoreType>
void
EnumStoreTest::testAddEnum(bool hasPostings)
{
    EnumStoreType ses(100, hasPostings);
    EXPECT_EQUAL(enumStoreAlign(100u) + RESERVED_BYTES,
                 ses.getBuffer(0).capacity());
    EXPECT_EQUAL(RESERVED_BYTES, ses.getBuffer(0).size());
    EXPECT_EQUAL(enumStoreAlign(100u), ses.getBuffer(0).remaining());
    EXPECT_EQUAL(RESERVED_BYTES, ses.getBuffer(0).getDeadElems());

    EnumIndex idx;
    uint64_t offset = ses.getBuffer(0).size();
    std::vector<EnumIndex> indices;
    std::vector<std::string> unique;
    unique.push_back("");
    unique.push_back("add");
    unique.push_back("enumstore");
    unique.push_back("unique");

    for (uint32_t i = 0; i < unique.size(); ++i) {
        ses.addEnum(unique[i].c_str(), idx);
        EXPECT_EQUAL(offset, idx.offset());
        EXPECT_EQUAL(0u, idx.bufferId());
        ses.incRefCount(idx);
        EXPECT_EQUAL(1u, ses.getRefCount(idx));
        indices.push_back(idx);
        offset += EnumStoreType::alignEntrySize(unique[i].size() + 1 + 8);
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
        EXPECT_TRUE(ses.getLastEnum() == i);
    }
    ses.freezeTree();

    for (uint32_t i = 0; i < indices.size(); ++i) {
        uint32_t e = ses.getEnum(indices[i]);
        EXPECT_EQUAL(i, e);
        EXPECT_TRUE(ses.findEnum(unique[i].c_str(), e));
        EXPECT_TRUE(ses.getEnum(datastore::EntryRef(e)) == i);
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
        EXPECT_TRUE(idx == indices[i]);
        EXPECT_EQUAL(1u, ses.getRefCount(indices[i]));
        StringEntryType::Type value = 0;
        EXPECT_TRUE(ses.getValue(indices[i], value));
        EXPECT_TRUE(strcmp(unique[i].c_str(), value) == 0);
    }

    if (hasPostings) {
        testUniques<EnumStoreType, EnumPostingTree>(ses, unique);
    } else {
        testUniques<EnumStoreType, EnumTree>(ses, unique);
    }
}
    
template <typename EnumStoreType, typename Dictionary>
void
EnumStoreTest::testUniques
(const EnumStoreType &ses, const std::vector<std::string> &unique)
{
    const EnumStoreDict<Dictionary> *enumDict =
        dynamic_cast<const EnumStoreDict<Dictionary> *>
        (&ses.getEnumStoreDict());
    assert(enumDict != NULL);
    const Dictionary &dict = enumDict->getDictionary();
    uint32_t i = 0;
    EnumIndex idx;
    for (typename Dictionary::Iterator iter = dict.begin();
         iter.valid(); ++iter, ++i) {
        idx = iter.getKey();
        EXPECT_TRUE(strcmp(unique[i].c_str(), ses.getValue(idx)) == 0);
    }
    EXPECT_EQUAL(static_cast<uint32_t>(unique.size()), i);
}


void
EnumStoreTest::testCompaction()
{
    testCompaction<StringEnumStore>(false, false);
    testCompaction<StringEnumStore>(true, false);
    testCompaction<StringEnumStore>(false, true);
    testCompaction<StringEnumStore>(true, true);
}

template <typename EnumStoreType>
void
EnumStoreTest::testCompaction(bool hasPostings, bool disableReEnumerate)
{
    // entrySize = 15 before alignment
    uint32_t entrySize = EnumStoreType::alignEntrySize(15);
    uint32_t initBufferSize = entrySize * 5;
    EnumStoreType ses(initBufferSize, hasPostings);
    // Note: Sizes of underlying data store buffers are power of 2.
    uint32_t adjustedBufferSize = vespalib::roundUp2inN(initBufferSize) - RESERVED_BYTES;
    EnumIndex idx;
    std::vector<EnumIndex> indices;
    typename EnumStoreType::Type t = "foo";
    std::vector<std::string> uniques;
    uniques.push_back("enum00");
    uniques.push_back("enum01");
    uniques.push_back("enum02");
    uniques.push_back("enum03");
    uniques.push_back("enum04");

    // fill with unique values
    for (uint32_t i = 0; i < 5; ++i) {
        size_t expRemaining = adjustedBufferSize - i * entrySize;
        EXPECT_EQUAL(expRemaining, ses.getRemaining());
        ses.addEnum(uniques[i].c_str(), idx);
        ses.incRefCount(idx);
        EXPECT_TRUE(ses.getRefCount(idx));
        indices.push_back(idx);
    }
    EXPECT_EQUAL(32u, ses.getRemaining());
    EXPECT_EQUAL(32u, ses.getBuffer(0).remaining());
    EXPECT_EQUAL(entrySize * 5 + RESERVED_BYTES, ses.getBuffer(0).size());
    EXPECT_EQUAL(RESERVED_BYTES, ses.getBuffer(0).getDeadElems());
    uint32_t failEntrySize = ses.getEntrySize("enum05");
    EXPECT_EQUAL(16u, failEntrySize);

    // change from enum00 -> enum01
    ses.decRefCount(indices[0]);
    ses.incRefCount(indices[1]);
    indices[0] = indices[1];

    // check correct refcount
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(ses.findIndex(uniques[i].c_str(), idx));
        uint32_t refCount = ses.getRefCount(idx);
        if (i == 0) {
            EXPECT_TRUE(refCount == 0);
        } else if (i == 1) {
            EXPECT_TRUE(refCount == 2);
        } else {
            EXPECT_TRUE(refCount == 1);
        }
    }

    // free unused enums
    ses.freeUnusedEnums(true);
    EXPECT_TRUE(!ses.findIndex("enum00", idx));
    EXPECT_EQUAL(entrySize + RESERVED_BYTES, ses.getBuffer(0).getDeadElems());

    // perform compaction
    if (disableReEnumerate) {
        ses.disableReEnumerate();
    }
    EXPECT_TRUE(ses.performCompaction(3 * entrySize));
    if (disableReEnumerate) {
        ses.enableReEnumerate();
    }
    EXPECT_TRUE(ses.getRemaining() >= 3 * entrySize);
    EXPECT_TRUE(ses.getBuffer(1).remaining() >= 3 * entrySize);
    EXPECT_TRUE(ses.getBuffer(1).size() == entrySize * 4);
    EXPECT_TRUE(ses.getBuffer(1).getDeadElems() == 0);

    EXPECT_EQUAL((disableReEnumerate ? 4u : 3u), ses.getLastEnum());

    // add new unique strings
    ses.addEnum("enum05", idx);
    EXPECT_EQUAL((disableReEnumerate ? 5u : 4u), ses.getEnum(idx));
    ses.addEnum("enum06", idx);
    EXPECT_EQUAL((disableReEnumerate ? 6u : 5u), ses.getEnum(idx));
    ses.addEnum("enum00", idx);
    EXPECT_EQUAL((disableReEnumerate ? 7u : 6u), ses.getEnum(idx));

    EXPECT_EQUAL((disableReEnumerate ? 7u : 6u), ses.getLastEnum());

    // compare old and new indices
    for (uint32_t i = 0; i < indices.size(); ++i) {
        EXPECT_TRUE(ses.getCurrentIndex(indices[i], idx));
        EXPECT_TRUE(indices[i].bufferId() == 0);
        EXPECT_TRUE(idx.bufferId() == 1);
        EXPECT_TRUE(ses.getValue(indices[i], t));
        typename EnumStoreType::Type s = "bar";
        EXPECT_TRUE(ses.getValue(idx, s));
        EXPECT_TRUE(strcmp(t, s) == 0);
    }
    // EnumIndex(0,0) is reserved so we have 4 bytes extra at the start of buffer 0
    EXPECT_TRUE(ses.getCurrentIndex(indices[0], idx));
    EXPECT_EQUAL(entrySize + RESERVED_BYTES, indices[0].offset());
    EXPECT_EQUAL(0u, idx.offset());
    EXPECT_TRUE(ses.getCurrentIndex(indices[1], idx));
    EXPECT_EQUAL(entrySize + RESERVED_BYTES, indices[1].offset());
    EXPECT_EQUAL(0u, idx.offset());
    EXPECT_TRUE(ses.getCurrentIndex(indices[2], idx));
    EXPECT_EQUAL(2 * entrySize + RESERVED_BYTES, indices[2].offset());
    EXPECT_EQUAL(entrySize, idx.offset());
    EXPECT_TRUE(ses.getCurrentIndex(indices[3], idx));
    EXPECT_EQUAL(3 * entrySize + RESERVED_BYTES, indices[3].offset());
    EXPECT_EQUAL(2 * entrySize, idx.offset());
    EXPECT_TRUE(ses.getCurrentIndex(indices[4], idx));
    EXPECT_EQUAL(4 * entrySize + RESERVED_BYTES, indices[4].offset());
    EXPECT_EQUAL(3 * entrySize, idx.offset());
}

void
EnumStoreTest::testReset()
{
    testReset<StringEnumStore>(false);

    testReset<StringEnumStore>(true);
}

template <typename EnumStoreType>
void
EnumStoreTest::testReset(bool hasPostings)
{
    uint32_t numUniques = 10000;
    srand(123456789);
    StringVector rndStrings = fillRandomStrings(numUniques, 10, 15);
    EXPECT_EQUAL(rndStrings.size(), size_t(numUniques));
    StringVector uniques = sortRandomStrings(rndStrings);
    EXPECT_EQUAL(uniques.size(), size_t(numUniques));
    // max entrySize = 25 before alignment
    uint32_t maxEntrySize = EnumStoreType::alignEntrySize(8 + 1 + 16);
    EnumStoreType ses(numUniques * maxEntrySize, hasPostings);
    EnumIndex idx;

    uint32_t cnt = 0;
    // add new unique strings
    for (StringVector::reverse_iterator iter = uniques.rbegin(); iter != uniques.rend(); ++iter) {
        ses.addEnum(iter->c_str(), idx);
        EXPECT_EQUAL(ses.getNumUniques(), ++cnt);
    }

    // check for unique strings
    for (StringVector::iterator iter = uniques.begin(); iter != uniques.end(); ++iter) {
        EXPECT_TRUE(ses.findIndex(iter->c_str(), idx));
    }

    EXPECT_EQUAL(ses.getNumUniques(), numUniques);
    if (hasPostings) {
        testUniques<EnumStoreType, EnumPostingTree>(ses, uniques);
    } else {
        testUniques<EnumStoreType, EnumTree>(ses, uniques);
    }

    rndStrings = fillRandomStrings(numUniques, 15, 20);
    StringVector newUniques = sortRandomStrings(rndStrings);

    typename EnumStoreType::Builder builder;
    for (StringVector::iterator iter = newUniques.begin(); iter != newUniques.end(); ++iter) {
        builder.insert(iter->c_str());
    }

    ses.reset(builder);
    // Note: Sizes of underlying data store buffers are power of 2.
    EXPECT_EQUAL(524288u, ses.getCapacity());
    EXPECT_EQUAL(204272u, ses.getRemaining());

    // check for old unique strings
    for (StringVector::iterator iter = uniques.begin(); iter != uniques.end(); ++iter) {
        EXPECT_TRUE(!ses.findIndex(iter->c_str(), idx));
    }

    // check for new unique strings
    for (StringVector::iterator iter = newUniques.begin(); iter != newUniques.end(); ++iter) {
        EXPECT_TRUE(ses.findIndex(iter->c_str(), idx));
    }

    EXPECT_EQUAL(ses.getNumUniques(), numUniques);
    if (hasPostings) {
        testUniques<EnumStoreType, EnumPostingTree>(ses, newUniques);
    } else {
        testUniques<EnumStoreType, EnumTree>(ses, newUniques);
    }
}

void
EnumStoreTest::testHoldListAndGeneration()
{
    uint32_t entrySize = StringEnumStore::alignEntrySize(8 + 1 + 6);
    StringEnumStore ses(100 * entrySize, false);
    StringEnumStore::Index idx;
    StringVector uniques;
    generation_t sesGen = 0u;
    uniques.reserve(100);
    for (uint32_t i = 0; i < 100; ++i) {
        char tmp[16];
        sprintf(tmp, i < 10 ? "enum0%u" : "enum%u", i);
        uniques.push_back(tmp);
    }
    StringVector newUniques;
    newUniques.reserve(100);
    for (uint32_t i = 0; i < 100; ++i) {
        char tmp[16];
        sprintf(tmp, i < 10 ? "unique0%u" : "unique%u", i);
        newUniques.push_back(tmp);
    }
    uint32_t generation = 0;
    std::vector<Reader> readers;

    // insert first batch of unique strings
    for (uint32_t i = 0; i < 100; ++i) {
        ses.addEnum(uniques[i].c_str(), idx);
        ses.incRefCount(idx);
        EXPECT_TRUE(ses.getRefCount(idx));

        // associate readers
        if (i % 10 == 9) {
            Reader::IndexVector indices;
            Reader::ExpectedVector expected;
            for (uint32_t j = i - 9; j <= i; ++j) {
                EXPECT_TRUE(ses.findIndex(uniques[j].c_str(), idx));
                indices.push_back(idx);
                StringEnumStore::Entry entry = ses.getEntry(idx);
                EXPECT_TRUE(entry.getEnum() == j);
                EXPECT_TRUE(entry.getRefCount() == 1);
                EXPECT_TRUE(strcmp(entry.getValue(), uniques[j].c_str()) == 0);
                expected.push_back(StringEntry(entry.getEnum(), entry.getRefCount(),
                                               std::string(entry.getValue())));
            }
            EXPECT_TRUE(indices.size() == 10);
            EXPECT_TRUE(expected.size() == 10);
            sesGen = generation++;
            readers.push_back(Reader(sesGen, indices, expected));
            checkReaders(ses, sesGen, readers);
        }
    }

    // Note: Sizes of underlying data store buffers are power of 2.
    EXPECT_EQUAL(432u, ses.getRemaining());
    EXPECT_EQUAL(RESERVED_BYTES, ses.getBuffer(0).getDeadElems());

    // remove all uniques
    for (uint32_t i = 0; i < 100; ++i) {
        EXPECT_TRUE(ses.findIndex(uniques[i].c_str(), idx));
        ses.decRefCount(idx);
        EXPECT_EQUAL(0u, ses.getRefCount(idx));
    }
    ses.freeUnusedEnums(true);
    EXPECT_EQUAL(100 * entrySize + RESERVED_BYTES, ses.getBuffer(0).getDeadElems());

    // perform compaction
    uint32_t newEntrySize = StringEnumStore::alignEntrySize(8 + 1 + 8);
    EXPECT_TRUE(ses.performCompaction(5 * newEntrySize));

    // check readers again
    checkReaders(ses, sesGen, readers);

    // fill up buffer
    uint32_t i = 0;
    while (ses.getRemaining() >= newEntrySize) {
        //LOG(info, "fill: %s", newUniques[i].c_str());
        ses.addEnum(newUniques[i++].c_str(), idx);
        ses.incRefCount(idx);
        EXPECT_TRUE(ses.getRefCount(idx));
    }
    EXPECT_LESS(ses.getRemaining(), newEntrySize);
    // buffer on hold list
    EXPECT_TRUE(!ses.performCompaction(5 * newEntrySize));

    checkReaders(ses, sesGen, readers);
    ses.transferHoldLists(sesGen);
    ses.trimHoldLists(sesGen + 1);

    // buffer no longer on hold list
    EXPECT_LESS(ses.getRemaining(), newEntrySize);
    EXPECT_TRUE(ses.performCompaction(5 * newEntrySize));
    EXPECT_TRUE(ses.getRemaining() >= 5 * newEntrySize);
}

void
EnumStoreTest::testMemoryUsage()
{
    StringEnumStore ses(200, false);
    StringEnumStore::Index idx;
    uint32_t num = 8;
    std::vector<StringEnumStore::Index> indices;
    std::vector<std::string> uniques;
    for (uint32_t i = 0; i < num; ++i) {
        std::stringstream ss;
        ss << "enum" << i;
        uniques.push_back(ss.str());
    }
    generation_t sesGen = 0u;
    uint32_t entrySize = StringEnumStore::alignEntrySize(8 + 1 + 5); // enum(4) + refcount(4) + 1(\0) + strlen("enumx")

    // usage before inserting enums
    MemoryUsage usage = ses.getMemoryUsage();
    EXPECT_EQUAL(ses.getNumUniques(), uint32_t(0));
    // Note: Sizes of underlying data store buffers are power of 2.
    EXPECT_EQUAL(vespalib::roundUp2inN(enumStoreAlign(200u) + RESERVED_BYTES), usage.allocatedBytes());
    EXPECT_EQUAL(RESERVED_BYTES, usage.usedBytes());
    EXPECT_EQUAL(RESERVED_BYTES, usage.deadBytes());
    EXPECT_EQUAL(0u, usage.allocatedBytesOnHold());

    for (uint32_t i = 0; i < num; ++i) {
        ses.addEnum(uniques[i].c_str(), idx);
        indices.push_back(idx);
        ses.incRefCount(idx);
        EXPECT_TRUE(ses.getRefCount(idx));
    }

    // usage after inserting enums
    usage = ses.getMemoryUsage();
    EXPECT_EQUAL(ses.getNumUniques(), num);
    // Note: Sizes of underlying data store buffers are power of 2.
    EXPECT_EQUAL(vespalib::roundUp2inN(enumStoreAlign(200u) + RESERVED_BYTES), usage.allocatedBytes());
    EXPECT_EQUAL(num * entrySize + RESERVED_BYTES, usage.usedBytes());
    EXPECT_EQUAL(RESERVED_BYTES, usage.deadBytes());
    EXPECT_EQUAL(0u, usage.allocatedBytesOnHold());

    // assign new enum for num / 2 of indices
    for (uint32_t i = 0; i < num / 2; ++i) {
        ses.decRefCount(indices[i]);
        EXPECT_TRUE(ses.findIndex(uniques.back().c_str(), idx));
        ses.incRefCount(idx);
        indices[i] = idx;
    }
    ses.freeUnusedEnums(true);

    // usage after removing enums
    usage = ses.getMemoryUsage();
    EXPECT_EQUAL(ses.getNumUniques(), num / 2);
    // Note: Sizes of underlying data store buffers are power of 2.
    EXPECT_EQUAL(vespalib::roundUp2inN(enumStoreAlign(200u) + RESERVED_BYTES), usage.allocatedBytes());
    EXPECT_EQUAL(num * entrySize + RESERVED_BYTES, usage.usedBytes());
    EXPECT_EQUAL((num / 2) * entrySize + RESERVED_BYTES, usage.deadBytes());
    EXPECT_EQUAL(0u, usage.allocatedBytesOnHold());

    ses.performCompaction(400);

    // usage after compaction
    MemoryUsage usage2 = ses.getMemoryUsage();
    EXPECT_EQUAL(ses.getNumUniques(), num / 2);
    EXPECT_EQUAL(usage.usedBytes() + (num / 2) * entrySize, usage2.usedBytes());
    EXPECT_EQUAL(usage.deadBytes(), usage2.deadBytes());
    EXPECT_EQUAL(usage.usedBytes() - usage.deadBytes(), usage2.allocatedBytesOnHold());

    ses.transferHoldLists(sesGen);
    ses.trimHoldLists(sesGen + 1);

    // usage after hold list trimming
    MemoryUsage usage3 = ses.getMemoryUsage();
    EXPECT_EQUAL((num / 2) * entrySize, usage3.usedBytes());
    EXPECT_EQUAL(0u, usage3.deadBytes());
    EXPECT_EQUAL(0u, usage3.allocatedBytesOnHold());
}

namespace {

NumericEnumStore::Index
addEnum(NumericEnumStore &store, uint32_t value)
{
    NumericEnumStore::Index result;
    store.addEnum(value, result);
    store.incRefCount(result);
    return result;
}

void
decRefCount(NumericEnumStore &store, NumericEnumStore::Index idx)
{
    store.decRefCount(idx);
    store.freeUnusedEnums(false);
}

}

void
EnumStoreTest::requireThatAddressSpaceUsageIsReported()
{
    const size_t ADDRESS_LIMIT = 34359738368; // NumericEnumStore::DataStoreType::RefType::offsetSize()
    NumericEnumStore store(200, false);

    EXPECT_EQUAL(AddressSpace(16, 16, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    NumericEnumStore::Index idx1 = addEnum(store, 10);
    EXPECT_EQUAL(AddressSpace(32, 16, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    NumericEnumStore::Index idx2 = addEnum(store, 20);
    EXPECT_EQUAL(AddressSpace(48, 16, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    decRefCount(store, idx1);
    EXPECT_EQUAL(AddressSpace(48, 32, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    decRefCount(store, idx2);
    EXPECT_EQUAL(AddressSpace(48, 48, ADDRESS_LIMIT), store.getAddressSpaceUsage());
}

size_t
digits(size_t num)
{
    size_t digits = 1;
    while (num / 10 > 0) {
        num /= 10;
        digits++;
    }
    return digits;
}

void
EnumStoreTest::testBufferLimit()
{
    size_t enumSize = StringEnumStore::Index::offsetSize();
    StringEnumStore es(enumSize, false);

    size_t strLen = 65536;
    char str[strLen + 1];
    for (size_t i = 0; i < strLen; ++i) {
        str[i] = 'X';
    }
    str[strLen] = 0;

    size_t entrySize = StringEnumStore::getEntrySize(str);
    size_t numUniques = enumSize / entrySize;
    size_t uniqDigits = digits(numUniques);

    EnumIndex idx;
    EnumIndex lastIdx;
    for (size_t i = 0; i < numUniques; ++i) {
        sprintf(str, "%0*zu", (int)uniqDigits, i);
        str[uniqDigits] = 'X';
        es.addEnum(str, idx);
        if (i % (numUniques / 32) == 1) {
            EXPECT_TRUE(idx.offset() > lastIdx.offset());
            EXPECT_EQUAL(i + 1, es.getNumUniques());
            std::cout << "idx.offset(" << idx.offset() << "), str(" << std::string(str, uniqDigits) << ")" << std::endl;
        }
        lastIdx = idx;
    }
    EXPECT_EQUAL(idx.offset(), lastIdx.offset());
    EXPECT_EQUAL(numUniques, es.getNumUniques());
    std::cout << "idx.offset(" << idx.offset() << "), str(" << std::string(str, uniqDigits) << ")" << std::endl;
}

template <typename T>
T
EnumStoreTest::random(T low, T high)
{
    return (rand() % (high - low)) + low;
}

std::string
EnumStoreTest::getRandomString(uint32_t minLen, uint32_t maxLen)
{
    uint32_t len = random(minLen, maxLen);
    std::string retval;
    for (uint32_t i = 0; i < len; ++i) {
        char c = random('a', 'z');
        retval.push_back(c);
    }
    return retval;
}

EnumStoreTest::StringVector
EnumStoreTest::fillRandomStrings(uint32_t numStrings, uint32_t minLen, uint32_t maxLen)
{
    StringVector retval;
    retval.reserve(numStrings);
    for (uint32_t i = 0; i < numStrings; ++i) {
        retval.push_back(getRandomString(minLen, maxLen));
    }
    return retval;
}

EnumStoreTest::StringVector
EnumStoreTest::sortRandomStrings(StringVector & strings)
{
    std::sort(strings.begin(), strings.end());
    std::vector<std::string> retval;
    retval.reserve(strings.size());
    std::vector<std::string>::iterator pos = std::unique(strings.begin(), strings.end());
    std::copy(strings.begin(), pos, std::back_inserter(retval));
    return retval;
}

void
EnumStoreTest::checkReaders(const StringEnumStore & ses,
                            generation_t sesGen,
                            const std::vector<Reader> & readers)
{
    (void) sesGen;
    //uint32_t refCount = 1000;
    StringEnumStore::Type t = "";
    for (uint32_t i = 0; i < readers.size(); ++i) {
        const Reader & r = readers[i];
        for (uint32_t j = 0; j < r._indices.size(); ++j) {
            EXPECT_EQUAL(r._expected[j]._enum, ses.getEnum(r._indices[j]));
            EXPECT_TRUE(ses.getValue(r._indices[j], t));
            EXPECT_TRUE(r._expected[j]._string == std::string(t));
        }
    }
}


int
EnumStoreTest::Main()
{
    TEST_INIT("enumstore_test");

    testIndex();
    testStringEntry();
    testNumericEntry();
    testFloatEnumStore();
    testAddEnum();
    testCompaction();
    testReset();
    testHoldListAndGeneration();
    testMemoryUsage();
    TEST_DO(requireThatAddressSpaceUsageIsReported());
    if (_argc > 1) {
        testBufferLimit(); // large test with 8 GB buffer
    }

    TEST_DONE();
}
}


TEST_APPHOOK(search::EnumStoreTest);
