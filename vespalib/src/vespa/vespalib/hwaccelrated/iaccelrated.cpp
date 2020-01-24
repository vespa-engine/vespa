// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iaccelrated.h"
#include "generic.h"
#include "sse2.h"
#include "avx.h"
#include "avx2.h"
#include "avx512.h"
#include <vespa/vespalib/util/memory.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.hwaccelrated");

namespace vespalib::hwaccelrated {

namespace {

class Factory {
public:
    virtual ~Factory() { }
    virtual IAccelrated::UP create() const = 0;
};

class GenericFactory :public Factory{
public:
    IAccelrated::UP create() const override { return std::make_unique<GenericAccelrator>(); }
};

class Sse2Factory :public Factory{
public:
    IAccelrated::UP create() const override { return std::make_unique<Sse2Accelrator>(); }
};

class AvxFactory :public Factory{
public:
    IAccelrated::UP create() const override { return std::make_unique<AvxAccelrator>(); }
};

class Avx2Factory :public Factory{
public:
    IAccelrated::UP create() const override { return std::make_unique<Avx2Accelrator>(); }
};

class Avx512Factory :public Factory{
public:
    IAccelrated::UP create() const override { return std::make_unique<Avx512Accelrator>(); }
};

template<typename T>
void verifyAccelrator(const IAccelrated & accel)
{
    const size_t testLength(127);
    T * a = new T[testLength];
    T * b = new T[testLength];
    for (size_t j(0); j < 0x20; j++) {
        T sum(0);
        for (size_t i(j); i < testLength; i++) {
            a[i] = b[i] = i;
            sum += i*i;
        }
        T hwComputedSum(accel.dotProduct(&a[j], &b[j], testLength - j));
        if (sum != hwComputedSum) {
            fprintf(stderr, "Accelrator is not computing dotproduct correctly.\n");
            LOG_ABORT("should not be reached");
        }
    }
    delete [] a;
    delete [] b;
}

void verifyPopulationCount(const IAccelrated & accel)
{
    const uint64_t words[7] = {0x123456789abcdef0L,  // 32
                               0x0000000000000000L,  // 0
                               0x8000000000000000L,  // 1
                               0xdeadbeefbeefdeadUL, // 48
                               0x5555555555555555L,  // 32
                               0x00000000000000001,  // 1
                               0xffffffffffffffff};  // 64
    constexpr size_t expected = 32 + 0 + 1 + 48 + 32 + 1 + 64;
    size_t hwComputedPopulationCount = accel.populationCount(words, VESPA_NELEMS(words));
    if (hwComputedPopulationCount != expected) {
        fprintf(stderr, "Accelrator is not computing populationCount correctly.Expected %zu, computed %zu\n", expected, hwComputedPopulationCount);
        LOG_ABORT("should not be reached");
    }
}

class RuntimeVerificator
{
public:
    RuntimeVerificator();
};

RuntimeVerificator::RuntimeVerificator()
{
   GenericAccelrator generic;
   verifyAccelrator<float>(generic); 
   verifyAccelrator<double>(generic); 
   verifyAccelrator<int32_t>(generic); 
   verifyAccelrator<int64_t>(generic);
   verifyPopulationCount(generic);

   IAccelrated::UP thisCpu(IAccelrated::getAccelrator());
   verifyAccelrator<float>(*thisCpu); 
   verifyAccelrator<double>(*thisCpu); 
   verifyAccelrator<int32_t>(*thisCpu); 
   verifyAccelrator<int64_t>(*thisCpu); 
   
}

class Selector
{
public:
    Selector() __attribute__((noinline));
    IAccelrated::UP create() { return _factory->create(); }
private:
    std::unique_ptr<Factory> _factory;
};

Selector::Selector() :
    _factory(new GenericFactory())
{
    __builtin_cpu_init ();
    if (__builtin_cpu_supports("avx512f")) {
        _factory.reset(new Avx512Factory());
    } else if (__builtin_cpu_supports("avx2")) {
        _factory.reset(new Avx2Factory());
    } else if (__builtin_cpu_supports("avx")) {
        _factory.reset(new AvxFactory());
    } else if (__builtin_cpu_supports("sse2")) {
        _factory.reset(new Sse2Factory());
    }
}

}

static Selector _G_selector;

RuntimeVerificator _G_verifyAccelrator;


IAccelrated::UP
IAccelrated::getAccelrator()
{
    return _G_selector.create();
}

}
