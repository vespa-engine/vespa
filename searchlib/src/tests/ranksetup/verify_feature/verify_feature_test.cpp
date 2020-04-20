// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchlib/features/valuefeature.h>

using namespace search::features;
using namespace search::fef::test;
using namespace search::fef;

struct RankFixture {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;

    RankFixture() : factory(), indexEnv() {
        setup_fef_test_plugin(factory);
        factory.addPrototype(Blueprint::SP(new ValueBlueprint()));
    }

    bool verify(const std::string &feature) const {
        return verifyFeature(factory, indexEnv, feature, "feature verification test");
    }
};

TEST_F("verify valid rank feature", RankFixture) {
    EXPECT_TRUE(f1.verify("value(1, 2, 3).0"));
    EXPECT_TRUE(f1.verify("value(1, 2, 3).1"));
    EXPECT_TRUE(f1.verify("value(1, 2, 3).2"));
}

TEST_F("verify unknown feature", RankFixture) {
    EXPECT_FALSE(f1.verify("unknown"));
}

TEST_F("verify unknown output", RankFixture) {
    EXPECT_FALSE(f1.verify("value(1, 2, 3).3"));
}

TEST_F("verify illegal input parameters", RankFixture) {
    EXPECT_FALSE(f1.verify("value.0"));
}

TEST_F("verify illegal feature name", RankFixture) {
    EXPECT_FALSE(f1.verify("value(2)."));
}

TEST_F("verify too deep dependency graph", RankFixture) {
    EXPECT_TRUE(f1.verify("chain(basic, 255, 4)"));
    EXPECT_FALSE(f1.verify("chain(basic, 256, 4)"));
}

TEST_F("verify dependency cycle", RankFixture) {
    EXPECT_FALSE(f1.verify("chain(cycle, 4, 2)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
