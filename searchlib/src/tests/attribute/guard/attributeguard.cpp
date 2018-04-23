// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("attributeguard_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/extendableattributes.h>

namespace search {

class AttributeGuardTest : public vespalib::TestApp
{
public:
    int Main() override;
};

int
AttributeGuardTest::Main()
{
    TEST_INIT("attributeguard_test");


    AttributeVector::SP ssattr(new SingleStringExtAttribute("ss1"));
    AttributeGuard guard(ssattr);
    EXPECT_TRUE(guard.valid());

    TEST_DONE();
}

}

TEST_APPHOOK(search::AttributeGuardTest);
