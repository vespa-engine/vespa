// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
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
using search::QueryTermSimple;
using search::attribute::ArrayBoolAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IArrayBoolReadView;
using search::attribute::IMultiValueAttribute;
using search::attribute::SearchContextParams;

namespace {

void remove_saved_attr() {
    std::filesystem::remove("array_bool.dat");
    std::filesystem::remove("array_bool.idx");
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
    _attr->commit();

    std::vector<int8_t> vals = {1, 0, 1, 1, 0};
    _bool_attr->set_bools(1, vals);
    EXPECT_EQ(5u, _attr->getValueCount(1));

    // Read via get(largeint_t*)
    std::vector<AttributeVector::largeint_t> buf(10);
    uint32_t count = _attr->get(1, buf.data(), buf.size());
    EXPECT_EQ(5u, count);
    EXPECT_EQ(1, buf[0]);
    EXPECT_EQ(0, buf[1]);
    EXPECT_EQ(1, buf[2]);
    EXPECT_EQ(1, buf[3]);
    EXPECT_EQ(0, buf[4]);

    // getInt returns first bool
    EXPECT_EQ(1, _attr->getInt(1));
}

TEST_F(ArrayBoolAttributeTest, set_bools_replaces_previous_values)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);
    EXPECT_EQ(3u, _attr->getValueCount(1));

    std::vector<int8_t> vals2 = {0, 1};
    _bool_attr->set_bools(1, vals2);
    EXPECT_EQ(2u, _attr->getValueCount(1));

    std::vector<AttributeVector::largeint_t> buf(10);
    _attr->get(1, buf.data(), buf.size());
    EXPECT_EQ(0, buf[0]);
    EXPECT_EQ(1, buf[1]);
}

TEST_F(ArrayBoolAttributeTest, clear_doc)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    EXPECT_EQ(3u, _attr->getValueCount(1));

    _attr->clearDoc(1);
    EXPECT_EQ(0u, _attr->getValueCount(1));
}

TEST_F(ArrayBoolAttributeTest, various_bool_counts)
{
    EXPECT_TRUE(_attr->addDocs(10));
    _attr->commit();

    // 0 bools
    EXPECT_EQ(0u, _attr->getValueCount(1));

    // 1 bool
    std::vector<int8_t> v1 = {1};
    _bool_attr->set_bools(2, v1);
    EXPECT_EQ(1u, _attr->getValueCount(2));

    // 7 bools (less than 8)
    std::vector<int8_t> v7 = {1, 0, 1, 0, 1, 0, 1};
    _bool_attr->set_bools(3, v7);
    EXPECT_EQ(7u, _attr->getValueCount(3));

    // exactly 8 bools
    std::vector<int8_t> v8 = {1, 0, 1, 0, 1, 0, 1, 0};
    _bool_attr->set_bools(4, v8);
    EXPECT_EQ(8u, _attr->getValueCount(4));

    // 9 bools (more than 8)
    std::vector<int8_t> v9 = {1, 0, 1, 0, 1, 0, 1, 0, 1};
    _bool_attr->set_bools(5, v9);
    EXPECT_EQ(9u, _attr->getValueCount(5));

    // 16 bools (exactly 2 bytes)
    std::vector<int8_t> v16(16, 1);
    _bool_attr->set_bools(6, v16);
    EXPECT_EQ(16u, _attr->getValueCount(6));

    // large count
    std::vector<int8_t> v100(100);
    for (int i = 0; i < 100; ++i) {
        v100[i] = (i % 3 == 0) ? 1 : 0;
    }
    _bool_attr->set_bools(7, v100);
    EXPECT_EQ(100u, _attr->getValueCount(7));

    // Verify large count values
    std::vector<AttributeVector::largeint_t> buf(100);
    uint32_t count = _attr->get(7, buf.data(), buf.size());
    EXPECT_EQ(100u, count);
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ((i % 3 == 0) ? 1 : 0, buf[i]) << "index " << i;
    }
}

TEST_F(ArrayBoolAttributeTest, read_view)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals = {1, 0, 1, 1};
    _bool_attr->set_bools(1, vals);

    auto* mv_attr = _attr->as_multi_value_attribute();
    ASSERT_NE(nullptr, mv_attr);

    vespalib::Stash stash;
    auto* read_view = mv_attr->make_read_view(IMultiValueAttribute::ArrayBoolTag(), stash);
    ASSERT_NE(nullptr, read_view);

    auto bs = read_view->get_values(1);
    EXPECT_EQ(4u, bs.size());
    EXPECT_TRUE(bs[0]);
    EXPECT_FALSE(bs[1]);
    EXPECT_TRUE(bs[2]);
    EXPECT_TRUE(bs[3]);

    // Empty document
    auto bs_empty = read_view->get_values(2);
    EXPECT_EQ(0u, bs_empty.size());
}

