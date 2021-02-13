// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/fastos/app.h>
#include <iostream>
#include <string>
#include <vector>
#include <cassert>

LOG_SETUP("bitvectorbenchmark");

namespace search {

class BitVectorBenchmark : public FastOS_Application
{
private:
    std::vector<BitVector *> _bv;
    std::vector<unsigned int> _bvc;
    void testCountSpeed1();
    void testCountSpeed2();
    void testCountSpeed3();
    void testOrSpeed1();
    void testOrSpeed2();
    static void usage();
    void init(size_t n);
public:
    BitVectorBenchmark();
    ~BitVectorBenchmark();
    int Main() override;
};

BitVectorBenchmark::BitVectorBenchmark() :
    _bv()
{
}

BitVectorBenchmark::~BitVectorBenchmark()
{
    for(size_t i(0); i < _bv.size(); i++) {
        delete _bv[i];
    }
}

void BitVectorBenchmark::usage()
{
    std::cout << "usage: bitvectorbenchmark [-n numBits] [-t operation]" << std::endl;
}

void BitVectorBenchmark::init(size_t n)
{
    BitVector *a(BitVector::create(n).release());
    BitVector *b(BitVector::create(n).release());
    srand(1);
    for(size_t i(0), j(0); i < n; i += rand()%10, j++) {
        a->flipBit(i);
    }
    for(size_t i(0), j(0); i < n; i += rand()%10, j++) {
        b->flipBit(i);
    }
    a->invalidateCachedCount();
    b->invalidateCachedCount();
    _bv.push_back(a);
    _bvc.push_back(a->countTrueBits());
    _bv.push_back(b);
    _bvc.push_back(b->countTrueBits());
}

void BitVectorBenchmark::testOrSpeed1()
{
    _bv[0]->orWith(*_bv[1]);
}

void BitVectorBenchmark::testCountSpeed1()
{
    _bv[0]->invalidateCachedCount();
    unsigned int cnt = _bv[0]->countTrueBits();
    assert(cnt == _bvc[0]);
    (void) cnt;
}

static int bitTab[256] = {
    0,1,1,2,1,2,2,3,1,2,2,3,2,3,3,4,
    1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,
    1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
    1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
    2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
    3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
    3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
    4,5,5,6,5,6,6,7,5,6,6,7,6,7,7,8
};

void BitVectorBenchmark::testCountSpeed2()
{
    const unsigned char * p = reinterpret_cast<const unsigned char *>(_bv[0]->getStart());
    size_t sz = _bv[0]->size()/8;
    size_t sum0(0);
    size_t sum1(0);
    size_t sum2(0);
    size_t sum3(0);
    for (size_t i(0); i < sz; i+=4) {
            sum0 += bitTab[p[i+0]];
            sum1 += bitTab[p[i+1]];
            sum2 += bitTab[p[i+2]];
            sum3 += bitTab[p[i+3]];
    }
    assert(sum0 + sum1 + sum2 + sum3 == _bvc[0]);
}


static int
popCount(unsigned int bits)
{
    unsigned int odd =  bits & 0x55555555;
    unsigned int even = bits & 0xaaaaaaaa;
    bits = odd + (even >> 1);
    odd =  bits & 0x33333333;
    even = bits & 0xcccccccc;
    bits = odd + (even >> 2);
    odd =  bits & 0x0f0f0f0f;
    even = bits & 0xf0f0f0f0;
    bits = odd + (even >> 4);
    odd =  bits & 0x00ff00ff;
    even = bits & 0xff00ff00;
    bits = odd + (even >> 8);
    odd =  bits & 0x0000ffff;
    even = bits & 0xffff0000;
    bits = odd + (even >> 16);
    return bits;
}


void
BitVectorBenchmark::testCountSpeed3()
{
    const unsigned int * p = static_cast<const unsigned int *>(_bv[0]->getStart());
    const unsigned int * pe = p + (_bv[0]->size()/(sizeof(uint32_t)*8));
    size_t sum(0);
    for (; p < pe; ++p) {
        sum += popCount(*p);
    }
    assert(sum == _bvc[0]);
}

void BitVectorBenchmark::testOrSpeed2()
{
    typedef uint64_t T;
    T * a = reinterpret_cast<T *>(_bv[0]->getStart());
    const T * b = reinterpret_cast<const T *>(_bv[1]->getStart());
    size_t sz = _bv[0]->size()/(8*sizeof(*a));
    for (size_t i(0); i < sz; i+=2) {
            a[i] |= b[i];
            a[i+1] |= b[i+1];
    //        a[i+2] |= b[i+2];
    //        a[i+3] |= b[i+3];
    }
}

int BitVectorBenchmark::Main()
{
    int idx = 1;
    std::string operation;
    size_t numBits(8*1000000);
    char opt;
    const char * arg;
    bool optError = false;
    while ((opt = GetOpt("n:t:", arg, idx)) != -1) {
        switch (opt) {
        case 'n':
            numBits = strtoll(arg, NULL, 10);
            break;
        case 't':
            operation = arg;
            break;
        default:
            optError = true;
            break;
        }
    }

    if ((_argc != idx ) || optError) {
        usage();
        return -1;
    }

    init(numBits);
    for (size_t i(0); i < operation.size(); i++) {
        char op(operation[i]);
        size_t splitBits1 = rand() % numBits;
        size_t splitBits2 = rand() % numBits;
        if (splitBits1 > splitBits2)
            std::swap(splitBits1, splitBits2);
        for (size_t j(0); j < 1000; j++) {
            if (op == 'c') {
                testCountSpeed1();
            } else if (op == 'd') {
                testCountSpeed2();
            } else if (op == 'e') {
                testCountSpeed3();
            } else if (op == 'o') {
                testOrSpeed1();
            } else if (op == 'p') {
                testOrSpeed2();
            } else {
                std::cerr << "Unknown operation " << op << std::endl;
            }
        }
    }

    return 0;
}
}

int main(int argc, char ** argv)
{
    search::BitVectorBenchmark myapp;
    return myapp.Entry(argc, argv);
}

