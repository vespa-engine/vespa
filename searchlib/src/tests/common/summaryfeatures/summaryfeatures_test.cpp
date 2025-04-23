// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("summaryfeatures_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/featureset.h>

using vespalib::FeatureSet;
using vespalib::Memory;

TEST(SummaryFeaturesTest, summaryfeatures_test) {
    {
        FeatureSet sf;
        EXPECT_EQ(sf.getNames().size(), 0u);
        EXPECT_EQ(sf.numFeatures(), 0u);
        EXPECT_EQ(sf.numDocs(), 0u);
        EXPECT_TRUE(sf.getFeaturesByIndex(0) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(0) == nullptr);
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
        EXPECT_EQ(sf.getNames().size(), 3u);
        EXPECT_EQ(sf.getNames()[0], "f1");
        EXPECT_EQ(sf.getNames()[1], "f2");
        EXPECT_EQ(sf.getNames()[2], "f3");
        EXPECT_EQ(sf.numFeatures(), 3u);
        EXPECT_EQ(sf.numDocs(), 0u);
        EXPECT_EQ(sf.addDocId(10), 0u);
        EXPECT_EQ(sf.addDocId(20), 1u);
        EXPECT_EQ(sf.addDocId(30), 2u);
        EXPECT_EQ(sf.addDocId(40), 3u);
        EXPECT_EQ(sf.addDocId(50), 4u);
        EXPECT_EQ(sf.numDocs(), 5u);
        FeatureSet::Value *f;
        const FeatureSet::Value *cf;
        f = sf.getFeaturesByIndex(0);
        ASSERT_TRUE(f != nullptr);
        f[0].set_double(11.0);
        f[1].set_double(12.0);
        f[2].set_double(13.0);
        f = sf.getFeaturesByIndex(1);
        ASSERT_TRUE(f != nullptr);
        f[0].set_double(21.0);
        f[1].set_double(22.0);
        f[2].set_double(23.0);
        f = sf.getFeaturesByIndex(2);
        ASSERT_TRUE(f != nullptr);
        f[0].set_double(31.0);
        f[1].set_double(32.0);
        f[2].set_double(33.0);
        f = sf.getFeaturesByIndex(3);
        ASSERT_TRUE(f != nullptr);
        f[0].set_double(41.0);
        f[1].set_data(Memory("test", 4));
        f[2].set_double(43.0);
        f = sf.getFeaturesByIndex(4);
        ASSERT_TRUE(f != nullptr);
        f[0].set_double(51.0);
        f[1].set_double(52.0);
        f[2].set_double(53.0);
        EXPECT_TRUE(sf.getFeaturesByIndex(5) == nullptr);
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
            ASSERT_TRUE(cf != nullptr);
            EXPECT_NEAR(cf[0].as_double(), 11.0, 10e-6);
            EXPECT_NEAR(cf[1].as_double(), 12.0, 10e-6);
            EXPECT_NEAR(cf[2].as_double(), 13.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(20);
            ASSERT_TRUE(cf != nullptr);
            EXPECT_NEAR(cf[0].as_double(), 21.0, 10e-6);
            EXPECT_NEAR(cf[1].as_double(), 22.0, 10e-6);
            EXPECT_NEAR(cf[2].as_double(), 23.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(30);
            ASSERT_TRUE(cf != nullptr);
            EXPECT_NEAR(cf[0].as_double(), 31.0, 10e-6);
            EXPECT_NEAR(cf[1].as_double(), 32.0, 10e-6);
            EXPECT_NEAR(cf[2].as_double(), 33.0, 10e-6);
        }
        {
            cf = sf.getFeaturesByDocId(40);
            ASSERT_TRUE(cf != nullptr);
            EXPECT_TRUE(cf[0].is_double());
            EXPECT_TRUE(!cf[0].is_data());
            EXPECT_EQ(cf[0].as_double(), 41.0);
            EXPECT_TRUE(!cf[1].is_double());
            EXPECT_TRUE(cf[1].is_data());
            EXPECT_EQ(cf[1].as_data(), Memory("test", 4));
            EXPECT_EQ(cf[2].as_double(), 43.0);
        }
        {
            cf = sf.getFeaturesByDocId(50);
            ASSERT_TRUE(cf != nullptr);
            EXPECT_NEAR(cf[0].as_double(), 51.0, 10e-6);
            EXPECT_NEAR(cf[1].as_double(), 52.0, 10e-6);
            EXPECT_NEAR(cf[2].as_double(), 53.0, 10e-6);
        }
        EXPECT_TRUE(sf.getFeaturesByDocId(5) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(15) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(25) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(35) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(45) == nullptr);
        EXPECT_TRUE(sf.getFeaturesByDocId(55) == nullptr);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
