// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/util/stringutil.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/fieldvalue/serializablearray.h>
#include "heapdebugger.h"
#include <iostream>
#include "testbytebuffer.h"
#include <vespa/vespalib/util/macro.h>
#include <vespa/document/util/bufferexceptions.h>

using namespace document;

CPPUNIT_TEST_SUITE_REGISTRATION( ByteBuffer_Test );

namespace {

template <typename S>
void assign(S &lhs, const S &rhs)
{
    lhs = rhs;
}

}

void ByteBuffer_Test::setUp()
{
    enableHeapUsageMonitor();
}


void ByteBuffer_Test::test_constructors()
{
    size_t MemUsedAtEntry=getHeapUsage();


    ByteBuffer* simple=new ByteBuffer();
    delete simple;

    ByteBuffer* less_simple=new ByteBuffer("hei",3);
    CPPUNIT_ASSERT(strcmp(less_simple->getBufferAtPos(),"hei")==0);
    delete less_simple;

    CPPUNIT_ASSERT(getHeapUsage()-MemUsedAtEntry == 0);
}

void ByteBuffer_Test::test_assignment_operator()
{
    size_t MemUsedAtEntry=getHeapUsage();
    try {
        ByteBuffer b1;
        ByteBuffer b2 = b1;

        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL(b1.getLimit(),b2.getLimit());
        CPPUNIT_ASSERT_EQUAL(b1.getRemaining(),b2.getRemaining());

    } catch (std::exception &e) {
        fprintf(stderr,"Unexpected exception at %s: \"%s\"\n", VESPA_STRLOC.c_str(),e.what());
        CPPUNIT_ASSERT(false);
    }

    try {
        ByteBuffer b1(100);
        b1.putInt(1);
        b1.putInt(2);

        ByteBuffer b2 = b1;

        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL(b1.getLimit(),b2.getLimit());
        CPPUNIT_ASSERT_EQUAL(b1.getRemaining(),b2.getRemaining());

        int test = 0;
        b2.flip();
        b2.getInt(test);
        CPPUNIT_ASSERT_EQUAL(1,test);
        b2.getInt(test);
        CPPUNIT_ASSERT_EQUAL(2,test);


        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL((size_t) 8,b2.getLimit());
        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getRemaining());

        // Test Selfassignment == no change
        //
        assign(b2, b2);


        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL((size_t) 8,b2.getLimit());
        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getRemaining());

        ByteBuffer b3;
        // Empty
        b2 = b3;

        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getPos());
        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getLength());
        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getLimit());
        CPPUNIT_ASSERT_EQUAL((size_t) 0,b2.getRemaining());

    } catch (std::exception &e) {
        fprintf(stderr,"Unexpected exception at %s: \"%s\"\n", VESPA_STRLOC.c_str(),e.what());
        CPPUNIT_ASSERT(false);
    }

    CPPUNIT_ASSERT(getHeapUsage()-MemUsedAtEntry == 0);

}

void ByteBuffer_Test::test_copy_constructor()
{
    size_t MemUsedAtEntry=getHeapUsage();
    try {
        // Empty buffer first
        ByteBuffer b1;
        ByteBuffer b2(b1);

        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL(b1.getLimit(),b2.getLimit());
        CPPUNIT_ASSERT_EQUAL(b1.getRemaining(),b2.getRemaining());

    } catch (std::exception &e) {
        fprintf(stderr,"Unexpected exception at %s: %s\n", VESPA_STRLOC.c_str(),e.what());
        CPPUNIT_ASSERT(false);
    }

    try {
        ByteBuffer b1(100);
        b1.putInt(1);
        b1.putInt(2);
        ByteBuffer b2(b1);


        CPPUNIT_ASSERT_EQUAL(b1.getPos(),b2.getPos());
        CPPUNIT_ASSERT_EQUAL(b1.getLength(),b2.getLength());
        CPPUNIT_ASSERT_EQUAL(b1.getLimit(),b2.getLimit());
        CPPUNIT_ASSERT_EQUAL(b1.getRemaining(),b2.getRemaining());

        int test = 0;
        b2.flip();
        b2.getInt(test);
        CPPUNIT_ASSERT_EQUAL(1,test);
        b2.getInt(test);
        CPPUNIT_ASSERT_EQUAL(2,test);

    } catch (std::exception &e) {
        fprintf(stderr,"Unexpected exception at %s: %s\n", VESPA_STRLOC.c_str(),e.what());
        CPPUNIT_ASSERT(false);
    }

    CPPUNIT_ASSERT(getHeapUsage()-MemUsedAtEntry == 0);

}

