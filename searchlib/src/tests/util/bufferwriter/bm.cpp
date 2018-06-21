// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("bufferwriter_bm");
#include <vespa/vespalib/testkit/testapp.h>
#include <iostream>
#include "work.h"
#include <vespa/searchlib/util/drainingbufferwriter.h>

using search::DrainingBufferWriter;

double getTime() { return fastos::TimeStamp(fastos::ClockSystem::now()).sec(); }

constexpr size_t million = 1000000;

enum class WorkFuncDispatch
{
    DIRECT,
    LAMBDA,
    FUNCTOR,
    FUNCTOR2
};


template <typename T>
void
callWork(size_t size, WorkFuncDispatch dispatch)
{
    std::vector<T> foo;
    DrainingBufferWriter writer;
    foo.resize(size);
    std::cout << "will write " << size << " elements of size " << sizeof(T) <<
        std::endl;
    double before = getTime();
    switch (dispatch) {
    case WorkFuncDispatch::DIRECT:
        work(foo, writer);
        break;
    case WorkFuncDispatch::LAMBDA:
        workLambda(foo, writer);
        break;
    case WorkFuncDispatch::FUNCTOR:
        workFunctor(foo, writer);
        break;
    case WorkFuncDispatch::FUNCTOR2:
        workFunctor2(foo, writer);
        break;
    default:
        LOG_ABORT("should not be reached");
    }
    double after = getTime();
    double delta = (after - before);
    double writeSpeed = writer.getBytesWritten() / delta;
    EXPECT_GREATER(writeSpeed, 1000);
    std::cout << "written is " << writer.getBytesWritten() << std::endl;
    std::cout << "time used is " << (delta * 1000.0) << " ms" << std::endl;
    std::cout << "write speed is " << writeSpeed << std::endl;
}


void
callWorks(WorkFuncDispatch dispatch)
{
    callWork<char>(million * 1000, dispatch);
    callWork<short>(million * 500, dispatch);
    callWork<int>(million * 250, dispatch);
    callWork<long>(million * 125, dispatch);
}

TEST("simple bufferwriter speed test")
{
    callWorks(WorkFuncDispatch::DIRECT);
}

TEST("lambda func bufferwriter speed test")
{
    callWorks(WorkFuncDispatch::LAMBDA);
}

TEST("functor bufferwriter speed test")
{
    callWorks(WorkFuncDispatch::FUNCTOR);
}

TEST("functor2 bufferwriter speed test")
{
    callWorks(WorkFuncDispatch::FUNCTOR2);
}


TEST_MAIN()
{
    TEST_RUN_ALL();
}
