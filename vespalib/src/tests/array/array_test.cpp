// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <deque>
#include <atomic>

using namespace vespalib;

namespace vespalib {

template <typename T>
std::ostream & operator << (std::ostream & os, const Array<T> & a)
{
    os << '{';
    if (! a.empty()) {
        for (size_t i(0), m(a.size()-1); i < m; i++) {
            os << a[i] << ", ";
        }
        os << a[a.size()-1];
    }
    os << '}';
    return os;
}

}

class Clever {
public:
    Clever() : _counter(&_global) { (*_counter)++; }
    Clever(std::atomic<size_t> * counter) :
        _counter(counter)
    {
        (*_counter)++;
    }
    Clever(const Clever & rhs) :
        _counter(rhs._counter)
    {
        (*_counter)++;
    }
    Clever & operator = (const Clever & rhs)
    {
        if (&rhs != this) {
            Clever tmp(rhs);
            swap(tmp);
        }
        return *this;
    }
    void swap(Clever & rhs)
    {
        std::swap(_counter, rhs._counter);
    }
    ~Clever() { (*_counter)--; }
    static size_t getGlobal() { return _global; }
    bool operator == (const Clever & b) const { return _counter == b._counter; }
private:
    std::atomic<size_t> * _counter;
    static std::atomic<size_t> _global;
};

std::ostream & operator << (std::ostream & os, const Clever & clever)
{
    (void) clever;
    return os;
}

template <typename T>
void
testArray(const T & a, const T & b)
{
    Array<T> array;

    ASSERT_EQUAL(sizeof(array), 32u);
    ASSERT_EQUAL(array.size(), 0u);
    ASSERT_EQUAL(array.capacity(), 0u);
    for(size_t i(0); i < 5; i++) {
        array.push_back(a);
        array.push_back(b);
        for (size_t j(0); j <= i; j++) {
            ASSERT_EQUAL(array[j*2 + 0], a);
            ASSERT_EQUAL(array[j*2 + 1], b);
        }
    }
    ASSERT_EQUAL(array.size(), 10u);
    ASSERT_EQUAL(array.capacity(), 16u);
    for (size_t i(array.size()), m(array.capacity()); i < m; i+=2) {
        array.push_back(a);
        array.push_back(b);
        for (size_t j(0); j <= (i/2); j++) {
            ASSERT_EQUAL(array[j*2 + 0], a);
            ASSERT_EQUAL(array[j*2 + 1], b);
        }
    }
    ASSERT_EQUAL(array.size(), array.capacity());
}

TEST("test basic array functionality")
{
    testArray<int>(7, 9);
    testArray<vespalib::string>("7", "9");
    const char * longS1 = "more than 48 bytes bytes that are needed to avoid the small string optimisation in vespalib::string";
    const char * longS2 = "even more more than 48 bytes bytes that are needed to avoid the small string optimisation in vespalib::string";
    EXPECT_EQUAL(64ul, sizeof(vespalib::string));
    EXPECT_TRUE(strlen(longS1) > sizeof(vespalib::string));
    EXPECT_TRUE(strlen(longS2) > sizeof(vespalib::string));
    testArray<vespalib::string>(longS1, longS2);
    Array<int> a(2);
    a[0] = 8;
    a[1] = 13;
    Array<int> b(3);
    b[0] = 8;
    b[1] = 13;
    b[2] = 15;
    testArray(a, b);
    EXPECT_TRUE(a == a);
    EXPECT_FALSE(a == b);
    std::atomic<size_t> counter(0);
    testArray(Clever(&counter),  Clever(&counter));
    EXPECT_EQUAL(0ul, counter);
}