void ByteBuffer_Test::test_slice()
{
    ByteBuffer* newBuf=ByteBuffer::copyBuffer("hei der",8);

    ByteBuffer* slice = new ByteBuffer;
    slice->sliceFrom(*newBuf, 0,newBuf->getLength());
    delete newBuf;
    newBuf = NULL;

    CPPUNIT_ASSERT(strcmp(slice->getBufferAtPos(),"hei der")==0);

    ByteBuffer* slice2 = new ByteBuffer;
    slice2->sliceFrom(*slice, 4, slice->getLength());
    delete slice;
    slice = NULL;

    CPPUNIT_ASSERT(strcmp(slice2->getBufferAtPos(),"der")==0);
    CPPUNIT_ASSERT(strcmp(slice2->getBuffer(),"hei der")==0);
    delete slice2;
    slice2 = NULL;

    ByteBuffer* newBuf2=new ByteBuffer("hei der", 8);
    ByteBuffer* slice3=new ByteBuffer;
    ByteBuffer* slice4=new ByteBuffer;

    slice3->sliceFrom(*newBuf2, 4, newBuf2->getLength());
    slice4->sliceFrom(*newBuf2, 0, newBuf2->getLength());
    delete newBuf2;
    newBuf2 = NULL;

    CPPUNIT_ASSERT(strcmp(slice3->getBufferAtPos(),"der")==0);
    CPPUNIT_ASSERT(strcmp(slice4->getBuffer(),"hei der")==0);

    delete slice3;
    slice3 = NULL;

    CPPUNIT_ASSERT(strcmp(slice4->getBuffer(),"hei der")==0);

    delete slice4;
    slice4 = NULL;
}

void ByteBuffer_Test::test_slice2()
{
    size_t MemUsedAtEntry=getHeapUsage();

    ByteBuffer* newBuf=ByteBuffer::copyBuffer("hei der",8);

    ByteBuffer slice;
    slice.sliceFrom(*newBuf, 0, newBuf->getLength());

    delete newBuf;
    newBuf = NULL;

    CPPUNIT_ASSERT(strcmp(slice.getBufferAtPos(),"hei der")==0);

    ByteBuffer slice2;
    slice2.sliceFrom(slice, 4, slice.getLength());

    CPPUNIT_ASSERT(strcmp(slice2.getBufferAtPos(),"der")==0);
    CPPUNIT_ASSERT(strcmp(slice2.getBuffer(),"hei der")==0);

    ByteBuffer* newBuf2=new ByteBuffer("hei der", 8);

    slice.sliceFrom(*newBuf2, 4, newBuf2->getLength());
    slice2.sliceFrom(*newBuf2, 0, newBuf2->getLength());
    delete newBuf2;
    newBuf2 = NULL;

    CPPUNIT_ASSERT(strcmp(slice.getBufferAtPos(),"der")==0);
    CPPUNIT_ASSERT(strcmp(slice2.getBuffer(),"hei der")==0);

    CPPUNIT_ASSERT(getHeapUsage()-MemUsedAtEntry == 0);
}


void ByteBuffer_Test::test_putGetFlip()
{
    ByteBuffer* newBuf=new ByteBuffer(100);

    try {
        newBuf->putInt(10);
        int test;
        newBuf->flip();

        newBuf->getInt(test);
        CPPUNIT_ASSERT(test==10);

        newBuf->clear();
        newBuf->putDouble(3.35);
        newBuf->flip();
        CPPUNIT_ASSERT(newBuf->getRemaining()==sizeof(double));
        double test2;
        newBuf->getDouble(test2);
        CPPUNIT_ASSERT(test2==3.35);

        newBuf->clear();
        newBuf->putBytes("heisann",8);
        newBuf->putInt(4);
        CPPUNIT_ASSERT(newBuf->getPos()==12);
        CPPUNIT_ASSERT(newBuf->getLength()==100);
        newBuf->flip();
        CPPUNIT_ASSERT(newBuf->getRemaining()==12);

        char testStr[12];
        newBuf->getBytes(testStr, 8);
        CPPUNIT_ASSERT(strcmp(testStr,"heisann")==0);
        newBuf->getInt(test);
        CPPUNIT_ASSERT(test==4);
    } catch (std::exception &e) {
        fprintf(stderr,"Unexpected exception at %s: %s\n", VESPA_STRLOC.c_str(),e.what());
        CPPUNIT_ASSERT(false);
    }
    delete newBuf;
}


