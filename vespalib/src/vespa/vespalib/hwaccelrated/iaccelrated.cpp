// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iaccelrated.h"
#include "generic.h"
#include "sse2.h"
#include "avx.h"
#include "avx2.h"
#include "avx512.h"

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
    IAccelrated::UP create() const override { return IAccelrated::UP(new GenericAccelrator()); }
};

class Sse2Factory :public Factory{
public:
    IAccelrated::UP create() const override { return IAccelrated::UP(new Sse2Accelrator()); }
};

class AvxFactory :public Factory{
public:
    IAccelrated::UP create() const override { return IAccelrated::UP(new AvxAccelrator()); }
};

class Avx2Factory :public Factory{
public:
    IAccelrated::UP create() const override { return IAccelrated::UP(new Avx2Accelrator()); }
};

class Avx512Factory :public Factory{
public:
    IAccelrated::UP create() const override { return IAccelrated::UP(new Avx512Accelrator()); }
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
