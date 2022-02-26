// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcore/proton/server/health_adapter.h>
#include <vespa/searchcore/proton/common/statusreport.h>

using namespace proton;

struct MyStatusProducer : public StatusProducer {
    StatusReport::List list;
    ~MyStatusProducer() override;
    void add(const std::string &comp, StatusReport::State state,
             const std::string &msg)
    {
        list.push_back(StatusReport::SP(new StatusReport(StatusReport::Params(comp).
                state(state).message(msg))));
    }
    virtual StatusReport::List getStatusReports() const override {
        return list;
    }
};

MyStatusProducer::~MyStatusProducer() = default;

TEST_FF("require that empty status list passes health check", MyStatusProducer(), HealthAdapter(f1)) {
    EXPECT_TRUE(f2.getHealth().ok);
    EXPECT_EQUAL(std::string("All OK"), f2.getHealth().msg);
}

TEST_FF("require that UP components passes health check", MyStatusProducer(), HealthAdapter(f1)) {
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::UPOK, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_TRUE(f2.getHealth().ok);
    EXPECT_EQUAL(std::string("All OK"), f2.getHealth().msg);
}

TEST_FF("require that PARTIAL component fails health check", MyStatusProducer(), HealthAdapter(f1)) {
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::PARTIAL, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQUAL(std::string("c2: yyy"), f2.getHealth().msg);
}

TEST_FF("require that DOWN component fails health check", MyStatusProducer(), HealthAdapter(f1)) {
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::DOWN, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQUAL(std::string("c2: yyy"), f2.getHealth().msg);
}

TEST_FF("require that multiple failure messages are concatenated", MyStatusProducer(), HealthAdapter(f1)) {
    f1.add("c1", StatusReport::PARTIAL, "xxx");
    f1.add("c2", StatusReport::UPOK, "yyy");
    f1.add("c3", StatusReport::DOWN, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQUAL(std::string("c1: xxx, c3: zzz"), f2.getHealth().msg);
}

TEST_MAIN() { TEST_RUN_ALL(); }
