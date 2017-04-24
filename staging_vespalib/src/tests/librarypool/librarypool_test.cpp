// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/librarypool.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>
LOG_SETUP("librarypool_test");

using namespace vespalib;

class Test : public TestApp
{
public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("librarypool_test");
    LibraryPool p;
    ASSERT_TRUE(p.get("z") == NULL);
    p.loadLibrary("z");
    ASSERT_TRUE(p.get("z") != NULL);
    ASSERT_TRUE(p.get("z")->GetSymbol("some_symbol_that_is_not_there") == NULL);
    ASSERT_TRUE(p.get("z")->GetSymbol("compress") != NULL);
    try {
        p.loadLibrary("not_found");
        ASSERT_TRUE(false);
    } catch (const IllegalArgumentException & e) {
        ASSERT_TRUE(p.get("not_found") == NULL);
    }
    {
        const LibraryPool & c(p);
        ASSERT_TRUE(c.get("z") != NULL);
        ASSERT_TRUE(c.get("not_found") == NULL);
    }
    TEST_DONE();
}

TEST_APPHOOK(Test)
