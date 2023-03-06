// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>

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

std::vector<char> as_vector(vespalib::stringref value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<char> as_vector(vespalib::ConstArrayRef<char> value) {
    return {value.data(), value.data() + value.size()};
}

class RawAttributeTest : public ::testing::Test
{
protected:
    std::shared_ptr<AttributeVector> _attr;
    SingleRawAttribute*              _raw;

    RawAttributeTest();
    ~RawAttributeTest() override;
    std::vector<char> get_raw(uint32_t docid);
};


RawAttributeTest::RawAttributeTest()
    : ::testing::Test(),
    _attr(),
    _raw(nullptr)
{
    Config cfg(BasicType::RAW, CollectionType::SINGLE);
    _attr = AttributeFactory::createAttribute("raw", cfg);
    _raw = &dynamic_cast<SingleRawAttribute&>(*_attr);
    _attr->addReservedDoc();
}

RawAttributeTest::~RawAttributeTest() = default;

std::vector<char>
RawAttributeTest::get_raw(uint32_t docid)
{
    return as_vector(_raw->get_raw(docid));
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

GTEST_MAIN_RUN_ALL_TESTS()
