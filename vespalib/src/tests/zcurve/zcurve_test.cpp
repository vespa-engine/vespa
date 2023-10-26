// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <algorithm>
#include <limits>
#include <map>
#include <sys/time.h>

#include <vespa/log/log.h>
LOG_SETUP("zcurve_test");

namespace vespalib {

using geo::ZCurve;

class ZCurveTest : public vespalib::TestApp
{
public:
    ZCurveTest(void)
        : vespalib::TestApp()
    {
    }

    void testEncoding();

    void testDecoding();

    double ftime();

    static inline int64_t encodexy3(int32_t x, int32_t y);

#define BMLIMIT 0x1000000

    template <bool decode>
    int64_t bm();

    template <bool decode>
    int64_t bm2();

    template <bool decode>
    int64_t bm3();

    int64_t bmcheck();

    int Main() override;
};


void
ZCurveTest::testEncoding(void)
{
    int32_t x = 0;
    int32_t y = 0;
    int64_t z = ZCurve::encode(x, y);
    ASSERT_TRUE(z == 0);

    x = std::numeric_limits<int32_t>::min();
    y = std::numeric_limits<int32_t>::min();
    z = ZCurve::encode(x, y);
    ASSERT_TRUE(static_cast<int64_t>(UINT64_C(0xc000000000000000)) == z);

    x = std::numeric_limits<int32_t>::min();
    y = std::numeric_limits<int32_t>::max();
    z = ZCurve::encode(x, y);
    ASSERT_TRUE(static_cast<int64_t>(UINT64_C(0x6aaaaaaaaaaaaaaa)) == z);

    x = std::numeric_limits<int32_t>::max();
    y = std::numeric_limits<int32_t>::max();
    z = ZCurve::encode(x, y);
    ASSERT_TRUE(static_cast<int64_t>(UINT64_C(0x3fffffffffffffff)) == z);

    x = -1;
    y = -1;
    z = ZCurve::encode(x, y);
    ASSERT_TRUE(static_cast<int64_t>(UINT64_C(0xffffffffffffffff)) ==  z);

    x = std::numeric_limits<int32_t>::max() / 2;
    y = std::numeric_limits<int32_t>::min() / 2;
    z = ZCurve::encode(x, y);
    ASSERT_TRUE(static_cast<int64_t>(UINT64_C(0xa555555555555555)) == z);
}


void
ZCurveTest::testDecoding(void)
{
    int32_t x = 0;
    int32_t y = 0;
    int64_t z = ZCurve::encode(x, y);
    int32_t dx;
    int32_t dy;
    dx = 0;
    dy = 0;
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);

    x = std::numeric_limits<int32_t>::max();
    y = std::numeric_limits<int32_t>::max();
    z = ZCurve::encode(x, y);
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);

    x = std::numeric_limits<int32_t>::min();
    y = std::numeric_limits<int32_t>::min();
    z = ZCurve::encode(x, y);
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);

    x = std::numeric_limits<int32_t>::min();
    y = std::numeric_limits<int32_t>::max();
    z = ZCurve::encode(x, y);
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);

    x = -18;
    y = 1333;
    z = ZCurve::encode(x, y);
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);

    x = 0;
    y = 0;
    z = ZCurve::encode(x, y);
    ZCurve::decode(z, &dx, &dy);
    ASSERT_TRUE(dx == x);
    ASSERT_TRUE(dy == y);
}


double
ZCurveTest::ftime()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec + tv.tv_usec / 1000000.0;
}

int64_t
ZCurveTest::encodexy3(int32_t x, int32_t y)
{
    uint32_t resxl;
    uint32_t resxh;
    uint32_t resyl;
    uint32_t resyh;

    resxl = (static_cast<uint32_t>(x) & 0x0000ffffu);
    resxh = (static_cast<uint32_t>(x) & 0xffff0000u) >> 16;
    resyl = (static_cast<uint32_t>(y) & 0x0000ffffu);
    resyh = (static_cast<uint32_t>(y) & 0xffff0000u) >> 16;
    resxl = ((resxl & 0xff00ff00u) << 8) | (resxl & 0x00ff00ffu);
    resyl = ((resyl & 0xff00ff00u) << 8) | (resyl & 0x00ff00ffu);
    resxh = ((resxh & 0xff00ff00u) << 8) | (resxh & 0x00ff00ffu);
    resyh = ((resyh & 0xff00ff00u) << 8) | (resyh & 0x00ff00ffu);
    resxl = ((resxl & 0xf0f0f0f0u) << 4) | (resxl & 0x0f0f0f0fu);
    resyl = ((resyl & 0xf0f0f0f0u) << 4) | (resyl & 0x0f0f0f0fu);
    resxh = ((resxh & 0xf0f0f0f0u) << 4) | (resxh & 0x0f0f0f0fu);
    resyh = ((resyh & 0xf0f0f0f0u) << 4) | (resyh & 0x0f0f0f0fu);
    resxl = ((resxl & 0xccccccccu) << 2) | (resxl & 0x33333333u);
    resyl = ((resyl & 0xccccccccu) << 2) | (resyl & 0x33333333u);
    resxh = ((resxh & 0xccccccccu) << 2) | (resxh & 0x33333333u);
    resyh = ((resyh & 0xccccccccu) << 2) | (resyh & 0x33333333u);
    resxl = ((resxl & 0xaaaaaaaau) << 1) | (resxl & 0x55555555u);
    resyl = ((resyl & 0xaaaaaaaau) << 1) | (resyl & 0x55555555u);
    resxh = ((resxh & 0xaaaaaaaau) << 1) | (resxh & 0x55555555u);
    resyh = ((resyh & 0xaaaaaaaau) << 1) | (resyh & 0x55555555u);
    return static_cast<int64_t>(resxl | (resyl << 1) |
                                (static_cast<uint64_t>(resxh |
                                        (resyh << 1)) << 32));
}


