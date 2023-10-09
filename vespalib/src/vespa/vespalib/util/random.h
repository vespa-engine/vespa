// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace vespalib {

/**
 * @brief Random number generator. Compatible with java.util.Random
 **/
class RandomGen {
private:
    uint64_t _state;

    /**
     * step the random generator once
     **/
    void iterate() {
        _state = (0x5DEECE66Dul * _state + 0xb) &
                 0xFFFFFFFFFFFFul;
    }

    /**
     * @brief return an integer with 1 to 32 random bits
     *
     * The general contract of next is that it returns an int value
     * and if the argument bits is between 1 and 32 (inclusive), then
     * that many low-order bits of the returned value will be
     * (approximately) independently chosen bit values, each of which
     * is (approximately) equally likely to be 0 or 1.
     *
     * This is a linear congruential pseudorandom number generator, as
     * defined by D. H. Lehmer and described by Donald E. Knuth in The
     * Art of Computer Programming, Volume 2: Seminumerical
     * Algorithms, section 3.2.1.
     *
     * @param bits - random bits
     *
     * @return the next pseudorandom value from this random number
     * generator's sequence.
     **/

    int next(int bits) {
        iterate();
        return _state >> (48 - bits);
    }

    void zigNorInit(int iC, double dR, double dV);

    double DRanNormalTail(double dMin, int iNegative);
    double DRanNormalZig();

public:

    /**
     * @brief construct a random number generator with a given seed
     **/
    RandomGen(int64_t seed) : _state(0) { setSeed(seed); }

    /**
     * @brief construct a random number generator with an auto-generated seed
     **/
    RandomGen();

    /**
     * @brief reset the seed
     **/
    void setSeed(int64_t seed) {
        _state = (seed ^ 0x5DEECE66Dul) & ((1L << 48) -1);
    };

    /**
     * @brief Return next random 32-bit signed integer
     **/
    int32_t nextInt32(void) {
        iterate();
        return (_state >> 16);
    }

    /**
     * @brief Returns the next pseudorandom, uniformly distributed
     * double value between 0.0 and 1.0 from this random number
     * generator's sequence.
     *
     * The general contract of nextDouble is that one double value,
     * chosen (approximately) uniformly from the range 0.0
     * (inclusive) to 1.0 (exclusive), is pseudorandomly generated
     * and returned. All 2^53 possible float values of the form
     *  m * 2^-53 , where m is a positive integer less than 2^53, are
     * produced with (approximately) equal probability.
     *
     * @return a double number in range [0.0, 1.0>
     **/
    double nextDouble() {
        uint64_t l = next(26);
        l <<= 27;
        l += next(27);
        double d = l;
        d /= (1LL << 53);
        return d;
    }

    /**
     * @brief Return next random 32-bit unsigned integer
     **/
    uint32_t nextUint32() {
        return (uint32_t)nextInt32();
    }

    /**
     * @brief Returns the next random number in the range [from..to]
     **/
    uint32_t nextUint32(uint32_t from, uint32_t to) {
        return from + nextUint32() % (to - from + 1);
    }

    /**
     * @brief Return next random 64-bit unsigned integer
     **/
    uint64_t nextUint64() {
        return ((uint64_t)next(32) << 32) + next(32);
    }

    double nextNormal();
    double nextNormal(double mean, double stddev) {
        return mean + stddev * nextNormal();
    }
};

}

