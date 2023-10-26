// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("component_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/vespalib/component/version.h>
#include <vespa/vespalib/component/versionspecification.h>

using namespace vespalib;

void
EXPECT_LT(const VersionSpecification::string &lhs, 
          const VersionSpecification::string &rhs)
{
    EXPECT_TRUE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

void
EXPECT_EQ(const VersionSpecification::string &lhs, 
          const VersionSpecification::string &rhs)
{
    EXPECT_FALSE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_TRUE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

void
EXPECT_NE(const VersionSpecification::string &lhs, 
          const VersionSpecification::string &rhs)
{
    EXPECT_TRUE(VersionSpecification(lhs) < VersionSpecification(rhs) ||
                VersionSpecification(rhs) < VersionSpecification(lhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
}

void
EXPECT_GT(const VersionSpecification::string &lhs, 
          const VersionSpecification::string &rhs)
{
    EXPECT_FALSE(VersionSpecification(lhs) < VersionSpecification(rhs));
    EXPECT_FALSE(VersionSpecification(lhs) == VersionSpecification(rhs));
    EXPECT_TRUE(VersionSpecification(rhs) < VersionSpecification(lhs));
}

TEST("requireThatCompareToIsSymmetric")
{
    EXPECT_LT("1", "2");
    EXPECT_EQ("2", "2");
    EXPECT_GT("2", "1");

    EXPECT_LT("1.2", "3.4");
    EXPECT_EQ("3.4", "3.4");
    EXPECT_GT("3.4", "1.2");

    EXPECT_LT("1.2.3", "4.5.6");
    EXPECT_EQ("4.5.6", "4.5.6");
    EXPECT_GT("4.5.6", "1.2.3");

    EXPECT_LT("1.2.3.4", "5.6.7.8");
    EXPECT_EQ("5.6.7.8", "5.6.7.8");
    EXPECT_GT("5.6.7.8", "1.2.3.4");
}

TEST("requireThatCompareToIsTransitive")
{
    EXPECT_LT("1", "2");
    EXPECT_LT("2", "3");
    EXPECT_LT("1", "3");

    EXPECT_LT("1.1", "1.2");
    EXPECT_LT("1.2", "1.3");
    EXPECT_LT("1.1", "1.3");

    EXPECT_LT("1.1.1", "1.1.2");
    EXPECT_LT("1.1.2", "1.1.3");
    EXPECT_LT("1.1.1", "1.1.3");

    EXPECT_LT("1.1.1.1", "1.1.1.2");
    EXPECT_LT("1.1.1.2", "1.1.1.3");
    EXPECT_LT("1.1.1.1", "1.1.1.3");
}

TEST("requireThatUnspecifiedComponentDoesNotMatchSpecified")
{
    EXPECT_EQ("1", "1");
    EXPECT_NE("1", "1.2");
    EXPECT_NE("1", "1.2.3");
    EXPECT_NE("1", "1.2.3.4");

    EXPECT_NE("1.2", "1");
    EXPECT_EQ("1.2", "1.2");
    EXPECT_NE("1.2", "1.2.3");
    EXPECT_NE("1.2", "1.2.3.4");

    EXPECT_NE("1.2.3", "1");
    EXPECT_NE("1.2.3", "1.2");
    EXPECT_EQ("1.2.3", "1.2.3");
    EXPECT_NE("1.2.3", "1.2.3.4");

    EXPECT_NE("1.2.3.4", "1");
    EXPECT_NE("1.2.3.4", "1.2");
    EXPECT_NE("1.2.3.4", "1.2.3");
    EXPECT_EQ("1.2.3.4", "1.2.3.4");
}

TEST("testText")
{
    VersionSpecification v("0.1.2.3");
    EXPECT_EQUAL(0, v.getMajor());
    EXPECT_EQUAL(1, v.getMinor());
    EXPECT_EQUAL(2, v.getMicro());
    EXPECT_EQUAL("3", v.getQualifier());
    v = VersionSpecification("1.2.3.4");
    EXPECT_EQUAL(1, v.getMajor());
    EXPECT_EQUAL(2, v.getMinor());
    EXPECT_EQUAL(3, v.getMicro());
    EXPECT_EQUAL("4", v.getQualifier());
    v = VersionSpecification("1");
    EXPECT_EQUAL(1, v.getMajor());
    EXPECT_EQUAL(0, v.getMinor());
    EXPECT_EQUAL(0, v.getMicro());
    EXPECT_EQUAL("", v.getQualifier());
    EXPECT_EXCEPTION(v = VersionSpecification("-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = VersionSpecification("1.-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = VersionSpecification("1.2.-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = VersionSpecification("1.2.3.-1"), IllegalArgumentException, "Invalid character in qualifier");
}

TEST("testText2")
{
    Version v("0.1.2.3");
    EXPECT_EQUAL(0, v.getMajor());
    EXPECT_EQUAL(1, v.getMinor());
    EXPECT_EQUAL(2, v.getMicro());
    EXPECT_EQUAL("3", v.getQualifier());
    v = Version("1.2.3.4");
    EXPECT_EQUAL(1, v.getMajor());
    EXPECT_EQUAL(2, v.getMinor());
    EXPECT_EQUAL(3, v.getMicro());
    EXPECT_EQUAL("4", v.getQualifier());
    v = Version("1");
    EXPECT_EQUAL(1, v.getMajor());
    EXPECT_EQUAL(0, v.getMinor());
    EXPECT_EQUAL(0, v.getMicro());
    EXPECT_EQUAL("", v.getQualifier());
    EXPECT_EXCEPTION(v = Version("-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = Version("1.-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = Version("1.2.-1"), IllegalArgumentException, "integer must start with a digit");
    EXPECT_EXCEPTION(v = Version("1.2.3.-1"), IllegalArgumentException, "Invalid character in qualifier");
}

TEST("testEmpty")
{
    Version ev;
    VersionSpecification evs;

    EXPECT_EQUAL("", ev.toString());
    EXPECT_EQUAL("*.*.*", evs.toString());

    EXPECT_TRUE(ev == Version(0,0,0,""));

    EXPECT_TRUE(evs.matches(ev));
    EXPECT_TRUE(evs.matches(Version(1,2,3)));
    EXPECT_TRUE(!evs.matches(Version(1,2,3,"foo")));
}

TEST("testSimple")
{
    // test Version:

    Version v(1, 2, 3, "qualifier");
    EXPECT_EQUAL(1, v.getMajor());
    EXPECT_EQUAL(2, v.getMinor());
    EXPECT_EQUAL(3, v.getMicro());
    EXPECT_EQUAL("qualifier", v.getQualifier());

    EXPECT_EQUAL("1.2.3.qualifier", v.toString());

    // test VersionSpecification:

    VersionSpecification vs(1, 2, 3, "qualifier");
    EXPECT_EQUAL(1, vs.getMajor());
    EXPECT_EQUAL(2, vs.getMinor());
    EXPECT_EQUAL(3, vs.getMicro());
    EXPECT_EQUAL("qualifier", vs.getQualifier());

    EXPECT_EQUAL(1, vs.getSpecifiedMajor());
    EXPECT_EQUAL(2, vs.getSpecifiedMinor());
    EXPECT_EQUAL(3, vs.getSpecifiedMicro());

    EXPECT_EQUAL("1.2.3.qualifier", vs.toString());

    // test cross-class function
    EXPECT_TRUE(vs.matches(v));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
