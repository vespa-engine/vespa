// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closure.h>
#include <string>

using std::shared_ptr;
using std::unique_ptr;
using namespace vespalib;

namespace {

class Test : public vespalib::TestApp {
    void testClosure0_0();
    void testClosure0_1();
    void testClosure0_2();
    void testClosure1_0();
    void testClosure1_1();
    void testClosure1_2();
    void testMemberClosure0_0();
    void testMemberClosure0_1();
    void testMemberClosure0_2();
    void testMemberClosure1_0();
    void testMemberClosure1_1();
    void testMemberClosure1_2();

public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("callback_test");

    TEST_DO(testClosure0_0());
    TEST_DO(testClosure0_1());
    TEST_DO(testClosure0_2());
    TEST_DO(testClosure1_0());
    TEST_DO(testClosure1_1());
    TEST_DO(testClosure1_2());
    TEST_DO(testMemberClosure0_0());
    TEST_DO(testMemberClosure0_1());
    TEST_DO(testMemberClosure0_2());
    TEST_DO(testMemberClosure1_0());
    TEST_DO(testMemberClosure1_1());
    TEST_DO(testMemberClosure1_2());

    TEST_DONE();
}

bool called = false;
void setCalled() { called = true; }
void setBool(bool *p) { *p = true; }
void setInt(int *p, int i) { *p = i; }
void setIntSum(int *p, int i, int j) { *p = i + j; }

int setCalledReturnInt() { called = true; return 42; }
int setBoolReturnInt(bool *p) { *p = true; return 42; }
int setIntReturnInt(int *p, int i) { *p = i; return i; }
int setIntSumReturnInt(int *p, int i, int j) { *p = i + j; return i + j; }

void Test::testClosure0_0() {
    called = false;
    unique_ptr<Closure> void_closure = makeClosure(setCalled);
    void_closure->call();
    EXPECT_TRUE(called);

    called = false;
    unique_ptr<Closure0<int> > closure = makeClosure(setCalledReturnInt);
    EXPECT_EQUAL(42, closure->call());
    EXPECT_TRUE(called);
}

void Test::testClosure0_1() {
    bool is_called = false;
    unique_ptr<Closure> void_closure = makeClosure(setBool, &is_called);
    void_closure->call();
    EXPECT_TRUE(is_called);

    is_called = false;
    unique_ptr<Closure0<int> > closure =
        makeClosure(setBoolReturnInt, &is_called);
    EXPECT_EQUAL(42, closure->call());
    EXPECT_TRUE(is_called);
}

void Test::testClosure0_2() {
    int i = 0;
    unique_ptr<Closure> void_closure = makeClosure(setInt, &i, 42);
    void_closure->call();
    EXPECT_EQUAL(42, i);

    unique_ptr<Closure0<int> > closure = makeClosure(setIntReturnInt, &i, 23);
    EXPECT_EQUAL(23, closure->call());
    EXPECT_EQUAL(23, i);
}

void Test::testClosure1_0() {
    bool is_called = false;
    unique_ptr<Closure1<bool *> > void_closure = makeClosure(setBool);
    void_closure->call(&is_called);
    EXPECT_TRUE(is_called);

    is_called = false;
    unique_ptr<Closure1<bool *, int> > closure = makeClosure(setBoolReturnInt);
    EXPECT_EQUAL(42, closure->call(&is_called));
    EXPECT_TRUE(is_called);
}

void Test::testClosure1_1() {
    int i = 0;
    unique_ptr<Closure1<int> > void_closure = makeClosure(setInt, &i);
    void_closure->call(42);
    EXPECT_EQUAL(42, i);

    unique_ptr<Closure1<int, int> > closure = makeClosure(setIntReturnInt, &i);
    EXPECT_EQUAL(23, closure->call(23));
    EXPECT_EQUAL(23, i);
}

void Test::testClosure1_2() {
    int i = 0;
    unique_ptr<Closure1<int> > void_closure = makeClosure(setIntSum, &i, 42);
    void_closure->call(8);
    EXPECT_EQUAL(50, i);

    unique_ptr<Closure1<int, int> > closure =
        makeClosure(setIntSumReturnInt, &i, 23);
    EXPECT_EQUAL(42, closure->call(19));
    EXPECT_EQUAL(42, i);
}


struct MyObj {
    bool is_called;
    MyObj() : is_called(false) {}

