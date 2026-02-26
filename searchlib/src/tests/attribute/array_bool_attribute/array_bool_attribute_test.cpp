// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/array_bool_ext_attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>
#include <filesystem>
#include <memory>

using search::AddressSpaceComponents;
using search::AddressSpaceUsage;
using search::AttributeFactory;
using search::AttributeVector;
using DocId = search::AttributeVector::DocId;
using search::QueryTermSimple;
using search::attribute::ArrayBoolAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IArrayBoolReadView;
using search::attribute::IMultiValueAttribute;
using search::attribute::SearchContextParams;

std::vector<bool> to_vec(vespalib::BitSpan span) {
    std::vector<bool> vec;
    for (bool bit : span) {
        vec.push_back(bit);
    }
    return vec;
}

namespace {

void remove_saved_attr() {
    std::filesystem::remove("array_bool.dat");
}

}

class ArrayBoolAttributeTest : public ::testing::Test
{
protected:
    std::shared_ptr<AttributeVector> _attr;
    ArrayBoolAttribute*              _bool_attr;

    ArrayBoolAttributeTest();
    ~ArrayBoolAttributeTest() override;
    void reset_attr(bool add_reserved);
};

ArrayBoolAttributeTest::ArrayBoolAttributeTest()
    : _attr(),
      _bool_attr(nullptr)
{
    reset_attr(true);
}

ArrayBoolAttributeTest::~ArrayBoolAttributeTest() = default;

void
ArrayBoolAttributeTest::reset_attr(bool add_reserved)
{
    Config cfg(BasicType::BOOL, CollectionType::ARRAY);
    _attr = AttributeFactory::createAttribute("array_bool", cfg);
    _bool_attr = &dynamic_cast<ArrayBoolAttribute&>(*_attr);
    if (add_reserved) {
        _attr->addReservedDoc();
    }
}

TEST_F(ArrayBoolAttributeTest, factory_creates_correct_type)
{
    Config cfg(BasicType::BOOL, CollectionType::ARRAY);
    auto attr = AttributeFactory::createAttribute("test_factory", cfg);
    EXPECT_NE(nullptr, dynamic_cast<ArrayBoolAttribute*>(attr.get()));
}

TEST_F(ArrayBoolAttributeTest, empty_document_has_zero_values)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();
    EXPECT_EQ(0u, _attr->getValueCount(1));
    EXPECT_EQ(0, _attr->getInt(1));
}

TEST_F(ArrayBoolAttributeTest, set_and_get_bools)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1, 1, 0};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    EXPECT_EQ(5u, _attr->getValueCount(1));

    std::vector<bool> expected = {true, false, true, true, false};
    auto bs = _bool_attr->get_bools(1);
    ASSERT_EQ(to_vec(bs), expected);

    // Empty document
    EXPECT_EQ(0u, _attr->getValueCount(2));
    EXPECT_EQ(0u, _bool_attr->get_bools(2).size());
}

TEST_F(ArrayBoolAttributeTest, set_bools_replaces_previous_values)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals1 = {1, 0, 1, 1, 0};
    _bool_attr->set_bools(1, vals1);
    EXPECT_EQ(5u, _bool_attr->getTotalValueCount());

    // Replace with fewer bools (5 -> 2)
    std::vector<int8_t> vals2 = {0, 1};
    _bool_attr->set_bools(1, vals2);
    EXPECT_EQ(2u, _bool_attr->getTotalValueCount());
    _attr->commit();

    EXPECT_EQ(2u, _attr->getValueCount(1));

    std::vector<bool> expected = {0, 1};
    EXPECT_EQ(to_vec(_bool_attr->get_bools(1)), expected);
}

TEST_F(ArrayBoolAttributeTest, clear_doc)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    EXPECT_EQ(3u, _attr->getValueCount(1));
    EXPECT_EQ(3u, _attr->clearDoc(1));
    EXPECT_EQ(0u, _attr->getValueCount(1));
    // Clearing an already empty doc returns 0
    EXPECT_EQ(0u, _attr->clearDoc(1));
}

