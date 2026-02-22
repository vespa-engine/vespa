// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/databuffer.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <chrono>

TEST(DataBufferTest, test_resetIfEmpty) {
    FNET_DataBuffer buf(64);
    EXPECT_TRUE(buf.GetData() == buf.GetDead());
    EXPECT_TRUE(buf.GetData() == buf.GetFree());
    buf.WriteInt32(11111111);
    EXPECT_TRUE(buf.GetData() == buf.GetDead());
    EXPECT_FALSE(buf.GetData() == buf.GetFree());
    buf.resetIfEmpty();
    EXPECT_TRUE(buf.GetData() == buf.GetDead());
    EXPECT_FALSE(buf.GetData() == buf.GetFree());
    EXPECT_EQ(11111111u, buf.ReadInt32());
    buf.resetIfEmpty();
    EXPECT_TRUE(buf.GetData() == buf.GetDead());
    EXPECT_TRUE(buf.GetData() == buf.GetFree());
}

TEST(DataBufferTest, testResize) {
    FNET_DataBuffer buf(64);
    uint32_t        initialSize = buf.GetBufSize();
    buf.WriteInt32(11111111);
    buf.WriteInt32(22222222);
    buf.WriteInt32(33333333);
    buf.WriteInt32(44444444);
    buf.WriteInt32(55555555);
    EXPECT_TRUE(buf.ReadInt32() == 11111111);
    buf.EnsureFree(initialSize);
    EXPECT_TRUE(buf.GetBufSize() > initialSize);
    EXPECT_TRUE(buf.ReadInt32() == 22222222);
    EXPECT_TRUE(!buf.Shrink(buf.GetBufSize()));
    EXPECT_TRUE(!buf.Shrink(buf.GetBufSize() + 16));
    EXPECT_TRUE(!buf.Shrink(2 * 4));
    EXPECT_TRUE(buf.Shrink(3 * 4));
    EXPECT_TRUE(buf.GetBufSize() == 3 * 4);
    EXPECT_TRUE(buf.ReadInt32() == 33333333);
    buf.WriteInt32(66666666);
    buf.EnsureFree(16);
    EXPECT_TRUE(buf.GetDataLen() == 3 * 4);
    EXPECT_TRUE(buf.GetBufSize() >= 16 + 3 * 4);
    EXPECT_TRUE(buf.ReadInt32() == 44444444);
    EXPECT_TRUE(buf.ReadInt32() == 55555555);
    EXPECT_TRUE(buf.ReadInt32() == 66666666);
    EXPECT_TRUE(buf.Shrink(0));
    EXPECT_TRUE(buf.GetBufSize() == 0);
    buf.WriteInt32(42);
    EXPECT_TRUE(buf.GetBufSize() >= 4);
    EXPECT_TRUE(buf.ReadInt32() == 42);
    EXPECT_TRUE(buf.GetDataLen() == 0);
}