    void setCalled() { is_called = true; }
    void setBool(bool *p) { *p = true; }
    void setInt(int *p, int i) { *p = i; }
    void setInt3Arg(int *p, int i, string) { *p = i; }

    string message() { return "Hello world"; }
    int twice(int i) { return 2 * i; }
    double multiply(double x, double y) { return x * y; }
    int sum(int a, int b, int c) { return a + b + c; }
};

void Test::testMemberClosure0_0() {
    MyObj obj;
    unique_ptr<Closure0<string> > closure = makeClosure(&obj, &MyObj::message);
    EXPECT_EQUAL("Hello world", closure->call());

    unique_ptr<Closure> void_closure = makeClosure(&obj, &MyObj::setCalled);
    void_closure->call();
    EXPECT_TRUE(obj.is_called);

    shared_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(obj_sp, &MyObj::setCalled);
    void_closure->call();
    EXPECT_TRUE(obj_sp->is_called);
}

void Test::testMemberClosure0_1() {
    MyObj obj;
    unique_ptr<Closure0<int> > closure = makeClosure(&obj, &MyObj::twice, 21);
    EXPECT_EQUAL(42, closure->call());

    bool is_called = false;
    unique_ptr<Closure> void_closure =
        makeClosure(&obj, &MyObj::setBool, &is_called);
    void_closure->call();
    EXPECT_TRUE(is_called);

    is_called = false;
    shared_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(obj_sp, &MyObj::setBool, &is_called);
    void_closure->call();
    EXPECT_TRUE(is_called);
}

void Test::testMemberClosure0_2() {
    MyObj obj;
    unique_ptr<Closure0<double> > closure =
        makeClosure(&obj, &MyObj::multiply, 1.5, 2.5);
    EXPECT_APPROX(3.75, closure->call(), 0.001);

    int i = 0;
    unique_ptr<Closure> void_closure = makeClosure(&obj, &MyObj::setInt, &i, 42);
    void_closure->call();
    EXPECT_EQUAL(42, i);

    shared_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(obj_sp, &MyObj::setInt, &i, 21);
    void_closure->call();
    EXPECT_EQUAL(21, i);
}


void Test::testMemberClosure1_0() {
    MyObj obj;
    unique_ptr<Closure1<int, int> > closure = makeClosure(&obj, &MyObj::twice);
    EXPECT_EQUAL(8, closure->call(4));

    bool is_called = false;
    unique_ptr<Closure1<bool *, void> > void_closure =
        makeClosure(&obj, &MyObj::setBool);
    void_closure->call(&is_called);
    EXPECT_TRUE(is_called);

    is_called = false;
    shared_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(obj_sp, &MyObj::setBool);
    void_closure->call(&is_called);
    EXPECT_TRUE(is_called);
}

void Test::testMemberClosure1_1() {
    MyObj obj;
    unique_ptr<Closure1<double, double> > closure =
        makeClosure(&obj, &MyObj::multiply, 1.5);
    EXPECT_APPROX(3.15, closure->call(2.1), 0.001);

    int i = 0;
    unique_ptr<Closure1<int, void> > void_closure =
        makeClosure(&obj, &MyObj::setInt, &i);
    void_closure->call(42);
    EXPECT_EQUAL(42, i);

    shared_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(obj_sp, &MyObj::setInt, &i);
    void_closure->call(21);
    EXPECT_EQUAL(21, i);
}

void Test::testMemberClosure1_2() {
    MyObj obj;
    unique_ptr<Closure1<int, int> > closure =
        makeClosure(&obj, &MyObj::sum, 1, 2);
    EXPECT_EQUAL(6, closure->call(3));

    int i = 0;
    unique_ptr<Closure1<string, void> > void_closure =
        makeClosure(&obj, &MyObj::setInt3Arg, &i, 23);
    void_closure->call("hello");
    EXPECT_EQUAL(23, i);

    unique_ptr<MyObj> obj_sp(new MyObj);
    void_closure = makeClosure(std::move(obj_sp), &MyObj::setInt3Arg, &i, 42);
    void_closure->call("world");
    EXPECT_EQUAL(42, i);
}

}  // namespace

TEST_APPHOOK(Test);
