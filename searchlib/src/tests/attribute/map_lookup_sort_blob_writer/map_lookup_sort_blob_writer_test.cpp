// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/map_lookup_sort_blob_writer.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchlib/attribute/numeric_sort_blob_writer.h>
#include <vespa/searchlib/attribute/string_sort_blob_writer.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <cmath>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

using search::AttributeFactory;
using search::AttributeVector;
using search::FloatingPointAttribute;
using search::IntegerAttribute;
using search::NotImplementedAttribute;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::getUndefined;
using search::attribute::IAttributeVector;
using search::attribute::IMultiValueAttribute;
using search::attribute::ISortBlobWriter;
using search::attribute::make_map_lookup_sort_blob_writer;
using search::attribute::NumericSortBlobWriter;
using search::attribute::StringSortBlobWriter;
using search::common::FieldSortSpec;
using search::common::LowercaseConverter;
using search::common::sortspec::MissingPolicy;
using search::common::sortspec::SortOrder;

namespace {

using SortData = std::vector<unsigned char>;

constexpr uint32_t num_test_docs = 16;

// Documents in the shared map fixture. Doc 0 is the reserved document.
constexpr uint32_t doc_key_middle = 1;      // selected key at position 1 of 3
constexpr uint32_t doc_key_first = 2;       // selected key at position 0 of 3
constexpr uint32_t doc_key_last = 3;        // selected key at position 2 of 3
constexpr uint32_t doc_key_absent = 4;      // map without the selected key
constexpr uint32_t doc_empty_map = 5;       // no entries at all
constexpr uint32_t doc_misaligned = 6;      // selected key present, but no value at that position
constexpr uint32_t doc_undefined_value = 7; // selected value is the reserved undefined value / NaN / empty string

const std::string selected_key("k1");
const std::string unknown_key("no_such_key_anywhere");

std::shared_ptr<AttributeVector> make_attribute(const std::string& name, BasicType basic_type,
                                                CollectionType collection_type, bool fast_search) {
    Config cfg(basic_type, collection_type);
    cfg.setFastSearch(fast_search);
    auto attr = AttributeFactory::createAttribute(name, cfg);
    attr->addReservedDoc();
    while (attr->getNumDocs() < num_test_docs) {
        uint32_t docid = 0;
        attr->addDoc(docid);
    }
    attr->commit();
    return attr;
}

void append_keys(AttributeVector& attr, uint32_t docid, const std::vector<std::string>& keys) {
    auto& string_attr = dynamic_cast<StringAttribute&>(attr);
    for (const auto& key : keys) {
        EXPECT_TRUE(string_attr.append(docid, key, 1));
    }
}

void append_ints(AttributeVector& attr, uint32_t docid, const std::vector<int64_t>& values) {
    auto& int_attr = dynamic_cast<IntegerAttribute&>(attr);
    for (auto value : values) {
        EXPECT_TRUE(int_attr.append(docid, value, 1));
    }
}

void append_doubles(AttributeVector& attr, uint32_t docid, const std::vector<double>& values) {
    auto& float_attr = dynamic_cast<FloatingPointAttribute&>(attr);
    for (auto value : values) {
        EXPECT_TRUE(float_attr.append(docid, value, 1));
    }
}

void append_strings(AttributeVector& attr, uint32_t docid, const std::vector<std::string>& values) {
    auto& string_attr = dynamic_cast<StringAttribute&>(attr);
    for (const auto& value : values) {
        EXPECT_TRUE(string_attr.append(docid, value, 1));
    }
}

/*
 * Populates the key attribute with the layout shared by all value types:
 * the selected key at the first, middle and last position, a document without
 * it, an empty map, and two documents used for misaligned/undefined values.
 */
void populate_keys(AttributeVector& keys) {
    append_keys(keys, doc_key_middle, {"k0", "k1", "k2"});
    append_keys(keys, doc_key_first, {"k1", "k0", "k2"});
    append_keys(keys, doc_key_last, {"k0", "k2", "k1"});
    append_keys(keys, doc_key_absent, {"k0", "k2"});
    // doc_empty_map has no keys
    append_keys(keys, doc_misaligned, {"k0", "k1"});
    append_keys(keys, doc_undefined_value, {"k1"});
    keys.commit();
}

FieldSortSpec make_spec(bool ascending, MissingPolicy policy = MissingPolicy::DEFAULT,
                        const std::string& missing_value = "", const std::string& key = selected_key) {
    return FieldSortSpec("m", ascending ? SortOrder::ASCENDING : SortOrder::DESCENDING, {}, policy, missing_value,
                         false, key);
}

/*
 * Writes the sort blob for docid, offering buffers of increasing size starting at 0, so every
 * blob is also written to every too-small buffer. Checks that the writer returns -1 for a
 * too-small buffer, accepts the first buffer that is large enough, and never writes past
 * the available size (verified with guard bytes after the offered buffer).
 */
SortData write_blob(ISortBlobWriter& writer, uint32_t docid) {
    constexpr size_t        guard_size = 16;
    constexpr unsigned char guard_byte = 0xa5;
    for (size_t len = 0; len < 1024; ++len) { // the writer must terminate long before this
        SortData buf(len + guard_size, guard_byte);
        auto     written = writer.write(docid, buf.data(), len);
        for (size_t i = len; i < buf.size(); ++i) {
            if (buf[i] != guard_byte) {
                ADD_FAILURE() << "Sort blob writer wrote past available size " << len << " for docid " << docid;
                return {};
            }
        }
        if (written >= 0) {
            EXPECT_EQ(static_cast<long>(len), written) << "Sort blob writer refused a large enough buffer";
            buf.resize(written);
            return buf;
        }
        EXPECT_EQ(-1, written);
    }
    ADD_FAILURE() << "Sort blob writer never accepted a buffer for docid " << docid;
    return {};
}

// Same single-value writer a plain attribute uses, so a prefix or missing-value change cannot
// leave map sorts and ordinary sorts disagreeing while this test stays green.
template <typename T, bool asc>
SortData numeric_blob(MissingPolicy policy, T missing_value, const T* value) {
    NumericSortBlobWriter<T, asc> writer(policy, missing_value, false);
    writer.reset();
    if (value != nullptr) {
        writer.candidate(*value);
    }
    SortData buf(sizeof(T) + 1);
    auto     written = writer.write(buf.data(), buf.size());
    EXPECT_GE(written, 0);
    if (written < 0) {
        return {};
    }
    buf.resize(static_cast<size_t>(written));
    return buf;
}

template <bool asc>
SortData string_blob(MissingPolicy policy, std::string_view missing_value, const char* value) {
    StringSortBlobWriter<asc> writer(nullptr, policy, missing_value, false);
    size_t                    cap = (value != nullptr) ? std::strlen(value) + 2 : missing_value.size() + 2;
    SortData                  buf(cap < 2 ? 2 : cap);
    writer.reset(buf.data(), buf.size());
    if (value != nullptr) {
        EXPECT_TRUE(writer.candidate(value));
    }
    auto written = writer.write();
    EXPECT_GE(written, 0);
    if (written < 0) {
        return {};
    }
    buf.resize(static_cast<size_t>(written));
    return buf;
}

template <typename T> SortData expect_value(T value, bool asc, MissingPolicy policy = MissingPolicy::DEFAULT) {
    return asc ? numeric_blob<T, true>(policy, T{}, &value) : numeric_blob<T, false>(policy, T{}, &value);
}

template <typename T>
SortData expect_missing(bool asc, MissingPolicy policy = MissingPolicy::DEFAULT, T missing_value = T()) {
    return asc ? numeric_blob<T, true>(policy, missing_value, nullptr)
               : numeric_blob<T, false>(policy, missing_value, nullptr);
}

SortData expect_string(const char* value, bool asc, MissingPolicy policy = MissingPolicy::DEFAULT) {
    return asc ? string_blob<true>(policy, "", value) : string_blob<false>(policy, "", value);
}

SortData expect_missing_string(bool asc, MissingPolicy policy = MissingPolicy::DEFAULT,
                               const char* missing_value = "") {
    return asc ? string_blob<true>(policy, missing_value, nullptr)
               : string_blob<false>(policy, missing_value, nullptr);
}

/*
 * Multi value attribute whose typed read views are never available.
 * Used to prove that the factory fails construction instead of silently
 * treating every document as missing.
 */
class MockMultiValueAttribute : public NotImplementedAttribute, public IMultiValueAttribute {
    bool _imported;

public:
    MockMultiValueAttribute(const std::string& name, BasicType basic_type, CollectionType collection_type,
                            bool has_enum, bool imported)
        : NotImplementedAttribute(name, Config(basic_type, collection_type)), _imported(imported) {
        setEnum(has_enum);
    }
    ~MockMultiValueAttribute() override;
    const IMultiValueAttribute* as_multi_value_attribute() const override { return this; }
    bool isImported() const override { return _imported; }
    bool is_sortable() const noexcept override { return true; }
    bool findEnum(const char*, EnumHandle& e) const override {
        e = 1;
        return true;
    }
    void onCommit() override {}
    void onUpdateStat(search::CommitParam::UpdateStats) override {}
};

MockMultiValueAttribute::~MockMultiValueAttribute() = default;

} // namespace

