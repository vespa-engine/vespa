// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <limits>
#include <string>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("enumstore_test");

namespace search {

size_t enumStoreAlign(size_t size)
{
    return (size + 15) & -UINT64_C(16);
}

using NumericEnumStore = EnumStoreT<int32_t>;
using generation_t = vespalib::GenerationHandler::generation_t;

class EnumStoreTest : public vespalib::TestApp
{
private:
    typedef EnumStoreT<const char*> StringEnumStore;
    typedef EnumStoreT<float> FloatEnumStore;
    typedef EnumStoreT<double> DoubleEnumStore;

    typedef IEnumStore::Index EnumIndex;

    template <typename EnumStoreType, typename T>
    void testFloatEnumStore(EnumStoreType & es);
    void testFloatEnumStore();

    void testFindFolded();
    void testInsert();
    template <typename EnumStoreType>
    void testInsert(bool hasPostings);

    template <typename EnumStoreType, typename Dictionary>
    void
    testUniques(const EnumStoreType &ses,
                const std::vector<std::string> &unique);

    void testHoldListAndGeneration();
    void requireThatAddressSpaceUsageIsReported();

    // helper methods
    typedef std::vector<std::string> StringVector;

    struct StringEntry {
        StringEntry(uint32_t r, const std::string & s) :
            _refCount(r), _string(s) {}
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

template <typename EnumStoreType, typename T>
void
EnumStoreTest::testFloatEnumStore(EnumStoreType & es)
{
    EnumIndex idx;

    T a[5] = {-20.5f, -10.5f, -0.5f, 9.5f, 19.5f};
    T b[5] = {-25.5f, -15.5f, -5.5f, 4.5f, 14.5f};

    for (uint32_t i = 0; i < 5; ++i) {
        es.insert(a[i]);
    }

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(es.findIndex(a[i], idx));
        EXPECT_TRUE(!es.findIndex(b[i], idx));
    }