TEST(DataBufferTest, testSpeed) {
    using clock = std::chrono::steady_clock;
    using ms_double = std::chrono::duration<double, std::milli>;

    FNET_DataBuffer   buf0(20000);
    FNET_DataBuffer   buf1(20000);
    FNET_DataBuffer   buf2(20000);
    clock::time_point start;
    ms_double         ms;

    int i;
    int k;

    // fill buf0 with random data
    for (i = 0; i < 16000; i++) {
        buf0.WriteInt8((uint8_t)rand());
    }

    // copy buf0 into buf1
    for (i = 0; i < 16000; i++) {
        buf1.WriteInt8(buf0.ReadInt8());
    }

    // undo read from buf0
    buf0.DeadToData(buf0.GetDeadLen());

    // test encode/decode speed
    start = clock::now();

    for (i = 0; i < 5000; i++) {
        buf2.Clear();
        for (k = 0; k < 500; k++) {
            buf2.WriteInt8(buf1.ReadInt8());
            buf2.WriteInt32(buf1.ReadInt32());
            buf2.WriteInt8(buf1.ReadInt8());
            buf2.WriteInt8(buf1.ReadInt8());
            buf2.WriteInt16(buf1.ReadInt16());
            buf2.WriteInt8(buf1.ReadInt8());
            buf2.WriteInt32(buf1.ReadInt32());
            buf2.WriteInt16(buf1.ReadInt16());
            buf2.WriteInt32(buf1.ReadInt32());
            buf2.WriteInt64(buf1.ReadInt64());
            buf2.WriteInt32(buf1.ReadInt32());
        }
        buf1.Clear();
        for (k = 0; k < 500; k++) {
            buf1.WriteInt8(buf2.ReadInt8());
            buf1.WriteInt16(buf2.ReadInt16());
            buf1.WriteInt8(buf2.ReadInt8());
            buf1.WriteInt32(buf2.ReadInt32());
            buf1.WriteInt32(buf2.ReadInt32());
            buf1.WriteInt8(buf2.ReadInt8());
            buf1.WriteInt64(buf2.ReadInt64());
            buf1.WriteInt32(buf2.ReadInt32());
            buf1.WriteInt8(buf2.ReadInt8());
            buf1.WriteInt16(buf2.ReadInt16());
            buf1.WriteInt32(buf2.ReadInt32());
        }
    }
    buf2.DeadToData(buf2.GetDeadLen());

    ms = (clock::now() - start);
    fprintf(stderr, "encode/decode time (~160MB): %1.2f\n", ms.count());

    EXPECT_TRUE(buf0.Equals(&buf1) && buf0.Equals(&buf2));

    // test encode[fast]/decode speed
    start = clock::now();

    for (i = 0; i < 5000; i++) {
        buf2.Clear();
        for (k = 0; k < 500; k++) {
            buf2.WriteInt8Fast(buf1.ReadInt8());
            buf2.WriteInt32Fast(buf1.ReadInt32());
            buf2.WriteInt8Fast(buf1.ReadInt8());
            buf2.WriteInt8Fast(buf1.ReadInt8());
            buf2.WriteInt16Fast(buf1.ReadInt16());
            buf2.WriteInt8Fast(buf1.ReadInt8());
            buf2.WriteInt32Fast(buf1.ReadInt32());
            buf2.WriteInt16Fast(buf1.ReadInt16());
            buf2.WriteInt32Fast(buf1.ReadInt32());
            buf2.WriteInt64Fast(buf1.ReadInt64());
            buf2.WriteInt32Fast(buf1.ReadInt32());
        }
        buf1.Clear();
        for (k = 0; k < 500; k++) {
            buf1.WriteInt8Fast(buf2.ReadInt8());
            buf1.WriteInt16Fast(buf2.ReadInt16());
            buf1.WriteInt8Fast(buf2.ReadInt8());
            buf1.WriteInt32Fast(buf2.ReadInt32());
            buf1.WriteInt32Fast(buf2.ReadInt32());
            buf1.WriteInt8Fast(buf2.ReadInt8());
            buf1.WriteInt64Fast(buf2.ReadInt64());
            buf1.WriteInt32Fast(buf2.ReadInt32());
            buf1.WriteInt8Fast(buf2.ReadInt8());
            buf1.WriteInt16Fast(buf2.ReadInt16());
            buf1.WriteInt32Fast(buf2.ReadInt32());
        }
    }
    buf2.DeadToData(buf2.GetDeadLen());

    ms = (clock::now() - start);
    fprintf(stderr, "encode[fast]/decode time (~160MB): %1.2f\n", ms.count());

    EXPECT_TRUE(buf0.Equals(&buf1) && buf0.Equals(&buf2));

    // init source table for table streaming test
    uint32_t table[4000];
    for (i = 0; i < 4000; i++) {
        table[i] = i;
    }

    // test byte-swap table encoding speed
    start = clock::now();

    for (i = 0; i < 10000; i++) {
        buf1.Clear();
        for (k = 0; k < 4000; k += 8) {
            buf1.WriteInt32Fast(table[k]);
            buf1.WriteInt32Fast(table[k + 1]);
            buf1.WriteInt32Fast(table[k + 2]);
            buf1.WriteInt32Fast(table[k + 3]);
            buf1.WriteInt32Fast(table[k + 4]);
            buf1.WriteInt32Fast(table[k + 5]);
            buf1.WriteInt32Fast(table[k + 6]);
            buf1.WriteInt32Fast(table[k + 7]);
        }
    }
    ms = (clock::now() - start);
    fprintf(stderr, "byte-swap array encoding[fast] (~160 MB): %1.2f ms\n", ms.count());

    // test direct-copy table encoding speed
    start = clock::now();

    for (i = 0; i < 10000; i++) {
        buf2.Clear();
        buf2.EnsureFree(16000);
        memcpy(buf2.GetFree(), table, 16000);
        buf2.FreeToData(16000);
    }
    ms = (clock::now() - start);
    fprintf(stderr, "direct-copy array encoding (~160 MB): %1.2f ms\n", ms.count());
}

GTEST_MAIN_RUN_ALL_TESTS()
