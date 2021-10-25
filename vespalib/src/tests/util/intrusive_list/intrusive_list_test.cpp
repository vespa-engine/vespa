// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/intrusive_list.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cstdlib>

using namespace vespalib;

struct Foo {
    int a;
    char b;
    char c;
};

struct Bar {
    int d;
    int e;
    int f;
};


class ListNode : public Foo,
                 public IntrusiveListNode<ListNode>,
                 public Bar
{
private:
    int _x;
    int _y;
    int _z;
public:
    ListNode() : Foo{.a=0,.b=1,.c=2},
                 IntrusiveListNode(),
                 Bar{.d=3,.e=4,.f=5},
                 _x(1), _y(1), _z(1)
    {}

    ListNode(int x, int z) : Foo(), IntrusiveListNode(), Bar(), _x(x), _y(1), _z(z) {}

    int x() const { return _x; }
    int y() const { return _y; }
    int z() const { return _z; }

    ListNode & x(int value) { _x = value; return *this; }
    ListNode & y(int value) { _y = value; return *this; }
    ListNode & z(int value) { _z = value; return *this; }
};


TEST(IntrusiveListTest, simple_usage) {
    IntrusiveList<ListNode> my_list;

    EXPECT_EQ(my_list.begin(), my_list.end());
    EXPECT_EQ(my_list.begin(), my_list.cbegin());
    EXPECT_EQ(my_list.begin(), my_list.cend());

    ListNode a{3,3}, b{5,5}, c{2,2}, d{20,25};
    d.y(42);
    my_list.push_back(a);
    my_list.push_back(b);
    my_list.push_back(c);
    int sum = 0;
    int prod = 1;
    for (const auto & v : my_list) {
        sum += v.x();
        prod *= v.z();
        EXPECT_EQ(v.y(), 1);
    }
    EXPECT_EQ(sum, 10);
    EXPECT_EQ(prod, 30);
    c.remove_from_list();
    a.remove_from_list();
    my_list.push_back(d);
    b.remove_from_list();
    auto iter = my_list.begin();
    EXPECT_NE(iter, my_list.end());
    auto first = iter++;
    EXPECT_EQ(iter, my_list.end());
    EXPECT_NE(first, my_list.end());
    EXPECT_EQ(first->x(), 20);
    EXPECT_EQ(first->y(), 42);
    EXPECT_EQ(first->z(), 25);
}

GTEST_MAIN_RUN_ALL_TESTS()
