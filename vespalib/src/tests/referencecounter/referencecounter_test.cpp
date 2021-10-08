// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("referencecounter_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/referencecounter.h>

struct Data
{
    int ctorCnt;
    int dtorCnt;
    Data() : ctorCnt(0), dtorCnt(0) {}
};

class DataRef : public vespalib::ReferenceCounter
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

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("referencecounter_test");

    Data data;
    {
        DataRef *pt1 = new DataRef(data);

        EXPECT_TRUE(pt1->refCount() == 1);

        DataRef *pt2 = pt1;
        pt2->addRef();

        EXPECT_TRUE(pt1->refCount() == 2);

        EXPECT_TRUE(data.ctorCnt == 1);
        EXPECT_TRUE(data.dtorCnt == 0);
        pt1->subRef();
        EXPECT_TRUE(pt1->refCount() == 1);
        pt2->subRef();
    }
    EXPECT_TRUE(data.ctorCnt == 1);
    EXPECT_TRUE(data.dtorCnt == 1);
    TEST_DONE();
}
