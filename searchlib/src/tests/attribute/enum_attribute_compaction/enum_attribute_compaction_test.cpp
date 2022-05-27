// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/test/weighted_type_test_utils.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("enum_attribute_compaction_test");

using search::IntegerAttribute;
using search::StringAttribute;
using search::AttributeVector;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using EnumHandle = search::attribute::IAttributeVector::EnumHandle;

template <typename VectorType> struct TestData;

template <>
struct TestData<IntegerAttribute> {
    using BufferType = search::attribute::IntegerContent;
    using CheckType = int32_t;
    static constexpr BasicType::Type basic_type = BasicType::INT32;
    static int32_t make_value(uint32_t doc_id, uint32_t idx) { return doc_id * 10 + idx; }
    static int32_t as_add(int32_t value) { return value; }
    static int32_t make_undefined_value() { return std::numeric_limits<int32_t>::min(); }
};

template <>
struct TestData<StringAttribute> {
    using BufferType = search::attribute::ConstCharContent;
    using CheckType = std::string;
    static constexpr BasicType::Type basic_type = BasicType::STRING;
    static std::string make_value(uint32_t doc_id, uint32_t idx) {
        uint32_t combined = doc_id * 10 + idx;
        vespalib::asciistream s;
        if (doc_id == 2 && idx == 0) {
            // Longer string will be stored in a different buffer
            s << "bb345678901234";
        } else {
            s << combined;
        }
        return s.str();
    }
    static const char *as_add(const std::string &value) { return value.c_str(); }
    static std::string make_undefined_value() { return std::string(); }
};

class CompactionTestBase : public ::testing::TestWithParam<CollectionType::Type> {
public:
    std::shared_ptr<AttributeVector> _v;

    CompactionTestBase()
        : _v()
    {
    }
    void SetUp() override;
    virtual BasicType get_basic_type() const = 0;
    CollectionType get_collection_type() const noexcept { return GetParam(); }
    void add_docs(uint32_t num_docs);
    uint32_t count_changed_enum_handles(const std::vector<EnumHandle> &handles, uint32_t stride);
};

void
CompactionTestBase::SetUp()
{
    Config cfg(get_basic_type(), get_collection_type());
    cfg.setFastSearch(true);
    _v = search::AttributeFactory::createAttribute("test", cfg);
}

void
CompactionTestBase::add_docs(uint32_t num_docs)
{
    uint32_t start_doc;
    uint32_t end_doc;
    _v->addDocs(start_doc, end_doc, num_docs);
    for (uint32_t doc = start_doc; doc <= end_doc; ++doc) {
        _v->clearDoc(doc);
    }
    _v->commit();
}

uint32_t
CompactionTestBase::count_changed_enum_handles(const std::vector<EnumHandle> &handles, uint32_t stride)
{
    uint32_t changed = 0;
    for (uint32_t doc_id = 0; doc_id < handles.size(); doc_id += stride) {
        if (_v->getEnum(doc_id) != handles[doc_id]) {
            ++changed;
        }
    }
    return changed;
}

template <typename VectorType>
class CompactionTest : public CompactionTestBase
{
public:
    CompactionTest();
    void set_values(uint32_t doc_id);
    void check_values(uint32_t doc_id);
    void check_cleared_values(uint32_t doc_id);
    void test_enum_store_compaction();
    BasicType get_basic_type() const override { return TestData<VectorType>::basic_type; }
};

template <typename VectorType>
CompactionTest<VectorType>::CompactionTest()
    : CompactionTestBase()
{
}

template <typename VectorType>
void
CompactionTest<VectorType>::set_values(uint32_t doc_id)
{
    using MyTestData = TestData<VectorType>;
    auto &typed_v = dynamic_cast<VectorType &>(*_v);
    _v->clearDoc(doc_id);
    if (_v->hasMultiValue()) {
        EXPECT_TRUE(typed_v.append(doc_id, MyTestData::as_add(MyTestData::make_value(doc_id, 0)), 1));
        EXPECT_TRUE(typed_v.append(doc_id, MyTestData::as_add(MyTestData::make_value(doc_id, 1)), 1));
    } else {
        EXPECT_TRUE(typed_v.update(doc_id, MyTestData::as_add(MyTestData::make_value(doc_id, 0))));
    }
    _v->commit();
}

