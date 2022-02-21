// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/priorityconverter.h>
#include <tests/common/testhelper.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage {

struct PriorityConverterTest : Test {
    std::unique_ptr<PriorityConverter> _converter;

    void SetUp() override {
        vdstestlib::DirConfig config(getStandardConfig(true));
        _converter = std::make_unique<PriorityConverter>(config::ConfigUri(config.getConfigId()));
    };
};

TEST_F(PriorityConverterTest, normal_usage) {
    for (int p = 0; p < 16; ++p) {
        EXPECT_EQ((50 + p * 10),
                  _converter->toStoragePriority(static_cast<documentapi::Priority::Value>(p)));
    }
    for (int i = 0; i < 256; ++i) {
        uint8_t p = i;
        if (p <= 50) {
            EXPECT_EQ(documentapi::Priority::PRI_HIGHEST, _converter->toDocumentPriority(p));
        } else if (p <= 60) {
            EXPECT_EQ(documentapi::Priority::PRI_VERY_HIGH, _converter->toDocumentPriority(p));
        } else if (p <= 70) {
            EXPECT_EQ(documentapi::Priority::PRI_HIGH_1, _converter->toDocumentPriority(p));
        } else if (p <= 80) {
            EXPECT_EQ(documentapi::Priority::PRI_HIGH_2, _converter->toDocumentPriority(p));
        } else if (p <= 90) {
            EXPECT_EQ(documentapi::Priority::PRI_HIGH_3, _converter->toDocumentPriority(p));
        } else if (p <= 100) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_1, _converter->toDocumentPriority(p));
        } else if (p <= 110) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_2, _converter->toDocumentPriority(p));
        } else if (p <= 120) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_3, _converter->toDocumentPriority(p));
        } else if (p <= 130) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_4, _converter->toDocumentPriority(p));
        } else if (p <= 140) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_5, _converter->toDocumentPriority(p));
        } else if (p <= 150) {
            EXPECT_EQ(documentapi::Priority::PRI_NORMAL_6, _converter->toDocumentPriority(p));
        } else if (p <= 160) {
            EXPECT_EQ(documentapi::Priority::PRI_LOW_1, _converter->toDocumentPriority(p));
        } else if (p <= 170) {
            EXPECT_EQ(documentapi::Priority::PRI_LOW_2, _converter->toDocumentPriority(p));
        } else if (p <= 180) {
            EXPECT_EQ(documentapi::Priority::PRI_LOW_3, _converter->toDocumentPriority(p));
        } else if (p <= 190) {
            EXPECT_EQ(documentapi::Priority::PRI_VERY_LOW, _converter->toDocumentPriority(p));
        } else if (p <= 200) {
            EXPECT_EQ(documentapi::Priority::PRI_LOWEST, _converter->toDocumentPriority(p));
        } else {
            EXPECT_EQ(documentapi::Priority::PRI_LOWEST, _converter->toDocumentPriority(p));
        }
    }
}

TEST_F(PriorityConverterTest, lowest_priority_is_returned_for_unknown_code) {
    EXPECT_EQ(255, static_cast<int>(_converter->toStoragePriority(
            static_cast<documentapi::Priority::Value>(123))));
}

}
