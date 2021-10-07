// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "testbase.h"
#include <vespa/vespalib/util/time.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".testbase");

using namespace std::chrono;

namespace vespalib {

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS(vespalib, Benchmark, Identifiable);

IMPLEMENT_BENCHMARK(ParamByReferenceVectorInt, Benchmark);
IMPLEMENT_BENCHMARK(ParamByValueVectorInt, Benchmark);
IMPLEMENT_BENCHMARK(ParamByReferenceVectorString, Benchmark);
IMPLEMENT_BENCHMARK(ParamByValueVectorString, Benchmark);
IMPLEMENT_BENCHMARK(ReturnByReferenceVectorString, Benchmark);
IMPLEMENT_BENCHMARK(ReturnByValueVectorString, Benchmark);
IMPLEMENT_BENCHMARK(ReturnByValueMultiVectorString, Benchmark);
IMPLEMENT_BENCHMARK(ClockSystem, Benchmark);
IMPLEMENT_BENCHMARK(ClockREALTIME, Benchmark);
IMPLEMENT_BENCHMARK(ClockMONOTONIC, Benchmark);
IMPLEMENT_BENCHMARK(ClockMONOTONIC_RAW, Benchmark);
IMPLEMENT_BENCHMARK(ClockPROCESS_CPUTIME_ID, Benchmark);
IMPLEMENT_BENCHMARK(ClockTHREAD_CPUTIME_ID, Benchmark);
IMPLEMENT_BENCHMARK(CreateVespalibString, Benchmark);

void Benchmark::run(const char *name, size_t numRuns, size_t concurrency)
{
    const Identifiable::RuntimeClass * cInfo = Identifiable::classFromName(name);
    if (cInfo) {
        std::unique_ptr<Benchmark> test(static_cast<Benchmark *>(cInfo->create()));
        test->run(numRuns, concurrency);
    } else {
        LOG(warning, "Could not find any test with the name %s", name);
    }
}
void Benchmark::run(size_t numRuns, size_t concurrency)
{
    LOG(info, "Starting benchmark %s with %ld threads and %ld rep", getClass().name(), concurrency, numRuns);
    for (size_t i(0); i < numRuns; i++) {
        onRun();
    }
    LOG(info, "Stopping benchmark %s", getClass().name());
}

size_t ParamByReferenceVectorInt::callByReference(const Vector & values) const
{
    return values.size();
}

size_t ParamByReferenceVectorInt::onRun()
{
    Vector values(1000);
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += callByReference(values);
    }
    return sum;
}

size_t ParamByValueVectorInt::callByValue(Vector values) const
{
    return values.size();
}

size_t ParamByValueVectorInt::onRun()
{
    Vector values(1000);
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += callByValue(values);
    }
    return sum;
}

size_t ParamByReferenceVectorString::callByReference(const Vector & values) const
{
    return values.size();
}

size_t ParamByReferenceVectorString::onRun()
{
    Vector values(1000, "This is a simple string copy test");
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += callByReference(values);
    }
    return sum;
}

size_t ParamByValueVectorString::callByValue(Vector values) const
{
    return values.size();
}

size_t ParamByValueVectorString::onRun()
{
    Vector values(1000, "This is a simple string copy test");
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += callByValue(values);
    }
    return sum;
}

const ReturnByReferenceVectorString::Vector & ReturnByReferenceVectorString::returnByReference(Vector & param) const
{
    Vector values(1000, "return by value");
    param.swap(values);
    return param;
}

size_t ReturnByReferenceVectorString::onRun()
{
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        Vector values;
        sum += returnByReference(values).size();
    }
    return sum;
}

ReturnByValueVectorString::Vector ReturnByValueVectorString::returnByValue() const
{
    Vector values;
    if (rand() % 7) {
        Vector tmp(1000, "return by value");
        values.swap(tmp);
    } else {
        Vector tmp(1000, "Return by value");
        values.swap(tmp);
    }
    return values;
}

size_t ReturnByValueVectorString::onRun()
{
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += returnByValue().size();
    }
    return sum;
}

ReturnByValueMultiVectorString::Vector ReturnByValueMultiVectorString::returnByValue() const
{
    if (rand() % 7) {
        Vector values(1000, "return by value");
        return values;
    } else {
        Vector values(1000, "Return by value");
        return values;
    }
}

size_t ReturnByValueMultiVectorString::onRun()
{
    size_t sum(0);
    for (size_t i=0; i < 1000; i++) {
        sum += returnByValue().size();
    }
    return sum;
}

size_t ClockSystem::onRun()
{
    vespalib::system_time start(vespalib::system_clock::now());
    vespalib::system_time end(start);
    for (size_t i=0; i < 1000; i++) {
        end = vespalib::system_clock::now();
    }
    return std::chrono::duration_cast<std::chrono::nanoseconds>(start - end).count();
}

size_t ClockREALTIME::onRun()
{
    struct timespec ts;
    int foo = clock_gettime(CLOCK_REALTIME, &ts);
    assert(foo == 0);
    (void) foo;
    nanoseconds start(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    nanoseconds end(start);
    for (size_t i=0; i < 1000; i++) {
        clock_gettime(CLOCK_REALTIME, &ts);
        end = nanoseconds(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    }
    return count_ns(start - end);
}

size_t ClockMONOTONIC::onRun()
{
    struct timespec ts;
    int foo = clock_gettime(CLOCK_MONOTONIC, &ts);
    assert(foo == 0);
    (void) foo;
    nanoseconds start(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    nanoseconds end(start);
    for (size_t i=0; i < 1000; i++) {
        clock_gettime(CLOCK_MONOTONIC, &ts);
        end = nanoseconds(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    }
    return count_ns(start - end);;
}

ClockMONOTONIC_RAW::ClockMONOTONIC_RAW()
{
#ifndef CLOCK_MONOTONIC_RAW
    LOG(warning, "CLOCK_MONOTONIC_RAW is not defined, using CLOCK_MONOTONIC instead.");
#endif
}

#ifndef CLOCK_MONOTONIC_RAW
    #define CLOCK_MONOTONIC_RAW CLOCK_MONOTONIC
#endif

size_t ClockMONOTONIC_RAW::onRun()
{
    struct timespec ts;
    int foo = clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    assert(foo == 0);
    (void) foo;
    nanoseconds start(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    nanoseconds end(start);
    for (size_t i=0; i < 1000; i++) {
        clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
        end = nanoseconds(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    }
    return count_ns(start - end);
}

size_t ClockPROCESS_CPUTIME_ID::onRun()
{
    struct timespec ts;
    int foo = clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts);
    assert(foo == 0);
    (void) foo;
    nanoseconds start(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    nanoseconds end(start);
    for (size_t i=0; i < 1000; i++) {
        clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts);
        end =nanoseconds(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    }
    return count_ns(start - end);
}

size_t ClockTHREAD_CPUTIME_ID::onRun()
{
    struct timespec ts;
    int foo = clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts);
    assert(foo == 0);
    (void) foo;
    nanoseconds start(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    nanoseconds end(start);
    for (size_t i=0; i < 1000; i++) {
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts);
        end = nanoseconds(ts.tv_sec*1000L*1000L*1000L + ts.tv_nsec);
    }
    return count_ns(start - end);
}

size_t CreateVespalibString::onRun()
{
    size_t sum(0);
    const char * text1("Dette er en passe");
    const char * text2(" kort streng som passer paa stacken");
    char text[100];
    strcpy(text, text1);
    strcat(text, text2);
    for (size_t i=0; i < 1000; i++) {
        string s(text);
        sum += s.size();
    }
    return sum;
}

}