template <typename VectorType>
void
CompactionTest<VectorType>::check_values(uint32_t doc_id)
{
    using MyTestData = TestData<VectorType>;
    using CheckType = typename MyTestData::CheckType;
    typename MyTestData::BufferType buffer;
    buffer.fill(*_v, doc_id);
    if (_v->hasMultiValue()) {
        EXPECT_EQ(2u, buffer.size());
        int i = 0, j = 1;
        if (_v->hasWeightedSetType() && !(CheckType(buffer[0]) == MyTestData::make_value(doc_id, 0))) {
            i = 1;
            j = 0;
        }
        EXPECT_EQ(CheckType(buffer[i]), MyTestData::make_value(doc_id, 0));
        EXPECT_EQ(CheckType(buffer[j]), MyTestData::make_value(doc_id, 1));
    } else {
        EXPECT_EQ(1u, buffer.size());
        EXPECT_EQ(CheckType(buffer[0]), MyTestData::make_value(doc_id, 0));
    }
}

template <typename VectorType>
void
CompactionTest<VectorType>::check_cleared_values(uint32_t doc_id)
{
    using MyTestData = TestData<VectorType>;
    using CheckType = typename MyTestData::CheckType;
    typename MyTestData::BufferType buffer;
    buffer.fill(*_v, doc_id);
    if (_v->hasMultiValue()) {
        EXPECT_EQ(0u, buffer.size());
    } else {
        EXPECT_EQ(1u, buffer.size());
        EXPECT_EQ(CheckType(buffer[0]), MyTestData::make_undefined_value());
    }
}

template <typename VectorType>
void
CompactionTest<VectorType>::test_enum_store_compaction()
{
    constexpr uint32_t canary_stride = 256;
    uint32_t dead_limit = vespalib::datastore::CompactionStrategy::DEAD_BYTES_SLACK / 8;
    uint32_t doc_count = dead_limit * 3;
    if (_v->hasMultiValue() || std::is_same_v<VectorType,StringAttribute>) {
        doc_count /= 2;
    }
    std::vector<EnumHandle> enum_handles;
    add_docs(doc_count);
    enum_handles.emplace_back(_v->getEnum(0));
    uint32_t doc_id;
    for (doc_id = 1; doc_id < doc_count; ++doc_id) {
        set_values(doc_id);
        enum_handles.emplace_back(_v->getEnum(doc_id));
    }
    uint32_t last_cleared_doc_id = 0;
    for (doc_id = 1; doc_id < doc_count; doc_id += 2) {
        _v->clearDoc(doc_id);
        _v->commit(true);
        enum_handles[doc_id] = enum_handles[0];
        last_cleared_doc_id = doc_id;
        if (count_changed_enum_handles(enum_handles, canary_stride) != 0) {
            LOG(info, "Detected enum store compaction at doc_id %u", doc_id);
            break;
        }
    }
    EXPECT_LT(doc_id, doc_count);
    uint32_t changed_enum_handles = count_changed_enum_handles(enum_handles, 1);
    LOG(info, "%u enum handles changed", changed_enum_handles);
    EXPECT_LT(0u, changed_enum_handles);
    for (doc_id = 1; doc_id < doc_count; ++doc_id) {
        if ((doc_id % 2) == 0 || doc_id > last_cleared_doc_id) {
            check_values(doc_id);
        } else {
            check_cleared_values(doc_id);
        }
    }
}

using IntegerCompactionTest = CompactionTest<IntegerAttribute>;

TEST_P(IntegerCompactionTest, compact)
{
    test_enum_store_compaction();
}

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(IntegerCompactionTestSet, IntegerCompactionTest, ::testing::Values(CollectionType::SINGLE, CollectionType::ARRAY, CollectionType::WSET));

using StringCompactionTest = CompactionTest<StringAttribute>;

TEST_P(StringCompactionTest, compact)
{
    test_enum_store_compaction();
}

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(StringCompactionTestSet, StringCompactionTest, ::testing::Values(CollectionType::SINGLE, CollectionType::ARRAY, CollectionType::WSET));

GTEST_MAIN_RUN_ALL_TESTS()
