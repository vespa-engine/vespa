// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/select/orderingselector.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/testdocrepo.h>

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/select/parser.h>
#include <memory>

using document::select::Node;
using document::select::Parser;

namespace document {

struct OrderingSelectorTest : public CppUnit::TestFixture {
    void testSimple();

    CPPUNIT_TEST_SUITE(OrderingSelectorTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(OrderingSelectorTest);

#define ASSERT_MATCH(expression, ordering, correct)     \
{ \
    BucketIdFactory idfactory; \
    TestDocRepo repo; \
    OrderingSelector selector;                                \
    Parser parser(repo.getTypeRepo(), idfactory); \
    std::unique_ptr<Node> node(parser.parse(expression)); \
    CPPUNIT_ASSERT(node.get() != 0); \
    OrderingSpecification::UP spec = selector.select(*node, ordering); \
    if (spec.get() == NULL && correct.get() == NULL) { \
        return;\
    }\
    if (spec.get() == NULL && correct.get() != NULL) { \
        CPPUNIT_ASSERT_MSG(std::string("Was NULL, expected ") + correct->toString(), false); \
    } \
    if (correct.get() == NULL && spec.get() != NULL) { \
        CPPUNIT_ASSERT_MSG(std::string("Expected NULL, was ") + spec->toString(), false); \
    } \
    CPPUNIT_ASSERT_EQUAL(*correct, *spec); \
}

void OrderingSelectorTest::testSimple()
{
    ASSERT_MATCH("id.order(10,10) < 100", OrderingSpecification::DESCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::DESCENDING, (long)99, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) <= 100", OrderingSpecification::DESCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::DESCENDING, (long)100, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) > 100", OrderingSpecification::DESCENDING, OrderingSpecification::UP());

    ASSERT_MATCH("id.order(10,10) > 100", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)101, (short)10, (short)10)));

    ASSERT_MATCH("id.user==1234 AND id.order(10,10) > 100", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)101, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) >= 100", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)100, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) == 100", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)100, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) = 100", OrderingSpecification::DESCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::DESCENDING, (long)100, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) > 30 AND id.order(10,10) < 100", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)31, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) > 30 AND id.order(10,10) < 100", OrderingSpecification::DESCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::DESCENDING, (long)99, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) > 30 OR id.order(10,10) > 70", OrderingSpecification::ASCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::ASCENDING, (long)31, (short)10, (short)10)));

    ASSERT_MATCH("id.order(10,10) < 30 OR id.order(10,10) < 70", OrderingSpecification::DESCENDING,
                        OrderingSpecification::UP(
                                new OrderingSpecification(OrderingSpecification::DESCENDING, (long)69, (short)10, (short)10)));
}


}
