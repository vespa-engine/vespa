// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/attribute_operation.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_operation_test");

using proton::matching::AttributeOperation;
using search::attribute::BasicType;

TEST("test legal operations on integer attribute") {
    const std::vector<uint32_t> docs;
    for (auto operation : {"++", "--", "+=7", "+= 7", "-=7", "*=8", "/=6", "%=7", "=3", "=-3"}) {
        EXPECT_TRUE(AttributeOperation::create(BasicType::INT64, operation, docs));
    }
}

TEST("test illegal operations on integer attribute") {
    const std::vector<uint32_t> docs;
    for (auto operation : {"", "-", "+", "+=7.1", "=a", "*=8.z", "=", "=.7"}) {
        EXPECT_FALSE(AttributeOperation::create(BasicType::INT64, operation, docs));
    }
}

TEST("test legal operations on float attribute") {
    const std::vector<uint32_t> docs;
    for (auto operation : {"++", "--", "+=7", "+= 7", "-=7", "*=8", "*=8.7", "*=.7", "/=6", "%=7", "=3", "=-3"}) {
        EXPECT_TRUE(AttributeOperation::create(BasicType::DOUBLE, operation, docs));
    }
}

TEST("test illegal operations on float attribute") {
    const std::vector<uint32_t> docs;
    for (auto operation : {"", "-", "+", "=a", "*=8.z", "="}) {
        EXPECT_FALSE(AttributeOperation::create(BasicType::DOUBLE, operation, docs));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
