// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for fieldvalue.

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("fieldvalue_test");

using namespace document;

namespace {

TEST("require that StringFieldValue can be assigned primitives") {
    StringFieldValue val;
    val = "foo";
    EXPECT_EQUAL("foo", val.getValue());
}

TEST("require that FieldValues does not change their storage size.") {
    EXPECT_EQUAL(16u, sizeof(FieldValue));
    EXPECT_EQUAL(16u, sizeof(IntFieldValue));
    EXPECT_EQUAL(24u, sizeof(LongFieldValue));
    EXPECT_EQUAL(104u, sizeof(StringFieldValue));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
