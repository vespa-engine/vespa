// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/log/log.h>


static uint64_t
maxExpGolombVal(uint64_t kValue, uint64_t maxBits)
{
    return static_cast<uint64_t>
        ((UINT64_C(1) << ((maxBits + kValue + 1) / 2)) -
         (UINT64_C(1) << kValue));
}

class UniformApp
{
    typedef search::bitcompression::EncodeContext64BE EC64;

    enum {
        MAXK = 30
    };

    uint64_t _bits[MAXK + 1];
    uint64_t _next;

    static uint32_t encodeSpace(uint64_t x, uint32_t k) { return EC64::encodeExpGolombSpace(x, k); }
    void clearBits();
    void reportBits();
public:
    int main(int argc, char **argv);
};


void
UniformApp::clearBits()
{
    for (unsigned int k = 0; k <= MAXK; ++k)
        _bits[k] = 0;
    _next = 0;
}


void
UniformApp::reportBits()
{
    printf("next=%" PRIu64 " ", _next);
    for (unsigned int k = 0; k <= MAXK; ++k)
        printf("b[%u]=%" PRIu64 " ",
               static_cast<unsigned int>(k),
               _bits[k]);
    printf("\n");

}



int
UniformApp::main(int, char **)
{
    int k, l, m, bestmask, oldbestmask;
    printf("Hello world\n");
    clearBits();
    reportBits();

    m = 0;
    oldbestmask = 0;
    for (;;) {
        uint64_t minnext = 0;
        int minnextk = 0;
        int bestk = 0;
        printf("_next=%" PRIu64 "\n", _next);
        for (k = 0; k <= MAXK; ++k) {
            uint32_t bits = encodeSpace(_next, k); // Current bits
            uint64_t next = maxExpGolombVal(k, bits);
            assert(encodeSpace(next - 1, k) == bits);
            assert(encodeSpace(next, k) > bits);
            if (k == 0 || next < minnext) {
                minnext = next;
                minnextk = k;
            }
            if (_bits[k] < _bits[bestk])
                bestk = k;
            printf("k=%d, bits=%d, next=%" PRIu64 "\n", k, bits, next);
        }
        printf("minnext=%" PRIu64 ", minnextk=%d, bestk=%d\n",
               minnext, minnextk, bestk);
        for (k = 0; k <= MAXK; ++k) {
            uint32_t kbits = encodeSpace(_next, k); // Current bits
            l = bestk;
            uint32_t lbits = encodeSpace(_next, l); // Current bits
            if (_bits[k] > _bits[l] && kbits < lbits) {
                uint32_t dbits = lbits - kbits;
                uint64_t dsbits = _bits[k] - _bits[l];
                uint64_t delt = (dsbits + dbits - 1) / dbits;
                if (minnext >= _next + delt) {
                    minnext = _next + delt;
                    bestk = k;
                }
            } else if (_bits[k] == _bits[l] && kbits < lbits) {
                minnext = _next + 1;
                bestk = k;
            }
        }
        printf("minnext=%" PRIu64 ", minnextk=%d, bestk=%d\n",
               minnext, minnextk, bestk);
        for (k = 0; k <= MAXK; ++k) {
            assert(encodeSpace(_next, k) == encodeSpace(minnext - 1, k));
            _bits[k] += (minnext - _next) * encodeSpace(_next, k);
        }
        _next = minnext;
        bestmask = 0;
        uint32_t smallk = 0;
        for (k = 0; k <= MAXK; ++k) {
            if (_bits[k] < _bits[smallk])
                smallk = k;
        }
        for (k = 0; k <= MAXK; ++k)
            if (_bits[k] <= _bits[smallk])
                bestmask |= (1 << k);
        if (bestmask == oldbestmask && _next < (UINT64_C(1) << 30))
            continue;
        reportBits();
        printf("Best k for interval [0..%" PRIu64 ") is", _next);
        for (k = 0; k <= MAXK; ++k)
            if (_bits[k] <= _bits[smallk])
                printf(" %d", k);
        printf("\n");
        oldbestmask = bestmask;
        if (_next >= (UINT64_C(1) << 30))
            break;
        printf("m iter=%d\n", m);
        ++m;
        if (m >= 10000) {
            printf("m breakout\n");
            break;
        }
    }

        return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    UniformApp app;
    return app.main(argc, argv);
}


