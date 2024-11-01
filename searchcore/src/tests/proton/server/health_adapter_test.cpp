// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/server/health_adapter.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/vespalib/gtest/gtest.h>

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

TEST(HealthAdapterTest, require_that_empty_status_list_passes_health_check)
{
    MyStatusProducer f1;
    HealthAdapter f2(f1);
    EXPECT_TRUE(f2.getHealth().ok);
    EXPECT_EQ(std::string("All OK"), f2.getHealth().msg);
}

TEST(HealthAdapterTest, require_that_UP_components_passes_health_check)
{
    MyStatusProducer f1;
    HealthAdapter f2(f1);
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::UPOK, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_TRUE(f2.getHealth().ok);
    EXPECT_EQ(std::string("All OK"), f2.getHealth().msg);
}

TEST(HealthAdapterTest, require_that_PARTIAL_component_fails_health_check)
{
    MyStatusProducer f1;
    HealthAdapter f2(f1);
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::PARTIAL, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQ(std::string("c2: yyy"), f2.getHealth().msg);
}

TEST(HealthAdapterTest, require_that_DOWN_component_fails_health_check)
{
    MyStatusProducer f1;
    HealthAdapter f2(f1);
    f1.add("c1", StatusReport::UPOK, "xxx");
    f1.add("c2", StatusReport::DOWN, "yyy");
    f1.add("c3", StatusReport::UPOK, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQ(std::string("c2: yyy"), f2.getHealth().msg);
}

TEST(HealthAdapterTest, require_that_multiple_failure_messages_are_concatenated)
{
    MyStatusProducer f1;
    HealthAdapter f2(f1);
    f1.add("c1", StatusReport::PARTIAL, "xxx");
    f1.add("c2", StatusReport::UPOK, "yyy");
    f1.add("c3", StatusReport::DOWN, "zzz");
    EXPECT_FALSE(f2.getHealth().ok);
    EXPECT_EQ(std::string("c1: xxx, c3: zzz"), f2.getHealth().msg);
}
