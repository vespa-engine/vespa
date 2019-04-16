// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/select/orderingselector.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/testdocrepo.h>

#include <vespa/document/select/parser.h>
#include <memory>
#include <gtest/gtest.h>

using document::select::Node;
using document::select::Parser;

namespace document {

#define ASSERT_MATCH(expression, ordering, correct)     \
{ \
    BucketIdFactory idfactory; \
    TestDocRepo repo; \
    OrderingSelector selector;                                \
    Parser parser(repo.getTypeRepo(), idfactory); \
    std::unique_ptr<Node> node(parser.parse(expression)); \
    ASSERT_TRUE(node); \
    OrderingSpecification::UP spec = selector.select(*node, ordering); \
    if (spec.get() == NULL && correct.get() == NULL) { \
        return;\
    }\
    if (spec.get() == NULL && correct.get() != NULL) { \
        FAIL() << "Was NULL, expected " << correct->toString(); \
    } \
    if (correct.get() == NULL && spec.get() != NULL) { \
        FAIL() << "Expected NULL, was " << spec->toString();  \
    } \
    EXPECT_EQ(*correct, *spec);      \
}

TEST(OrderingSelectorTest, testSimple)
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
