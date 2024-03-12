// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::attribute;

namespace search::fef {

TEST(AttributeContentTest, test_write_and_read)
{
    using UintContent = search::attribute::AttributeContent<uint32_t>;
    UintContent buf;
    EXPECT_EQ(buf.capacity(), 16u);
    EXPECT_EQ(buf.size(), 0u);

    uint32_t i;
    uint32_t * data;
    const uint32_t * itr;
    for (i = 0, data = buf.data(); i < 16; ++i, ++data) {
        *data = i;
    }
    buf.setSize(16);
    EXPECT_EQ(buf.size(), 16u);
    for (i = 0, itr = buf.begin(); itr != buf.end(); ++i, ++itr) {
        EXPECT_EQ(*itr, i);
        EXPECT_EQ(buf[i], i);
    }
    EXPECT_EQ(i, 16u);

    buf.allocate(10);
    EXPECT_EQ(buf.capacity(), 16u);
    EXPECT_EQ(buf.size(), 16u);
    buf.allocate(32);
    EXPECT_EQ(buf.capacity(), 32u);
    EXPECT_EQ(buf.size(), 0u);

    for (i = 0, data = buf.data(); i < 32; ++i, ++data) {
        *data = i;
    }
    buf.setSize(32);
    EXPECT_EQ(buf.size(), 32u);
    for (i = 0, itr = buf.begin(); itr != buf.end(); ++i, ++itr) {
        EXPECT_EQ(*itr, i);
        EXPECT_EQ(buf[i], i);
    }
    EXPECT_EQ(i, 32u);
}

TEST(AttributeContentTest, test_fill)
{
    Config cfg(BasicType::INT32, CollectionType::ARRAY);
    AttributeVector::SP av = AttributeFactory::createAttribute("aint32", cfg);
    av->addDocs(2);
    IntegerAttribute * ia = static_cast<IntegerAttribute *>(av.get());
    ia->append(0, 10, 0);
    ia->append(1, 20, 0);
    ia->append(1, 30, 0);
    av->commit();
    const IAttributeVector & iav = *av.get();
    IntegerContent buf;
    buf.fill(iav, 0);
    EXPECT_EQ(1u, buf.size());
    EXPECT_EQ(10, buf[0]);
    buf.fill(iav, 1);
    EXPECT_EQ(2u, buf.size());
    EXPECT_EQ(20, buf[0]);
    EXPECT_EQ(30, buf[1]);
    buf.fill(iav, 0);
    EXPECT_EQ(1u, buf.size());
    EXPECT_EQ(10, buf[0]);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