    es.insert(std::numeric_limits<T>::quiet_NaN());
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
        FloatEnumStore fes(false);
        testFloatEnumStore<FloatEnumStore, float>(fes);
    }
    {
        DoubleEnumStore des(false);
        testFloatEnumStore<DoubleEnumStore, double>(des);
    }
}

void
EnumStoreTest::testFindFolded()
{
    StringEnumStore ses(false);
    std::vector<EnumIndex> indices;
    std::vector<std::string> unique({"", "one", "two", "TWO", "Two", "three"});
    for (std::string &str : unique) {
        EnumIndex idx = ses.insert(str.c_str());
        indices.push_back(idx);
        EXPECT_EQUAL(1u, ses.getRefCount(idx));
    }
    ses.freezeTree();
    for (uint32_t i = 0; i < indices.size(); ++i) {
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
    }
    EXPECT_EQUAL(1u, ses.findFoldedEnums("").size());
    EXPECT_EQUAL(0u, ses.findFoldedEnums("foo").size());
    EXPECT_EQUAL(1u, ses.findFoldedEnums("one").size());
    EXPECT_EQUAL(3u, ses.findFoldedEnums("two").size());
    EXPECT_EQUAL(3u, ses.findFoldedEnums("TWO").size());
    EXPECT_EQUAL(3u, ses.findFoldedEnums("tWo").size());
    const auto v = ses.findFoldedEnums("Two");
    EXPECT_EQUAL(std::string("TWO"), ses.getValue(v[0]));
    EXPECT_EQUAL(std::string("Two"), ses.getValue(v[1]));
    EXPECT_EQUAL(std::string("two"), ses.getValue(v[2]));
    EXPECT_EQUAL(1u, ses.findFoldedEnums("three").size());
}

void
EnumStoreTest::testInsert()
{
    testInsert<StringEnumStore>(false);

    testInsert<StringEnumStore>(true);
}

template <typename EnumStoreType>
void
EnumStoreTest::testInsert(bool hasPostings)
{
    EnumStoreType ses(hasPostings);

    std::vector<EnumIndex> indices;
    std::vector<std::string> unique;
    unique.push_back("");
    unique.push_back("add");
    unique.push_back("enumstore");
    unique.push_back("unique");

    for (uint32_t i = 0; i < unique.size(); ++i) {
        EnumIndex idx = ses.insert(unique[i].c_str());
        EXPECT_EQUAL(1u, ses.getRefCount(idx));
        indices.push_back(idx);
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
    }
    ses.freezeTree();

    for (uint32_t i = 0; i < indices.size(); ++i) {
        uint32_t e = 0;
        EXPECT_TRUE(ses.findEnum(unique[i].c_str(), e));
        EXPECT_EQUAL(1u, ses.findFoldedEnums(unique[i].c_str()).size());
        EXPECT_EQUAL(e, ses.findFoldedEnums(unique[i].c_str())[0]);
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
        EXPECT_TRUE(idx == indices[i]);
        EXPECT_EQUAL(1u, ses.getRefCount(indices[i]));
        const char* value = nullptr;
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
    const auto* enumDict = dynamic_cast<const EnumStoreDictionary<Dictionary> *>(&ses.getEnumStoreDict());
    assert(enumDict != nullptr);
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
EnumStoreTest::testHoldListAndGeneration()
{
    StringEnumStore ses(false);
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
        EnumIndex idx = ses.insert(uniques[i].c_str());
        EXPECT_TRUE(ses.getRefCount(idx));

        // associate readers
        if (i % 10 == 9) {
            Reader::IndexVector indices;
            Reader::ExpectedVector expected;
            for (uint32_t j = i - 9; j <= i; ++j) {
                EXPECT_TRUE(ses.findIndex(uniques[j].c_str(), idx));
                indices.push_back(idx);
                uint32_t ref_count = ses.getRefCount(idx);
                std::string value(ses.getValue(idx));
                EXPECT_EQUAL(1u, ref_count);
                EXPECT_EQUAL(uniques[j], value);
                expected.emplace_back(ref_count, value);
            }
            EXPECT_TRUE(indices.size() == 10);
            EXPECT_TRUE(expected.size() == 10);
            sesGen = generation++;
            readers.push_back(Reader(sesGen, indices, expected));
            checkReaders(ses, sesGen, readers);
        }
    }

    // remove all uniques
    auto updater = ses.make_batch_updater();
    for (uint32_t i = 0; i < 100; ++i) {
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(uniques[i].c_str(), idx));
        updater.dec_ref_count(idx);
        EXPECT_EQUAL(0u, ses.getRefCount(idx));
    }
    updater.commit();

    // check readers again
    checkReaders(ses, sesGen, readers);

    ses.transferHoldLists(sesGen);
    ses.trimHoldLists(sesGen + 1);
}

namespace {

void
dec_ref_count(NumericEnumStore& store, NumericEnumStore::Index idx)
{
    auto updater = store.make_batch_updater();
    updater.dec_ref_count(idx);
    updater.commit();

    generation_t gen = 5;
    store.transferHoldLists(gen);
    store.trimHoldLists(gen + 1);
}

}

void
EnumStoreTest::requireThatAddressSpaceUsageIsReported()
{
    const size_t ADDRESS_LIMIT = 4290772994; // Max allocated elements in un-allocated buffers + allocated elements in allocated buffers.
    NumericEnumStore store(false);

    using vespalib::AddressSpace;
    EXPECT_EQUAL(AddressSpace(1, 1, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    EnumIndex idx1 = store.insert(10);
    EXPECT_EQUAL(AddressSpace(2, 1, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    EnumIndex idx2 = store.insert(20);
    // Address limit increases because buffer is re-sized.
    EXPECT_EQUAL(AddressSpace(3, 1, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
    dec_ref_count(store, idx1);
    EXPECT_EQUAL(AddressSpace(3, 2, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
    dec_ref_count(store, idx2);
    EXPECT_EQUAL(AddressSpace(3, 3, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
}

void
EnumStoreTest::checkReaders(const StringEnumStore & ses,
                            generation_t sesGen,
                            const std::vector<Reader> & readers)
{
    (void) sesGen;
    //uint32_t refCount = 1000;
    const char* t = "";
    for (uint32_t i = 0; i < readers.size(); ++i) {
        const Reader & r = readers[i];
        for (uint32_t j = 0; j < r._indices.size(); ++j) {
            EXPECT_TRUE(ses.getValue(r._indices[j], t));
            EXPECT_TRUE(r._expected[j]._string == std::string(t));
        }
    }
}


int
EnumStoreTest::Main()
{
    TEST_INIT("enumstore_test");

    testFloatEnumStore();
    testFindFolded();
    testInsert();
    testHoldListAndGeneration();
    TEST_DO(requireThatAddressSpaceUsageIsReported());

    TEST_DONE();
}
}


TEST_APPHOOK(search::EnumStoreTest);
