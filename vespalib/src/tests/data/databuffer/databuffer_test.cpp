// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/data/databuffer.h>
#include <iostream>

using namespace vespalib;

class Test : public vespalib::TestApp {
private:
    void testBasic();
public:
    int Main() override {
        TEST_INIT("databuffer_test");

        testBasic();           TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

void
Test::testBasic()
{
    DataBuffer a(50);
    EXPECT_EQUAL(256u, a.getBufSize());
    EXPECT_EQUAL(a.getFreeLen(), a.getBufSize());
    a.ensureFree(1000);
    EXPECT_EQUAL(1_Ki, a.getBufSize());
    EXPECT_EQUAL(a.getFreeLen(), a.getBufSize());
    EXPECT_EQUAL(0u, a.getDeadLen());
    EXPECT_EQUAL(0u, a.getDataLen());
    EXPECT_EQUAL(a.getData(), a.getDead());
    EXPECT_EQUAL(a.getData(), a.getFree());
    EXPECT_EQUAL(a.getBufSize(), a.getFreeLen());
    a.assertValid();

    a.writeInt16(7);
    EXPECT_EQUAL(0u, a.getDeadLen());
    EXPECT_EQUAL(2u, a.getDataLen());
    EXPECT_EQUAL(a.getBufSize()-2, a.getFreeLen());
    EXPECT_EQUAL(a.getData(), a.getDead());
    EXPECT_EQUAL(a.getData()+2, a.getFree());
    a.clear();
    EXPECT_EQUAL(0u, a.getDeadLen());
    EXPECT_EQUAL(0u, a.getDataLen());
    EXPECT_EQUAL(a.getBufSize(), a.getFreeLen());

    a.writeInt8(0xaau);
    EXPECT_EQUAL(1u, a.getDataLen());    
    EXPECT_EQUAL(0xaau, a.peekInt8(0));
    EXPECT_EQUAL(1u, a.getDataLen());    
    EXPECT_EQUAL(0xaau, a.readInt8());
    EXPECT_EQUAL(0u, a.getDataLen());

    a.writeInt16(0xaabbu);
    EXPECT_EQUAL(2u, a.getDataLen());
    EXPECT_EQUAL(0xaabbu, a.peekInt16(0));
    EXPECT_EQUAL(2u, a.getDataLen());
    EXPECT_EQUAL(0xaabbu, a.readInt16());
    EXPECT_EQUAL(0u, a.getDataLen());
    a.writeInt16(0xaabbu);
    EXPECT_EQUAL(2u, a.getDataLen());
    EXPECT_EQUAL(0xbbaau, a.peekInt16Reverse(0));
    EXPECT_EQUAL(2u, a.getDataLen());
    EXPECT_EQUAL(0xbbaau, a.readInt16Reverse());
    EXPECT_EQUAL(0u, a.getDataLen());
    
    a.writeInt32(0xaabbccddu);
    EXPECT_EQUAL(4u, a.getDataLen());    
    EXPECT_EQUAL(0xaabbccddu, a.peekInt32(0));
    EXPECT_EQUAL(4u, a.getDataLen());    
    EXPECT_EQUAL(0xaabbccddu, a.readInt32());
    EXPECT_EQUAL(0u, a.getDataLen());
    a.writeInt32(0xaabbccddu);
    EXPECT_EQUAL(4u, a.getDataLen());    
    EXPECT_EQUAL(0xddccbbaau, a.peekInt32Reverse(0));
    EXPECT_EQUAL(4u, a.getDataLen());    
    EXPECT_EQUAL(0xddccbbaau, a.readInt32Reverse());
    EXPECT_EQUAL(0u, a.getDataLen());

    a.writeInt64(0xaabbccddeeff9988ul);
    EXPECT_EQUAL(8u, a.getDataLen());    
    EXPECT_EQUAL(0xaabbccddeeff9988ul, a.peekInt64(0));
    EXPECT_EQUAL(8u, a.getDataLen());    
    EXPECT_EQUAL(0xaabbccddeeff9988ul, a.readInt64());
    EXPECT_EQUAL(0u, a.getDataLen());
    a.writeInt64(0xaabbccddeeff9988ul);
    EXPECT_EQUAL(8u, a.getDataLen());
    EXPECT_EQUAL(0x8899ffeeddccbbaaul, a.peekInt64Reverse(0));
    EXPECT_EQUAL(8u, a.getDataLen());    
    EXPECT_EQUAL(0x8899ffeeddccbbaaul, a.readInt64Reverse());
    EXPECT_EQUAL(0u, a.getDataLen());

    a.writeFloat(8.9f);
    EXPECT_EQUAL(4u, a.getDataLen());    
    EXPECT_EQUAL(8.9f, a.readFloat());
    EXPECT_EQUAL(0u, a.getDataLen());

    a.writeDouble(8.9);
    EXPECT_EQUAL(8u, a.getDataLen());    
    EXPECT_EQUAL(8.9, a.readDouble());
    EXPECT_EQUAL(0u, a.getDataLen());

    const char *c = "abc";
    char b[3];
    a.writeBytes(c, 3);
    EXPECT_EQUAL(3u, a.getDataLen());
    EXPECT_EQUAL(0, memcmp(c, a.getData(), a.getDataLen()));
    a.peekBytes(b, 3, 0);
    EXPECT_EQUAL(3u, a.getDataLen());
    EXPECT_EQUAL(0, memcmp(c, b, sizeof(b)));
    a.readBytes(b, sizeof(b));
    EXPECT_EQUAL(0u, a.getDataLen());
    EXPECT_EQUAL(0, memcmp(c, b, sizeof(b)));

    a.writeInt64(67);
    EXPECT_EQUAL(8u, a.getDataLen());
    EXPECT_FALSE(a.shrink(1025));
    EXPECT_FALSE(a.shrink(7));
    EXPECT_TRUE(a.shrink(16));
    EXPECT_EQUAL(8u, a.getDataLen());
    EXPECT_EQUAL(16u, a.getBufSize());

    a.writeInt64(89);
    EXPECT_EQUAL(16u, a.getDataLen());
    EXPECT_EQUAL(16u, a.getBufSize());
    EXPECT_EQUAL(0u, a.getDeadLen());
    EXPECT_EQUAL(67u, a.readInt64());
    EXPECT_EQUAL(8u, a.getDataLen());
    EXPECT_EQUAL(8u, a.getDeadLen());
    EXPECT_EQUAL(16u, a.getBufSize());
    a.pack(16);
    EXPECT_EQUAL(8u, a.getDataLen());
    EXPECT_EQUAL(0u, a.getDeadLen());
    EXPECT_EQUAL(256u, a.getBufSize());
    EXPECT_EQUAL(89u, a.readInt64());
    EXPECT_EQUAL(0u, a.getDataLen());
    EXPECT_EQUAL(256u, a.getBufSize());
}