TEST("test that organic growth is by 2 in N and reserve resize are exact")
{
    Array<char> c(256);
    EXPECT_EQUAL(256u, c.size());
    EXPECT_EQUAL(256u, c.capacity());
    c.reserve(258);
    EXPECT_EQUAL(256u, c.size());
    EXPECT_EQUAL(258u, c.capacity());
    c.resize(258);
    EXPECT_EQUAL(258u, c.size());
    EXPECT_EQUAL(258u, c.capacity());
    c.resize(511);
    EXPECT_EQUAL(511u, c.size());
    EXPECT_EQUAL(511u, c.capacity());
    c.push_back('j');
    EXPECT_EQUAL(512u, c.size());
    EXPECT_EQUAL(512u, c.capacity());
    c.push_back('j');
    EXPECT_EQUAL(513u, c.size());
    EXPECT_EQUAL(1_Ki, c.capacity());
    for(size_t i(513); i < 1024; i++) {
        c.push_back('a');
    }
    EXPECT_EQUAL(1_Ki, c.size());
    EXPECT_EQUAL(1_Ki, c.capacity());
    c.reserve(1025);
    EXPECT_EQUAL(1_Ki, c.size());
    EXPECT_EQUAL(1025u, c.capacity());
    c.push_back('b');   // Within, no growth
    EXPECT_EQUAL(1025u, c.size());
    EXPECT_EQUAL(1025u, c.capacity());
    c.push_back('b');   // Above, grow.
    EXPECT_EQUAL(1026u, c.size());
    EXPECT_EQUAL(2048u, c.capacity());
}

std::atomic<size_t> Clever::_global = 0;

