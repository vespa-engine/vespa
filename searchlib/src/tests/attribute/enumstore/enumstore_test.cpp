// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>
#include <limits>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("enumstore_test");

namespace search {

using DoubleEnumStore = EnumStoreT<double>;
using EnumIndex = IEnumStore::Index;
using FloatEnumStore = EnumStoreT<float>;
using NumericEnumStore = EnumStoreT<int32_t>;
using StringEnumStore = EnumStoreT<const char*>;
using StringVector = std::vector<std::string>;
using generation_t = vespalib::GenerationHandler::generation_t;

struct StringEntry {
    uint32_t _refCount;
    std::string _string;
    StringEntry(uint32_t refCount, const std::string& str)
        : _refCount(refCount), _string(str) {}
};

struct Reader {
    typedef StringEnumStore::Index Index;
    typedef std::vector<Index> IndexVector;
    typedef std::vector<StringEntry> ExpectedVector;
    uint32_t _generation;
    IndexVector _indices;
    ExpectedVector _expected;
    Reader(uint32_t generation, const IndexVector& indices,
           const ExpectedVector& expected);
    ~Reader();
};

Reader::Reader(uint32_t generation, const IndexVector& indices, const ExpectedVector& expected)
    : _generation(generation), _indices(indices), _expected(expected)
{}

Reader::~Reader() = default;


void
checkReaders(const StringEnumStore& ses,
             const std::vector<Reader>& readers)
{
    const char* t = "";
    for (uint32_t i = 0; i < readers.size(); ++i) {
        const Reader& r = readers[i];
        for (uint32_t j = 0; j < r._indices.size(); ++j) {
            EXPECT_TRUE(ses.getValue(r._indices[j], t));
            EXPECT_TRUE(r._expected[j]._string == std::string(t));
        }
    }
}

template <typename EnumStoreT>
class FloatEnumStoreTest : public ::testing::Test {
public:
    EnumStoreT es;
    FloatEnumStoreTest()
        : es(false)
    {}
};

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

using FloatEnumStoreTestTypes = ::testing::Types<FloatEnumStore, DoubleEnumStore>;
TYPED_TEST_CASE(FloatEnumStoreTest, FloatEnumStoreTestTypes);

TYPED_TEST(FloatEnumStoreTest, numbers_can_be_inserted_and_retrieved)
{
    using EntryType = typename TypeParam::EntryType;
    EnumIndex idx;

    EntryType a[5] = {-20.5f, -10.5f, -0.5f, 9.5f, 19.5f};
    EntryType b[5] = {-25.5f, -15.5f, -5.5f, 4.5f, 14.5f};

    for (uint32_t i = 0; i < 5; ++i) {
        this->es.insert(a[i]);
    }

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(this->es.findIndex(a[i], idx));
        EXPECT_TRUE(!this->es.findIndex(b[i], idx));
    }

    this->es.insert(std::numeric_limits<EntryType>::quiet_NaN());
    EXPECT_TRUE(this->es.findIndex(std::numeric_limits<EntryType>::quiet_NaN(), idx));
    EXPECT_TRUE(this->es.findIndex(std::numeric_limits<EntryType>::quiet_NaN(), idx));

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(this->es.findIndex(a[i], idx));
        EXPECT_TRUE(!this->es.findIndex(b[i], idx));
    }
}

#pragma GCC diagnostic pop

