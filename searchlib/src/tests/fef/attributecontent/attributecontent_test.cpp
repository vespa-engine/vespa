// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/log/log.h>
LOG_SETUP("attributecontent_test");

using namespace search::attribute;

namespace search {
namespace fef {

class Test : public vespalib::TestApp {
private:
    void testWriteAndRead();
    void testFill();

public:
    int Main() override;
};

void
Test::testWriteAndRead()
{
    typedef search::attribute::AttributeContent<uint32_t> UintContent;
    UintContent buf;
    EXPECT_EQUAL(buf.capacity(), 16u);
    EXPECT_EQUAL(buf.size(), 0u);

    uint32_t i;
    uint32_t * data;
    const uint32_t * itr;
    for (i = 0, data = buf.data(); i < 16; ++i, ++data) {
        *data = i;
    }
    buf.setSize(16);
    EXPECT_EQUAL(buf.size(), 16u);
    for (i = 0, itr = buf.begin(); itr != buf.end(); ++i, ++itr) {
        EXPECT_EQUAL(*itr, i);
        EXPECT_EQUAL(buf[i], i);
    }
    EXPECT_EQUAL(i, 16u);

    buf.allocate(10);
    EXPECT_EQUAL(buf.capacity(), 16u);
    EXPECT_EQUAL(buf.size(), 16u);
    buf.allocate(32);
    EXPECT_EQUAL(buf.capacity(), 32u);
    EXPECT_EQUAL(buf.size(), 0u);

    for (i = 0, data = buf.data(); i < 32; ++i, ++data) {
        *data = i;
    }
    buf.setSize(32);
    EXPECT_EQUAL(buf.size(), 32u);
    for (i = 0, itr = buf.begin(); itr != buf.end(); ++i, ++itr) {
        EXPECT_EQUAL(*itr, i);
        EXPECT_EQUAL(buf[i], i);
    }
    EXPECT_EQUAL(i, 32u);
}

void
Test::testFill()
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
    EXPECT_EQUAL(1u, buf.size());
    EXPECT_EQUAL(10, buf[0]);
    buf.fill(iav, 1);
    EXPECT_EQUAL(2u, buf.size());
    EXPECT_EQUAL(20, buf[0]);
    EXPECT_EQUAL(30, buf[1]);
    buf.fill(iav, 0);
    EXPECT_EQUAL(1u, buf.size());
    EXPECT_EQUAL(10, buf[0]);
}

int
Test::Main()
{
    TEST_INIT("attributecontent_test");

    testWriteAndRead();
    testFill();

    TEST_DONE();
}

} // namespace fef
} // namespace search

TEST_APPHOOK(search::fef::Test);
