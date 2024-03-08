// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::PtrHolder;


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


TEST(PtrHolderTest, test_empty)
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


TEST(PtrHolderTest, test_simple)
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

GTEST_MAIN_RUN_ALL_TESTS()
