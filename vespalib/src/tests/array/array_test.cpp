// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/testkit/test_master.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/size_literals.h>
#include <atomic>
#include <deque>
#include <string>

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

using alloc::Alloc;
using alloc::MemoryAllocator;
using MyMemoryAllocator = vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MyMemoryAllocator::Stats;

class Clever {
public:
    Clever() : _counter(&_global) { (*_counter)++; }
    explicit Clever(std::atomic<size_t> * counter) :
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

std::ostream & operator << (std::ostream & os, const Clever & clever) noexcept
{
    (void) clever;
    return os;
}

template <typename T>
void
testArray(const T & a, const T & b)
{
    Array<T> array;

    ASSERT_EQ(sizeof(array), 32u);
    ASSERT_EQ(array.size(), 0u);
    ASSERT_EQ(array.capacity(), 0u);
    for(size_t i(0); i < 5; i++) {
        array.push_back(a);
        array.push_back(b);
        for (size_t j(0); j <= i; j++) {
            ASSERT_EQ(array[j*2 + 0], a);
            ASSERT_EQ(array[j*2 + 1], b);
        }
    }
    ASSERT_EQ(array.size(), 10u);
    ASSERT_EQ(array.capacity(), 16u);
    for (size_t i(array.size()), m(array.capacity()); i < m; i+=2) {
        array.push_back(a);
        array.push_back(b);
        for (size_t j(0); j <= (i/2); j++) {
            ASSERT_EQ(array[j*2 + 0], a);
            ASSERT_EQ(array[j*2 + 1], b);
        }
    }
    ASSERT_EQ(array.size(), array.capacity());
}

TEST(ArrayTest, test_basic_array_functionality)
{
    testArray<int>(7, 9);
    testArray<std::string>("7", "9");
    const char * longS1 = "more than 48 bytes bytes that are needed to avoid the small string optimisation in std::string";
    const char * longS2 = "even more more than 48 bytes bytes that are needed to avoid the small string optimisation in std::string";
    EXPECT_TRUE(strlen(longS1) > sizeof(std::string));
    EXPECT_TRUE(strlen(longS2) > sizeof(std::string));
    testArray<std::string>(longS1, longS2);
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
    EXPECT_EQ(0ul, counter);
}

TEST(ArrayTest, test_that_organic_growth_is_by_2_in_N_and_reserve_resize_are_exact)
{
    Array<char> c(256);
    EXPECT_EQ(256u, c.size());
    EXPECT_EQ(256u, c.capacity());
    c.reserve(258);
    EXPECT_EQ(256u, c.size());
    EXPECT_EQ(258u, c.capacity());
    c.resize(258);
    EXPECT_EQ(258u, c.size());
    EXPECT_EQ(258u, c.capacity());
    c.resize(511);
    EXPECT_EQ(511u, c.size());
    EXPECT_EQ(511u, c.capacity());
    c.push_back('j');
    EXPECT_EQ(512u, c.size());
    EXPECT_EQ(512u, c.capacity());
    c.push_back('j');
    EXPECT_EQ(513u, c.size());
    EXPECT_EQ(1_Ki, c.capacity());
    for(size_t i(513); i < 1024; i++) {
        c.push_back('a');
    }
    EXPECT_EQ(1_Ki, c.size());
    EXPECT_EQ(1_Ki, c.capacity());
    c.reserve(1025);
    EXPECT_EQ(1_Ki, c.size());
    EXPECT_EQ(1025u, c.capacity());
    c.push_back('b');   // Within, no growth
    EXPECT_EQ(1025u, c.size());
    EXPECT_EQ(1025u, c.capacity());
    c.push_back('b');   // Above, grow.
    EXPECT_EQ(1026u, c.size());
    EXPECT_EQ(2048u, c.capacity());
}

std::atomic<size_t> Clever::_global = 0;

TEST(ArrayTest, test_complicated)
{
    std::atomic<size_t> counter(0);
    {
        EXPECT_EQ(0ul, Clever::getGlobal());
        Clever c(&counter);
        EXPECT_EQ(1ul, counter);
        EXPECT_EQ(0ul, Clever::getGlobal());
        {
            Array<Clever> h;
            EXPECT_EQ(0ul, h.size());
            h.resize(1);
            EXPECT_EQ(1ul, Clever::getGlobal());
            h[0] = c;
            EXPECT_EQ(0ul, Clever::getGlobal());
            h.resize(10000);
            EXPECT_EQ(9999ul, Clever::getGlobal());
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQ(2+i, counter);
            }
            EXPECT_EQ(10001ul, counter);
            EXPECT_EQ(0ul, Clever::getGlobal());
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQ(10001ul, counter);
            }
            EXPECT_EQ(10001ul, counter);
            h.clear();
            EXPECT_EQ(1ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h.push_back(c);
                EXPECT_EQ(2+i, counter);
            }
            EXPECT_EQ(10001ul, counter);
            h.pop_back();
            EXPECT_EQ(10000ul, counter);
        }
        EXPECT_EQ(0ul, Clever::getGlobal());
        EXPECT_EQ(1ul, counter);
    }
    EXPECT_EQ(0ul, Clever::getGlobal());
    EXPECT_EQ(0ul, counter);
}

