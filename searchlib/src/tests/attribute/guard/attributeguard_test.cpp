// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace search {

TEST(AttributeGuardTest, test_attribute_guard)
{
    AttributeVector::SP ssattr(new SingleStringExtAttribute("ss1"));
    AttributeGuard guard(ssattr);
    EXPECT_TRUE(guard.valid());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
