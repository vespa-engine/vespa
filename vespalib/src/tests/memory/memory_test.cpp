// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/memory.h>

using namespace vespalib;

class B
{
public:
    virtual ~B() { }
    virtual B * clone() const { return new B(*this); }
};

class A : public B
{
public:
    virtual ~A() { }
    virtual A * clone() const override { return new A(*this); }
};

TEST("require that MallocAutoPtr works as expected") {
    MallocAutoPtr a(malloc(30));
    EXPECT_TRUE(a.get() != nullptr);
    void * tmp = a.get();
    MallocAutoPtr b(std::move(a));
    EXPECT_TRUE(tmp == b.get());
    EXPECT_TRUE(a.get() == nullptr);
    MallocAutoPtr c;
    c = std::move(b);
    EXPECT_TRUE(b.get() == nullptr);
    EXPECT_TRUE(tmp == c.get());
    MallocAutoPtr d(malloc(30));
    EXPECT_TRUE(d.get() != nullptr);
    tmp = c.get();
    d = std::move(c);
    EXPECT_TRUE(tmp == d.get());
    EXPECT_TRUE(c.get() == nullptr);
}

TEST("require that MallocPtr works as expected") {
    MallocPtr a(100);
    EXPECT_TRUE(a.size() == 100);
    EXPECT_TRUE(a.get() != nullptr);
    memset(a.get(), 17, a.size());
    MallocPtr b(a);
    EXPECT_TRUE(a.size() == 100);
    EXPECT_TRUE(a.get() != nullptr);
    EXPECT_TRUE(b.size() == 100);
    EXPECT_TRUE(b.get() != nullptr);
    EXPECT_TRUE(a.get() != b.get());
    EXPECT_TRUE(memcmp(a.get(), b.get(), a.size()) == 0);
    void * tmp = a.get();
    a = b;
    EXPECT_TRUE(a.size() == 100);
    EXPECT_TRUE(a.get() != nullptr);
    EXPECT_TRUE(a.get() != tmp);
    EXPECT_TRUE(memcmp(a.get(), b.get(), a.size()) == 0);
    MallocPtr d = std::move(b);
    EXPECT_TRUE(d.size() == 100);
    EXPECT_TRUE(d.get() != nullptr);
    EXPECT_TRUE(b.size() == 0);
    EXPECT_TRUE(b.get() == nullptr);
    MallocPtr c;
    c.realloc(89);
    EXPECT_EQUAL(c.size(), 89u);
    c.realloc(0);
    EXPECT_EQUAL(c.size(), 0u);
    EXPECT_TRUE(c == nullptr);    
}

