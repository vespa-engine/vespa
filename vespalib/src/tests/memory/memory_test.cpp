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

class Test : public TestApp
{
public:
    int Main() override;
};

B* fn(auto_arr<B> param) { return param.get(); }
auto_arr<B> fn(B *param) { auto_arr<B> bb(param); return bb; }

int
Test::Main()
{
    TEST_INIT("memory_test");
    {
        B* p = new B[5];
        auto_arr<B> apb(p);
        EXPECT_TRUE(apb.get() == p);
        EXPECT_TRUE(fn(apb) == p);
        EXPECT_TRUE(apb.get() == nullptr);
    }
    {
        A* p = new A[5];
        auto_arr<A> apa(p);
        EXPECT_TRUE(apa.get() == p);
        auto_arr<A> apb = apa;
        EXPECT_TRUE(apa.get() == nullptr);
        EXPECT_TRUE(apb.get() == p);
        A& ref = apb[2];
        EXPECT_TRUE(&ref == (p+2));
    }
    {
        B* p = new B[5];
        auto_arr<B> apb = fn(p);
        EXPECT_TRUE(apb.get() == p);
    }
    {
        MallocAutoPtr a(malloc(30));
        EXPECT_TRUE(a.get() != nullptr);
        void * tmp = a.get();
        MallocAutoPtr b(a);
        EXPECT_TRUE(tmp == b.get());
        EXPECT_TRUE(a.get() == nullptr);
        MallocAutoPtr c;
        c = b;
        EXPECT_TRUE(b.get() == nullptr);
        EXPECT_TRUE(tmp == c.get());
        MallocAutoPtr d(malloc(30));
        EXPECT_TRUE(d.get() != nullptr);
        tmp = c.get();
        d = c;
        EXPECT_TRUE(tmp == d.get());
        EXPECT_TRUE(c.get() == nullptr);
    }
    {

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
    {
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
    {
        int a[3];
        int b[4] = {0,1,2,3};
        int c[4] = {0,1,2};
        int d[] = {0,1,2,3,4};
        EXPECT_EQUAL(VESPA_NELEMS(a), 3u);
        EXPECT_EQUAL(VESPA_NELEMS(b), 4u);
        EXPECT_EQUAL(VESPA_NELEMS(c), 4u);
        EXPECT_EQUAL(VESPA_NELEMS(d), 5u);
    }
    TEST_DONE();
}

TEST_APPHOOK(Test)
