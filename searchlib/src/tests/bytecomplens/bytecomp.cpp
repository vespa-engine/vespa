// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <memory>
#include <vespa/log/log.h>
LOG_SETUP("bytecomplens_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/searchlib/docstore/bytecomplens.h>


class Test : public vespalib::TestApp {
private:
    void testRandomLengths();

public:
    int Main() override {
        TEST_INIT("bytecomplens_test");
        testRandomLengths();    TEST_FLUSH();
        TEST_DONE();
    }
};

TEST_APPHOOK(Test);


void
Test::testRandomLengths()
{
    vespalib::RandomGen rndgen(0x07031969);

#define TBLSIZ 0xc00000

    auto lentable = std::unique_ptr<uint32_t[]>(new uint32_t[TBLSIZ]);
    auto offtable = std::unique_ptr<uint64_t[]>(new uint64_t[TBLSIZ]);

    uint64_t offset = 16;

    for (int i = 0; i < TBLSIZ; i++) {
        int sel = rndgen.nextInt32();
        int val = rndgen.nextInt32();
        switch (sel & 0x7) {
        case 0:
            val &= 0x7F;
            break;
        case 1:
            val &= 0xFF;
            break;
        case 3:
            val &= 0x1FFF;
            break;
        case 4:
            val &= 0x3FFF;
            break;
        case 5:
            val &= 0x7FFF;
            break;
        case 6:
            val &= 0xFFFF;
            break;
        case 7:
        default:
            val &= 0xFFFFF;
            break;
        }
        offtable[i] = offset;
        lentable[i] = val;
        offset += val;
    }

    LOG(info, "made %d random offsets", TBLSIZ);

    search::ByteCompressedLengths foo;

    LOG(info, "empty BCL using %9ld bytes memory", foo.memoryUsed());

    foo.addOffsetTable(TBLSIZ/4, offtable.get());
    foo.addOffsetTable(TBLSIZ/4, offtable.get() + 1*(TBLSIZ/4));

    LOG(info, "half  BCL using %9ld bytes memory", foo.memoryUsed());

    search::ByteCompressedLengths bar;
    foo.swap(bar);
    bar.addOffsetTable(TBLSIZ/4, offtable.get() + 2*(TBLSIZ/4));
    bar.addOffsetTable(TBLSIZ/4, offtable.get() + 3*(TBLSIZ/4));
    foo.swap(bar);

    LOG(info, "full  BCL using %9ld bytes memory", foo.memoryUsed());

    LOG(info, "constructed %d byte compressed lengths", TBLSIZ-1);

    for (int i = 0; i < TBLSIZ-1; i++) {
        search::ByteCompressedLengths::OffLen offlen;
        offlen = foo.getOffLen(i);

        if ((i % 1000000) == 0) {
            LOG(info, "data blob [%d] length %" PRIu64 " offset %" PRIu64, i, offlen.length, offlen.offset);
        }
        EXPECT_EQUAL(lentable[i], offlen.length);
        EXPECT_EQUAL(offtable[i], offlen.offset);
    }
}

