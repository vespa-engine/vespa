// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/linkedptr.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::LinkedPtr;


class Test : public vespalib::TestApp
{
public:
    void testEmpty();
    void testSimple();
    void testCopy();
    void testReset();
    void testAccess();
    void testRelease();
    void testEqual();
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
typedef LinkedPtr<DataRef> PT;

PT copyPT(const PT &pt) { return pt; }

void
Test::testEmpty()
{
    PT pt1;
    PT pt2(NULL);
    EXPECT_TRUE(pt1.get() == NULL);
    EXPECT_TRUE(pt2.get() == NULL);
}

void
Test::testRelease()
{
    {
        PT p(NULL);
        EXPECT_TRUE(p.release() == NULL);
    }
    {
        Data data;
        PT p(new DataRef(data));
        std::unique_ptr<DataRef> ap(p.release());
        EXPECT_TRUE(ap.get() != NULL);
        EXPECT_TRUE(p.release() == NULL);
    }
    {
        Data data;
        PT p(new DataRef(data));
        PT p2(p);
        EXPECT_TRUE(p.release() == NULL);
        EXPECT_TRUE(p2.release() == NULL);
        EXPECT_TRUE(p.get() != NULL);
        EXPECT_TRUE(p2.get() != NULL);
    }
}


void
Test::testSimple()
{
    Data data;
    {
        PT pt1(new DataRef(data));
        EXPECT_EQUAL(data.ctorCnt, 1);
        EXPECT_EQUAL(data.dtorCnt, 0);
    }
    EXPECT_EQUAL(data.ctorCnt, 1);
    EXPECT_EQUAL(data.dtorCnt, 1);
}


void
Test::testCopy()
{
    Data data;
    {
        PT pt3;
        {
            PT pt1(new DataRef(data));
            PT pt2(pt1);
            EXPECT_TRUE(pt1.get() == pt2.get());
            EXPECT_TRUE(pt3.get() == NULL);
            pt3 = pt1;
            EXPECT_TRUE(pt3.get() == pt1.get());
            {
                PT pt4;
                PT pt5 = pt1;
                EXPECT_TRUE(pt4.get() == NULL);
                EXPECT_TRUE(pt5.get() == pt1.get());
                pt4 = pt5;
                EXPECT_TRUE(pt4.get() == pt1.get());
                {
                    PT pt6 = copyPT(pt3);
                    PT pt7;
                    EXPECT_TRUE(pt6.get() == pt1.get());
                    EXPECT_TRUE(pt7.get() == NULL);
                    pt7 = copyPT(pt5);
                    EXPECT_TRUE(pt7.get() == pt1.get());
                    {
                        PT pt8 = pt1;
                        EXPECT_TRUE(pt8.get() == pt1.get());
                        pt8 = pt8;
                        EXPECT_TRUE(pt8.get() == pt1.get());
                        EXPECT_EQUAL(data.ctorCnt, 1);
                        EXPECT_EQUAL(data.dtorCnt, 0);
                    }
                    EXPECT_EQUAL(data.ctorCnt, 1);
                    EXPECT_EQUAL(data.dtorCnt, 0);
                }
                EXPECT_EQUAL(data.ctorCnt, 1);
                EXPECT_EQUAL(data.dtorCnt, 0);
            }
            EXPECT_EQUAL(data.ctorCnt, 1);
            EXPECT_EQUAL(data.dtorCnt, 0);
        }
        EXPECT_EQUAL(data.ctorCnt, 1);
        EXPECT_EQUAL(data.dtorCnt, 0);
    }
    EXPECT_EQUAL(data.ctorCnt, 1);
    EXPECT_EQUAL(data.dtorCnt, 1);
}


void
Test::testReset()
{
    Data data;
    {
        PT pt1(new DataRef(data));
        EXPECT_EQUAL(data.ctorCnt, 1);
        EXPECT_EQUAL(data.dtorCnt, 0);
        pt1.reset(new DataRef(data));
        EXPECT_EQUAL(data.ctorCnt, 2);
        EXPECT_EQUAL(data.dtorCnt, 1);
        pt1.reset();
        EXPECT_EQUAL(data.ctorCnt, 2);
        EXPECT_EQUAL(data.dtorCnt, 2);
        pt1.reset(new DataRef(data));
        EXPECT_EQUAL(data.ctorCnt, 3);
        EXPECT_EQUAL(data.dtorCnt, 2);
        {
            PT pt2(pt1);
            pt1.reset(new DataRef(data));
            EXPECT_EQUAL(data.ctorCnt, 4);
            EXPECT_EQUAL(data.dtorCnt, 2);
        }
        EXPECT_EQUAL(data.ctorCnt, 4);
        EXPECT_EQUAL(data.dtorCnt, 3);
    }
    EXPECT_EQUAL(data.ctorCnt, 4);
    EXPECT_EQUAL(data.dtorCnt, 4);
}


void
Test::testAccess()
{
    Data data;
    {
        PT pt1(new DataRef(data));
        EXPECT_EQUAL(pt1->getCtorCnt(), 1);
        EXPECT_EQUAL((*pt1).getDtorCnt(), 0);
    }
}

class A {
    int _v;
public:
    A(int v) : _v(v) {}
    bool operator == (const A & rhs) const { return _v == rhs._v; }
};
typedef LinkedPtr<A> ALP;

void
Test::testEqual()
{
    ALP a(new A(1));
    ALP a2(new A(1));
    ALP b(new A(2));
    ALP c;
    EXPECT_TRUE(a == a);
    EXPECT_TRUE(a2 == a2);
    EXPECT_TRUE(a == a2);
    EXPECT_TRUE(a2 == a);
    EXPECT_TRUE(b == b);
    EXPECT_TRUE(c == c);
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == c);
    EXPECT_FALSE(a == c);
    EXPECT_FALSE(c == a);
}

int
Test::Main()
{
    TEST_INIT("linkedptr_test");
    testEmpty();
    testSimple();
    testCopy();
    testEqual();
    testReset();
    testAccess();
    testRelease();
    TEST_DONE();
}

TEST_APPHOOK(Test)
