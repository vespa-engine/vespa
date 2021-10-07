// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for fieldvalue.

#include <vespa/log/log.h>
LOG_SETUP("fieldvalue_test");

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>

#include <vespa/vespalib/testkit/testapp.h>

using namespace document;

namespace {

TEST("require that StringFieldValue can be assigned primitives") {
    StringFieldValue val;
    val = "foo";
    EXPECT_EQUAL("foo", val.getValue());
    val = 1;
    EXPECT_EQUAL("1", val.getValue());
    val = static_cast<int64_t>(2);
    EXPECT_EQUAL("2", val.getValue());
    val = 3.0f;
    EXPECT_EQUAL("3", val.getValue());
    val = 4.0;
    EXPECT_EQUAL("4", val.getValue());
}

TEST("require that FieldValues does not change their storage size.") {
    EXPECT_EQUAL(8u, sizeof(FieldValue));
    EXPECT_EQUAL(16u, sizeof(IntFieldValue));
    EXPECT_EQUAL(24u, sizeof(LongFieldValue));
    EXPECT_EQUAL(104u, sizeof(StringFieldValue));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
