// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

using namespace vespalib;

TEST(DataBufferTest, test_basic)
{
    DataBuffer a(50);
    EXPECT_EQ(256u, a.getBufSize());
    EXPECT_EQ(a.getFreeLen(), a.getBufSize());
    a.ensureFree(1000);
    EXPECT_EQ(1_Ki, a.getBufSize());
    EXPECT_EQ(a.getFreeLen(), a.getBufSize());
    EXPECT_EQ(0u, a.getDeadLen());
    EXPECT_EQ(0u, a.getDataLen());
    EXPECT_EQ(a.getData(), a.getDead());
    EXPECT_EQ(a.getData(), a.getFree());
    EXPECT_EQ(a.getBufSize(), a.getFreeLen());
    a.assertValid();

    a.writeInt16(7);
    EXPECT_EQ(0u, a.getDeadLen());
    EXPECT_EQ(2u, a.getDataLen());
    EXPECT_EQ(a.getBufSize()-2, a.getFreeLen());
    EXPECT_EQ(a.getData(), a.getDead());
    EXPECT_EQ(a.getData()+2, a.getFree());
    a.clear();
    EXPECT_EQ(0u, a.getDeadLen());
    EXPECT_EQ(0u, a.getDataLen());
    EXPECT_EQ(a.getBufSize(), a.getFreeLen());

    a.writeInt8(0xaau);
    EXPECT_EQ(1u, a.getDataLen());
    EXPECT_EQ(0xaau, a.peekInt8(0));
    EXPECT_EQ(1u, a.getDataLen());
    EXPECT_EQ(0xaau, a.readInt8());
    EXPECT_EQ(0u, a.getDataLen());

    a.writeInt16(0xaabbu);
    EXPECT_EQ(2u, a.getDataLen());
    EXPECT_EQ(0xaabbu, a.peekInt16(0));
    EXPECT_EQ(2u, a.getDataLen());
    EXPECT_EQ(0xaabbu, a.readInt16());
    EXPECT_EQ(0u, a.getDataLen());
    a.writeInt16(0xaabbu);
    EXPECT_EQ(2u, a.getDataLen());
    EXPECT_EQ(0xbbaau, a.peekInt16Reverse(0));
    EXPECT_EQ(2u, a.getDataLen());
    EXPECT_EQ(0xbbaau, a.readInt16Reverse());
    EXPECT_EQ(0u, a.getDataLen());

    a.writeInt32(0xaabbccddu);
    EXPECT_EQ(4u, a.getDataLen());
    EXPECT_EQ(0xaabbccddu, a.peekInt32(0));
    EXPECT_EQ(4u, a.getDataLen());
    EXPECT_EQ(0xaabbccddu, a.readInt32());
    EXPECT_EQ(0u, a.getDataLen());
    a.writeInt32(0xaabbccddu);
    EXPECT_EQ(4u, a.getDataLen());
    EXPECT_EQ(0xddccbbaau, a.peekInt32Reverse(0));
    EXPECT_EQ(4u, a.getDataLen());
    EXPECT_EQ(0xddccbbaau, a.readInt32Reverse());
    EXPECT_EQ(0u, a.getDataLen());

    a.writeInt64(0xaabbccddeeff9988ul);
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(0xaabbccddeeff9988ul, a.peekInt64(0));
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(0xaabbccddeeff9988ul, a.readInt64());
    EXPECT_EQ(0u, a.getDataLen());
    a.writeInt64(0xaabbccddeeff9988ul);
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(0x8899ffeeddccbbaaul, a.peekInt64Reverse(0));
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(0x8899ffeeddccbbaaul, a.readInt64Reverse());
    EXPECT_EQ(0u, a.getDataLen());

    a.writeFloat(8.9f);
    EXPECT_EQ(4u, a.getDataLen());
    EXPECT_EQ(8.9f, a.readFloat());
    EXPECT_EQ(0u, a.getDataLen());

    a.writeDouble(8.9);
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(8.9, a.readDouble());
    EXPECT_EQ(0u, a.getDataLen());

    const char *c = "abc";
    char b[3];
    a.writeBytes(c, 3);
    EXPECT_EQ(3u, a.getDataLen());
    EXPECT_EQ(0, memcmp(c, a.getData(), a.getDataLen()));
    a.peekBytes(b, 3, 0);
    EXPECT_EQ(3u, a.getDataLen());
    EXPECT_EQ(0, memcmp(c, b, sizeof(b)));
    a.readBytes(b, sizeof(b));
    EXPECT_EQ(0u, a.getDataLen());
    EXPECT_EQ(0, memcmp(c, b, sizeof(b)));

    a.writeInt64(67);
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_FALSE(a.shrink(1025));
    EXPECT_FALSE(a.shrink(7));
    EXPECT_TRUE(a.shrink(16));
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(16u, a.getBufSize());

    a.writeInt64(89);
    EXPECT_EQ(16u, a.getDataLen());
    EXPECT_EQ(16u, a.getBufSize());
    EXPECT_EQ(0u, a.getDeadLen());
    EXPECT_EQ(67u, a.readInt64());
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(8u, a.getDeadLen());
    EXPECT_EQ(16u, a.getBufSize());
    a.pack(16);
    EXPECT_EQ(8u, a.getDataLen());
    EXPECT_EQ(0u, a.getDeadLen());
    EXPECT_EQ(256u, a.getBufSize());
    EXPECT_EQ(89u, a.readInt64());
    EXPECT_EQ(0u, a.getDataLen());
    EXPECT_EQ(256u, a.getBufSize());
}

GTEST_MAIN_RUN_ALL_TESTS()
