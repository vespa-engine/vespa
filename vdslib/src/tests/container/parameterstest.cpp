// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/container/parameters.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::GrowableByteBuffer;
using document::ByteBuffer;
using namespace vdslib;

TEST(ParametersTest, test_parameters)
{
    Parameters par;
    par.set("fast", "overture");
    par.set("overture", "yahoo");
    par.set("number", 6);
    par.set("int64_t", INT64_C(8589934590));
    par.set("double", 0.25);

    GrowableByteBuffer buffer;
    par.serialize(buffer);

    ByteBuffer bBuf(buffer.getBuffer(), buffer.position());
    Parameters par2(bBuf);

    EXPECT_EQ(vespalib::stringref("overture"), par2.get("fast"));
    EXPECT_EQ(vespalib::stringref("yahoo"), par2.get("overture"));
    std::string stringDefault = "wayne corp";
    int numberDefault = 123;
    int64_t int64Default = 456;
    double doubleDefault = 0.5;
    EXPECT_EQ(6, par2.get("number", numberDefault));
    EXPECT_EQ(INT64_C(8589934590), par2.get("int64_t", int64Default));
    EXPECT_DOUBLE_EQ(0.25, par2.get("double", doubleDefault));

    EXPECT_EQ(stringDefault, par2.get("nonexistingstring", stringDefault));
    EXPECT_EQ(numberDefault, par2.get("nonexistingnumber", numberDefault));
    EXPECT_EQ(int64Default,  par2.get("nonexistingint64_t", int64Default));
    EXPECT_EQ(doubleDefault, par2.get("nonexistingdouble", doubleDefault));

}