TEST(EnumStoreTest, test_find_folded_on_string_enum_store)
{
    StringEnumStore ses(false);
    std::vector<EnumIndex> indices;
    std::vector<std::string> unique({"", "one", "two", "TWO", "Two", "three"});
    for (std::string &str : unique) {
        EnumIndex idx = ses.insert(str.c_str());
        indices.push_back(idx);
        EXPECT_EQ(1u, ses.getRefCount(idx));
    }
    ses.freezeTree();
    for (uint32_t i = 0; i < indices.size(); ++i) {
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
    }
    EXPECT_EQ(1u, ses.findFoldedEnums("").size());
    EXPECT_EQ(0u, ses.findFoldedEnums("foo").size());
    EXPECT_EQ(1u, ses.findFoldedEnums("one").size());
    EXPECT_EQ(3u, ses.findFoldedEnums("two").size());
    EXPECT_EQ(3u, ses.findFoldedEnums("TWO").size());
    EXPECT_EQ(3u, ses.findFoldedEnums("tWo").size());
    const auto v = ses.findFoldedEnums("Two");
    EXPECT_EQ(std::string("TWO"), ses.getValue(v[0]));
    EXPECT_EQ(std::string("Two"), ses.getValue(v[1]));
    EXPECT_EQ(std::string("two"), ses.getValue(v[2]));
    EXPECT_EQ(1u, ses.findFoldedEnums("three").size());
}

template <typename DictionaryT>
void
testUniques(const StringEnumStore& ses, const std::vector<std::string>& unique)
{
    const auto* enumDict = dynamic_cast<const EnumStoreDictionary<DictionaryT>*>(&ses.getEnumStoreDict());
    assert(enumDict != nullptr);
    const DictionaryT& dict = enumDict->getDictionary();
    uint32_t i = 0;
    EnumIndex idx;
    for (typename DictionaryT::Iterator iter = dict.begin();
         iter.valid(); ++iter, ++i) {
        idx = iter.getKey();
        EXPECT_TRUE(strcmp(unique[i].c_str(), ses.getValue(idx)) == 0);
    }
    EXPECT_EQ(static_cast<uint32_t>(unique.size()), i);
}

class StringEnumStoreTest : public ::testing::Test {
public:
    void testInsert(bool hasPostings);
};

void
StringEnumStoreTest::testInsert(bool hasPostings)
{
    StringEnumStore ses(hasPostings);

    std::vector<EnumIndex> indices;
    std::vector<std::string> unique;
    unique.push_back("");
    unique.push_back("add");
    unique.push_back("enumstore");
    unique.push_back("unique");

    for (uint32_t i = 0; i < unique.size(); ++i) {
        EnumIndex idx = ses.insert(unique[i].c_str());
        EXPECT_EQ(1u, ses.getRefCount(idx));
        indices.push_back(idx);
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
    }
    ses.freezeTree();

    for (uint32_t i = 0; i < indices.size(); ++i) {
        uint32_t e = 0;
        EXPECT_TRUE(ses.findEnum(unique[i].c_str(), e));
        EXPECT_EQ(1u, ses.findFoldedEnums(unique[i].c_str()).size());
        EXPECT_EQ(e, ses.findFoldedEnums(unique[i].c_str())[0]);
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(unique[i].c_str(), idx));
        EXPECT_TRUE(idx == indices[i]);
        EXPECT_EQ(1u, ses.getRefCount(indices[i]));
        const char* value = nullptr;
        EXPECT_TRUE(ses.getValue(indices[i], value));
        EXPECT_TRUE(strcmp(unique[i].c_str(), value) == 0);
    }

    if (hasPostings) {
        testUniques<EnumPostingTree>(ses, unique);
    } else {
        testUniques<EnumTree>(ses, unique);
    }
}

TEST_F(StringEnumStoreTest, test_insert_on_store_without_posting_lists)
{
    testInsert(false);
}

TEST_F(StringEnumStoreTest, test_insert_on_store_with_posting_lists)
{
    testInsert(true);
}

TEST(EnumStoreTest, test_hold_lists_and_generation)
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
                EXPECT_EQ(1u, ref_count);
                EXPECT_EQ(uniques[j], value);
                expected.emplace_back(ref_count, value);
            }
            EXPECT_TRUE(indices.size() == 10);
            EXPECT_TRUE(expected.size() == 10);
            sesGen = generation++;
            readers.push_back(Reader(sesGen, indices, expected));
            checkReaders(ses, readers);
        }
    }

    // remove all uniques
    auto updater = ses.make_batch_updater();
    for (uint32_t i = 0; i < 100; ++i) {
        EnumIndex idx;
        EXPECT_TRUE(ses.findIndex(uniques[i].c_str(), idx));
        updater.dec_ref_count(idx);
        EXPECT_EQ(0u, ses.getRefCount(idx));
    }
    updater.commit();

    // check readers again
    checkReaders(ses, readers);

    ses.transferHoldLists(sesGen);
    ses.trimHoldLists(sesGen + 1);
}

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

