// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/util/stringutil.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/fieldvalue/serializablearray.h>
#include <iostream>
#include <vespa/vespalib/util/macro.h>
#include <vespa/document/util/bufferexceptions.h>
#include <gtest/gtest.h>

using namespace document;

namespace {

template <typename S>
void assign(S &lhs, const S &rhs)
{
    lhs = rhs;
}

}

TEST(ByteBuffer_Test, test_constructors)
{
    ByteBuffer* simple=new ByteBuffer();
    delete simple;

    ByteBuffer* less_simple=new ByteBuffer("hei",3);
    EXPECT_TRUE(strcmp(less_simple->getBufferAtPos(),"hei")==0);
    delete less_simple;
}

TEST(ByteBuffer_Test, test_assignment_operator)
{
    try {
        ByteBuffer b1;
        ByteBuffer b2 = b1;

        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ(b1.getLimit(),b2.getLimit());
        EXPECT_EQ(b1.getRemaining(),b2.getRemaining());

    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }

    try {
        ByteBuffer b1(100);
        b1.putInt(1);
        b1.putInt(2);

        ByteBuffer b2 = b1;

        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ(b1.getLimit(),b2.getLimit());
        EXPECT_EQ(b1.getRemaining(),b2.getRemaining());

        int test = 0;
        b2.flip();
        b2.getInt(test);
        EXPECT_EQ(1,test);
        b2.getInt(test);
        EXPECT_EQ(2,test);


        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ((size_t) 8,b2.getLimit());
        EXPECT_EQ((size_t) 0,b2.getRemaining());

        // Test Selfassignment == no change
        //
        assign(b2, b2);


        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ((size_t) 8,b2.getLimit());
        EXPECT_EQ((size_t) 0,b2.getRemaining());

        ByteBuffer b3;
        // Empty
        b2 = b3;

        EXPECT_EQ((size_t) 0,b2.getPos());
        EXPECT_EQ((size_t) 0,b2.getLength());
        EXPECT_EQ((size_t) 0,b2.getLimit());
        EXPECT_EQ((size_t) 0,b2.getRemaining());

    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }
}

TEST(ByteBuffer_Test, test_copy_constructor)
{
    try {
        // Empty buffer first
        ByteBuffer b1;
        ByteBuffer b2(b1);

        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ(b1.getLimit(),b2.getLimit());
        EXPECT_EQ(b1.getRemaining(),b2.getRemaining());

    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }

    try {
        ByteBuffer b1(100);
        b1.putInt(1);
        b1.putInt(2);
        ByteBuffer b2(b1);


        EXPECT_EQ(b1.getPos(),b2.getPos());
        EXPECT_EQ(b1.getLength(),b2.getLength());
        EXPECT_EQ(b1.getLimit(),b2.getLimit());
        EXPECT_EQ(b1.getRemaining(),b2.getRemaining());

        int test = 0;
        b2.flip();
        b2.getInt(test);
        EXPECT_EQ(1,test);
        b2.getInt(test);
        EXPECT_EQ(2,test);

    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }
}

TEST(ByteBuffer_Test, test_putGetFlip)
{
    ByteBuffer* newBuf=new ByteBuffer(100);

    try {
        newBuf->putInt(10);
        int test;
        newBuf->flip();

        newBuf->getInt(test);
        EXPECT_TRUE(test==10);

        newBuf->clear();
        newBuf->putDouble(3.35);
        newBuf->flip();
        EXPECT_TRUE(newBuf->getRemaining()==sizeof(double));
        double test2;
        newBuf->getDouble(test2);
        EXPECT_TRUE(test2==3.35);

        newBuf->clear();
        newBuf->putBytes("heisann",8);
        newBuf->putInt(4);
        EXPECT_TRUE(newBuf->getPos()==12);
        EXPECT_TRUE(newBuf->getLength()==100);
        newBuf->flip();
        EXPECT_TRUE(newBuf->getRemaining()==12);

        char testStr[12];
        newBuf->getBytes(testStr, 8);
        EXPECT_TRUE(strcmp(testStr,"heisann")==0);
        newBuf->getInt(test);
        EXPECT_TRUE(test==4);
    } catch (std::exception &e) {
        FAIL() << "Unexpected exception at " << VESPA_STRLOC << ": \"" << e.what() << "\"";
    }
    delete newBuf;
}