TEST("require that CloneablePtr works as expected") {
    CloneablePtr<B> a(new A());
    EXPECT_TRUE(a.get() != nullptr);
    CloneablePtr<B> b(a);
    EXPECT_TRUE(a.get() != nullptr);
    EXPECT_TRUE(b.get() != nullptr);
    EXPECT_TRUE(b.get() != a.get());
    CloneablePtr<B> c;
    c = a;
    EXPECT_TRUE(a.get() != nullptr);
    EXPECT_TRUE(c.get() != nullptr);
    EXPECT_TRUE(c.get() != a.get());

    b = CloneablePtr<B>(new B());
    EXPECT_TRUE(dynamic_cast<B*>(b.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<A*>(b.get()) == nullptr);
    EXPECT_TRUE(dynamic_cast<B*>(a.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<A*>(a.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<B*>(c.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<A*>(c.get()) != nullptr);
    c = b;
    EXPECT_TRUE(dynamic_cast<B*>(c.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<A*>(c.get()) == nullptr);
}

TEST("require that CloneablePtr bool conversion works as expected") {
    {
        CloneablePtr<B> null;
        if (null) {
            EXPECT_TRUE(false);
        } else {
            EXPECT_FALSE(bool(null));
            EXPECT_TRUE(!null);
        }
    }
    {
        CloneablePtr<B> notNull(new A());
        if (notNull) {
            EXPECT_TRUE(bool(notNull));
            EXPECT_FALSE(!notNull);
        } else {
            EXPECT_TRUE(false);
        }
    }
}

TEST("require that VESPA_NELEMS works as expected") {
    int a[3];
    int b[4] = {0,1,2,3};
    int c[4] = {0,1,2};
    int d[] = {0,1,2,3,4};
    EXPECT_EQUAL(VESPA_NELEMS(a), 3u);
    EXPECT_EQUAL(VESPA_NELEMS(b), 4u);
    EXPECT_EQUAL(VESPA_NELEMS(c), 4u);
    EXPECT_EQUAL(VESPA_NELEMS(d), 5u);
}

TEST("require that memcpy_safe works as expected") {
    vespalib::string a("abcdefgh");
    vespalib::string b("01234567");
    memcpy_safe(&b[0], &a[0], 4);
    memcpy_safe(nullptr, &a[0], 0);
    memcpy_safe(&b[0], nullptr, 0);
    memcpy_safe(nullptr, nullptr, 0);
    EXPECT_EQUAL(vespalib::string("abcdefgh"), a);
    EXPECT_EQUAL(vespalib::string("abcd4567"), b);
}

TEST("require that memmove_safe works as expected") {
    vespalib::string str("0123456789");
    memmove_safe(&str[2], &str[0], 5);
    memmove_safe(nullptr, &str[0], 0);
    memmove_safe(&str[0], nullptr, 0);
    memmove_safe(nullptr, nullptr, 0);
    EXPECT_EQUAL(vespalib::string("0101234789"), str);
}

TEST("require that memcmp_safe works as expected") {
    vespalib::string a("ab");
    vespalib::string b("ac");
    EXPECT_EQUAL(memcmp_safe(&a[0], &b[0], 0), 0);
    EXPECT_EQUAL(memcmp_safe(nullptr, &b[0], 0), 0);
    EXPECT_EQUAL(memcmp_safe(&a[0], nullptr, 0), 0);
    EXPECT_EQUAL(memcmp_safe(nullptr, nullptr, 0), 0);
    EXPECT_EQUAL(memcmp_safe(&a[0], &b[0], 1), 0);
    EXPECT_LESS(memcmp_safe(&a[0], &b[0], 2), 0);
    EXPECT_GREATER(memcmp_safe(&b[0], &a[0], 2), 0);
}

TEST("require that Unaligned wrapper works as expected") {
    struct Data {
        char buf[sizeof(uint32_t) * 11]; // space for 10 unaligned values
        void *get(size_t idx) { return buf + (idx * sizeof(uint32_t)) + 3; }
        const void *cget(size_t idx) { return get(idx); }
        Data() { memset(buf, 0, sizeof(buf)); }
    };
    Data data;
    EXPECT_EQUAL(sizeof(Unaligned<uint32_t>), sizeof(uint32_t));
    EXPECT_EQUAL(alignof(Unaligned<uint32_t>), 1u);
    Unaligned<uint32_t> *arr = Unaligned<uint32_t>::ptr(data.get(0));
    const Unaligned<uint32_t> *carr = Unaligned<uint32_t>::ptr(data.cget(0));
    Unaligned<uint32_t>::at(data.get(0)).write(123);
    Unaligned<uint32_t>::at(data.get(1)) = 456;
    arr[2] = 789;
    arr[3] = arr[0];
    arr[4] = arr[1].read();
    arr[5].write(arr[2]);
    EXPECT_EQUAL(Unaligned<uint32_t>::at(data.get(0)).read(), 123u);
    EXPECT_EQUAL(Unaligned<uint32_t>::at(data.get(1)), 456u);
    EXPECT_EQUAL(arr[2], 789u);
    EXPECT_EQUAL(carr[3].read(), 123u);
    EXPECT_EQUAL(carr[4], 456u);
    EXPECT_EQUAL(carr[5], 789u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
