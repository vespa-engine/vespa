// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>

using namespace search;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using namespace search::features::util;

SimpleTermData make_term(uint32_t uid) {
    SimpleTermData term;
    term.setUniqueId(uid);
    return term;
}

struct TermLabelFixture {
    IndexEnvironment indexEnv;
    QueryEnvironment queryEnv;
    TermLabelFixture() : indexEnv(), queryEnv(&indexEnv) {
        queryEnv.getTerms().push_back(make_term(5));
        queryEnv.getTerms().push_back(make_term(0));
        queryEnv.getTerms().push_back(make_term(10));
        queryEnv.getProperties().add("vespa.label.foo.id", "5");
        queryEnv.getProperties().add("vespa.label.bar.id", "0"); // undefined uid
        queryEnv.getProperties().add("vespa.label.baz.id", "10");
        queryEnv.getProperties().add("vespa.label.fox.id", "7"); // non-existing
    }
};

TEST_F("require that label can be mapped to term", TermLabelFixture) {    
    EXPECT_EQUAL((ITermData*)&f1.queryEnv.getTerms()[0], util::getTermByLabel(f1.queryEnv, "foo"));
    EXPECT_EQUAL((ITermData*)0, util::getTermByLabel(f1.queryEnv, "bar"));
    EXPECT_EQUAL((ITermData*)&f1.queryEnv.getTerms()[2], util::getTermByLabel(f1.queryEnv, "baz"));
    EXPECT_EQUAL((ITermData*)0, util::getTermByLabel(f1.queryEnv, "fox"));
    EXPECT_EQUAL((ITermData*)0, util::getTermByLabel(f1.queryEnv, "unknown"));
}

template <typename T>
void verifyStrToNum() {
    EXPECT_EQUAL(-17, static_cast<long>(strToNum<T>("-17")));
    EXPECT_EQUAL(-1, static_cast<long>(strToNum<T>("-1")));
    EXPECT_EQUAL(0, static_cast<long>(strToNum<T>("0")));
    EXPECT_EQUAL(1, static_cast<long>(strToNum<T>("1")));
    EXPECT_EQUAL(17, static_cast<long>(strToNum<T>("17")));
    EXPECT_EQUAL(0, static_cast<long>(strToNum<T>("0x0")));
    EXPECT_EQUAL(1, static_cast<long>(strToNum<T>("0x1")));
    EXPECT_EQUAL(27, static_cast<long>(strToNum<T>("0x1b")));
}

TEST("verify str2Num") {
    verifyStrToNum<int8_t>();
    verifyStrToNum<int16_t>();
    verifyStrToNum<int32_t>();
    verifyStrToNum<int64_t>();
}

TEST_MAIN() { TEST_RUN_ALL(); }