TEST(ByteBuffer_Test, test_NumberEncodings)
{
    ByteBuffer* buf=new ByteBuffer(1024);

    // Check 0
    buf->putInt1_2_4Bytes(124);
    buf->putInt2_4_8Bytes(124);
    buf->putInt1_4Bytes(124);
    // Check 1
    buf->putInt1_2_4Bytes(127);
    buf->putInt2_4_8Bytes(127);
    buf->putInt1_4Bytes(127);
    // Check 2
    buf->putInt1_2_4Bytes(128);
    buf->putInt2_4_8Bytes(128);
    buf->putInt1_4Bytes(128);
    // Check 3
    buf->putInt1_2_4Bytes(255);
    buf->putInt2_4_8Bytes(255);
    buf->putInt1_4Bytes(255);
    // Check 4
    buf->putInt1_2_4Bytes(256);
    buf->putInt2_4_8Bytes(256);
    buf->putInt1_4Bytes(256);
    // Check 5
    buf->putInt1_2_4Bytes(0);
    buf->putInt2_4_8Bytes(0);
    buf->putInt1_4Bytes(0);
    // Check 6
    buf->putInt1_2_4Bytes(1);
    buf->putInt2_4_8Bytes(1);
    buf->putInt1_4Bytes(1);

    // Check 7
    try {
        buf->putInt1_2_4Bytes(0x7FFFFFFF);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }
    buf->putInt2_4_8Bytes(0x7FFFFFFFll);
    buf->putInt1_4Bytes(0x7FFFFFFF);

    try {
        buf->putInt2_4_8Bytes(0x7FFFFFFFFFFFFFFFll);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }

    buf->putInt1_2_4Bytes(0x7FFF);
    // Check 8
    buf->putInt2_4_8Bytes(0x7FFFll);
    buf->putInt1_4Bytes(0x7FFF);
    buf->putInt1_2_4Bytes(0x7F);
    // Check 9
    buf->putInt2_4_8Bytes(0x7Fll);
    buf->putInt1_4Bytes(0x7F);

    try {
        buf->putInt1_2_4Bytes(-1);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt2_4_8Bytes(-1);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt1_4Bytes(-1);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }

    try {
        buf->putInt1_2_4Bytes(-0x7FFFFFFF);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt2_4_8Bytes(-0x7FFFFFFF);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt1_4Bytes(-0x7FFFFFFF);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }

    try {
        buf->putInt2_4_8Bytes(-0x7FFFFFFFFFFFFFFFll);
        FAIL() << "Expected input out of range exception";
    } catch (InputOutOfRangeException& e) { }

    uint32_t endWritePos = buf->getPos();
    buf->setPos(0);

    int32_t tmp32;
    int64_t tmp64;

    // Check 0
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(124, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)124, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(124, tmp32);
    // Check 1
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(127, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)127, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(127, tmp32);
    // Check 2
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(128, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)128, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(128, tmp32);
    // Check 3
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(255, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)255, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(255, tmp32);
    // Check 4
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(256, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)256, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(256, tmp32);
    // Check 5
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(0, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)0, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(0, tmp32);
    // Check 6
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(1, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)1, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(1, tmp32);
    // Check 7
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)0x7FFFFFFF, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(0x7FFFFFFF, tmp32);
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(0x7FFF, tmp32);
    // Check 8
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)0x7FFF, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(0x7FFF, tmp32);
    buf->getInt1_2_4Bytes(tmp32);
    EXPECT_EQ(0x7F, tmp32);
    // Check 9
    buf->getInt2_4_8Bytes(tmp64);
    EXPECT_EQ((int64_t)0x7F, tmp64);
    buf->getInt1_4Bytes(tmp32);
    EXPECT_EQ(0x7F, tmp32);

    uint32_t endReadPos = buf->getPos();
    EXPECT_EQ(endWritePos, endReadPos);

    delete buf;
}

TEST(ByteBuffer_Test, test_NumberLengths)
{
    ByteBuffer b;
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(0));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(1));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(4));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(31));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(126));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_4Bytes(127));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_4Bytes(128));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_4Bytes(129));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_4Bytes(255));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_4Bytes(256));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_4Bytes(0x7FFFFFFF));

    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(0));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(1));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(4));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(31));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(126));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(127));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(128));
    EXPECT_EQ((size_t) 2, b.getSerializedSize2_4_8Bytes(32767));
    EXPECT_EQ((size_t) 4, b.getSerializedSize2_4_8Bytes(32768));
    EXPECT_EQ((size_t) 4, b.getSerializedSize2_4_8Bytes(32769));
    EXPECT_EQ((size_t) 4, b.getSerializedSize2_4_8Bytes(1030493));
    EXPECT_EQ((size_t) 4, b.getSerializedSize2_4_8Bytes(0x3FFFFFFF));
    EXPECT_EQ((size_t) 8, b.getSerializedSize2_4_8Bytes(0x40000000));
    EXPECT_EQ((size_t) 8, b.getSerializedSize2_4_8Bytes(0x40000001));

    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(0));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(1));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(4));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(31));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(126));
    EXPECT_EQ((size_t) 1, b.getSerializedSize1_2_4Bytes(127));
    EXPECT_EQ((size_t) 2, b.getSerializedSize1_2_4Bytes(128));
    EXPECT_EQ((size_t) 2, b.getSerializedSize1_2_4Bytes(16383));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_2_4Bytes(16384));
    EXPECT_EQ((size_t) 4, b.getSerializedSize1_2_4Bytes(16385));
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
    EXPECT_EQ(0, strcmp(copy.get(0).c_str(), array.get(0).c_str()));
    EXPECT_EQ(16ul, sizeof(SerializableArray::Entry));
}