TEST("test complicated")
{
    std::atomic<size_t> counter(0);
    {
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        Clever c(&counter);
        EXPECT_EQUAL(1ul, counter);
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        {
            Array<Clever> h;
            EXPECT_EQUAL(0ul, h.size());
            h.resize(1);
            EXPECT_EQUAL(1ul, Clever::getGlobal());
            h[0] = c;
            EXPECT_EQUAL(0ul, Clever::getGlobal());
            h.resize(10000);
            EXPECT_EQUAL(9999ul, Clever::getGlobal());
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQUAL(2+i, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
            EXPECT_EQUAL(0ul, Clever::getGlobal());
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQUAL(10001ul, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
            h.clear();
            EXPECT_EQUAL(1ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h.push_back(c);
                EXPECT_EQUAL(2+i, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
            h.pop_back();
            EXPECT_EQUAL(10000ul, counter);
        }
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        EXPECT_EQUAL(1ul, counter);
    }
    EXPECT_EQUAL(0ul, Clever::getGlobal());
    EXPECT_EQUAL(0ul, counter);
}

template<class T>
void
testBeginEnd(T & v)
{
    EXPECT_EQUAL(0u, v.end() - v.begin());
    EXPECT_EQUAL(0u, v.rend() - v.rbegin());
    v.push_back(1);
    v.push_back(2);
    v.push_back(3);

    EXPECT_EQUAL(1u, *(v.begin()));
    EXPECT_EQUAL(3u, *(v.end() - 1));

    typename T::iterator i(v.begin());
    EXPECT_EQUAL(1u, *i);
    EXPECT_EQUAL(2u, *(i+1));
    EXPECT_EQUAL(1u, *i++);
    EXPECT_EQUAL(2u, *i);
    EXPECT_EQUAL(3u, *++i);
    EXPECT_EQUAL(3u, *i);
    EXPECT_EQUAL(3u, *i--);
    EXPECT_EQUAL(2u, *i);
    EXPECT_EQUAL(1u, *--i);

    typename T::const_iterator ic(v.begin());
    EXPECT_EQUAL(1u, *ic);
    EXPECT_EQUAL(2u, *(ic+1));
    EXPECT_EQUAL(1u, *ic++);
    EXPECT_EQUAL(2u, *ic);
    EXPECT_EQUAL(3u, *++ic);
    EXPECT_EQUAL(3u, *ic);
    EXPECT_EQUAL(3u, *ic--);
    EXPECT_EQUAL(2u, *ic);
    EXPECT_EQUAL(1u, *--ic);

    EXPECT_EQUAL(3u, *(v.rbegin()));
    EXPECT_EQUAL(1u, *(v.rend() - 1));

    typename T::reverse_iterator r(v.rbegin());
    EXPECT_EQUAL(3u, *r);
    EXPECT_EQUAL(2u, *(r+1));
    EXPECT_EQUAL(3u, *r++);
    EXPECT_EQUAL(2u, *r);
    EXPECT_EQUAL(1u, *++r);
    EXPECT_EQUAL(1u, *r);
    EXPECT_EQUAL(1u, *r--);
    EXPECT_EQUAL(2u, *r);
    EXPECT_EQUAL(3u, *--r);

    typename T::const_reverse_iterator rc(v.rbegin());
    EXPECT_EQUAL(3u, *rc);
    EXPECT_EQUAL(2u, *(rc+1));
    EXPECT_EQUAL(3u, *rc++);
    EXPECT_EQUAL(2u, *rc);
    EXPECT_EQUAL(1u, *++rc);
    EXPECT_EQUAL(1u, *rc);
    EXPECT_EQUAL(1u, *rc--);
    EXPECT_EQUAL(2u, *rc);
    EXPECT_EQUAL(3u, *--rc);

    EXPECT_EQUAL(3u, v.end() - v.begin());
    EXPECT_EQUAL(3u, v.rend() - v.rbegin());
}

TEST("test begin end")
{
    std::vector<size_t> v;
    Array<size_t> a;
    testBeginEnd(v);
    testBeginEnd(a);
}

TEST("test move constructor")
{
    Array<size_t> orig;
    orig.push_back(42);
    EXPECT_EQUAL(1u, orig.size());
    EXPECT_EQUAL(42u, orig[0]);
    {
        Array<size_t> copy(orig);
        EXPECT_EQUAL(1u, orig.size());
        EXPECT_EQUAL(42u, orig[0]);
        EXPECT_EQUAL(1u, copy.size());
        EXPECT_EQUAL(42u, copy[0]);
    }
    ++orig[0];
    {
        Array<size_t> copy(std::move(orig));
        EXPECT_EQUAL(0u, orig.size());
        EXPECT_EQUAL(1u, copy.size());
        EXPECT_EQUAL(43u, copy[0]);
    }
}

TEST("test move assignment")
{
    Array<size_t> orig;
    orig.push_back(44);
    EXPECT_EQUAL(1u, orig.size());
    EXPECT_EQUAL(44u, orig[0]);
    {
        Array<size_t> copy;
        copy = orig;
        EXPECT_EQUAL(1u, orig.size());
        EXPECT_EQUAL(44u, orig[0]);
        EXPECT_EQUAL(1u, copy.size());
        EXPECT_EQUAL(44u, copy[0]);
    }
    ++orig[0];
    {
        Array<size_t> copy;
        copy = std::move(orig);
        EXPECT_EQUAL(0u, orig.size());
        EXPECT_EQUAL(1u, copy.size());
        EXPECT_EQUAL(45u, copy[0]);
    }
}

struct UnreserveFixture {
    Array<int> arr;
    UnreserveFixture() : arr(1025, 7, alloc::Alloc::allocMMap(0))
    {
        EXPECT_EQUAL(1025u, arr.size());
        EXPECT_EQUAL(2048u, arr.capacity());
    }
};

TEST_F("require that try_unreserve() fails if wanted capacity >= current capacity", UnreserveFixture)
{
    EXPECT_FALSE(f.arr.try_unreserve(2048));
}

TEST_F("require that try_unreserve() fails if wanted capacity < current size", UnreserveFixture)
{
    EXPECT_FALSE(f.arr.try_unreserve(1024));
}

TEST_F("require that try_unreserve() succeedes if mmap can be shrinked", UnreserveFixture)
{
    int *oldPtr = &f.arr[0];
    f.arr.resize(512);
    EXPECT_TRUE(f.arr.try_unreserve(1023));
    EXPECT_EQUAL(1_Ki, f.arr.capacity());
    int *newPtr = &f.arr[0];
    EXPECT_EQUAL(oldPtr, newPtr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
