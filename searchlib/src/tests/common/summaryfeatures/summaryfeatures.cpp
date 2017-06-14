// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("summaryfeatures_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/featureset.h>

using namespace search;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("summaryfeatures_test");
    {
        FeatureSet sf;
        EXPECT_EQUAL(sf.getNames().size(), 0u);
        EXPECT_EQUAL(sf.numFeatures(), 0u);
        EXPECT_EQUAL(sf.numDocs(), 0u);
        EXPECT_TRUE(sf.getFeaturesByIndex(0) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(0) == 0);
        std::vector<uint32_t> docs;
        EXPECT_TRUE(sf.contains(docs));
        docs.push_back(1);
        EXPECT_TRUE(!sf.contains(docs));
    }
    {
        FeatureSet::StringVector n;
        n.push_back("f1");
        n.push_back("f2");
        n.push_back("f3");

        FeatureSet sf(n, 5);
        EXPECT_EQUAL(sf.getNames().size(), 3u);
        EXPECT_EQUAL(sf.getNames()[0], "f1");
        EXPECT_EQUAL(sf.getNames()[1], "f2");
        EXPECT_EQUAL(sf.getNames()[2], "f3");
        EXPECT_EQUAL(sf.numFeatures(), 3u);
        EXPECT_EQUAL(sf.numDocs(), 0u);
        EXPECT_EQUAL(sf.addDocId(10), 0u);
        EXPECT_EQUAL(sf.addDocId(20), 1u);
        EXPECT_EQUAL(sf.addDocId(30), 2u);
        EXPECT_EQUAL(sf.addDocId(40), 3u);
        EXPECT_EQUAL(sf.addDocId(50), 4u);
        EXPECT_EQUAL(sf.numDocs(), 5u);
        feature_t *f;
        const feature_t *cf;
        f = sf.getFeaturesByIndex(0);
        ASSERT_TRUE(f != 0);
        f[0] = 11.0;
        f[1] = 12.0;
        f[2] = 13.0;
        f = sf.getFeaturesByIndex(1);
        ASSERT_TRUE(f != 0);
        f[0] = 21.0;
        f[1] = 22.0;
        f[2] = 23.0;
        f = sf.getFeaturesByIndex(2);
        ASSERT_TRUE(f != 0);
        f[0] = 31.0;
        f[1] = 32.0;
        f[2] = 33.0;
        f = sf.getFeaturesByIndex(3);
        ASSERT_TRUE(f != 0);
        f[0] = 41.0;
        f[1] = 42.0;
        f[2] = 43.0;
        f = sf.getFeaturesByIndex(4);
        ASSERT_TRUE(f != 0);
        f[0] = 51.0;
        f[1] = 52.0;
        f[2] = 53.0;
        EXPECT_TRUE(sf.getFeaturesByIndex(5) == 0);
        {
            std::vector<uint32_t> docs;
            EXPECT_TRUE(sf.contains(docs));
        }
        {
            std::vector<uint32_t> docs;
            docs.push_back(1);
            EXPECT_TRUE(!sf.contains(docs));
        }
        {
            std::vector<uint32_t> docs;
            docs.push_back(31);
            EXPECT_TRUE(!sf.contains(docs));
        }
        {
            std::vector<uint32_t> docs;
            docs.push_back(51);
            EXPECT_TRUE(!sf.contains(docs));
        }
        {
            std::vector<uint32_t> docs;
            docs.push_back(20);
            docs.push_back(40);
            EXPECT_TRUE(sf.contains(docs));
        }
        {
            std::vector<uint32_t> docs;
            docs.push_back(10);
            docs.push_back(20);
            docs.push_back(30);
            docs.push_back(40);
            docs.push_back(50);
            EXPECT_TRUE(sf.contains(docs));
        }
        {
            cf = sf.getFeaturesByDocId(10);
            ASSERT_TRUE(cf != 0);
            EXPECT_APPROX(cf[0], 11.0, 10e-6);
            EXPECT_APPROX(cf[1], 12.0, 10e-6);
            EXPECT_APPROX(cf[2], 13.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(20);
            ASSERT_TRUE(cf != 0);
            EXPECT_APPROX(cf[0], 21.0, 10e-6);
            EXPECT_APPROX(cf[1], 22.0, 10e-6);
            EXPECT_APPROX(cf[2], 23.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(30);
            ASSERT_TRUE(cf != 0);
            EXPECT_APPROX(cf[0], 31.0, 10e-6);
            EXPECT_APPROX(cf[1], 32.0, 10e-6);
            EXPECT_APPROX(cf[2], 33.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(40);
            ASSERT_TRUE(cf != 0);
            EXPECT_APPROX(cf[0], 41.0, 10e-6);
            EXPECT_APPROX(cf[1], 42.0, 10e-6);
            EXPECT_APPROX(cf[2], 43.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(50);
            ASSERT_TRUE(cf != 0);
            EXPECT_APPROX(cf[0], 51.0, 10e-6);
            EXPECT_APPROX(cf[1], 52.0, 10e-6);
            EXPECT_APPROX(cf[2], 53.0, 10e-6);
        }
        EXPECT_TRUE(sf.getFeaturesByDocId(5) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(15) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(25) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(35) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(45) == 0);
        EXPECT_TRUE(sf.getFeaturesByDocId(55) == 0);
    }
    TEST_DONE();
}
