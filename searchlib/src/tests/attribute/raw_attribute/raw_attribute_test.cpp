// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>
#include <memory>

using search::AddressSpaceComponents;
using search::AddressSpaceUsage;
using search::AttributeFactory;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::SingleRawAttribute;
using vespalib::ConstArrayRef;


std::vector<char> empty;
vespalib::string hello("hello");
vespalib::ConstArrayRef<char> raw_hello(hello.c_str(), hello.size());

std::filesystem::path attr_path("raw.dat");

std::vector<char> as_vector(vespalib::stringref value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<char> as_vector(vespalib::ConstArrayRef<char> value) {
    return {value.data(), value.data() + value.size()};
}

void remove_saved_attr() {
    std::filesystem::remove(attr_path);
}

class RawAttributeTest : public ::testing::Test
{
protected:
    std::shared_ptr<AttributeVector> _attr;
    SingleRawAttribute*              _raw;

    RawAttributeTest();
    ~RawAttributeTest() override;
    std::vector<char> get_raw(uint32_t docid);
    void reset_attr(bool add_reserved);
};


RawAttributeTest::RawAttributeTest()
    : ::testing::Test(),
    _attr(),
    _raw(nullptr)
{
    reset_attr(true);
}

RawAttributeTest::~RawAttributeTest() = default;

std::vector<char>
RawAttributeTest::get_raw(uint32_t docid)
{
    return as_vector(_raw->get_raw(docid));
}

void
RawAttributeTest::reset_attr(bool add_reserved)
{
    Config cfg(BasicType::RAW, CollectionType::SINGLE);
    _attr = AttributeFactory::createAttribute("raw", cfg);
    _raw = &dynamic_cast<SingleRawAttribute&>(*_attr);
    if (add_reserved) {
        _attr->addReservedDoc();
    }
}

TEST_F(RawAttributeTest, can_set_and_clear_value)
{
    EXPECT_TRUE(_attr->addDocs(10));
    _attr->commit();
    EXPECT_EQ(empty, get_raw(1));
    _raw->set_raw(1, raw_hello);
    EXPECT_EQ(as_vector(hello), get_raw(1));
    _attr->clearDoc(1);
    EXPECT_EQ(empty, get_raw(1));
}

TEST_F(RawAttributeTest, implements_serialize_for_sort) {
    std::vector<char> escapes{1, 0, char(0xff), char(0xfe), 1};
    vespalib::string long_hello("hello, is there anybody out there");
    vespalib::ConstArrayRef<char> raw_long_hello(long_hello.c_str(), long_hello.size());
    uint8_t buf[8];
    memset(buf, 0, sizeof(buf));
    _attr->addDocs(10);
    _attr->commit();
    EXPECT_EQ(1, _attr->serializeForAscendingSort(1, buf, sizeof(buf)));
    EXPECT_EQ(0, buf[0]);
    EXPECT_EQ(1, _attr->serializeForDescendingSort(1, buf, sizeof(buf)));
    EXPECT_EQ(0xff, buf[0]);
    _raw->set_raw(1, raw_hello);
    EXPECT_EQ(6, _attr->serializeForAscendingSort(1, buf, sizeof(buf)));
    uint8_t hello_asc[] = {0x01+'h', 0x01+'e', 0x01+'l', 0x01+'l', 0x01+'o', 0x00};
    EXPECT_EQ(0, memcmp(hello_asc, buf, 6));
    EXPECT_EQ(6, _attr->serializeForDescendingSort(1, buf, sizeof(buf)));
    uint8_t hello_desc[] = {0xfe -'h', 0xfe -'e', 0xfe -'l', 0xfe -'l', 0xfe -'o', 0xff};
    EXPECT_EQ(0, memcmp(hello_desc, buf, 6));
    _raw->set_raw(1, escapes);
    EXPECT_EQ(8, _attr->serializeForAscendingSort(1, buf, sizeof(buf)));
    uint8_t escapes_asc[] = {0x02, 0x01, 0xff, 0xff, 0xff, 0xfe, 0x02, 0x00};
    EXPECT_EQ(0, memcmp(escapes_asc, buf, 8));
    EXPECT_EQ(8, _attr->serializeForDescendingSort(1, buf, sizeof(buf)));
    uint8_t escapes_desc[] = {0xfd, 0xfe, 0x00, 0x00, 0x00, 0x01, 0xfd, 0xff};
    EXPECT_EQ(0, memcmp(escapes_desc, buf, 8));
    _raw->set_raw(1, raw_long_hello);
    EXPECT_EQ(-1, _attr->serializeForAscendingSort(1, buf, sizeof(buf)));
    EXPECT_EQ(-1, _attr->serializeForDescendingSort(1, buf, sizeof(buf)));
}

TEST_F(RawAttributeTest, save_and_load)
{
    auto mini_test = as_vector("mini test");
    remove_saved_attr();
    _attr->addDocs(10);
    _attr->commit();
    _raw->set_raw(1, raw_hello);
    _raw->set_raw(2, mini_test);
    _attr->setCreateSerialNum(20);
    _attr->save();
    reset_attr(false);
    _attr->load();
    EXPECT_EQ(11, _attr->getCommittedDocIdLimit());
    EXPECT_EQ(11, _attr->getStatus().getNumDocs());
    EXPECT_EQ(20, _attr->getCreateSerialNum());
    EXPECT_EQ(as_vector("hello"), as_vector(_raw->get_raw(1)));
    EXPECT_EQ(mini_test, as_vector(_raw->get_raw(2)));
    remove_saved_attr();
}

TEST_F(RawAttributeTest, address_space_usage_is_reported)
{
    auto& raw_store = AddressSpaceComponents::raw_store;
    _attr->addDocs(1);
    _attr->commit();
    AddressSpaceUsage usage = _attr->getAddressSpaceUsage();
    const auto& all = usage.get_all();
    EXPECT_EQ(1u, all.size());
    EXPECT_EQ(1u, all.count(raw_store));
    EXPECT_EQ(1, all.at(raw_store).used());
    _raw->set_raw(1, as_vector("foo"));
    EXPECT_EQ(2, _attr->getAddressSpaceUsage().get_all().at(raw_store).used());
}

GTEST_MAIN_RUN_ALL_TESTS()