class MapLookupSortBlobWriterTest : public ::testing::Test {
protected:
    std::shared_ptr<AttributeVector> _keys;

    MapLookupSortBlobWriterTest()
        : ::testing::Test(), _keys(make_attribute("m.key", BasicType::STRING, CollectionType::ARRAY, false)) {
        populate_keys(*_keys);
    }
    ~MapLookupSortBlobWriterTest() override;

    std::shared_ptr<AttributeVector> make_int_values(bool fast_search = false) {
        auto values = make_attribute("m.value", BasicType::INT32, CollectionType::ARRAY, fast_search);
        append_ints(*values, doc_key_middle, {10, 20, 30});
        append_ints(*values, doc_key_first, {21, 11, 31});
        append_ints(*values, doc_key_last, {12, 32, 22});
        append_ints(*values, doc_key_absent, {13, 33});
        // doc_empty_map has no values
        append_ints(*values, doc_misaligned, {16}); // key at position 1, only one value
        append_ints(*values, doc_undefined_value, {getUndefined<int32_t>()});
        values->commit();
        return values;
    }

    std::shared_ptr<AttributeVector> make_double_values() {
        auto values = make_attribute("m.value", BasicType::DOUBLE, CollectionType::ARRAY, false);
        append_doubles(*values, doc_key_middle, {10.5, 20.5, 30.5});
        append_doubles(*values, doc_key_first, {21.5, 11.5, 31.5});
        append_doubles(*values, doc_key_last, {12.5, 32.5, 22.5});
        append_doubles(*values, doc_key_absent, {13.5, 33.5});
        append_doubles(*values, doc_misaligned, {16.5});
        append_doubles(*values, doc_undefined_value, {std::numeric_limits<double>::quiet_NaN()});
        values->commit();
        return values;
    }

