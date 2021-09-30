// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("featurenameparser_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/searchlib/fef/featurenameparser.h>
#include <vector>
#include <string>

using namespace search::fef;

struct ParamList {
    std::vector<vespalib::string> list;
    ParamList() : list() {}
    ParamList(const std::vector<vespalib::string> &l) : list(l) {}
    ParamList &add(const vespalib::string &str) {
        list.push_back(str);
        return *this;
    }
    bool operator==(const ParamList &rhs) const {
        return rhs.list == list;
    }
};

std::ostream &operator<<(std::ostream &os, const ParamList &pl) {
    os << std::endl;
    for (uint32_t i = 0; i < pl.list.size(); ++i) {
        os << "  " << pl.list[i] << std::endl;
    }
    return os;
}

class Test : public vespalib::TestApp
{
public:
    bool testParse(const vespalib::string &input, bool valid,
                   const vespalib::string &base, ParamList pl,
                   const vespalib::string &output);
    void testFile(const vespalib::string &name);
    int Main() override;
};

bool
Test::testParse(const vespalib::string &input, bool valid,
                const vespalib::string &base, ParamList pl,
                const vespalib::string &output)
{
    bool ok = true;
    FeatureNameParser parser(input);
    if (!parser.valid()) {
        LOG(warning, "parse error: input:'%s', rest:'%s'",
            input.c_str(), input.substr(parser.parsedBytes()).c_str());
    }
    ok &= EXPECT_EQUAL(parser.valid(), valid);
    ok &= EXPECT_EQUAL(parser.baseName(), base);
    ok &= EXPECT_EQUAL(ParamList(parser.parameters()), pl);
    ok &= EXPECT_EQUAL(parser.output(), output);
    return ok;
}

void
Test::testFile(const vespalib::string &name)
{
    char buf[4_Ki];
    uint32_t lineN = 0;
    FILE *f = fopen(name.c_str(), "r");
    ASSERT_TRUE(f != 0);
    while (fgets(buf, sizeof(buf), f) != NULL) {
        ++lineN;
        vespalib::string line(buf);
        if (*line.rbegin() == '\n') {
            line.resize(line.size() - 1);
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        uint32_t idx = line.find("<=>");
        if (!EXPECT_TRUE(idx < line.size())) {
            LOG(error, "(%s:%u): malformed line: '%s'",
                name.c_str(), lineN, line.c_str());
        } else {
            vespalib::string input = line.substr(0, idx);
            vespalib::string expect = line.substr(idx + strlen("<=>"));
            if (!EXPECT_EQUAL(FeatureNameParser(input).featureName(), expect)) {
                LOG(error, "(%s:%u): test failed: '%s'",
                    name.c_str(), lineN, line.c_str());
            }
        }
    }
    ASSERT_TRUE(!ferror(f));
    fclose(f);
}

int
Test::Main()
{
    TEST_INIT("featurenameparser_test");

    // normal cases
    EXPECT_TRUE(testParse("foo", true, "foo", ParamList(), ""));
    EXPECT_TRUE(testParse("foo.out", true, "foo", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo(a)", true, "foo", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo(a,b)", true, "foo", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo(a,b).out", true, "foo", ParamList().add("a").add("b"), "out"));

    // @ in feature name (for macros)
    EXPECT_TRUE(testParse("foo@", true, "foo@", ParamList(), ""));
    EXPECT_TRUE(testParse("foo@.out", true, "foo@", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo@(a)", true, "foo@", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo@(a,b)", true, "foo@", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo@(a,b).out", true, "foo@", ParamList().add("a").add("b"), "out"));

    // $ in feature name (for macros)
    EXPECT_TRUE(testParse("foo$", true, "foo$", ParamList(), ""));
    EXPECT_TRUE(testParse("foo$.out", true, "foo$", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo$(a)", true, "foo$", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo$(a,b)", true, "foo$", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo$(a,b).out", true, "foo$", ParamList().add("a").add("b"), "out"));

    // de-quoting of parameters
    EXPECT_TRUE(testParse("foo(a,\"b\")", true, "foo", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo(a,\" b \")", true, "foo", ParamList().add("a").add(" b "), ""));
    EXPECT_TRUE(testParse("foo( \"a\" , \" b \" )", true, "foo", ParamList().add("a").add(" b "), ""));
    EXPECT_TRUE(testParse("foo(\"\\\"\\\\\\t\\n\\r\\f\\x20\")", true, "foo", ParamList().add("\"\\\t\n\r\f "), ""));

    // only default output if '.' not specified
    EXPECT_TRUE(testParse("foo.", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,b).", false, "", ParamList(), ""));

    // string cannot end in parameter list
    EXPECT_TRUE(testParse("foo(", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a\\", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a\\)", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,b", false, "", ParamList(), ""));

    // empty parameters
    EXPECT_TRUE(testParse("foo()", true, "foo", ParamList().add(""), ""));
    EXPECT_TRUE(testParse("foo(,)", true, "foo", ParamList().add("").add(""), ""));
    EXPECT_TRUE(testParse("foo(,,)", true, "foo", ParamList().add("").add("").add(""), ""));
    EXPECT_TRUE(testParse("foo(,x,)", true, "foo", ParamList().add("").add("x").add(""), ""));
    EXPECT_TRUE(testParse("foo(  )", true, "foo", ParamList().add(""), ""));
    EXPECT_TRUE(testParse("foo(  ,  ,  )", true, "foo", ParamList().add("").add("").add(""), ""));
    EXPECT_TRUE(testParse("foo( \t , \n , \r , \f )", true, "foo", ParamList().add("").add("").add("").add(""), ""));

    testFile(TEST_PATH("parsetest.txt"));
    TEST_DONE();
}

TEST_APPHOOK(Test);