void ByteBuffer_Test::test_NumberEncodings()
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
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }
    buf->putInt2_4_8Bytes(0x7FFFFFFFll);
    buf->putInt1_4Bytes(0x7FFFFFFF);

    try {
        buf->putInt2_4_8Bytes(0x7FFFFFFFFFFFFFFFll);
        CPPUNIT_ASSERT(false);
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
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt2_4_8Bytes(-1);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt1_4Bytes(-1);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }

    try {
        buf->putInt1_2_4Bytes(-0x7FFFFFFF);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt2_4_8Bytes(-0x7FFFFFFF);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }
    try {
        buf->putInt1_4Bytes(-0x7FFFFFFF);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }

    try {
        buf->putInt2_4_8Bytes(-0x7FFFFFFFFFFFFFFFll);
        CPPUNIT_ASSERT(false);
    } catch (InputOutOfRangeException& e) { }

    uint32_t endWritePos = buf->getPos();
    buf->setPos(0);

    int32_t tmp32;
    int64_t tmp64;

    // Check 0
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(124, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)124, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(124, tmp32);
    // Check 1
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(127, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)127, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(127, tmp32);
    // Check 2
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(128, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)128, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(128, tmp32);
    // Check 3
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(255, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)255, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(255, tmp32);
    // Check 4
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(256, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)256, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(256, tmp32);
    // Check 5
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)0, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0, tmp32);
    // Check 6
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(1, tmp32);
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)1, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(1, tmp32);
    // Check 7
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)0x7FFFFFFF, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0x7FFFFFFF, tmp32);
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0x7FFF, tmp32);
    // Check 8
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)0x7FFF, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0x7FFF, tmp32);
    buf->getInt1_2_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0x7F, tmp32);
    // Check 9
    buf->getInt2_4_8Bytes(tmp64);
    CPPUNIT_ASSERT_EQUAL((int64_t)0x7F, tmp64);
    buf->getInt1_4Bytes(tmp32);
    CPPUNIT_ASSERT_EQUAL(0x7F, tmp32);

    uint32_t endReadPos = buf->getPos();
    CPPUNIT_ASSERT_EQUAL(endWritePos, endReadPos);

    delete buf;
}

void ByteBuffer_Test::test_NumberLengths()
{
    ByteBuffer b;
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(0));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(1));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(4));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(31));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(126));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_4Bytes(127));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_4Bytes(128));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_4Bytes(129));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_4Bytes(255));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_4Bytes(256));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_4Bytes(0x7FFFFFFF));

    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(0));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(1));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(4));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(31));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(126));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(127));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(128));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize2_4_8Bytes(32767));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize2_4_8Bytes(32768));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize2_4_8Bytes(32769));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize2_4_8Bytes(1030493));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize2_4_8Bytes(0x3FFFFFFF));
    CPPUNIT_ASSERT_EQUAL((size_t) 8, b.getSerializedSize2_4_8Bytes(0x40000000));
    CPPUNIT_ASSERT_EQUAL((size_t) 8, b.getSerializedSize2_4_8Bytes(0x40000001));

    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(0));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(1));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(4));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(31));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(126));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, b.getSerializedSize1_2_4Bytes(127));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize1_2_4Bytes(128));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, b.getSerializedSize1_2_4Bytes(16383));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_2_4Bytes(16384));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, b.getSerializedSize1_2_4Bytes(16385));
}

void ByteBuffer_Test::test_SerializableArray()
{
    SerializableArray array;
    array.set(0,"http",4);
    CPPUNIT_ASSERT_EQUAL(4ul, array.get(0).size());
    SerializableArray copy(array);
    CPPUNIT_ASSERT_EQUAL(4ul, array.get(0).size());
    CPPUNIT_ASSERT_EQUAL(copy.get(0).size(), array.get(0).size());
    CPPUNIT_ASSERT(copy.get(0).c_str() != array.get(0).c_str());
    CPPUNIT_ASSERT_EQUAL(0, strcmp(copy.get(0).c_str(), array.get(0).c_str()));
    CPPUNIT_ASSERT_EQUAL(16ul, sizeof(SerializableArray::Entry));
}
