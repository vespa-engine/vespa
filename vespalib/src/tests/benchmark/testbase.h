// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/identifiable.h>
#include <vector>
#include <string>

#define BENCHMARK_BASE_CID(n) (0x1000000 + n)

#define CID_vespalib_Benchmark                      BENCHMARK_BASE_CID(0)
#define CID_vespalib_ParamByReferenceVectorInt      BENCHMARK_BASE_CID(1)
#define CID_vespalib_ParamByReferenceVectorString   BENCHMARK_BASE_CID(2)
#define CID_vespalib_ParamByValueVectorInt          BENCHMARK_BASE_CID(3)
#define CID_vespalib_ParamByValueVectorString       BENCHMARK_BASE_CID(4)
#define CID_vespalib_ReturnByReferenceVectorString  BENCHMARK_BASE_CID(5)
#define CID_vespalib_ReturnByValueVectorString      BENCHMARK_BASE_CID(6)
#define CID_vespalib_ReturnByValueMultiVectorString BENCHMARK_BASE_CID(7)
#define CID_vespalib_ClockSystem             BENCHMARK_BASE_CID(8)
#define CID_vespalib_ClockREALTIME           BENCHMARK_BASE_CID(10)
#define CID_vespalib_ClockMONOTONIC          BENCHMARK_BASE_CID(11)
#define CID_vespalib_ClockMONOTONIC_RAW      BENCHMARK_BASE_CID(12)
#define CID_vespalib_ClockPROCESS_CPUTIME_ID BENCHMARK_BASE_CID(13)
#define CID_vespalib_ClockTHREAD_CPUTIME_ID  BENCHMARK_BASE_CID(14)
#define CID_vespalib_CreateVespalibString    BENCHMARK_BASE_CID(15)

#define DECLARE_BENCHMARK(a) DECLARE_IDENTIFIABLE_NS(vespalib, a)
#define IMPLEMENT_BENCHMARK(a, d) IMPLEMENT_IDENTIFIABLE_NS(vespalib, a, d)

namespace vespalib {

class Benchmark : public Identifiable
{
public:
    DECLARE_IDENTIFIABLE_ABSTRACT_NS(vespalib, Benchmark);
    void run(size_t numRuns, size_t concurrency=1);
    static void run(const char * testName, size_t numRuns, size_t concurrency);
private:
    virtual size_t onRun() = 0;
};

class ParamByReferenceVectorInt : public Benchmark
{
public:
    DECLARE_BENCHMARK(ParamByReferenceVectorInt);
private:
    typedef std::vector<int> Vector;
    size_t callByReference(const Vector & values) const __attribute__((noinline));
    size_t onRun() override;
};

class ParamByValueVectorInt : public Benchmark
{
public:
    DECLARE_BENCHMARK(ParamByValueVectorInt);
private:
    typedef std::vector<int> Vector;
    size_t callByValue(Vector values) const __attribute__((noinline));
    size_t onRun() override;
};

class ParamByReferenceVectorString : public Benchmark
{
public:
    DECLARE_BENCHMARK(ParamByReferenceVectorString);
private:
    typedef std::vector<std::string> Vector;
    size_t callByReference(const Vector & values) const __attribute__((noinline));
    size_t onRun() override;
};

class ParamByValueVectorString : public Benchmark
{
public:
    DECLARE_BENCHMARK(ParamByValueVectorString);
private:
    typedef std::vector<std::string> Vector;
    size_t callByValue(Vector values) const __attribute__((noinline));
    size_t onRun() override;
};

class ReturnByReferenceVectorString : public Benchmark
{
public:
    DECLARE_BENCHMARK(ReturnByReferenceVectorString);
private:
    typedef std::vector<std::string> Vector;
    const Vector & returnByReference(Vector & values) const __attribute__((noinline));
    size_t onRun() override;
};

class ReturnByValueVectorString : public Benchmark
{
public:
    DECLARE_BENCHMARK(ReturnByValueVectorString);
private:
    typedef std::vector<std::string> Vector;
    Vector returnByValue() const __attribute__((noinline));
    size_t onRun() override;
};

class ReturnByValueMultiVectorString : public Benchmark
{
public:
    DECLARE_BENCHMARK(ReturnByValueMultiVectorString);
private:
    typedef std::vector<std::string> Vector;
    Vector returnByValue() const __attribute__((noinline));
    size_t onRun() override;
};

class CreateVespalibString : public Benchmark
{
public:
    DECLARE_BENCHMARK(CreateVespalibString);
private:
    size_t onRun() override;
};

class ClockSystem : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockSystem);
private:
    size_t onRun() override;
};

class ClockREALTIME : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockREALTIME);
private:
    size_t onRun() override;
};

class ClockMONOTONIC : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockMONOTONIC);
private:
    size_t onRun() override;
};

class ClockMONOTONIC_RAW : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockMONOTONIC_RAW);
    ClockMONOTONIC_RAW();
private:
    size_t onRun() override;
};

class ClockPROCESS_CPUTIME_ID : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockPROCESS_CPUTIME_ID);
private:
    size_t onRun() override;
};

class ClockTHREAD_CPUTIME_ID : public Benchmark
{
public:
    DECLARE_BENCHMARK(ClockTHREAD_CPUTIME_ID);
private:
    size_t onRun() override;
};

}
