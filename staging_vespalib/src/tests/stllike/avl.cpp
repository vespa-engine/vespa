// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("memory_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespalib/stllike/avl_set.h>
#include <vespa/vespalib/stllike/avl_map.h>

using namespace vespalib;
using std::make_pair;

class Test : public TestApp
{
public:
    int Main();
    void testAvlTreeSet();
    void testAvlTreeSet2();
    void testAvlTreeMap();
    void testAvlTreeFind();
};

int
Test::Main()
{
    TEST_INIT("avl_test");
    testAvlTreeSet();
    testAvlTreeSet2();
    testAvlTreeMap();
    testAvlTreeFind();
    TEST_DONE();
}

namespace {
    struct Foo {
        int i;

        Foo() : i(0) {}
        Foo(int i_) : i(i_) {}

        bool operator<(const Foo& f) const
            { return (i < f.i); }
        friend std::ostream & operator << (std::ostream & os, const Foo & f) { return os << f.i; }
    };
}

void Test::testAvlTreeSet2()
{
    const size_t testSize(2000);
    avl_set<Foo> set(100);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(Foo(7));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(Foo(7)) != set.end());
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(set.find(Foo(8)) == set.end());
    // erase non existing
    set.erase(Foo(8));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(Foo(7)) != set.end());
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(set.find(Foo(8)) == set.end());
    // erase existing
    set.erase(Foo(7));
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(Foo(7)) == set.end());
    for (size_t i(0); i < testSize; i++) {
        set.insert(Foo(i));
        avl_set<Foo, Foo::avl>::iterator it = set.find(Foo(i));
        ASSERT_TRUE(it != set.end());
        for (size_t j=0; j < i; j++) {
            it = set.find(Foo(j));
            ASSERT_TRUE(it != set.end());
        }
    }
    EXPECT_TRUE(set.size() == testSize);
    avl_set<Foo, Foo::avl>::iterator it = set.find(Foo((testSize/2)-1));
    ASSERT_TRUE(it != set.end());
    EXPECT_EQUAL(*it, Foo((testSize/2)-1));
    for (size_t i(0); i < testSize/2; i++) {
        set.erase(Foo(i*2));
    }
    ASSERT_TRUE(it != set.end());
    EXPECT_EQUAL(*it, Foo((testSize/2)-1));
    EXPECT_TRUE(set.find(Foo(testSize/2)) == set.end());
    EXPECT_TRUE(set.size() == testSize/2);
    for (size_t i(0); i < testSize; i++) {
        set.insert(Foo(i));
    }
    EXPECT_EQUAL(set.size(), testSize);
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(*set.find(Foo(0)) == Foo(0));
    EXPECT_TRUE(*set.find(Foo(1)) == Foo(1));
    EXPECT_TRUE(*set.find(Foo(testSize-1)) == Foo(testSize-1));
    EXPECT_TRUE(set.find(Foo(testSize)) == set.end());

    set.clear();

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(Foo(7)) == set.end());
}

void Test::testAvlTreeSet()
{
    avl_set<int> set(1000);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(7);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase non existing
    set.erase(8);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase existing
    set.erase(7);
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    for (size_t i(0); i < 10000; i++) {
        set.insert(i);
    }
    EXPECT_TRUE(set.size() == 10000);
    for (size_t i(0); i < 5000; i++) {
        set.erase(i*2);
    }
    EXPECT_TRUE(*set.find(4999) == 4999);
    EXPECT_TRUE(set.find(5000) == set.end());
    EXPECT_TRUE(set.size() == 5000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(i);
    }
    EXPECT_EQUAL(set.size(), 10000u);
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(*set.find(0) == 0);
    EXPECT_TRUE(*set.find(1) == 1);
    EXPECT_TRUE(*set.find(9999) == 9999);
    EXPECT_TRUE(set.find(10000) == set.end());

    set.clear();

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(7) == set.end());
}

void Test::testAvlTreeMap()
{
    avl_map<int, int> set(1000);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(make_pair(7, 70));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase non existing
    set.erase(8);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase existing
    set.erase(7);
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    for (size_t i(0); i < 10000; i++) {
        set.insert(make_pair(i,i*10));
    }
    EXPECT_TRUE(set.size() == 10000);
    for (size_t i(0); i < 5000; i++) {
        set.erase(i*2);
    }
    EXPECT_TRUE(set.find(4999)->first == 4999);
    EXPECT_TRUE(set.find(4999)->second == 49990);
    EXPECT_TRUE(set.find(5000) == set.end());
    EXPECT_TRUE(set.size() == 5000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(make_pair(i,i*10));
    }
    EXPECT_EQUAL(set.size(), 10000u);
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(0)->first == 0);
    EXPECT_TRUE(set.find(0)->second == 0);
    EXPECT_TRUE(set.find(1)->first == 1);
    EXPECT_TRUE(set.find(1)->second == 10);
    EXPECT_TRUE(set.find(9999)->first == 9999);
    EXPECT_TRUE(set.find(9999)->second == 99990);
    EXPECT_TRUE(set.find(10000) == set.end());

    avl_map<int, int> set2(7);
    set.swap(set2);
    EXPECT_EQUAL(set2.size(), 10000u);
    EXPECT_TRUE(set2.find(7)->first == 7);
    EXPECT_TRUE(set2.find(7)->second == 70);

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(7) == set.end());
    for (int i=0; i < 100; i++) {
        set.insert(make_pair(i,i*10));
    }
    for (int i=0; i < 100; i++) {
        EXPECT_TRUE(set.find(i)->second == i*10);
    }

    avl_map<int, int> set3;
    set3.insert(set.begin(), set.end());
    for (int i=0; i < 100; i++) {
        EXPECT_EQUAL(i*10, set.find(i)->second);
    }
}

class S {
public:
    explicit S(uint64_t l=0) : _a(l&0xfffffffful), _b(l>>32) { }
    uint32_t avl() const { return _a; }
    uint32_t a() const { return _a; }
    friend bool operator == (const S & a, const S & b) { return a._a == b._a && a._b == b._b; }
private:
    uint32_t _a, _b;
};

struct myavl {
    size_t operator() (const S & arg) const { return arg.avl(); }
};

struct myextract {
    uint32_t operator() (const S & arg) const { return arg.a(); }
};

void Test::testAvlTreeFind()
{
    avl_set<S, myavl> set(1000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(S(i));
    }
    EXPECT_TRUE(*set.find(S(1)) == S(1));
    avl_set<S, myavl>::iterator cit = set.find<uint32_t, myextract, vespalib::avl<uint32_t>, std::equal_to<uint32_t> >(7);
    EXPECT_TRUE(*cit == S(7));
}

TEST_APPHOOK(Test)
