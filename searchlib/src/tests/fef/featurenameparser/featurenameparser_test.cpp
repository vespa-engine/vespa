// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/fef/featurenameparser.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vector>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("featurenameparser_test");

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

bool
testParse(const vespalib::string &input, bool valid,
          const vespalib::string &base, ParamList pl,
          const vespalib::string &output)
{
    bool ok = true;
    FeatureNameParser parser(input);
    if (!parser.valid()) {
        LOG(warning, "parse error: input:'%s', rest:'%s'",
            input.c_str(), input.substr(parser.parsedBytes()).c_str());
    }
    EXPECT_EQ(parser.valid(), valid) << (ok = false, "");
    EXPECT_EQ(parser.baseName(), base) << (ok = false, "");
    EXPECT_EQ(ParamList(parser.parameters()), pl) << (ok = false, "");
    EXPECT_EQ(parser.output(), output) << (ok = false, "");
    return ok;
}

void
testFile(const vespalib::string &name)
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
        bool failed = false;
        EXPECT_TRUE(idx < line.size()) << (failed = true, "");
        if (failed) {
            LOG(error, "(%s:%u): malformed line: '%s'",
                name.c_str(), lineN, line.c_str());
        } else {
            vespalib::string input = line.substr(0, idx);
            vespalib::string expect = line.substr(idx + strlen("<=>"));
            EXPECT_EQ(FeatureNameParser(input).featureName(), expect) << (failed = true, "");
            if (failed) {
                LOG(error, "(%s:%u): test failed: '%s'",
                    name.c_str(), lineN, line.c_str());
            }
        }
    }
    ASSERT_TRUE(!ferror(f));
    fclose(f);
}

TEST(FeatureNameParserTest, test_normal_cases)
{
    // normal cases
    EXPECT_TRUE(testParse("foo", true, "foo", ParamList(), ""));
    EXPECT_TRUE(testParse("foo.out", true, "foo", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo(a)", true, "foo", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo(a,b)", true, "foo", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo(a,b).out", true, "foo", ParamList().add("a").add("b"), "out"));
}

TEST(FeatureNameParserTest, test_0_in_feature_name)
{
    // @ in feature name (for macros)
    EXPECT_TRUE(testParse("foo@", true, "foo@", ParamList(), ""));
    EXPECT_TRUE(testParse("foo@.out", true, "foo@", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo@(a)", true, "foo@", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo@(a,b)", true, "foo@", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo@(a,b).out", true, "foo@", ParamList().add("a").add("b"), "out"));
}

TEST(FeatureNameParserTest, test_dollar_in_feature_name)
{
    // $ in feature name (for macros)
    EXPECT_TRUE(testParse("foo$", true, "foo$", ParamList(), ""));
    EXPECT_TRUE(testParse("foo$.out", true, "foo$", ParamList(), "out"));
    EXPECT_TRUE(testParse("foo$(a)", true, "foo$", ParamList().add("a"), ""));
    EXPECT_TRUE(testParse("foo$(a,b)", true, "foo$", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo$(a,b).out", true, "foo$", ParamList().add("a").add("b"), "out"));
}

TEST(FeatureNameParserTest, test_de_quoting_of_parameters)
{
    // de-quoting of parameters
    EXPECT_TRUE(testParse("foo(a,\"b\")", true, "foo", ParamList().add("a").add("b"), ""));
    EXPECT_TRUE(testParse("foo(a,\" b \")", true, "foo", ParamList().add("a").add(" b "), ""));
    EXPECT_TRUE(testParse("foo( \"a\" , \" b \" )", true, "foo", ParamList().add("a").add(" b "), ""));
    EXPECT_TRUE(testParse("foo(\"\\\"\\\\\\t\\n\\r\\f\\x20\")", true, "foo", ParamList().add("\"\\\t\n\r\f "), ""));
}

TEST(FeatureNameParserTest, test_no_default_output_when_ending_with_dot)
{
    // only default output if '.' not specified
    EXPECT_TRUE(testParse("foo.", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,b).", false, "", ParamList(), ""));
}

TEST(FeatureNameParserTest, test_string_cannot_end_in_parmeter_list)
{
    // string cannot end in parameter list
    EXPECT_TRUE(testParse("foo(", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a\\", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a\\)", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,", false, "", ParamList(), ""));
    EXPECT_TRUE(testParse("foo(a,b", false, "", ParamList(), ""));
}

TEST(FeatureNameParserTest, test_empty_parameters)
{
    // empty parameters
    EXPECT_TRUE(testParse("foo()", true, "foo", ParamList().add(""), ""));
    EXPECT_TRUE(testParse("foo(,)", true, "foo", ParamList().add("").add(""), ""));
    EXPECT_TRUE(testParse("foo(,,)", true, "foo", ParamList().add("").add("").add(""), ""));
    EXPECT_TRUE(testParse("foo(,x,)", true, "foo", ParamList().add("").add("x").add(""), ""));
    EXPECT_TRUE(testParse("foo(  )", true, "foo", ParamList().add(""), ""));
    EXPECT_TRUE(testParse("foo(  ,  ,  )", true, "foo", ParamList().add("").add("").add(""), ""));
    EXPECT_TRUE(testParse("foo( \t , \n , \r , \f )", true, "foo", ParamList().add("").add("").add("").add(""), ""));
}

TEST(FeatureNameParserTest, test_cases_from_file)
{
    testFile(TEST_PATH("parsetest.txt"));
}

GTEST_MAIN_RUN_ALL_TESTS()