TEST_F(ArrayBoolAttributeTest, various_bool_counts)
{
    EXPECT_TRUE(_attr->addDocs(10));
    // doc 1: 0 bools
    std::vector<int8_t> v1 = {1};
    _bool_attr->set_bools(2, v1);
    std::vector<int8_t> v7 = {1, 0, 1, 0, 1, 0, 1};
    _bool_attr->set_bools(3, v7);
    std::vector<int8_t> v8 = {1, 0, 1, 0, 1, 0, 1, 0};
    _bool_attr->set_bools(4, v8);
    std::vector<int8_t> v9 = {1, 0, 1, 0, 1, 0, 1, 0, 1};
    _bool_attr->set_bools(5, v9);
    std::vector<int8_t> v16(16, 1);
    _bool_attr->set_bools(6, v16);
    std::vector<int8_t> v100(100);
    for (int i = 0; i < 100; ++i) {
        v100[i] = (i % 3 == 0) ? 1 : 0;
    }
    _bool_attr->set_bools(7, v100);
    _attr->commit();

    EXPECT_EQ(0u, _attr->getValueCount(1));
    EXPECT_EQ(1u, _attr->getValueCount(2));
    EXPECT_EQ(7u, _attr->getValueCount(3));
    EXPECT_EQ(8u, _attr->getValueCount(4));
    EXPECT_EQ(9u, _attr->getValueCount(5));
    EXPECT_EQ(16u, _attr->getValueCount(6));
    EXPECT_EQ(100u, _attr->getValueCount(7));

    // Verify large count values
    auto bools = to_vec(_bool_attr->get_bools(7));
    EXPECT_EQ(100u, bools.size());
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ((i % 3 == 0) ? 1 : 0, bools[i]) << "index " << i;
    }
}

TEST_F(ArrayBoolAttributeTest, read_view)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    auto* mv_attr = _attr->as_multi_value_attribute();
    ASSERT_NE(nullptr, mv_attr);

    vespalib::Stash stash;
    auto* read_view = mv_attr->make_read_view(IMultiValueAttribute::ArrayBoolTag(), stash);
    ASSERT_NE(nullptr, read_view);

    std::vector<bool> expected = {true, false, true, true};
    auto bs = read_view->get_values(1);
    ASSERT_EQ(to_vec(bs), expected);

    // Empty document
    EXPECT_EQ(0u, read_view->get_values(2).size());
}

TEST_F(ArrayBoolAttributeTest, search_context_true)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals1 = {0, 1, 0};
    _bool_attr->set_bools(1, vals1);
    std::vector<int8_t> vals2 = {0, 0, 0};
    _bool_attr->set_bools(2, vals2);
    std::vector<int8_t> vals3 = {1};
    _bool_attr->set_bools(3, vals3);
    _attr->commit();

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("true", QueryTermSimple::Type::WORD),
                                SearchContextParams());
    ASSERT_TRUE(ctx->valid());

    int32_t weight = 0;
    // Doc 1: has true at element 1
    EXPECT_EQ(1, ctx->find(1, 0, weight));
    EXPECT_EQ(1, weight);

    // Doc 2: all false, no match
    EXPECT_EQ(-1, ctx->find(2, 0));

    // Doc 3: has true at element 0
    EXPECT_EQ(0, ctx->find(3, 0));

    // Doc 4: empty, no match
    EXPECT_EQ(-1, ctx->find(4, 0));
}

TEST_F(ArrayBoolAttributeTest, search_context_false)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    _attr->commit();

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("false", QueryTermSimple::Type::WORD),
                                SearchContextParams());
    ASSERT_TRUE(ctx->valid());

    // Doc 1: has false at element 1
    EXPECT_EQ(1, ctx->find(1, 0));
}

TEST_F(ArrayBoolAttributeTest, search_context_numeric)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    // "1" matches true
    auto ctx1 = _attr->getSearch(std::make_unique<QueryTermSimple>("1", QueryTermSimple::Type::WORD),
                                 SearchContextParams());
    ASSERT_TRUE(ctx1->valid());
    EXPECT_EQ(1, ctx1->find(1, 0));

    // "0" matches false
    auto ctx0 = _attr->getSearch(std::make_unique<QueryTermSimple>("0", QueryTermSimple::Type::WORD),
                                 SearchContextParams());
    ASSERT_TRUE(ctx0->valid());
    EXPECT_EQ(0, ctx0->find(1, 0));
}

TEST_F(ArrayBoolAttributeTest, search_context_invalid_term)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("hello", QueryTermSimple::Type::WORD),
                                SearchContextParams());
    EXPECT_FALSE(ctx->valid());
}

