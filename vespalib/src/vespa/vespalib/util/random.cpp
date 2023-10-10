// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random.h"
#include <cmath>
#include <cstring>
#include <ctime>
#include <unistd.h>
#include <chrono>

namespace vespalib {

namespace {

    enum { ZIGNOR_C = 128 };

    const double ZIGNOR_R = 3.442619855899; /* start of the right tail */

    /* (R * phi(R) + Pr(X>=R)) * sqrt(2\pi) */
    const double ZIGNOR_V = 9.91256303526217e-3;

    /* s_adZigX holds coordinates, such that each rectangle has*/
    /* same area; s_adZigR holds s_adZigX[i + 1] / s_adZigX[i] */

    double s_adZigX[ZIGNOR_C + 1];
    double s_adZigR[ZIGNOR_C];

    bool _G_needInit = true;
}

RandomGen::RandomGen() :
    _state(0)
{
    unsigned long seed = getpid();
    seed ^= std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
    char hn[32];
    memset(hn, 0, sizeof(hn));
    gethostname(hn, 32);
    unsigned long hnl;
    memcpy(&hnl, hn, sizeof(hnl));
    seed ^= hnl;
    memcpy(&hnl, hn+4, sizeof(hnl));
    seed ^= hnl;
    memcpy(&hnl, hn+8, sizeof(hnl));
    seed ^= hnl;

    setSeed(seed);
}


/*
Below code taken from
http://www.doornik.com/research/ziggurat.pdf
*/

double
RandomGen::DRanNormalTail(double dMin, int iNegative)
{
    double x, y;
    do {
        x = std::log(nextDouble()) / dMin;
        y = std::log(nextDouble());
    } while (-2 * y < x * x);

    return iNegative ? x - dMin : dMin - x;
}

void
RandomGen::zigNorInit(int iC, double dR, double dV)
{
    double f(std::exp(-0.5 * dR * dR));
    s_adZigX[0] = dV / f; /* [0] is bottom block: V / f(R) */
    s_adZigX[1] = dR;
    for (int i = 2; i < iC; ++i) {
        s_adZigX[i] = std::sqrt(-2 * std::log(dV / s_adZigX[i - 1] + f));
        f = std::exp(-0.5 * s_adZigX[i] * s_adZigX[i]);
    }
    s_adZigX[iC] = 0.0;
    for (int i = 0; i < iC; ++i) {
        s_adZigR[i] = s_adZigX[i + 1] / s_adZigX[i];
    }
}

double
RandomGen::DRanNormalZig()
{
    for (;;) {
        double u = 2 * nextDouble() - 1;
        unsigned int i = nextInt32() & 0x7F;
        /* first try the rectangular boxes */
        if (std::fabs(u) < s_adZigR[i])
            return u * s_adZigX[i];
        /* bottom box: sample from the tail */
        if (i == 0)
            return DRanNormalTail(ZIGNOR_R, u < 0);
        /* is this a sample from the wedges? */
        double x = u * s_adZigX[i];
        double f0 = std::exp(-0.5 * (s_adZigX[i] * s_adZigX[i] - x * x) );
        double f1 = std::exp(-0.5 * (s_adZigX[i+1] * s_adZigX[i+1] - x * x) );

        if (f1 + nextDouble() * (f0 - f1) < 1.0) {
            return x;
        }
    }
}

double
RandomGen::nextNormal()
{
    if (_G_needInit) {
        zigNorInit(ZIGNOR_C, ZIGNOR_R, ZIGNOR_V);
        _G_needInit = false;
    }
    return DRanNormalZig();
}


} // namespace vespalib