    std::shared_ptr<AttributeVector> make_string_values() {
        auto values = make_attribute("m.value", BasicType::STRING, CollectionType::ARRAY, false);
        append_strings(*values, doc_key_middle, {"aa", "bb", "cc"});
        append_strings(*values, doc_key_first, {"bc", "ab", "cd"});
        append_strings(*values, doc_key_last, {"ad", "cf", "bd"});
        append_strings(*values, doc_key_absent, {"ae", "ce"});
        append_strings(*values, doc_misaligned, {"af"});
        append_strings(*values, doc_undefined_value, {""}); // empty string counts as undefined
        values->commit();
        return values;
    }
};

MapLookupSortBlobWriterTest::~MapLookupSortBlobWriterTest() = default;

TEST_F(MapLookupSortBlobWriterTest, selects_value_at_key_position) {
    auto values = make_int_values();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_value<int32_t>(20, asc), write_blob(*writer, doc_key_middle));
        EXPECT_EQ(expect_value<int32_t>(21, asc), write_blob(*writer, doc_key_first));
        EXPECT_EQ(expect_value<int32_t>(22, asc), write_blob(*writer, doc_key_last));
    }
}

TEST_F(MapLookupSortBlobWriterTest, absent_key_empty_map_and_misalignment_are_missing) {
    auto values = make_int_values();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, doc_key_absent));
        EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, doc_empty_map));
        EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, doc_misaligned));
    }
}

