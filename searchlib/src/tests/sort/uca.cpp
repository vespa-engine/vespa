// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/vespalib/util/array.h>
#include <unicode/ustring.h>
#include <unicode/coll.h>
#include <fcntl.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("uca_stress");

using icu::Collator;

class Test : public vespalib::TestApp
{
public:
    int Main() override;
    void testFromDat();
};


void Test::testFromDat()
{
    size_t badnesses = 0;

    std::string startMark("abc");
    std::string midMark("def");
    std::string endMark("ghi");

    UErrorCode status = U_ZERO_ERROR;
    auto coll = std::unique_ptr<Collator>(Collator::createInstance(icu::Locale("en"), status));
    
    coll->setStrength(Collator::PRIMARY);

    std::vector<UChar> u16buffer(100);
    std::vector<uint8_t>  u8buffer(10);

    int fd = open("sort-blobs.dat", O_RDONLY);
    char sbuf[4];

    int num=0;

    uint32_t atleast = 0;

    while (read(fd, sbuf, 4) == 4) {
        if (startMark == sbuf) {
            uint32_t len = 0;
            int r = read(fd, &len, 4);

            EXPECT_EQUAL(4, r);
            r = read(fd, sbuf, 4);
            EXPECT_EQUAL(4, r);
            EXPECT_EQUAL(midMark, sbuf);

            if (u16buffer.size() < len) {
                u16buffer.resize(len);
            }
            r = read(fd, &u16buffer[0], len*2);
            EXPECT_EQUAL((int)len*2, r);

            r = read(fd, sbuf, 4);
            EXPECT_EQUAL(4, r);
            EXPECT_EQUAL(endMark, sbuf);

            uint32_t wanted = coll->getSortKey(&u16buffer[0], len, NULL, 0);

            EXPECT_TRUE(wanted > 0);
            EXPECT_TRUE(wanted >= len);
            EXPECT_TRUE(wanted < len*6);

            if (wanted + 20 > u8buffer.size()) {
                u8buffer.resize(wanted+20);
            }

            for (uint32_t pretend = 1; pretend < wanted+8; ++pretend) {
                memset(&u8buffer[0], 0x99, u8buffer.size());
                uint32_t got = coll->getSortKey(&u16buffer[0], len, &u8buffer[0], pretend);
                EXPECT_EQUAL(wanted, got);

                if (u8buffer[pretend+1] != 0x99) {
                    printf("wrote 2 bytes too far: wanted space %d, pretend allocated %d, last good=%02x, bad=%02x %02x\n",
                           wanted, pretend, u8buffer[pretend-1],
                           u8buffer[pretend], u8buffer[pretend+1]);
                } else if (u8buffer[pretend] != 0x99) {
                    ++badnesses;
                    if (wanted > atleast) {
                        atleast = wanted;
                        printf("wrote 1 byte too far: wanted space %d, pretend allocated %d, last good=%02x, bad=%02x\n",
                               wanted, pretend, u8buffer[pretend-1], u8buffer[pretend]);
                    }
                }
            }

            memset(&u8buffer[0], 0x99, u8buffer.size());
            uint32_t got = coll->getSortKey(&u16buffer[0], len, &u8buffer[0], u8buffer.size());
            EXPECT_EQUAL(wanted, got);

            EXPECT_EQUAL('\0', u8buffer[got-1]);
            EXPECT_EQUAL((uint8_t)0x99, u8buffer[got]);
        }
        if (++num >= 10000) {
            TEST_FLUSH();
            num=0;
        }
    }
    EXPECT_EQUAL(0u, badnesses);
}

TEST_APPHOOK(Test);

int Test::Main()
{
    TEST_INIT("uca_stress");
    testFromDat();
    TEST_DONE();
}
