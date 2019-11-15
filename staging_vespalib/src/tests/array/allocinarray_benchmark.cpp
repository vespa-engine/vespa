// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/rusage.h>
#include <vespa/vespalib/util/optimized.h>
#include <vespa/vespalib/util/allocinarray.h>
#include <vespa/vespalib/util/array.hpp>
#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP("allocinarray_benchmark");

using namespace vespalib;

class Test : public TestApp
{
public:
private:
    void benchmarkTree(size_t count);
    void benchmarkTreeInArray(size_t count);
    int Main() override;
};

template <typename T>
class TreeNode
{
public:
    typedef TreeNode * P;
    TreeNode(const T & p) :_left(NULL), _right(NULL), _payLoad(p) { }
    ~TreeNode() {
        if (_left) {
            delete _left;
        }
        if (_right) {
            delete _right;
        }
    }
    P  left() { return _left; }
    P right() { return _right; }
    void  left(P l) { _left = l; }
    void right(P l) { _right = l; }
private:
    P _left;
    P _right;
    T _payLoad;
};

template <typename T>
class RefTreeNode
{
public:
    typedef uint32_t P;
    RefTreeNode(const T & p) :_left(-1), _right(-1), _payLoad(p) { }
    P  left() { return _left; }
    P right() { return _right; }
    void  left(P l) { _left = l; }
    void right(P l) { _right = l; }
private:
    P _left;
    P _right;
    T _payLoad;
};

typedef TreeNode<long> N;
typedef RefTreeNode<long> R;
typedef AllocInArray<R, vespalib::Array<R> > Store;

void populate(Store & store, uint32_t parent, size_t depth)
{
    if (depth > 0) {
        store[parent].left(store.alloc(R(0)));
        populate(store, store[parent].left(), depth-1);
        store[parent].right(store.alloc(R(1)));
        populate(store, store[parent].right(), depth-1);
    }
}

void populate(N * parent, size_t depth)
{
    if (depth > 0) {
        parent->left(new N(0));
        populate(parent->left(), depth-1);
        parent->right(new N(1));
        populate(parent->right(), depth-1);
    }
}

void Test::benchmarkTree(size_t count)
{
    N root(0);
    size_t depth = Optimized::msbIdx(count);
    populate(&root, depth);
}

void Test::benchmarkTreeInArray(size_t count)
{
    Store store;
    store.alloc(R(0));
    size_t depth = Optimized::msbIdx(count);
    populate(store, 0, depth);
}

int
Test::Main()
{
    std::string type("direct");
    size_t count = 1000000;
    if (_argc > 1) {
        type = _argv[1];
    }
    if (_argc > 2) {
        count = strtol(_argv[2], NULL, 0);
    }
    TEST_INIT("allocinarray_benchmark");
    fastos::TimeStamp start(fastos::ClockSteady::now());
    if (type == "direct") {
        benchmarkTree(count);
    } else {
        benchmarkTreeInArray(count);
    }
    LOG(info, "rusage = {\n%s\n}", vespalib::RUsage::createSelf(start).toString().c_str());
    ASSERT_EQUAL(0, kill(0, SIGPROF));
    TEST_DONE();
}

TEST_APPHOOK(Test);

