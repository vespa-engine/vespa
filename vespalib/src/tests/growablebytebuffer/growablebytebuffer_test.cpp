// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

using namespace vespalib;

class Test : public TestApp
{
public:
    void testGrowing();
    int Main() override;
};

void
Test::testGrowing()
{
    GrowableByteBuffer buf(10);

    buf.putInt(3);
    buf.putInt(7);
    buf.putLong(1234);
    buf.putDouble(1234);
    buf.putString("hei der");

    EXPECT_EQUAL(35u, buf.position());
}

int
Test::Main()
{
    TEST_INIT("guard_test");
    testGrowing();
    TEST_DONE();
}

TEST_APPHOOK(Test)
