// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/make_sort_blob_writer.h>
#include <vespa/searchlib/attribute/string_to_number.h>
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <type_traits>
#include <cinttypes>
#include <vespa/log/log.h>
LOG_SETUP("multilevelsort_test");

using namespace search;
using search::attribute::make_sort_blob_writer;

using Float = FloatingPointAttributeTemplate<float>;
using Double = FloatingPointAttributeTemplate<double>;
using VectorMap = std::map<std::string, AttributeVector::SP >;
using AttributePtr = AttributeVector::SP;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;

class MultilevelSortTest : public ::testing::Test {
public:
    enum AttrType {
        INT8,
        INT16,
        INT32,
        INT64,
        FLOAT,
        DOUBLE,
        STRING,
        RANK,
        DOCID,
        NONE
    };
    struct Spec {
        Spec() : _name("unknown"), _type(NONE), _asc(true) {}
        Spec(const std::string &name, AttrType type) : _name(name), _type(type), _asc(true) {}
        Spec(const std::string &name, AttrType type, bool asc) : _name(name), _type(type), _asc(asc) {}
        std::string _name;
        AttrType _type;
        bool _asc;
    };
private:
    template<typename T>
    static T getRandomValue() {
        T min = std::numeric_limits<T>::min();
        T max = std::numeric_limits<T>::max();
        return static_cast<T>(double(min) + (double(max) - double(min)) * (double(rand()) / double(RAND_MAX)));
    }
    template<typename T>
    static void fill(IntegerAttribute *attr, uint32_t size, uint32_t unique = 0);
    template<typename T>
    static void fill(FloatingPointAttribute *attr, uint32_t size, uint32_t unique = 0);
    static void fill(StringAttribute *attr, uint32_t size, const std::vector<std::string> &values);
    template <typename V>
    int compareTemplate(AttributeVector *vector, uint32_t a, uint32_t b);
    int compare(AttributeVector *vector, AttrType type, uint32_t a, uint32_t b);
    void sortAndCheck(const std::vector<Spec> &spec, uint32_t num,
                      uint32_t unique, const std::vector<std::string> &strValues);
public:
    MultilevelSortTest() { srand(time(nullptr)); }
    void testSort();
};

template<typename T>
void MultilevelSortTest::fill(IntegerAttribute *attr, uint32_t size, uint32_t unique)
{
    ASSERT_TRUE(attr->addDocs(size));
    std::vector<T> values;
    for (uint32_t j = 0; j < unique; ++j) {
        if (j % 2 == 0) {
            values.push_back(std::numeric_limits<T>::min() + static_cast<T>(j));
        } else {
            values.push_back(std::numeric_limits<T>::max() - static_cast<T>(j));
        }
    }
    for (uint32_t i = 0; i < size; ++i) {
        if (unique == 0) {
            attr->update(i, getRandomValue<T>());
        } else {
            uint32_t idx = rand() % values.size();
            attr->update(i, values[idx]);
        }
    }
}

template<typename T>
void MultilevelSortTest::fill(FloatingPointAttribute *attr, uint32_t size, uint32_t unique)
{
    ASSERT_TRUE(attr->addDocs(size));
    std::vector<T> values;
    for (uint32_t j = 0; j < unique; ++j) {
        if (j % 2 == 0) {
            values.push_back(std::numeric_limits<T>::min() + static_cast<T>(j));
        } else {
            values.push_back(std::numeric_limits<T>::max() - static_cast<T>(j));
        }
    }
    for (uint32_t i = 0; i < size; ++i) {
        if (unique == 0) {
            attr->update(i, getRandomValue<T>());
        } else {
            uint32_t idx = rand() % values.size();
            //LOG(info, "fill vector<%s>::doc<%d> = %f (idx=%d)", attr->getName().c_str(), i, values[idx], idx);
            attr->update(i, values[idx]);
        }
    }
}

void
MultilevelSortTest::fill(StringAttribute *attr, uint32_t size, const std::vector<std::string> &values)
{
    ASSERT_TRUE(attr->addDocs(size));
    for (uint32_t i = 0; i < size; ++i) {
        if (values.empty()) {
            uint32_t len = 1 + static_cast<uint32_t>(127 * (((float)rand() / (float)RAND_MAX)));
            std::string value;
            for (uint32_t j = 0; j < len; ++j) {
                char c = 'a' + static_cast<char>(('Z' - 'a') * (((float)rand() / (float)RAND_MAX)));
                value.append(1, c);
            }
            attr->update(i, value.c_str());
        } else {
            uint32_t idx = rand() % values.size();
            //LOG(info, "fill vector<%s>::doc<%d> = %s (idx=%d)", attr->getName().c_str(),
            //    i, values[idx].c_str(), idx);
            attr->update(i, values[idx].c_str());
        }
    }
}

