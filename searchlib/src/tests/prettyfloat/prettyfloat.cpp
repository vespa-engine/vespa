// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("prettyfloat_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchlib/common/hitrank.h>

using namespace search;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("prettyfloat_test");
    {
        RawBuf buf(5000);
        SignedHitRank rank = 10;
        buf.addSignedHitRank(rank);
        *buf.GetWritableFillPos() = '\0';
        EXPECT_EQUAL(std::string("10"), buf.GetDrainPos());
    }
    {
        RawBuf buf(5000);
        HitRank rank = 10;
        buf.addHitRank(rank);
        *buf.GetWritableFillPos() = '\0';
        EXPECT_EQUAL(std::string("10"), buf.GetDrainPos());
    }
    TEST_DONE();
}