TEST(EnumStoreTest, address_space_usage_is_reported)
{
    const size_t ADDRESS_LIMIT = 4290772994; // Max allocated elements in un-allocated buffers + allocated elements in allocated buffers.
    NumericEnumStore store(false);

    using vespalib::AddressSpace;
    EXPECT_EQ(AddressSpace(1, 1, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    EnumIndex idx1 = store.insert(10);
    EXPECT_EQ(AddressSpace(2, 1, ADDRESS_LIMIT), store.getAddressSpaceUsage());
    EnumIndex idx2 = store.insert(20);
    // Address limit increases because buffer is re-sized.
    EXPECT_EQ(AddressSpace(3, 1, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
    dec_ref_count(store, idx1);
    EXPECT_EQ(AddressSpace(3, 2, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
    dec_ref_count(store, idx2);
    EXPECT_EQ(AddressSpace(3, 3, ADDRESS_LIMIT + 2), store.getAddressSpaceUsage());
}

class BatchUpdaterTest : public ::testing::Test {
public:
    NumericEnumStore store;
    EnumIndex i3;
    EnumIndex i5;

    BatchUpdaterTest()
        : store(false),
          i3(),
          i5()
    {
        auto updater = store.make_batch_updater();
        i3 = updater.insert(3);
        i5 = updater.insert(5);
        updater.inc_ref_count(i3);
        updater.inc_ref_count(i5);
        updater.inc_ref_count(i5);
        updater.commit();
        expect_value_in_store(3, 1, i3);
        expect_value_in_store(5, 2, i5);
    }

    void expect_value_in_store(int32_t exp_value, uint32_t exp_ref_count, EnumIndex idx) {
        EnumIndex tmp_idx;
        EXPECT_TRUE(store.findIndex(exp_value, tmp_idx));
        EXPECT_EQ(idx, tmp_idx);
        EXPECT_EQ(exp_value, store.getValue(idx));
        EXPECT_EQ(exp_ref_count, store.getRefCount(idx));
    }

    void expect_value_not_in_store(int32_t value, EnumIndex idx) {
        EnumIndex temp_idx;
        EXPECT_FALSE(store.findIndex(value, idx));
        EXPECT_EQ(0, store.getRefCount(idx));
    }
};

TEST_F(BatchUpdaterTest, ref_counts_can_be_changed)
{
    auto updater = store.make_batch_updater();
    EXPECT_EQ(i3, updater.insert(3));
    updater.inc_ref_count(i3);
    updater.dec_ref_count(i5);
    updater.commit();

    expect_value_in_store(3, 2, i3);
    expect_value_in_store(5, 1, i5);
}

TEST_F(BatchUpdaterTest, new_value_can_be_inserted)
{
    auto updater = store.make_batch_updater();
    EnumIndex i7 = updater.insert(7);
    updater.inc_ref_count(i7);
    updater.commit();

    expect_value_in_store(7, 1, i7);
}

TEST_F(BatchUpdaterTest, value_with_ref_count_zero_is_removed)
{
    auto updater = store.make_batch_updater();
    updater.dec_ref_count(i3);
    updater.commit();

    expect_value_not_in_store(3, i3);
}

TEST_F(BatchUpdaterTest, unused_new_value_is_removed)
{
    auto updater = store.make_batch_updater();
    EnumIndex i7 = updater.insert(7);
    updater.commit();

    expect_value_not_in_store(7, i7);
}

template <typename EnumStoreT>
class LoaderTest : public ::testing::Test {
public:
    using EntryType = typename EnumStoreT::EntryType;
    EnumStoreT store;
    static std::vector<EntryType> values;

    LoaderTest()
        : store(true)
    {}

    void load_values(enumstore::EnumeratedLoaderBase& loader) const {
        loader.load_unique_values(values.data(), values.size() * sizeof(EntryType));
    }

    EnumIndex find_index(size_t values_idx) const {
        EnumIndex result;
        EXPECT_TRUE(store.findIndex(values[values_idx], result));
        return result;
    }

    void set_ref_count(size_t values_idx, uint32_t ref_count, enumstore::EnumeratedPostingsLoader& loader) const {
        EnumIndex idx = find_index(values_idx);
        loader.set_ref_count(idx, ref_count);
    }

    void expect_value_in_store(size_t values_idx, uint32_t exp_ref_count) const {
        EnumIndex idx = find_index(values_idx);
        EXPECT_EQ(exp_ref_count, store.getRefCount(idx));
    }

    void expect_value_not_in_store(size_t values_idx) const {
        EnumIndex idx;
        EXPECT_FALSE(store.findIndex(values[values_idx], idx));
    }

    void expect_values_in_store() {
        expect_value_in_store(0, 1);
        expect_value_in_store(1, 2);
        expect_value_not_in_store(2);
        expect_value_in_store(3, 4);
    }

    void expect_posting_idx(size_t values_idx, uint32_t exp_posting_idx) const {
        auto cmp = store.make_comparator();
        auto itr = store.getPostingDictionary().find(find_index(values_idx), cmp);
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(exp_posting_idx, itr.getData());
    }

};

template <> std::vector<int32_t> LoaderTest<NumericEnumStore>::values{3, 5, 7, 9};
template <> std::vector<float> LoaderTest<FloatEnumStore>::values{3.1, 5.2, 7.3, 9.4};
template <> std::vector<const char *> LoaderTest<StringEnumStore>::values{"aa", "bbb", "ccc", "dd"};

template <>
void
LoaderTest<StringEnumStore>::load_values(enumstore::EnumeratedLoaderBase& loader) const
{
    std::vector<char> raw_values;
    for (auto value : values) {
        for (auto c : std::string(value)) {
            raw_values.push_back(c);
        }
        raw_values.push_back('\0');
    }
    loader.load_unique_values(raw_values.data(), raw_values.size());
}

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

using LoaderTestTypes = ::testing::Types<NumericEnumStore, FloatEnumStore, StringEnumStore>;
TYPED_TEST_CASE(LoaderTest, LoaderTestTypes);

TYPED_TEST(LoaderTest, store_is_instantiated_with_enumerated_loader)
{
    auto loader = this->store.make_enumerated_loader();
    this->load_values(loader);
    loader.allocate_enums_histogram();
    loader.get_enums_histogram()[0] = 1;
    loader.get_enums_histogram()[1] = 2;
    loader.get_enums_histogram()[3] = 4;
    loader.set_ref_counts();

    this->expect_values_in_store();
}

TYPED_TEST(LoaderTest, store_is_instantiated_with_enumerated_postings_loader)
{
    auto loader = this->store.make_enumerated_postings_loader();
    this->load_values(loader);
    this->set_ref_count(0, 1, loader);
    this->set_ref_count(1, 2, loader);
    this->set_ref_count(3, 4, loader);
    loader.free_unused_values();

    this->expect_values_in_store();
}

TYPED_TEST(LoaderTest, store_is_instantiated_with_non_enumerated_loader)
{
    auto loader = this->store.make_non_enumerated_loader();
    loader.insert(this->values[0], 100);
    loader.set_ref_count_for_last_value(1);
    loader.insert(this->values[1], 101);
    loader.set_ref_count_for_last_value(2);
    loader.insert(this->values[3], 103);
    loader.set_ref_count_for_last_value(4);
    loader.build_dictionary();

    this->expect_values_in_store();

    this->expect_posting_idx(0, 100);
    this->expect_posting_idx(1, 101);
    this->expect_posting_idx(3, 103);
}

#pragma GCC diagnostic pop

}

GTEST_MAIN_RUN_ALL_TESTS()
