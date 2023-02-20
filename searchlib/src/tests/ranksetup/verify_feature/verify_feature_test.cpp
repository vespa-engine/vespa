// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <regex>

using namespace search::features;
using namespace search::fef::test;
using namespace search::fef;
using vespalib::make_string_short::fmt;

typedef bool (*cmp)(const vespalib::string & a, const vespalib::string &b);

namespace search::fef {
std::ostream &operator<<(std::ostream &os, Level level) {
    if (level == Level::INFO) {
        return os << "info";
    }
    if (level == Level::WARNING) {
        return os << "warning";
    }
    return os << "error";
}
}

bool equal(const vespalib::string & a, const vespalib::string &b) {
    return a == b;
}

bool regex(const vespalib::string & a, const vespalib::string &b) {
    std::regex exp(b.c_str());
    return std::regex_match(a.c_str(), exp);
}

struct RankFixture {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;

    RankFixture() : factory(), indexEnv() {
        setup_fef_test_plugin(factory);
        factory.addPrototype(std::make_shared<ValueBlueprint>());
    }

    bool verify(const std::string &feature, std::vector<std::pair<cmp, Message>> expected) const {
        std::vector<Message> errors;
        bool ok = verifyFeature(factory, indexEnv, feature, "feature verification test", errors);
        EXPECT_EQUAL(errors.size(), expected.size());
        for (size_t i(0); i < std::min(errors.size(), expected.size()); i++) {
            EXPECT_EQUAL(errors[i].first, expected[i].second.first);
            EXPECT_TRUE(expected[i].first(errors[i].second, expected[i].second.second));
        }
        return ok;
    }
};

TEST_F("verify valid rank feature", RankFixture) {
    EXPECT_TRUE(f1.verify("value(1, 2, 3).0", {}));
    EXPECT_TRUE(f1.verify("value(1, 2, 3).1", {}));
    EXPECT_TRUE(f1.verify("value(1, 2, 3).2", {}));
}

TEST_F("verify unknown feature", RankFixture) {
    EXPECT_FALSE(f1.verify("unknown",
                           {{equal, {Level::WARNING, "invalid rank feature unknown: unknown basename: 'unknown'"}},
                            {equal, {Level::ERROR, "verification failed: rank feature unknown (feature verification test)"}}}));
}

TEST_F("verify unknown output", RankFixture) {
    EXPECT_FALSE(f1.verify("value(1, 2, 3).3",
                           {{equal, {Level::WARNING, "invalid rank feature value(1,2,3).3: unknown output: '3'"}},
                            {equal, {Level::ERROR, "verification failed: rank feature value(1, 2, 3).3 (feature verification test)"}}}));
}

TEST_F("verify illegal input parameters", RankFixture) {
    EXPECT_FALSE(f1.verify("value.0",
                           {{equal, {Level::WARNING, "invalid rank feature value.0:"
                                                     " The parameter list used for setting up rank feature value is not valid:"
                                                     " Expected 1+1x parameter(s), but got 0"}},
                            {equal, {Level::ERROR, "verification failed: rank feature value.0 (feature verification test)"}}}));
}

TEST_F("verify illegal feature name", RankFixture) {
    EXPECT_FALSE(f1.verify("value(2).",
                           {{equal, {Level::WARNING, "invalid rank feature value(2).: malformed name"}},
                            {equal, {Level::ERROR, "verification failed: rank feature value(2). (feature verification test)"}}}));
}

TEST_F("verify too deep dependency graph", RankFixture) {
    EXPECT_TRUE(f1.verify("chain(basic, 255, 4)", {}));
    EXPECT_FALSE(f1.verify("chain(basic, 256, 4)",
                           {{equal,
                             {Level::WARNING,
                              "invalid rank feature value(4): dependency graph too deep\n"
                              "  ... needed by rank feature chain(basic,1,4)\n"
                              "  ... needed by rank feature chain(basic,2,4)\n"
                              "  ... needed by rank feature chain(basic,3,4)\n"
                              "  ... needed by rank feature chain(basic,4,4)\n"
                              "  ... needed by rank feature chain(basic,5,4)\n"
                              "  ... needed by rank feature chain(basic,6,4)\n"
                              "  ... needed by rank feature chain(basic,7,4)\n"
                              "  ... needed by rank feature chain(basic,8,4)\n"
                              "  ... needed by rank feature chain(basic,9,4)\n"
                              "  ... needed by rank feature chain(basic,10,4)\n"
                              "  (skipped 241 entries)\n"
                              "  ... needed by rank feature chain(basic,252,4)\n"
                              "  ... needed by rank feature chain(basic,253,4)\n"
                              "  ... needed by rank feature chain(basic,254,4)\n"
                              "  ... needed by rank feature chain(basic,255,4)\n"
                              "  ... needed by rank feature chain(basic,256,4)"}},
                            {regex, {Level::WARNING, "high stack usage: [0-9]+ bytes"}},
                            {equal, {Level::ERROR, "verification failed: rank feature chain(basic, 256, 4) (feature verification test)"}}}));
}

TEST_F("verify dependency cycle", RankFixture) {
    EXPECT_FALSE(f1.verify("chain(cycle, 4, 2)",
                           {{equal,
                             {Level::WARNING,
                              "invalid rank feature chain(cycle,2,2): dependency cycle detected\n"
                              "  ... needed by rank feature chain(cycle,1,2)\n"
                              "  ... needed by rank feature chain(cycle,2,2)\n"
                              "  ... needed by rank feature chain(cycle,3,2)\n"
                              "  ... needed by rank feature chain(cycle,4,2)"}},
                            {equal, {Level::ERROR, "verification failed: rank feature chain(cycle, 4, 2) (feature verification test)"}}}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
