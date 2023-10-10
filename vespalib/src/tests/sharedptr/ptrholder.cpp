// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::PtrHolder;


class Test : public vespalib::TestApp
{
public:
    void testEmpty();
    void testSimple();
    int Main() override;
};


struct Data
{
    int ctorCnt;
    int dtorCnt;
    Data() : ctorCnt(0), dtorCnt(0) {}
};


class DataRef
{
private:
    Data &_d;
    DataRef(const DataRef &);
    DataRef &operator=(const DataRef &);
public:
    DataRef(Data &d) : _d(d) { ++d.ctorCnt; }
    ~DataRef() { ++_d.dtorCnt; }
    int getCtorCnt() const { return _d.ctorCnt; }
    int getDtorCnt() const { return _d.dtorCnt; }
};
using PT = std::shared_ptr<DataRef>;
using HOLD = PtrHolder<DataRef>;


void
Test::testEmpty()
{
    HOLD hold;
    EXPECT_TRUE(hold.get().get() == NULL);
    EXPECT_TRUE(!hold.hasValue());
    EXPECT_TRUE(!hold.hasNewValue());
    EXPECT_TRUE(!hold.latch());
    EXPECT_TRUE(hold.get().get() == NULL);
    EXPECT_TRUE(!hold.hasValue());
    EXPECT_TRUE(!hold.hasNewValue());
    hold.set(NULL);
    EXPECT_TRUE(!hold.hasValue());
    EXPECT_TRUE(!hold.hasNewValue());
}


void
Test::testSimple()
{
    Data data;
    HOLD hold;
    {
        hold.set(new DataRef(data));
        EXPECT_TRUE(hold.hasValue());
        EXPECT_TRUE(!hold.hasNewValue());
        EXPECT_TRUE(!hold.latch());
        PT pt1(hold.get());
        EXPECT_TRUE(pt1.get() == hold.get().get());
        hold.set(new DataRef(data));
        EXPECT_TRUE(pt1.get() == hold.get().get());
        EXPECT_TRUE(hold.hasValue());
        EXPECT_TRUE(hold.hasNewValue());
        EXPECT_TRUE(hold.latch());
        EXPECT_TRUE(hold.hasValue());
        EXPECT_TRUE(!hold.hasNewValue());
        EXPECT_TRUE(pt1.get() != hold.get().get());
        EXPECT_TRUE(data.ctorCnt == 2);
        EXPECT_TRUE(data.dtorCnt == 0);
    }
    EXPECT_TRUE(data.ctorCnt == 2);
    EXPECT_TRUE(data.dtorCnt == 1);
    hold.clear();
    EXPECT_TRUE(data.ctorCnt == 2);
    EXPECT_TRUE(data.dtorCnt == 2);
}


int
Test::Main()
{
    TEST_INIT("ptrholder_test");
    testEmpty();
    testSimple();
    TEST_DONE();
}

TEST_APPHOOK(Test)