TEST_F(MapLookupSortBlobWriterTest, globally_unknown_key_is_missing_for_every_document) {
    auto values = make_int_values();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values,
                                                       make_spec(asc, MissingPolicy::DEFAULT, "", unknown_key));
        ASSERT_TRUE(writer);
        for (uint32_t docid = 1; docid <= doc_undefined_value; ++docid) {
            EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, docid));
        }
    }
}

TEST_F(MapLookupSortBlobWriterTest, blob_order_matches_value_order) {
    auto values = make_int_values();
    auto asc_writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(true));
    auto desc_writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(false));
    ASSERT_TRUE(asc_writer);
    ASSERT_TRUE(desc_writer);
    // Values are 20, 21, 22 for these documents.
    EXPECT_LT(write_blob(*asc_writer, doc_key_middle), write_blob(*asc_writer, doc_key_first));
    EXPECT_LT(write_blob(*asc_writer, doc_key_first), write_blob(*asc_writer, doc_key_last));
    EXPECT_GT(write_blob(*desc_writer, doc_key_middle), write_blob(*desc_writer, doc_key_first));
    EXPECT_GT(write_blob(*desc_writer, doc_key_first), write_blob(*desc_writer, doc_key_last));
    // Missing sorts first ascending and last descending under the default policy.
    EXPECT_LT(write_blob(*asc_writer, doc_key_absent), write_blob(*asc_writer, doc_key_middle));
    EXPECT_GT(write_blob(*desc_writer, doc_key_absent), write_blob(*desc_writer, doc_key_middle));
}

TEST_F(MapLookupSortBlobWriterTest, integer_missing_policies) {
    auto values = make_int_values();
    for (bool asc : {true, false}) {
        for (auto policy : {MissingPolicy::DEFAULT, MissingPolicy::FIRST, MissingPolicy::LAST, MissingPolicy::AS}) {
            SCOPED_TRACE(::testing::Message() << "ascending=" << asc << " policy=" << static_cast<int>(policy));
            auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc, policy, "42"));
            ASSERT_TRUE(writer);
            EXPECT_EQ(expect_value<int32_t>(20, asc, policy), write_blob(*writer, doc_key_middle));
            EXPECT_EQ(expect_missing<int32_t>(asc, policy, 42), write_blob(*writer, doc_key_absent));
            EXPECT_EQ(expect_missing<int32_t>(asc, policy, 42), write_blob(*writer, doc_empty_map));
            EXPECT_EQ(expect_missing<int32_t>(asc, policy, 42), write_blob(*writer, doc_misaligned));
            // A selected reserved undefined value counts as missing, not as a real value.
            EXPECT_EQ(expect_missing<int32_t>(asc, policy, 42), write_blob(*writer, doc_undefined_value));
        }
    }
}

TEST_F(MapLookupSortBlobWriterTest, double_missing_policies_and_nan) {
    auto values = make_double_values();
    for (bool asc : {true, false}) {
        for (auto policy : {MissingPolicy::DEFAULT, MissingPolicy::FIRST, MissingPolicy::LAST, MissingPolicy::AS}) {
            SCOPED_TRACE(::testing::Message() << "ascending=" << asc << " policy=" << static_cast<int>(policy));
            auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc, policy, "42.5"));
            ASSERT_TRUE(writer);
            EXPECT_EQ(expect_value<double>(20.5, asc, policy), write_blob(*writer, doc_key_middle));
            EXPECT_EQ(expect_missing<double>(asc, policy, 42.5), write_blob(*writer, doc_key_absent));
            // A selected NaN counts as missing.
            EXPECT_EQ(expect_missing<double>(asc, policy, 42.5), write_blob(*writer, doc_undefined_value));
        }
    }
}