template <typename V>
V get_helper(AttributeVector *vector, uint32_t doc_id) {
    if constexpr (std::is_floating_point_v<V>) {
        return vector->getFloat(doc_id);
    } else {
        return vector->getInt(doc_id);
    }
}

template <typename V>
int
MultilevelSortTest::compareTemplate(AttributeVector *vector, uint32_t a, uint32_t b)
{
    V va;
    V vb;
    va = get_helper<V>(vector, a);
    vb = get_helper<V>(vector, b);
    if (va == vb) {
        return 0;
    } else if (va < vb) {
        return -1;
    }
    return 1;
}

int
MultilevelSortTest::compare(AttributeVector *vector, AttrType type, uint32_t a, uint32_t b)
{
    if (type == INT8) {
        return compareTemplate<int8_t>(vector, a, b);
    } else if (type == INT16) {
        return compareTemplate<int16_t>(vector, a, b);
    } else if (type == INT32) {
        return compareTemplate<int32_t>(vector, a, b);
    } else if (type == INT64) {
        return compareTemplate<int64_t>(vector, a, b);
    } else if (type == FLOAT) {
        return compareTemplate<float>(vector, a, b);
    } else if (type == DOUBLE) {
        return compareTemplate<double>(vector, a, b);
    } else if (type == STRING) {
        StringAttribute *vString = dynamic_cast<StringAttribute*>(vector);
        const char *va = vString->get(a);
        const char *vb = vString->get(b);
        std::string sa(va);
        std::string sb(vb);
        if (sa == sb) {
            return 0;
        } else if (sa < sb) {
            return -1;
        }
        return 1;
    } else {
        assert(false);
        return 0;
    }
}