template<class T>
void
testBeginEnd(T & v)
{
    EXPECT_EQ(0u, v.end() - v.begin());
    EXPECT_EQ(0u, v.rend() - v.rbegin());
    v.push_back(1);
    v.push_back(2);
    v.push_back(3);

    EXPECT_EQ(1u, *(v.begin()));
    EXPECT_EQ(3u, *(v.end() - 1));

    auto i(v.begin());
    EXPECT_EQ(1u, *i);
    EXPECT_EQ(2u, *(i+1));
    EXPECT_EQ(1u, *i++);
    EXPECT_EQ(2u, *i);
    EXPECT_EQ(3u, *++i);
    EXPECT_EQ(3u, *i);
    EXPECT_EQ(3u, *i--);
    EXPECT_EQ(2u, *i);
    EXPECT_EQ(1u, *--i);

    typename T::const_iterator ic(v.begin());
    EXPECT_EQ(1u, *ic);
    EXPECT_EQ(2u, *(ic+1));
    EXPECT_EQ(1u, *ic++);
    EXPECT_EQ(2u, *ic);
    EXPECT_EQ(3u, *++ic);
    EXPECT_EQ(3u, *ic);
    EXPECT_EQ(3u, *ic--);
    EXPECT_EQ(2u, *ic);
    EXPECT_EQ(1u, *--ic);

    EXPECT_EQ(3u, *(v.rbegin()));
    EXPECT_EQ(1u, *(v.rend() - 1));

    auto r(v.rbegin());
    EXPECT_EQ(3u, *r);
    EXPECT_EQ(2u, *(r+1));
    EXPECT_EQ(3u, *r++);
    EXPECT_EQ(2u, *r);
    EXPECT_EQ(1u, *++r);
    EXPECT_EQ(1u, *r);
    EXPECT_EQ(1u, *r--);
    EXPECT_EQ(2u, *r);
    EXPECT_EQ(3u, *--r);

    typename T::const_reverse_iterator rc(v.rbegin());
    EXPECT_EQ(3u, *rc);
    EXPECT_EQ(2u, *(rc+1));
    EXPECT_EQ(3u, *rc++);
    EXPECT_EQ(2u, *rc);
    EXPECT_EQ(1u, *++rc);
    EXPECT_EQ(1u, *rc);
    EXPECT_EQ(1u, *rc--);
    EXPECT_EQ(2u, *rc);
    EXPECT_EQ(3u, *--rc);

    EXPECT_EQ(3u, v.end() - v.begin());
    EXPECT_EQ(3u, v.rend() - v.rbegin());
}

TEST(ArrayTest, test_begin_end)
{
    std::vector<size_t> v;
    Array<size_t> a;
    testBeginEnd(v);
    testBeginEnd(a);
}