TEST_F(MapLookupSortBlobWriterTest, string_missing_policies_and_empty_value) {
    auto values = make_string_values();
    for (bool asc : {true, false}) {
        for (auto policy : {MissingPolicy::DEFAULT, MissingPolicy::FIRST, MissingPolicy::LAST, MissingPolicy::AS}) {
            SCOPED_TRACE(::testing::Message() << "ascending=" << asc << " policy=" << static_cast<int>(policy));
            auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc, policy, "zz"));
            ASSERT_TRUE(writer);
            EXPECT_EQ(expect_string("bb", asc, policy), write_blob(*writer, doc_key_middle));
            EXPECT_EQ(expect_string("bc", asc, policy), write_blob(*writer, doc_key_first));
            EXPECT_EQ(expect_string("bd", asc, policy), write_blob(*writer, doc_key_last));
            EXPECT_EQ(expect_missing_string(asc, policy, "zz"), write_blob(*writer, doc_key_absent));
            EXPECT_EQ(expect_missing_string(asc, policy, "zz"), write_blob(*writer, doc_empty_map));
            EXPECT_EQ(expect_missing_string(asc, policy, "zz"), write_blob(*writer, doc_misaligned));
            // A selected empty string counts as missing, matching single value string sorting.
            EXPECT_EQ(expect_missing_string(asc, policy, "zz"), write_blob(*writer, doc_undefined_value));
        }
    }
}

TEST_F(MapLookupSortBlobWriterTest, long_and_utf8_string_values) {
    auto        values = make_attribute("m.value", BasicType::STRING, CollectionType::ARRAY, false);
    std::string long_value(64, 'x');
    append_strings(*values, doc_key_middle, {"aa", long_value, "cc"});
    append_strings(*values, doc_key_first, {"smørrebrød", "ab", "cd"});
    values->commit();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_string(long_value.c_str(), asc), write_blob(*writer, doc_key_middle));
        EXPECT_EQ(expect_string("smørrebrød", asc), write_blob(*writer, doc_key_first));
    }
}

TEST_F(MapLookupSortBlobWriterTest, fast_search_value_attribute) {
    // A fast-search numeric array attribute exposes an enumerated read view; results must be identical.
    auto values = make_int_values(true);
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_value<int32_t>(20, asc), write_blob(*writer, doc_key_middle));
        EXPECT_EQ(expect_value<int32_t>(21, asc), write_blob(*writer, doc_key_first));
        EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, doc_key_absent));
        EXPECT_EQ(expect_missing<int32_t>(asc), write_blob(*writer, doc_undefined_value));
    }
}

TEST_F(MapLookupSortBlobWriterTest, fast_search_key_attribute) {
    auto keys = make_attribute("m.key", BasicType::STRING, CollectionType::ARRAY, true);
    populate_keys(*keys);
    auto values = make_int_values();
    auto writer = make_map_lookup_sort_blob_writer(*keys, *values, make_spec(true));
    ASSERT_TRUE(writer);
    EXPECT_EQ(expect_value<int32_t>(20, true), write_blob(*writer, doc_key_middle));
    EXPECT_EQ(expect_missing<int32_t>(true), write_blob(*writer, doc_key_absent));
}

// Every supported numeric width builds a writer and selects the right entry.
template <typename Param> class MapLookupNumericTypesTest : public ::testing::Test {};

struct Int8Param {
    using Type = int8_t;
    static constexpr BasicType::Type basic_type = BasicType::INT8;
    static constexpr int64_t         value = 20;
};
struct Int16Param {
    using Type = int16_t;
    static constexpr BasicType::Type basic_type = BasicType::INT16;
    static constexpr int64_t         value = 2000;
};
struct Int32Param {
    using Type = int32_t;
    static constexpr BasicType::Type basic_type = BasicType::INT32;
    static constexpr int64_t         value = 2000000;
};
struct Int64Param {
    using Type = int64_t;
    static constexpr BasicType::Type basic_type = BasicType::INT64;
    static constexpr int64_t         value = 20000000000L;
};

using MapLookupNumericTypes = ::testing::Types<Int8Param, Int16Param, Int32Param, Int64Param>;