TEST_F(ArrayBoolAttributeTest, save_and_load)
{
    remove_saved_attr();
    EXPECT_TRUE(_attr->addDocs(10));
    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    std::vector<int8_t> vals2 = {0, 1, 1, 0, 1, 0, 1, 0, 1};
    _bool_attr->set_bools(2, vals2);
    // Doc 3 empty
    std::vector<int8_t> vals4(100);
    for (int i = 0; i < 100; ++i) {
        vals4[i] = (i % 2 == 0) ? 1 : 0;
    }
    _bool_attr->set_bools(4, vals4);
    _attr->commit();

    _attr->setCreateSerialNum(42);
    _attr->save();

    reset_attr(false);
    _attr->load();

    EXPECT_EQ(11u, _attr->getCommittedDocIdLimit());
    EXPECT_EQ(11u, _attr->getStatus().getNumDocs());
    EXPECT_EQ(42u, _attr->getCreateSerialNum());

    // Verify doc 1
    EXPECT_EQ(3u, _attr->getValueCount(1));
    std::vector<bool> expected1 = {1, 0, 1};
    EXPECT_EQ(to_vec(_bool_attr->get_bools(1)), expected1);

    // Verify doc 2
    EXPECT_EQ(9u, _attr->getValueCount(2));
    std::vector<bool> expected2 = {0, 1, 1, 0, 1, 0, 1, 0, 1};
    EXPECT_EQ(to_vec(_bool_attr->get_bools(2)), expected2);

    // Verify doc 3 empty
    EXPECT_EQ(0u, _attr->getValueCount(3));

    // Verify doc 4 (100 bools)
    EXPECT_EQ(100u, _attr->getValueCount(4));
    auto bools4 = to_vec(_bool_attr->get_bools(4));
    EXPECT_EQ(100u, bools4.size());
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ((i % 2 == 0) ? 1 : 0, bools4[i]) << "index " << i;
    }

    remove_saved_attr();
}

TEST_F(ArrayBoolAttributeTest, address_space_usage_is_reported)
{
    auto& raw_store = AddressSpaceComponents::raw_store;
    _attr->addDocs(1);
    _attr->commit();
    AddressSpaceUsage usage = _attr->getAddressSpaceUsage();
    const auto& all = usage.get_all();
    EXPECT_EQ(1u, all.size());
    EXPECT_EQ(1u, all.count(raw_store));
}

TEST_F(ArrayBoolAttributeTest, get_array_values)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1, 1, 0};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    // Full buffer
    std::vector<AttributeVector::largeint_t> expected_int = {1, 0, 1, 1, 0};
    std::vector<AttributeVector::largeint_t> buf_int(expected_int.size());
    EXPECT_EQ(expected_int.size(), _attr->get(1, buf_int.data(), buf_int.size()));
    EXPECT_EQ(expected_int, buf_int);

    std::vector<double> expected_double = {1.0, 0.0, 1.0, 1.0, 0.0};
    std::vector<double> buf_double(expected_double.size());
    EXPECT_EQ(expected_double.size(), _attr->get(1, buf_double.data(), buf_double.size()));
    EXPECT_EQ(expected_double, buf_double);

    std::vector<std::string> expected_string = {"1", "0", "1", "1", "0"};
    std::vector<std::string> buf_string(expected_string.size());
    EXPECT_EQ(expected_string.size(), _attr->get(1, buf_string.data(), buf_string.size()));
    EXPECT_EQ(expected_string, buf_string);

    std::vector<AttributeVector::WeightedInt> expected_wint = {{1, 1}, {0, 1}, {1, 1}, {1, 1}, {0, 1}};
    std::vector<AttributeVector::WeightedInt> buf_wint(expected_wint.size());
    EXPECT_EQ(expected_wint.size(), _attr->get(1, buf_wint.data(), buf_wint.size()));
    EXPECT_EQ(expected_wint, buf_wint);

    std::vector<AttributeVector::WeightedFloat> expected_wfloat = {{1.0, 1}, {0.0, 1}, {1.0, 1}, {1.0, 1}, {0.0, 1}};
    std::vector<AttributeVector::WeightedFloat> buf_wfloat(expected_wfloat.size());
    EXPECT_EQ(expected_wfloat.size(), _attr->get(1, buf_wfloat.data(), buf_wfloat.size()));
    EXPECT_EQ(expected_wfloat, buf_wfloat);

    std::vector<AttributeVector::WeightedString> expected_wstring = {{"1", 1}, {"0", 1}, {"1", 1}, {"1", 1}, {"0", 1}};
    std::vector<AttributeVector::WeightedString> buf_wstring(expected_wstring.size());
    EXPECT_EQ(expected_wstring.size(), _attr->get(1, buf_wstring.data(), buf_wstring.size()));
    EXPECT_EQ(expected_wstring, buf_wstring);

    // Types that always return 0
    const char* cbuf[5];
    EXPECT_EQ(0u, _attr->get(1, cbuf, 5));

    AttributeVector::EnumHandle ebuf[5];
    EXPECT_EQ(0u, _attr->get(1, ebuf, 5));

    AttributeVector::WeightedConstChar wccbuf[5];
    EXPECT_EQ(0u, _attr->get(1, wccbuf, 5));

    AttributeVector::WeightedEnum webuf[5];
    EXPECT_EQ(0u, _attr->get(1, webuf, 5));

    // Undersized buffer: fills up to sz, returns total count
    std::vector<AttributeVector::largeint_t> partial_int = {1, 0};
    std::vector<AttributeVector::largeint_t> pbuf_int(partial_int.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_int.data(), pbuf_int.size()));
    EXPECT_EQ(partial_int, pbuf_int);

    std::vector<double> partial_double = {1.0, 0.0};
    std::vector<double> pbuf_double(partial_double.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_double.data(), pbuf_double.size()));
    EXPECT_EQ(partial_double, pbuf_double);

    std::vector<std::string> partial_string = {"1", "0"};
    std::vector<std::string> pbuf_string(partial_string.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_string.data(), pbuf_string.size()));
    EXPECT_EQ(partial_string, pbuf_string);

    std::vector<AttributeVector::WeightedInt> partial_wint = {{1, 1}, {0, 1}};
    std::vector<AttributeVector::WeightedInt> pbuf_wint(partial_wint.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_wint.data(), pbuf_wint.size()));
    EXPECT_EQ(partial_wint, pbuf_wint);

    std::vector<AttributeVector::WeightedFloat> partial_wfloat = {{1.0, 1}, {0.0, 1}};
    std::vector<AttributeVector::WeightedFloat> pbuf_wfloat(partial_wfloat.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_wfloat.data(), pbuf_wfloat.size()));
    EXPECT_EQ(partial_wfloat, pbuf_wfloat);

    std::vector<AttributeVector::WeightedString> partial_wstring = {{"1", 1}, {"0", 1}};
    std::vector<AttributeVector::WeightedString> pbuf_wstring(partial_wstring.size());
    EXPECT_EQ(5u, _attr->get(1, pbuf_wstring.data(), pbuf_wstring.size()));
    EXPECT_EQ(partial_wstring, pbuf_wstring);
}

