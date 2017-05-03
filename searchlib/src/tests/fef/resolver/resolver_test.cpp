// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/features/valuefeature.h>

#include <vespa/log/log.h>
LOG_SETUP("resolver_test");

namespace search {
namespace fef {

class BaseBlueprint : public Blueprint {
public:
    BaseBlueprint() : Blueprint("base") { }
    virtual void visitDumpFeatures(const IIndexEnvironment &,
                                   IDumpFeatureVisitor &) const override {}
    virtual Blueprint::UP createInstance() const override { return Blueprint::UP(new BaseBlueprint()); }
    virtual bool setup(const IIndexEnvironment & indexEnv,
                       const ParameterList & params) override {
        (void) indexEnv; (void) params;
        describeOutput("foo", "foo");
        describeOutput("bar", "bar");
        describeOutput("baz", "baz");
        return true;
    }
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        std::vector<feature_t> values;
        values.push_back(0.0);
        values.push_back(0.0);
        values.push_back(0.0);
        return stash.create<features::ValueExecutor>(values);
    }
};

class CombineBlueprint : public Blueprint {
public:
    CombineBlueprint() : Blueprint("combine") { }
    virtual void visitDumpFeatures(const IIndexEnvironment &,
                                   IDumpFeatureVisitor &) const override {}
    virtual Blueprint::UP createInstance() const override { return Blueprint::UP(new CombineBlueprint()); }
    virtual bool setup(const IIndexEnvironment & indexEnv,
                       const ParameterList & params) override {
        (void) indexEnv; (void) params;
        defineInput("base.foo");
        defineInput("base.bar");
        defineInput("base.baz");
        describeOutput("out", "out");
        return true;
    }
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<features::SingleZeroValueExecutor>();
    }
};

class Test : public vespalib::TestApp {
private:
    BlueprintFactory _factory;
    void requireThatWeGetUniqueBlueprints();
public:
    Test();
    ~Test();
    int Main() override;
};

Test::Test() :
    _factory()
{
    _factory.addPrototype(Blueprint::SP(new BaseBlueprint()));
    _factory.addPrototype(Blueprint::SP(new CombineBlueprint()));
}
Test::~Test() {}

void
Test::requireThatWeGetUniqueBlueprints()
{
    test::IndexEnvironment ienv;
    BlueprintResolver::SP res(new BlueprintResolver(_factory, ienv));
    res->addSeed("combine");
    EXPECT_TRUE(res->compile());
    const BlueprintResolver::ExecutorSpecList & spec = res->getExecutorSpecs();
    EXPECT_EQUAL(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint *>(spec[0].blueprint.get()) != NULL);
    EXPECT_TRUE(dynamic_cast<CombineBlueprint *>(spec[1].blueprint.get()) != NULL);
}

int
Test::Main()
{
    TEST_INIT("resolver_test");

    requireThatWeGetUniqueBlueprints();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::fef::Test);