TYPED_TEST_SUITE(MapLookupNumericTypesTest, MapLookupNumericTypes);

TYPED_TEST(MapLookupNumericTypesTest, selects_and_encodes_value) {
    using Type = typename TypeParam::Type;
    auto keys = make_attribute("m.key", BasicType::STRING, CollectionType::ARRAY, false);
    populate_keys(*keys);
    auto values = make_attribute("m.value", TypeParam::basic_type, CollectionType::ARRAY, false);
    append_ints(*values, doc_key_middle, {1, TypeParam::value, 3});
    append_ints(*values, doc_undefined_value, {getUndefined<Type>()});
    values->commit();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_value<Type>(static_cast<Type>(TypeParam::value), asc), write_blob(*writer, doc_key_middle));
        EXPECT_EQ(expect_missing<Type>(asc), write_blob(*writer, doc_key_absent));
        EXPECT_EQ(expect_missing<Type>(asc), write_blob(*writer, doc_undefined_value));
    }
}

TEST_F(MapLookupSortBlobWriterTest, float_values) {
    auto values = make_attribute("m.value", BasicType::FLOAT, CollectionType::ARRAY, false);
    append_doubles(*values, doc_key_middle, {1.25, 20.25, 3.25});
    append_doubles(*values, doc_undefined_value, {std::numeric_limits<double>::quiet_NaN()});
    values->commit();
    for (bool asc : {true, false}) {
        auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(asc));
        ASSERT_TRUE(writer);
        EXPECT_EQ(expect_value<float>(20.25f, asc), write_blob(*writer, doc_key_middle));
        EXPECT_EQ(expect_missing<float>(asc), write_blob(*writer, doc_undefined_value));
    }
}

TEST_F(MapLookupSortBlobWriterTest, valid_and_invalid_missing_as_values) {
    struct Case {
        BasicType::Type basic_type;
        const char*     valid;
    };
    const std::vector<Case> cases{{BasicType::INT8, "12"},    {BasicType::INT16, "1234"},
                                  {BasicType::INT32, "1234"}, {BasicType::INT64, "1234"},
                                  {BasicType::FLOAT, "12.5"}, {BasicType::DOUBLE, "12.5"}};
    for (const auto& c : cases) {
        SCOPED_TRACE(BasicType(c.basic_type).asString());
        auto values = make_attribute("m.value", c.basic_type, CollectionType::ARRAY, false);
        EXPECT_TRUE(make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(true, MissingPolicy::AS, c.valid)));
        EXPECT_FALSE(
            make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(true, MissingPolicy::AS, "not_a_number")));
    }
}

TEST_F(MapLookupSortBlobWriterTest, writer_holds_a_stable_enum_guard_on_the_key_attribute) {
    auto values = make_int_values();
    EXPECT_FALSE(_keys->hasActiveEnumGuards());
    auto writer = make_map_lookup_sort_blob_writer(*_keys, *values, make_spec(true));
    ASSERT_TRUE(writer);
    EXPECT_TRUE(_keys->hasActiveEnumGuards());
    EXPECT_FALSE(values->hasActiveEnumGuards());
    writer.reset();
    EXPECT_FALSE(_keys->hasActiveEnumGuards());

    // A writer that cannot be made does not leave a guard behind.
    auto single_value = make_attribute("m.value", BasicType::INT32, CollectionType::SINGLE, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, *single_value, make_spec(true)));
    MockMultiValueAttribute bad_value("m.value", BasicType::BOOL, CollectionType::ARRAY, false, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, bad_value, make_spec(true)));
    EXPECT_FALSE(_keys->hasActiveEnumGuards());
}