TEST_F(ArrayBoolAttributeTest, get_single_values)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    EXPECT_EQ(1, _attr->getInt(1));
    EXPECT_EQ(1.0, _attr->getFloat(1));
    EXPECT_TRUE(_attr->get_raw(1).empty());
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), _attr->getEnum(1));
}

TEST_F(ArrayBoolAttributeTest, is_not_sortable)
{
    EXPECT_FALSE(_attr->is_sortable());
}

TEST_F(ArrayBoolAttributeTest, find_enum_returns_false)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();
    AttributeVector::EnumHandle h;
    EXPECT_FALSE(_attr->findEnum("1", h));
    EXPECT_FALSE(_attr->findEnum("0", h));
}

TEST_F(ArrayBoolAttributeTest, find_folded_enums_returns_empty)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();
    EXPECT_TRUE(_attr->findFoldedEnums("1").empty());
    EXPECT_TRUE(_attr->findFoldedEnums("0").empty());
}

TEST_F(ArrayBoolAttributeTest, shrink_lid_space)
{
    EXPECT_TRUE(_attr->addDocs(10));
    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _bool_attr->set_bools(8, vals);
    _attr->commit();

    _attr->compactLidSpace(5);
    EXPECT_EQ(5u, _attr->getCommittedDocIdLimit());

    // Doc 1 should still be accessible
    EXPECT_EQ(3u, _attr->getValueCount(1));
}

TEST_F(ArrayBoolAttributeTest, search_context_from_nonzero_elem_id)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals = {1, 0, 1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _attr->commit();

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("true", QueryTermSimple::Type::WORD),
                                SearchContextParams());

    // Start from element 0: finds true at element 0
    EXPECT_EQ(0, ctx->find(1, 0));

    // Start from element 1: skips element 0, finds true at element 2
    EXPECT_EQ(2, ctx->find(1, 1));

    // Start from element 3: skips elements 0-2, finds true at element 4
    EXPECT_EQ(4, ctx->find(1, 3));

    // Start from element 5: past the end, no match
    EXPECT_EQ(-1, ctx->find(1, 5));
}

