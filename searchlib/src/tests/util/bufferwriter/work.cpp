// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "work.h"
#include <vespa/searchlib/util/bufferwriter.h>

namespace search
{

template <class T>
class WriteFunctor
{
    BufferWriter &_writer;
public:
    WriteFunctor(BufferWriter &writer)
        : _writer(writer)
    {
    }

    void operator()(const T &val) { _writer.write(&val, sizeof(val)); }
};

template <class T>
class WriteFunctor2
{
    BufferWriter &_writer;
public:
    WriteFunctor2(BufferWriter &writer)
        : _writer(writer)
    {
    }

    void operator()(const T &val) __attribute((noinline))
    { _writer.write(&val, sizeof(val)); }
};

template <class T, class Func>
void workLoop(const std::vector<T> &v, Func &&func)
{
    for (const auto &val : v) {
        func(val);
    }
}

template <class T>
void work(const std::vector<T> &v, BufferWriter &writer)
{
    for (const auto &val : v) {
        writer.write(&val, sizeof(val));
    }
    writer.flush();
}

template <class T>
void workLambda(const std::vector<T> &v, BufferWriter &writer)
{
    workLoop<T>(v,
                [&writer](const T &val) { writer.write(&val, sizeof(val)); });
    writer.flush();
}

template <class T>
void workFunctor(const std::vector<T> &v, BufferWriter &writer)
{
    workLoop<T>(v, WriteFunctor<T>(writer));
    writer.flush();
}

template <class T>
void workFunctor2(const std::vector<T> &v, BufferWriter &writer)
{
    workLoop<T>(v, WriteFunctor2<T>(writer));
    writer.flush();
}

template void work(const std::vector<char> &v, BufferWriter &writer);
template void work(const std::vector<short> &v, BufferWriter &writer);
template void work(const std::vector<int> &v, BufferWriter &writer);
template void work(const std::vector<long> &v, BufferWriter &writer);
template void workLambda(const std::vector<char> &v, BufferWriter &writer);
template void workLambda(const std::vector<short> &v, BufferWriter &writer);
template void workLambda(const std::vector<int> &v, BufferWriter &writer);
template void workLambda(const std::vector<long> &v, BufferWriter &writer);
template void workFunctor(const std::vector<char> &v, BufferWriter &writer);
template void workFunctor(const std::vector<short> &v, BufferWriter &writer);
template void workFunctor(const std::vector<int> &v, BufferWriter &writer);
template void workFunctor(const std::vector<long> &v, BufferWriter &writer);
template void workFunctor2(const std::vector<char> &v, BufferWriter &writer);
template void workFunctor2(const std::vector<short> &v, BufferWriter &writer);
template void workFunctor2(const std::vector<int> &v, BufferWriter &writer);
template void workFunctor2(const std::vector<long> &v, BufferWriter &writer);

} // namespace search
