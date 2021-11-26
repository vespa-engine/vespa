// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstdint>
#include <vector>

namespace vespalib::hwaccelrated {

/**
 * This contains an interface to all primitives that has different cpu supported accelrations.
 * The actual implementation you get by calling the the static getAccelrator method.
 */
class IAccelrated
{
public:
    virtual ~IAccelrated() = default;
    typedef std::unique_ptr<IAccelrated> UP;
    virtual float dotProduct(const float * a, const float * b, size_t sz) const = 0;
    virtual double dotProduct(const double * a, const double * b, size_t sz) const = 0;
    virtual int64_t dotProduct(const int8_t * a, const int8_t * b, size_t sz) const = 0;
    virtual int64_t dotProduct(const int16_t * a, const int16_t * b, size_t sz) const = 0;
    virtual int64_t dotProduct(const int32_t * a, const int32_t * b, size_t sz) const = 0;
    virtual long long dotProduct(const int64_t * a, const int64_t * b, size_t sz) const = 0;
    virtual void orBit(void * a, const void * b, size_t bytes) const = 0;
    virtual void andBit(void * a, const void * b, size_t bytes) const = 0;
    virtual void andNotBit(void * a, const void * b, size_t bytes) const = 0;
    virtual void notBit(void * a, size_t bytes) const = 0;
    virtual size_t populationCount(const uint64_t *a, size_t sz) const = 0;
    virtual double squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const = 0;
    virtual double squaredEuclideanDistance(const float * a, const float * b, size_t sz) const = 0;
    virtual double squaredEuclideanDistance(const double * a, const double * b, size_t sz) const = 0;
    // AND 64 bytes from multiple, optionally inverted sources
    virtual void and64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const = 0;
    // OR 64 bytes from multiple, optionally inverted sources
    virtual void or64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const = 0;

    static const IAccelrated & getAccelerator() __attribute__((noinline));
};

}
