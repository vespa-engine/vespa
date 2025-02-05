// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for fieldvalue.

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("fieldvalue_test");

using namespace document;

namespace {

TEST(FieldValueTest, require_that_StringFieldValue_can_be_assigned_primitives)
{
    StringFieldValue val;
    val = "foo";
    EXPECT_EQ("foo", val.getValue());
}

TEST(FieldValueTest, require_that_FieldValues_does_not_change_their_storage_size)
{
    EXPECT_EQ(16u, sizeof(FieldValue));
    EXPECT_EQ(16u, sizeof(IntFieldValue));
    EXPECT_EQ(24u, sizeof(LongFieldValue));
    EXPECT_EQ(40u + sizeof(std::string), sizeof(StringFieldValue));
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