TEST_F(MapLookupSortBlobWriterTest, rejects_unsupported_attribute_combinations) {
    auto int_values = make_int_values();

    // No map lookup target in the sort spec.
    FieldSortSpec plain("m.value", SortOrder::ASCENDING, {}, MissingPolicy::DEFAULT, "");
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, *int_values, plain));

    // Single value key attribute.
    auto single_key = make_attribute("m.key", BasicType::STRING, CollectionType::SINGLE, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*single_key, *int_values, make_spec(true)));

    // Integer key attribute.
    auto int_key = make_attribute("m.key", BasicType::INT32, CollectionType::ARRAY, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*int_key, *int_values, make_spec(true)));

    // Single value value attribute.
    auto single_value = make_attribute("m.value", BasicType::INT32, CollectionType::SINGLE, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, *single_value, make_spec(true)));

    // Weighted set value attribute.
    auto wset_value = make_attribute("m.value", BasicType::INT32, CollectionType::WSET, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, *wset_value, make_spec(true)));

    // Key attribute without an enum store.
    MockMultiValueAttribute no_enum_key("m.key", BasicType::STRING, CollectionType::ARRAY, false, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(no_enum_key, *int_values, make_spec(true)));

    // Imported attributes, key side and value side.
    MockMultiValueAttribute imported_key("m.key", BasicType::STRING, CollectionType::ARRAY, true, true);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(imported_key, *int_values, make_spec(true)));
    MockMultiValueAttribute imported_value("m.value", BasicType::INT32, CollectionType::ARRAY, false, true);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, imported_value, make_spec(true)));

    // Unsupported value basic types.
    for (auto basic_type :
         {BasicType::BOOL, BasicType::TENSOR, BasicType::RAW, BasicType::PREDICATE, BasicType::REFERENCE})
    {
        SCOPED_TRACE(BasicType(basic_type).asString());
        MockMultiValueAttribute bad_value("m.value", basic_type, CollectionType::ARRAY, false, false);
        EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, bad_value, make_spec(true)));
    }
}

TEST_F(MapLookupSortBlobWriterTest, missing_read_view_fails_construction) {
    auto int_values = make_int_values();
    // Key side: a null read view must not be mistaken for a globally unknown key.
    MockMultiValueAttribute key_without_view("m.key", BasicType::STRING, CollectionType::ARRAY, true, false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(key_without_view, *int_values, make_spec(true)));

    // Value side, numeric and string.
    MockMultiValueAttribute numeric_value_without_view("m.value", BasicType::INT32, CollectionType::ARRAY, false,
                                                       false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, numeric_value_without_view, make_spec(true)));
    MockMultiValueAttribute string_value_without_view("m.value", BasicType::STRING, CollectionType::ARRAY, true,
                                                      false);
    EXPECT_FALSE(make_map_lookup_sort_blob_writer(*_keys, string_value_without_view, make_spec(true)));
}

TEST(MapLookupExactKey, uncased_dictionary_selects_the_exact_key) {
    // The default string dictionary compares uncased, then breaks ties by case, so "A" and "a"
    // are different keys. A sort function on the value does not change which entry is selected.
    auto keys = make_attribute("m.key", BasicType::STRING, CollectionType::ARRAY, false);
    append_keys(*keys, doc_key_middle, {"a", "A"});
    keys->commit();

    auto numbers = make_attribute("m.value", BasicType::INT32, CollectionType::ARRAY, false);
    append_ints(*numbers, doc_key_middle, {1, 2});
    numbers->commit();
    auto numeric =
        make_map_lookup_sort_blob_writer(*keys, *numbers, make_spec(true, MissingPolicy::DEFAULT, "", "A"));
    ASSERT_TRUE(numeric);
    EXPECT_EQ(expect_value<int32_t>(2, true), write_blob(*numeric, doc_key_middle));

    auto strings = make_attribute("m.str", BasicType::STRING, CollectionType::ARRAY, false);
    append_strings(*strings, doc_key_middle, {"alpha", "Beta"});
    strings->commit();
    FieldSortSpec folded("m", SortOrder::ASCENDING, std::make_shared<LowercaseConverter>(), MissingPolicy::DEFAULT,
                         "", false, "A");
    auto          text = make_map_lookup_sort_blob_writer(*keys, *strings, folded);
    ASSERT_TRUE(text);
    EXPECT_EQ(expect_string("beta", true), write_blob(*text, doc_key_middle));
}

GTEST_MAIN_RUN_ALL_TESTS()
