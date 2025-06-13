// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("component_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/component/version.h>
#include <vespa/vespalib/component/versionspecification.h>
#include <format>

using namespace vespalib;

void
check_lt(const VersionSpecification::string &lhs, 
         const VersionSpecification::string &rhs)
{
    SCOPED_TRACE(std::format("check: {} < {}", lhs, rhs));
    EXPECT_TRUE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

void
check_eq(const VersionSpecification::string &lhs, 
         const VersionSpecification::string &rhs)
{
    SCOPED_TRACE(std::format("check: {} == {}", lhs, rhs));
    EXPECT_FALSE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_TRUE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

void
check_ne(const VersionSpecification::string &lhs, 
         const VersionSpecification::string &rhs)
{
    SCOPED_TRACE(std::format("check: {} != {}", lhs, rhs));
    EXPECT_TRUE(VersionSpecification(lhs) < VersionSpecification(rhs) ||
                VersionSpecification(rhs) < VersionSpecification(lhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
}

void
check_gt(const VersionSpecification::string &lhs, 
         const VersionSpecification::string &rhs)
{
    SCOPED_TRACE(std::format("check: {} > {}", lhs, rhs));
    EXPECT_FALSE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_TRUE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

TEST(ComponentTest, requireThatCompareToIsSymmetric)
{
    check_lt("1", "2");
    check_eq("2", "2");
    check_gt("2", "1");

    check_lt("1.2", "3.4");
    check_eq("3.4", "3.4");
    check_gt("3.4", "1.2");

    check_lt("1.2.3", "4.5.6");
    check_eq("4.5.6", "4.5.6");
    check_gt("4.5.6", "1.2.3");

    check_lt("1.2.3.4", "5.6.7.8");
    check_eq("5.6.7.8", "5.6.7.8");
    check_gt("5.6.7.8", "1.2.3.4");
}

TEST(ComponentTest, requireThatCompareToIsTransitive)
{
    check_lt("1", "2");
    check_lt("2", "3");
    check_lt("1", "3");

    check_lt("1.1", "1.2");
    check_lt("1.2", "1.3");
    check_lt("1.1", "1.3");

    check_lt("1.1.1", "1.1.2");
    check_lt("1.1.2", "1.1.3");
    check_lt("1.1.1", "1.1.3");

    check_lt("1.1.1.1", "1.1.1.2");
    check_lt("1.1.1.2", "1.1.1.3");
    check_lt("1.1.1.1", "1.1.1.3");
}

TEST(ComponentTest, requireThatUnspecifiedComponentDoesNotMatchSpecified)
{
    check_eq("1", "1");
    check_ne("1", "1.2");
    check_ne("1", "1.2.3");
    check_ne("1", "1.2.3.4");

    check_ne("1.2", "1");
    check_eq("1.2", "1.2");
    check_ne("1.2", "1.2.3");
    check_ne("1.2", "1.2.3.4");

    check_ne("1.2.3", "1");
    check_ne("1.2.3", "1.2");
    check_eq("1.2.3", "1.2.3");
    check_ne("1.2.3", "1.2.3.4");

    check_ne("1.2.3.4", "1");
    check_ne("1.2.3.4", "1.2");
    check_ne("1.2.3.4", "1.2.3");
    check_eq("1.2.3.4", "1.2.3.4");
}

TEST(ComponentTest, testText)
{
    VersionSpecification v("0.1.2.3");
    EXPECT_EQ(0, v.getMajor());
    EXPECT_EQ(1, v.getMinor());
    EXPECT_EQ(2, v.getMicro());
    EXPECT_EQ("3", v.getQualifier());
    v = VersionSpecification("1.2.3.4");
    EXPECT_EQ(1, v.getMajor());
    EXPECT_EQ(2, v.getMinor());
    EXPECT_EQ(3, v.getMicro());
    EXPECT_EQ("4", v.getQualifier());
    v = VersionSpecification("1");
    EXPECT_EQ(1, v.getMajor());
    EXPECT_EQ(0, v.getMinor());
    EXPECT_EQ(0, v.getMicro());
    EXPECT_EQ("", v.getQualifier());
    VESPA_EXPECT_EXCEPTION(v = VersionSpecification("-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = VersionSpecification("1.-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = VersionSpecification("1.2.-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = VersionSpecification("1.2.3.-1"), IllegalArgumentException, "Invalid character in qualifier");
}

TEST(ComponentTest, testText2)
{
    Version v("0.1.2.3");
    EXPECT_EQ(0, v.getMajor());
    EXPECT_EQ(1, v.getMinor());
    EXPECT_EQ(2, v.getMicro());
    EXPECT_EQ("3", v.getQualifier());
    v = Version("1.2.3.4");
    EXPECT_EQ(1, v.getMajor());
    EXPECT_EQ(2, v.getMinor());
    EXPECT_EQ(3, v.getMicro());
    EXPECT_EQ("4", v.getQualifier());
    v = Version("1");
    EXPECT_EQ(1, v.getMajor());
    EXPECT_EQ(0, v.getMinor());
    EXPECT_EQ(0, v.getMicro());
    EXPECT_EQ("", v.getQualifier());
    VESPA_EXPECT_EXCEPTION(v = Version("-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = Version("1.-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = Version("1.2.-1"), IllegalArgumentException, "integer must start with a digit");
    VESPA_EXPECT_EXCEPTION(v = Version("1.2.3.-1"), IllegalArgumentException, "Invalid character in qualifier");
}

TEST(ComponentTest, testEmpty)
{
    Version ev;
    VersionSpecification evs;

    EXPECT_EQ("", ev.toAbbreviatedString());
    EXPECT_EQ("0.0.0", ev.toString());
    EXPECT_EQ("*.*.*", evs.toString());

    EXPECT_TRUE(ev == Version(0,0,0,""));

    EXPECT_TRUE(evs.matches(ev));
    EXPECT_TRUE(evs.matches(Version(1,2,3)));
    EXPECT_TRUE(!evs.matches(Version(1,2,3,"foo")));
}

TEST(ComponentTest, testSimple)
{
    // test Version:

    Version v(1, 2, 3, "qualifier");
    EXPECT_EQ(1, v.getMajor());
    EXPECT_EQ(2, v.getMinor());
    EXPECT_EQ(3, v.getMicro());
    EXPECT_EQ("qualifier", v.getQualifier());

    EXPECT_EQ("1.2.3.qualifier", v.toString());

    // test VersionSpecification:

    VersionSpecification vs(1, 2, 3, "qualifier");
    EXPECT_EQ(1, vs.getMajor());
    EXPECT_EQ(2, vs.getMinor());
    EXPECT_EQ(3, vs.getMicro());
    EXPECT_EQ("qualifier", vs.getQualifier());

    EXPECT_EQ(1, vs.getSpecifiedMajor());
    EXPECT_EQ(2, vs.getSpecifiedMinor());
    EXPECT_EQ(3, vs.getSpecifiedMicro());

    EXPECT_EQ("1.2.3.qualifier", vs.toString());

    // test cross-class function
    EXPECT_TRUE(vs.matches(v));
}

TEST(ComponentTest, Version_toAbbreviatedString_truncates_trailing_zeroed_components_while_toString_does_not)
{
    Version v000(0, 0, 0, "");
    EXPECT_EQ("", v000.toAbbreviatedString());
    EXPECT_EQ("0.0.0", v000.toString());
    Version v000s("");
    EXPECT_EQ("", v000s.toAbbreviatedString());
    EXPECT_EQ("0.0.0", v000s.toString());

    Version v100(1, 0, 0, "");
    EXPECT_EQ("1", v100.toAbbreviatedString());
    EXPECT_EQ("1.0.0", v100.toString());
    Version v100s("1");
    EXPECT_EQ("1", v100s.toAbbreviatedString());
    EXPECT_EQ("1.0.0", v100s.toString());

    Version v110(1, 2, 0, "");
    EXPECT_EQ("1.2", v110.toAbbreviatedString());
    EXPECT_EQ("1.2.0", v110.toString());
    Version v110s("1.2");
    EXPECT_EQ("1.2", v110s.toAbbreviatedString());
    EXPECT_EQ("1.2.0", v110.toString());

    Version v111(1, 2, 3, "");
    EXPECT_EQ("1.2.3", v111.toAbbreviatedString());
    EXPECT_EQ("1.2.3", v111.toString());
    Version v111s("1.2.3");
    EXPECT_EQ("1.2.3", v111s.toAbbreviatedString());
    EXPECT_EQ("1.2.3", v111s.toString());

    Version v000q(0, 0, 0, "qualifier");
    EXPECT_EQ("0.0.0.qualifier", v000q.toAbbreviatedString());
    EXPECT_EQ("0.0.0.qualifier", v000q.toString());
    Version v000qs("0.0.0.qualifier");
    EXPECT_EQ("0.0.0.qualifier", v000qs.toAbbreviatedString());
    EXPECT_EQ("0.0.0.qualifier", v000qs.toString());

    Version v100q(1, 0, 0, "qualifier");
    EXPECT_EQ("1.0.0.qualifier", v100q.toAbbreviatedString());
    EXPECT_EQ("1.0.0.qualifier", v100q.toString());
    Version v100qs("1.0.0.qualifier");
    EXPECT_EQ("1.0.0.qualifier", v100qs.toAbbreviatedString());
    EXPECT_EQ("1.0.0.qualifier", v100qs.toString());

    Version v110q(1, 2, 0, "qualifier");
    EXPECT_EQ("1.2.0.qualifier", v110q.toAbbreviatedString());
    EXPECT_EQ("1.2.0.qualifier", v110q.toString());
    Version v110qs("1.2.0.qualifier");
    EXPECT_EQ("1.2.0.qualifier", v110qs.toAbbreviatedString());
    EXPECT_EQ("1.2.0.qualifier", v110qs.toString());

    Version v111q(1, 2, 3, "qualifier");
    EXPECT_EQ("1.2.3.qualifier", v111q.toAbbreviatedString());
    EXPECT_EQ("1.2.3.qualifier", v111q.toString());
    Version v111qs("1.2.3.qualifier");
    EXPECT_EQ("1.2.3.qualifier", v111qs.toAbbreviatedString());
    EXPECT_EQ("1.2.3.qualifier", v111qs.toString());
}

TEST(ComponentTest, VersionSpecification_toString_does_not_truncate_trailing_zeroed_components)
{
    VersionSpecification vs000(0, 0, 0, "");
    EXPECT_EQ("0.0.0", vs000.toString());
    VersionSpecification vs000s("0.0.0");
    EXPECT_EQ("0.0.0", vs000s.toString());

    VersionSpecification vs000q(0, 0, 0, "qualifier");
    EXPECT_EQ("0.0.0.qualifier", vs000q.toString());
    VersionSpecification vs000qs("0.0.0.qualifier");
    EXPECT_EQ("0.0.0.qualifier", vs000qs.toString());
}

GTEST_MAIN_RUN_ALL_TESTS()