template <bool decode>
int64_t
ZCurveTest::bm()
{
    int64_t res = 0;
    double before = ftime();
    int32_t x = 0;
    do {
        x++;
        int64_t enc = ZCurve::encodeSlow(x, 0);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decodeSlow(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == x);
            ASSERT_TRUE(checky == 0);
        }
    } while (x != BMLIMIT);
    int32_t y = 0;
    do {
        y++;
        int64_t enc = ZCurve::encodeSlow(0, y);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decodeSlow(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == 0);
            ASSERT_TRUE(checky == y);
        }
    } while (y != BMLIMIT);
    double after = ftime();
    LOG(info,
        "Elapsed bm<decode = %s> = %6.2f",
        decode ? "true" : "false",
        after - before);
    return res;
}


template <bool decode>
int64_t
ZCurveTest::bm2(void)
{
    int64_t res = 0;
    double before = ftime();
    int32_t x = 0;
    do {
        x++;
        int64_t enc = ZCurve::encode(x, 0);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decode(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == x);
            ASSERT_TRUE(checky == 0);
        }
    } while (x != BMLIMIT);
    int32_t y = 0;
    do {
        y++;
        int64_t enc = ZCurve::encode(0, y);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decode(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == 0);
            ASSERT_TRUE(checky == y);
        }
    } while (y != BMLIMIT);
    double after = ftime();
    LOG(info,
        "Elapsed bm2<decode = %s> = %6.2f",
        decode ? "true" : "false",
        after - before);
    return res;
}


template <bool decode>
int64_t
ZCurveTest::bm3()
{
    int64_t res = 0;
    double before = ftime();
    int32_t x = 0;
    do {
        x++;
        int64_t enc = encodexy3(x, 0);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decode(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == x);
            ASSERT_TRUE(checky == 0);
        }
    } while (x != BMLIMIT);
    int32_t y = 0;
    do {
        y++;
        int64_t enc = encodexy3(0, y);
        res += enc;
        if (decode) {
            int32_t checkx = 0;
            int32_t checky = 0;
            ZCurve::decode(enc, &checkx, &checky);
            ASSERT_TRUE(checkx == 0);
            ASSERT_TRUE(checky == y);
        }
    } while (y != BMLIMIT);
    double after = ftime();
    LOG(info,
        "Elapsed bm3<decode = %s> = %6.2f",
        decode ? "true" : "false",
        after - before);
    return res;
}


int64_t
ZCurveTest::bmcheck()
{
    int64_t res = 0;
    double before = ftime();
    int32_t x = 0;
    do {
        x++;
        int64_t enc = ZCurve::encodeSlow(x, 0);
        int64_t enc2 = ZCurve::encode(x, 0);
        int64_t enc3 = encodexy3(x, 0);
        ASSERT_TRUE(enc == enc2);
        ASSERT_TRUE(enc == enc3);
        res += enc;
        int32_t checkx = 0;
        int32_t checky = 0;
        ZCurve::decode(enc, &checkx, &checky);
        ASSERT_TRUE(checkx == x);
        ASSERT_TRUE(checky == 0);
    } while (x != BMLIMIT);
    int32_t y = 0;
    do {
        y++;
        int64_t enc = ZCurve::encodeSlow(0, y);
        int64_t enc2 = ZCurve::encode(0, y);
        int64_t enc3 = encodexy3(0, y);
        ASSERT_TRUE(enc == enc2);
        ASSERT_TRUE(enc == enc3);
        res += enc;
        int32_t checkx = 0;
        int32_t checky = 0;
        ZCurve::decode(enc, &checkx, &checky);
        ASSERT_TRUE(checkx == 0);
        ASSERT_TRUE(checky == y);
    } while (y != BMLIMIT);
    double after = ftime();
    LOG(info,
        "Elapsed bmcheck = %6.2f",
        after - before);
    return res;
}


int
ZCurveTest::Main()
{
    TEST_INIT("zcurve_test");

    for (int32_t x = 0; x < 4; x++) {
        for (int32_t y = 0; y < 4; y++) {
            int64_t enc = 0;
            int64_t enc2 = 0;
            int64_t enc3 = 0;
            int32_t checkx = 0;
            int32_t checky = 0;
            enc = ZCurve::encodeSlow(x, y);
            enc2 = ZCurve::encode(x, y);
            enc3 = encodexy3(x, y);
            ASSERT_TRUE(enc == enc2);
            ASSERT_TRUE(enc == enc3);
            // printf("x=%u, y=%u, enc=%" PRId64 "\n", x, y, enc);
            checkx = 0;
            checky = 0;
            ZCurve::decodeSlow(enc, &checkx, &checky);
            ASSERT_TRUE(x == checkx);
            ASSERT_TRUE(y == checky);
        }
    }
    testEncoding();
    testDecoding();
    if (_argc >= 2) {
        int64_t enc1 = bm<true>();
        int64_t enc1b = bm<false>();
        int64_t enc2 = bm2<true>();
        int64_t enc2b = bm2<false>();
        int64_t enc3 = bm3<true>();
        int64_t enc3b = bm3<false>();
        int64_t enc4 = bmcheck();
        ASSERT_TRUE(enc1 == enc1b);
        ASSERT_TRUE(enc1 == enc2);
        ASSERT_TRUE(enc1 == enc2b);
        ASSERT_TRUE(enc1 == enc3);
        ASSERT_TRUE(enc1 == enc3b);
        ASSERT_TRUE(enc1 == enc4);
    }

    TEST_DONE();
}

}

TEST_APPHOOK(vespalib::ZCurveTest);