TEST(ArrayTest, test_move_constructor)
{
    Array<size_t> orig;
    orig.push_back(42);
    EXPECT_EQ(1u, orig.size());
    EXPECT_EQ(42u, orig[0]);
    {
        Array<size_t> copy(orig);
        EXPECT_EQ(1u, orig.size());
        EXPECT_EQ(42u, orig[0]);
        EXPECT_EQ(1u, copy.size());
        EXPECT_EQ(42u, copy[0]);
    }
    ++orig[0];
    {
        Array<size_t> copy(std::move(orig));
        EXPECT_EQ(0u, orig.size());
        EXPECT_EQ(1u, copy.size());
        EXPECT_EQ(43u, copy[0]);
    }
}

TEST(ArrayTest, test_move_assignment)
{
    Array<size_t> orig;
    orig.push_back(44);
    EXPECT_EQ(1u, orig.size());
    EXPECT_EQ(44u, orig[0]);
    {
        Array<size_t> copy;
        copy = orig;
        EXPECT_EQ(1u, orig.size());
        EXPECT_EQ(44u, orig[0]);
        EXPECT_EQ(1u, copy.size());
        EXPECT_EQ(44u, copy[0]);
    }
    ++orig[0];
    {
        Array<size_t> copy;
        copy = std::move(orig);
        EXPECT_EQ(0u, orig.size());
        EXPECT_EQ(1u, copy.size());
        EXPECT_EQ(45u, copy[0]);
    }
}

struct UnreserveFixture {
    Array<int> arr;
    UnreserveFixture() : arr(page_ints() + 1, 7, alloc::Alloc::allocMMap(0))
    {
        EXPECT_EQ(page_ints() + 1, arr.size());
        EXPECT_EQ(2 * page_ints(), arr.capacity());
    }

    static size_t page_ints() {
        return round_up_to_page_size(1) / sizeof(int);
    }
};

TEST(ArrayTest, require_that_try_unreserve_fails_if_wanted_capacity_ge_current_capacity)
{
    UnreserveFixture f;
    EXPECT_FALSE(f.arr.try_unreserve(2 * UnreserveFixture::page_ints()));
}

TEST(ArrayTest, require_that_try_unreserve_fails_if_wanted_capacity_lt_current_size)
{
    UnreserveFixture f;
    EXPECT_FALSE(f.arr.try_unreserve(UnreserveFixture::page_ints()));
}

TEST(ArrayTest, require_that_try_unreserve_succeedes_if_mmap_can_be_shrinked)
{
    UnreserveFixture f;
    int *oldPtr = &f.arr[0];
    f.arr.resize(512);
    EXPECT_TRUE(f.arr.try_unreserve(UnreserveFixture::page_ints() - 1));
    EXPECT_EQ(UnreserveFixture::page_ints(), f.arr.capacity());
    int *newPtr = &f.arr[0];
    EXPECT_EQ(oldPtr, newPtr);
}

struct Fixture {
    AllocStats stats;
    std::unique_ptr<MemoryAllocator> allocator;
    Alloc initial_alloc;
    Array<int> arr;

    Fixture();
    ~Fixture();
};

Fixture::Fixture()
    : stats(),
      allocator(std::make_unique<MyMemoryAllocator>(stats)),
      initial_alloc(Alloc::alloc_with_allocator(allocator.get())),
      arr(initial_alloc)
{
}

Fixture::~Fixture() = default;

TEST(ArrayTest, require_that_memory_allocator_can_be_set)
{
    Fixture f;
    f.arr.resize(1);
    EXPECT_EQ(AllocStats(1, 0), f.stats);
}

TEST(ArrayTest, require_that_memory_allocator_is_preserved_across_reset)
{
    Fixture f;
    f.arr.resize(1);
    f.arr.reset();
    f.arr.resize(1);
    EXPECT_EQ(AllocStats(2, 1), f.stats);
}

TEST(ArrayTest, require_that_created_array_uses_same_memory_allocator)
{
    Fixture f;
    auto arr2 = f.arr.create();
    EXPECT_EQ(AllocStats(0, 0), f.stats);
    arr2.resize(1);
    EXPECT_EQ(AllocStats(1, 0), f.stats);
}

GTEST_MAIN_RUN_ALL_TESTS()