TEST_F(ArrayBoolAttributeTest, total_value_count_tracking)
{
    EXPECT_TRUE(_attr->addDocs(5));
    EXPECT_EQ(0u, _bool_attr->getTotalValueCount());

    // Set 3 bools on doc 1
    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    EXPECT_EQ(3u, _bool_attr->getTotalValueCount());

    // Set 5 bools on doc 2
    std::vector<int8_t> vals2 = {1, 0, 1, 1, 0};
    _bool_attr->set_bools(2, vals2);
    EXPECT_EQ(8u, _bool_attr->getTotalValueCount());

    // Replace doc 1 with 2 bools (3 -> 2, total 8 - 3 + 2 = 7)
    std::vector<int8_t> vals1b = {0, 1};
    _bool_attr->set_bools(1, vals1b);
    EXPECT_EQ(7u, _bool_attr->getTotalValueCount());

    // Clear doc 2 (total 7 - 5 = 2)
    _attr->clearDoc(2);
    EXPECT_EQ(2u, _bool_attr->getTotalValueCount());

    // Clear already empty doc 3 (no change)
    _attr->clearDoc(3);
    EXPECT_EQ(2u, _bool_attr->getTotalValueCount());

    // Clear doc 1 (total 2 - 2 = 0)
    _attr->clearDoc(1);
    EXPECT_EQ(0u, _bool_attr->getTotalValueCount());
}

TEST_F(ArrayBoolAttributeTest, total_value_count_after_save_load)
{
    remove_saved_attr();
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    std::vector<int8_t> vals2 = {0, 1, 1, 0, 1};
    _bool_attr->set_bools(2, vals2);
    _attr->commit();

    EXPECT_EQ(8u, _bool_attr->getTotalValueCount());

    _attr->setCreateSerialNum(99);
    _attr->save();

    reset_attr(false);
    _attr->load();

    EXPECT_EQ(8u, _bool_attr->getTotalValueCount());

    remove_saved_attr();
}

TEST_F(ArrayBoolAttributeTest, estimated_save_byte_size)
{
    EXPECT_TRUE(_attr->addDocs(5));
    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    std::vector<int8_t> vals2 = {0, 1, 1, 0, 1};
    _bool_attr->set_bools(2, vals2);
    _attr->commit();

    uint64_t estimate = _attr->getEstimatedSaveByteSize();
    // headerSize = 4096, totalBits = 8, numDocs = 6
    // 4096 + (8+7)/8 + 6*5 = 4096 + 1 + 30 = 4127 (with 1 reserved doc, 5 added = 6 docs)
    uint64_t expected = 4096 + (8 + 7) / 8 + 6 * 5;
    EXPECT_EQ(expected, estimate);
}

class ArrayBoolExtAttributeTest : public ::testing::Test
{
protected:
    using ArrayBoolExtAttribute = search::attribute::ArrayBoolExtAttribute;
    std::shared_ptr<ArrayBoolExtAttribute> _attr;
    std::map<DocId,std::vector<bool>> _added;

    ArrayBoolExtAttributeTest()
        : _attr(std::make_shared<ArrayBoolExtAttribute>("ext_array_bool"))
    {}

    void add(std::vector<bool> values) {
        DocId docid;
        _attr->addDoc(docid);
        auto* ext = _attr->getExtendInterface();
        for (bool v : values) {
            ext->add(int64_t(v ? 1 : 0));
        }
        ASSERT_TRUE(_added.find(docid) == _added.end());
        _added[docid] = values;
    }
};

TEST_F(ArrayBoolExtAttributeTest, build_and_verify)
{
    std::vector<bool> empty;
    std::vector<bool> many_vals;
    for (int i = 0; i < 100; ++i) {
        many_vals.push_back(((i % 11 == 0) || (i % 7 == 0)) ? true : false);
    }
    add(empty);
    add({1, 1, 0, 1, 0, 1, 1});
    add(many_vals);
    add({1, 1, 0, 1, 1, 0, 0, 0, 1});
    add(empty);
    add({1, 0, 1, 1, 0, 0, 1, 1});

    vespalib::Stash stash;
    auto* mv_attr = _attr->as_multi_value_attribute();
    ASSERT_NE(nullptr, mv_attr);
    auto* read_view = mv_attr->make_read_view(IMultiValueAttribute::ArrayBoolTag(), stash);
    ASSERT_NE(nullptr, read_view);

    std::optional<DocId> max_id;
    for (const auto& [docid, expected] : _added) {
        if (!max_id.has_value() || docid > max_id.value()) {
            max_id = docid;
        }
        EXPECT_EQ(expected, to_vec(_attr->get_bools(docid)));
        EXPECT_EQ(expected, to_vec(read_view->get_values(docid)));
    }
    EXPECT_EQ(empty, to_vec(_attr->get_bools(max_id.value() + 1)));
    EXPECT_EQ(empty, to_vec(read_view->get_values(max_id.value() + 1)));
}

GTEST_MAIN_RUN_ALL_TESTS()