void
MultilevelSortTest::sortAndCheck(const std::vector<Spec> &specs, uint32_t num,
                                 uint32_t unique, const std::vector<std::string> &strValues)
{
    VectorMap vec;
    // generate attribute vectors
    for (const auto & spec : specs) {
        std::string name = spec._name;
        AttrType type = spec._type;
        if (type == INT8) {
            Config cfg(BasicType::INT8, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<int8_t>(dynamic_cast<IntegerAttribute *>(vec[name].get()), num, unique);
        } else if (type == INT16) {
            Config cfg(BasicType::INT16, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<int16_t>(dynamic_cast<IntegerAttribute *>(vec[name].get()), num, unique);
        } else if (type == INT32) {
            Config cfg(BasicType::INT32, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<int32_t>(dynamic_cast<IntegerAttribute *>(vec[name].get()), num, unique);
        } else if (type == INT64) {
            Config cfg(BasicType::INT64, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<int64_t>(dynamic_cast<IntegerAttribute *>(vec[name].get()), num, unique);
        } else if (type == FLOAT) {
            Config cfg(BasicType::FLOAT, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<float>(dynamic_cast<FloatingPointAttribute *>(vec[name].get()), num, unique);
        } else if (type == DOUBLE) {
            Config cfg(BasicType::DOUBLE, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill<double>(dynamic_cast<FloatingPointAttribute *>(vec[name].get()), num, unique);
        } else if (type == STRING) {
            Config cfg(BasicType::STRING, CollectionType::SINGLE);
            vec[name] = AttributeFactory::createAttribute(name, cfg);
            fill(dynamic_cast<StringAttribute *>(vec[name].get()), num, strValues);
        }
        if (vec[name])
            vec[name]->commit();
    }

    std::vector<RankedHit> hits;
    hits.reserve(num);
    for (uint32_t i = 0; i < num; ++i) {
        hits.emplace_back(i,  getRandomValue<uint32_t>());
    }

    search::uca::UcaConverterFactory ucaFactory;
    FastS_SortSpec sorter("no-metastore", 7, vespalib::Doom::never(), ucaFactory);
    // init sorter with sort data
    for (const auto & spec : specs) {
        AttributeGuard ag;
        if (spec._type == RANK) {
            sorter._vectors.emplace_back(spec._asc ? FastS_SortSpec::ASC_RANK : FastS_SortSpec::DESC_RANK, nullptr, nullptr);
        } else if (spec._type == DOCID) {
            sorter._vectors.emplace_back(spec._asc ? FastS_SortSpec::ASC_DOCID : FastS_SortSpec::DESC_DOCID, nullptr, nullptr);
        } else {
            const search::attribute::IAttributeVector * v = vec[spec._name].get();
            search::common::FieldSortSpec fss(spec._name, spec._asc, {});
            auto sort_blob_writer = make_sort_blob_writer(v, fss);
            sorter._vectors.emplace_back(spec._asc ? FastS_SortSpec::ASC_VECTOR : FastS_SortSpec::DESC_VECTOR, v, std::move(sort_blob_writer));
        }
    }

    vespalib::Timer timer;
    sorter.sortResults(&hits[0], num, num);
    LOG(info, "sort time = %" PRId64 " ms", vespalib::count_ms(timer.elapsed()));

    std::vector<uint32_t> offsets(num + 1, 0);
    auto buf = std::make_unique<char []>(sorter.getSortDataSize(0, num));
    sorter.copySortData(0, num, &offsets[0], buf.get());

    // check results
    for (uint32_t i = 0; i < num - 1; ++i) {
        for (const Spec & spec : specs) {
            int cmp = 0;
            if (spec._type == RANK) {
                if (hits[i].getRank() < hits[i+1].getRank()) {
                    cmp = -1;
                } else if (hits[i].getRank() > hits[i+1].getRank()) {
                    cmp = 1;
                }
            } else if (spec._type == DOCID) {
                if (hits[i].getDocId() < hits[i+1].getDocId()) {
                    cmp = -1;
                } else if (hits[i].getDocId() > hits[i+1].getDocId()) {
                    cmp = 1;
                }
            } else {
                AttributeVector *av = vec[spec._name].get();
                cmp = compare(av, spec._type, hits[i].getDocId(), hits[i+1].getDocId());
            }
            if (spec._asc) {
                EXPECT_TRUE(cmp <= 0);
                if (cmp < 0) {
                    break;
                }
            } else {
                EXPECT_TRUE(cmp >= 0);
                if (cmp > 0) {
                    break;
                }
            }
        }
        // check binary sort data
        uint32_t minLen = std::min(sorter._sortDataArray[i]._len, sorter._sortDataArray[i+1]._len);
        int cmp = memcmp(&sorter._binarySortData[0] + sorter._sortDataArray[i]._idx,
                         &sorter._binarySortData[0] + sorter._sortDataArray[i+1]._idx,
                         minLen);
        EXPECT_TRUE(cmp <= 0);
        EXPECT_TRUE(sorter._sortDataArray[i]._len == (offsets[i+1] - offsets[i]));
        cmp = memcmp(&sorter._binarySortData[0] + sorter._sortDataArray[i]._idx,
                     buf.get() + offsets[i], sorter._sortDataArray[i]._len);
        EXPECT_TRUE(cmp == 0);
    }
    EXPECT_TRUE(sorter._sortDataArray[num-1]._len == (offsets[num] - offsets[num-1]));
    int cmp = memcmp(&sorter._binarySortData[0] + sorter._sortDataArray[num-1]._idx,
                 buf.get() + offsets[num-1], sorter._sortDataArray[num-1]._len);
    EXPECT_TRUE(cmp == 0);
}

void MultilevelSortTest::testSort()
{
    {
        std::vector<Spec> spec;
        spec.emplace_back("int8", INT8);
        spec.emplace_back("int16", INT16);
        spec.emplace_back("int32", INT32);
        spec.emplace_back("int64", INT64);
        spec.emplace_back("float", FLOAT);
        spec.emplace_back("double", DOUBLE);
        spec.emplace_back("string", STRING);
        spec.emplace_back("rank", RANK);
        spec.emplace_back("docid", DOCID);

        std::vector<std::string> strValues;
        strValues.emplace_back("applications");
        strValues.emplace_back("places");
        strValues.emplace_back("system");
        strValues.emplace_back("vespa search core");

        srand(12345);
        sortAndCheck(spec, 5000, 4, strValues);
        srand(time(nullptr));
        sortAndCheck(spec, 5000, 4, strValues);

        strValues.emplace_back("multilevelsort");
        strValues.emplace_back("trondheim");
        strValues.emplace_back("ubuntu");
        strValues.emplace_back("fastserver4");

        srand(56789);
        sortAndCheck(spec, 5000, 8, strValues);
        srand(time(nullptr));
        sortAndCheck(spec, 5000, 8, strValues);
    }
    {
        std::vector<std::string> none;
        uint32_t num = 50;
        sortAndCheck(std::vector<Spec>(1, Spec("int8", INT8, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int16", INT16, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int32", INT32, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int64", INT64, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("float", FLOAT, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("double", DOUBLE, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("string", STRING, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("rank", RANK, true)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("docid", DOCID, true)), num, 0, none);

        sortAndCheck(std::vector<Spec>(1, Spec("int8", INT8, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int16", INT16, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int32", INT32, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("int64", INT64, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("float", FLOAT, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("double", DOUBLE, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("string", STRING, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("rank", RANK, false)), num, 0, none);
        sortAndCheck(std::vector<Spec>(1, Spec("docid", DOCID, false)), num, 0, none);
    }

}

TEST_F(MultilevelSortTest, require_that_all_sort_methods_behave_the_same)
{
    testSort();
}

TEST(SortTest, test_that_docid_translates_to_lid_paritionid) {
    search::uca::UcaConverterFactory ucaFactory;
    FastS_SortSpec asc("no-metastore", 7, vespalib::Doom::never(), ucaFactory);
    RankedHit hits[2] = {RankedHit(91, 0.0), RankedHit(3, 2.0)};
    search::AttributeManager mgr;
    search::AttributeContext ac(mgr);
    EXPECT_TRUE(asc.Init("+[docid]", ac));
    asc.initWithoutSorting(hits, 2);
    constexpr uint8_t FIRST_ASC[6] = {0,0,0,91,0,7};
    constexpr uint8_t SECOND_ASC[6] = {0,0,0,3,0,7};
    constexpr uint8_t FIRST_DESC[6] = {255,255,255,255-91,255,255-7};
    constexpr uint8_t SECOND_DESC[6] = {255,255,255,255-3,255,255-7};
    auto sr1 = asc.getSortRef(0);
    EXPECT_EQ(6u, sr1.second);
    EXPECT_EQ(0, memcmp(FIRST_ASC, sr1.first, 6));
    auto sr2 = asc.getSortRef(1);
    EXPECT_EQ(6u, sr2.second);
    EXPECT_EQ(0, memcmp(SECOND_ASC, sr2.first, 6));

    FastS_SortSpec desc("no-metastore", 7, vespalib::Doom::never(), ucaFactory);
    desc.Init("-[docid]", ac);
    desc.initWithoutSorting(hits, 2);
    sr1 = desc.getSortRef(0);
    EXPECT_EQ(6u, sr1.second);
    EXPECT_EQ(0, memcmp(FIRST_DESC, sr1.first, 6));
    sr2 = desc.getSortRef(1);
    EXPECT_EQ(6u, sr2.second);
    EXPECT_EQ(0, memcmp(SECOND_DESC, sr2.first, 6));
}

TEST(SortTest, test_that_docid_uses_attribute_when_one_exists) {
    search::uca::UcaConverterFactory ucaFactory;
    FastS_SortSpec asc("metastore", 7, vespalib::Doom::never(), ucaFactory);
    RankedHit hits[2] = {RankedHit(91, 0.0), RankedHit(3, 2.0)};
    Config cfg(BasicType::INT64, CollectionType::SINGLE);
    auto metastore = AttributeFactory::createAttribute("metastore", cfg);
    ASSERT_TRUE(metastore->addDocs(100));
    auto * iattr = dynamic_cast<IntegerAttribute *>(metastore.get());
    for (uint32_t lid(0); lid < 100; lid++) {
        iattr->update(lid, lid);
    }
    metastore->commit();
    search::AttributeManager mgr;
    mgr.add(metastore);
    search::AttributeContext ac(mgr);
    EXPECT_TRUE(asc.Init("+[docid]", ac));
    asc.initWithoutSorting(hits, 2);
    constexpr uint8_t FIRST_ASC[8] = {0x80,0,0,0,0,0,0,91};
    constexpr uint8_t SECOND_ASC[8] = {0x80,0,0,0,0,0,0,3};
    constexpr uint8_t FIRST_DESC[8] = {0x7f,0xff,0xff,0xff,0xff,0xff,0xff,0xff - 91};
    constexpr uint8_t SECOND_DESC[8] = {0x7f,0xff,0xff,0xff,0xff,0xff,0xff,0xff - 3};
    auto sr1 = asc.getSortRef(0);
    EXPECT_EQ(8u, sr1.second);
    EXPECT_EQ(0, memcmp(FIRST_ASC, sr1.first, 8));
    auto sr2 = asc.getSortRef(1);
    EXPECT_EQ(8u, sr2.second);
    EXPECT_EQ(0, memcmp(SECOND_ASC, sr2.first, 8));

    FastS_SortSpec desc("metastore", 7, vespalib::Doom::never(), ucaFactory);
    desc.Init("-[docid]", ac);
    desc.initWithoutSorting(hits, 2);
    sr1 = desc.getSortRef(0);
    EXPECT_EQ(8u, sr1.second);
    EXPECT_EQ(0, memcmp(FIRST_DESC, sr1.first, 8));
    sr2 = desc.getSortRef(1);
    EXPECT_EQ(8u, sr2.second);
    EXPECT_EQ(0, memcmp(SECOND_DESC, sr2.first, 8));
}

using search::string_to_number;

TEST(SortTest, string_to_number_for_missing_value_in_sort_spec) {
    EXPECT_EQ((int8_t)0, string_to_number<int8_t>(""));
    EXPECT_EQ((int16_t)0, string_to_number<int16_t>(""));
    EXPECT_EQ((int32_t)0, string_to_number<int32_t>(""));
    EXPECT_EQ((int64_t)0, string_to_number<int64_t>(""));
    EXPECT_EQ((float)0.0, string_to_number<float>(""));
    EXPECT_EQ((double)0.0, string_to_number<double>(""));

    EXPECT_EQ(std::numeric_limits<int8_t>::max(), string_to_number<int8_t>("127"));
    EXPECT_EQ(std::numeric_limits<int16_t>::max(), string_to_number<int16_t>("32767"));
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), string_to_number<int32_t>("2147483647"));
    EXPECT_EQ((int64_t)(std::numeric_limits<int32_t>::max())+1, string_to_number<int64_t>("2147483648"));
    EXPECT_EQ((float)37.4, string_to_number<float>("37.4"));
    EXPECT_EQ((double)37.4, string_to_number<double>("37.4"));

    EXPECT_EQ(std::numeric_limits<int8_t>::min(), string_to_number<int8_t>("-128"));
    EXPECT_EQ(std::numeric_limits<int16_t>::min(), string_to_number<int16_t>("-32768"));
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), string_to_number<int32_t>("-2147483648"));
    EXPECT_EQ((int64_t)(std::numeric_limits<int32_t>::min())-1, string_to_number<int64_t>("-2147483649"));
    EXPECT_EQ((float)-37.4, string_to_number<float>("-37.4"));
    EXPECT_EQ((double)-37.4, string_to_number<double>("-37.4"));
}

void verify_make_sort_blob_writer_throws(BasicType b_type, CollectionType c_type, bool fast_search) {
    Config cfg(b_type, c_type);
    cfg.setFastSearch(fast_search);
    auto attr = AttributeFactory::createAttribute("my_attr", cfg);
    EXPECT_THROW(attr->make_sort_blob_writer(true, nullptr, search::common::sortspec::MissingPolicy::AS, "illegal"),
                     vespalib::IllegalArgumentException);
}

TEST(SortTest, make_sort_blob_writer_throws_when_missing_value_is_illegal) {
    verify_make_sort_blob_writer_throws(BasicType::INT64, CollectionType::ARRAY, false);
    verify_make_sort_blob_writer_throws(BasicType::INT64, CollectionType::ARRAY, true);
    verify_make_sort_blob_writer_throws(BasicType::FLOAT, CollectionType::ARRAY, false);
    verify_make_sort_blob_writer_throws(BasicType::FLOAT, CollectionType::ARRAY, true);
}

TEST(SortTest, fieldpath_sort_parsing) {
    search::uca::UcaConverterFactory ucaFactory;
    search::AttributeManager mgr;
    search::AttributeContext ac(mgr);
    
    // Create mock map attributes
    Config keyConfig(BasicType::STRING, CollectionType::ARRAY);
    Config valueConfig(BasicType::INT32, CollectionType::ARRAY);
    
    auto keyAttr = AttributeFactory::createAttribute("myMap.key", keyConfig);
    auto valueAttr = AttributeFactory::createAttribute("myMap.value", valueConfig);
    
    mgr.add(keyAttr);
    mgr.add(valueAttr);
    
    // Test FieldPath sort spec parsing
    FastS_SortSpec sortSpec("no-metastore", 0, vespalib::Doom::never(), ucaFactory);
    
    // This should succeed - the FieldPath syntax should be detected and handled
    EXPECT_TRUE(sortSpec.Init("+myMap{myKey}", ac));
    
    // Test descending FieldPath sort
    FastS_SortSpec sortSpecDesc("no-metastore", 0, vespalib::Doom::never(), ucaFactory);
    EXPECT_TRUE(sortSpecDesc.Init("-myMap{myKey}", ac));
}

GTEST_MAIN_RUN_ALL_TESTS()