TEST_F(ArrayBoolAttributeTest, get_float)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals = {0, 1, 0};
    _bool_attr->set_bools(1, vals);

    std::vector<double> buf(10);
    uint32_t count = _attr->get(1, buf.data(), buf.size());
    EXPECT_EQ(3u, count);
    EXPECT_EQ(0.0, buf[0]);
    EXPECT_EQ(1.0, buf[1]);
    EXPECT_EQ(0.0, buf[2]);

    EXPECT_EQ(0.0, _attr->getFloat(1));
}

TEST_F(ArrayBoolAttributeTest, search_context_true)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals1 = {0, 1, 0};
    _bool_attr->set_bools(1, vals1);

    std::vector<int8_t> vals2 = {0, 0, 0};
    _bool_attr->set_bools(2, vals2);

    std::vector<int8_t> vals3 = {1};
    _bool_attr->set_bools(3, vals3);

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("true", QueryTermSimple::Type::WORD),
                                SearchContextParams());
    ASSERT_TRUE(ctx->valid());

    int32_t weight = 0;
    // Doc 1: has true at position 1
    EXPECT_EQ(1, ctx->find(1, 0, weight));
    EXPECT_EQ(1, weight);

    // Doc 2: all false, no match
    EXPECT_EQ(-1, ctx->find(2, 0));

    // Doc 3: has true at position 0
    EXPECT_EQ(0, ctx->find(3, 0));

    // Doc 4: empty, no match
    EXPECT_EQ(-1, ctx->find(4, 0));
}

TEST_F(ArrayBoolAttributeTest, search_context_false)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals1 = {1, 0, 1};
    _bool_attr->set_bools(1, vals1);

    auto ctx = _attr->getSearch(std::make_unique<QueryTermSimple>("false", QueryTermSimple::Type::WORD),
                                SearchContextParams());
    ASSERT_TRUE(ctx->valid());

    // Doc 1: has false at position 1
    EXPECT_EQ(1, ctx->find(1, 0));
}

TEST_F(ArrayBoolAttributeTest, search_context_numeric)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals = {0, 1};
    _bool_attr->set_bools(1, vals);

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
    _attr->commit();

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

    _attr->setCreateSerialNum(42);
    _attr->save();

    reset_attr(false);
    _attr->load();

    EXPECT_EQ(11u, _attr->getCommittedDocIdLimit());
    EXPECT_EQ(11u, _attr->getStatus().getNumDocs());
    EXPECT_EQ(42u, _attr->getCreateSerialNum());

    // Verify doc 1
    EXPECT_EQ(3u, _attr->getValueCount(1));
    std::vector<AttributeVector::largeint_t> buf(100);
    _attr->get(1, buf.data(), buf.size());
    EXPECT_EQ(1, buf[0]);
    EXPECT_EQ(0, buf[1]);
    EXPECT_EQ(1, buf[2]);

    // Verify doc 2
    EXPECT_EQ(9u, _attr->getValueCount(2));
    _attr->get(2, buf.data(), buf.size());
    EXPECT_EQ(0, buf[0]);
    EXPECT_EQ(1, buf[1]);
    EXPECT_EQ(1, buf[2]);
    EXPECT_EQ(0, buf[3]);
    EXPECT_EQ(1, buf[4]);
    EXPECT_EQ(0, buf[5]);
    EXPECT_EQ(1, buf[6]);
    EXPECT_EQ(0, buf[7]);
    EXPECT_EQ(1, buf[8]);

    // Verify doc 3 empty
    EXPECT_EQ(0u, _attr->getValueCount(3));

    // Verify doc 4 (100 bools)
    EXPECT_EQ(100u, _attr->getValueCount(4));
    _attr->get(4, buf.data(), buf.size());
    for (int i = 0; i < 100; ++i) {
        EXPECT_EQ((i % 2 == 0) ? 1 : 0, buf[i]) << "index " << i;
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

TEST_F(ArrayBoolAttributeTest, weighted_int_get)
{
    EXPECT_TRUE(_attr->addDocs(5));
    _attr->commit();

    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);

    std::vector<AttributeVector::WeightedInt> buf(10);
    uint32_t count = _attr->get(1, buf.data(), buf.size());
    EXPECT_EQ(3u, count);
    EXPECT_EQ(1, buf[0].getValue());
    EXPECT_EQ(0, buf[1].getValue());
    EXPECT_EQ(1, buf[2].getValue());
}

TEST_F(ArrayBoolAttributeTest, shrink_lid_space)
{
    EXPECT_TRUE(_attr->addDocs(10));
    _attr->commit();

    std::vector<int8_t> vals = {1, 0, 1};
    _bool_attr->set_bools(1, vals);
    _bool_attr->set_bools(8, vals);

    _attr->compactLidSpace(5);
    EXPECT_EQ(5u, _attr->getCommittedDocIdLimit());

    // Doc 1 should still be accessible
    EXPECT_EQ(3u, _attr->getValueCount(1));
}

GTEST_MAIN_RUN_ALL_TESTS()
