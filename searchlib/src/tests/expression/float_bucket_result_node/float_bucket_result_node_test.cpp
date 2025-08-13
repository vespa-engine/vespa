// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/floatbucketresultnode.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP("float_bucket_result_node_test");

using namespace search::expression;
using namespace vespalib;

namespace {

// ugly test-only utility:
std::string stringValue(const FloatBucketResultNode &result) {
    nbostream buf;
    NBOSerializer s(buf);
    result.onSerialize(s);
    double f, t;
    s >> f >> t;
    return "bucket[" + std::to_string(f) + ", " + std::to_string(t) + ">";
}

} // namespace

FloatBucketResultNode mkn(double f, double t) {
    return FloatBucketResultNode(f, t);
}

void check_cmp(const FloatBucketResultNode &a, const FloatBucketResultNode &b, int expect) {
    int res = a.cmp(b);
    printf("Got %2d when comparing:   %s  <=>  %s\n", res,
           stringValue(a).c_str(),
           stringValue(b).c_str());
    EXPECT_EQ(expect, res);
}

TEST(FloatBucketResultNodeTest, test_sorting)
{
    auto n01 = mkn(0, 1);
    auto n12 = mkn(1, 2);
    auto n23 = mkn(2, 3);
    auto n02 = mkn(0, 2);
    auto n13 = mkn(1, 3);

    check_cmp(n01, n01, 0);
    check_cmp(n13, n13, 0);

    check_cmp(n01, n12, -1);
    check_cmp(n12, n23, -1);
    check_cmp(n01, n02, -1);

    check_cmp(n12, n01, 1);
    check_cmp(n23, n12, 1);
    check_cmp(n02, n01, 1);

    double nanv = std::nan("");
    auto nan = mkn(nanv, nanv);
    check_cmp(nan, nan, 0);
    check_cmp(n01, nan, 1);
    check_cmp(nan, n01, -1);

    auto bad1 = mkn(nanv, 1);
    check_cmp(bad1, bad1, 0);
    check_cmp(bad1, nan, 1);
    check_cmp(nan, bad1, -1);

    check_cmp(n01, bad1, 1);
    check_cmp(bad1, n01, -1);
    check_cmp(n23, bad1, 1);
    check_cmp(bad1, n23, -1);

    auto bad2 = mkn(1, nanv);
    check_cmp(bad2, bad2, 0);
    check_cmp(bad2, nan, 1);
    check_cmp(nan, bad2, -1);

    check_cmp(bad2, n01, 1);
    check_cmp(n01, bad2, -1);
    check_cmp(n12, bad2, 1);
    check_cmp(bad2, n12, -1);
    check_cmp(n23, bad2, 1);
    check_cmp(bad2, n23, -1);

    check_cmp(bad2, bad1, 1);
    check_cmp(bad1, bad2, -1);
}

GTEST_MAIN_RUN_ALL_TESTS()
