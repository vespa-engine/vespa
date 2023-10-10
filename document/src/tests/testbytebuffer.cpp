// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/util/stringutil.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/fieldvalue/serializablearray.h>
#include <vespa/document/util/bufferexceptions.h>
#include <vespa/vespalib/util/macro.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <gtest/gtest.h>


using namespace document;
using vespalib::GrowableByteBuffer;

namespace {

template <typename S>
void assign(S &lhs, const S &rhs)
{
    lhs = rhs;
}

}

TEST(ByteBuffer_Test, test_constructors)
{
    ByteBuffer less_simple("hei",3);
    EXPECT_TRUE(strcmp(less_simple.getBufferAtPos(),"hei")==0);
}

TEST(ByteBuffer_Test, test_copy_constructor)
{
    try {
        GrowableByteBuffer gb(100);
        gb.putInt(1);
        gb.putInt(2);
        ByteBuffer b1(gb.getBuffer(), gb.position());
        ByteBuffer b2(b1);


        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ(b1.getRemaining(),b2.getRemaining());

        int test = 0;
        b2.getIntNetwork(test);
        EXPECT_EQ(1,test);
        b2.getIntNetwork(test);
        EXPECT_EQ(2,test);

    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }
}

TEST(ByteBuffer_Test, test_SerializableArray)
{
    SerializableArray array;
    array.set(0,"http",4);
    EXPECT_EQ(4ul, array.get(0).size());
    SerializableArray copy(array);
    EXPECT_EQ(4ul, array.get(0).size());
    EXPECT_EQ(copy.get(0).size(), array.get(0).size());
    EXPECT_TRUE(copy.get(0).c_str() != array.get(0).c_str());
    EXPECT_EQ(0, strncmp(copy.get(0).c_str(), array.get(0).c_str(), 4));
    EXPECT_EQ(16ul, sizeof(SerializableArray::Entry));
}
