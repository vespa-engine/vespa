// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/storage/storageserver/priorityconverter.h>
#include <tests/common/testhelper.h>

namespace storage {

struct PriorityConverterTest : public CppUnit::TestFixture
{
    std::unique_ptr<PriorityConverter> _converter;

    void setUp() override {
        vdstestlib::DirConfig config(getStandardConfig(true));
        _converter.reset(new PriorityConverter(config.getConfigId()));
    };

    void testNormalUsage();
    void testLowestPriorityIsReturnedForUnknownCode();

    CPPUNIT_TEST_SUITE(PriorityConverterTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testLowestPriorityIsReturnedForUnknownCode);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PriorityConverterTest);

void PriorityConverterTest::testNormalUsage()
{
    for (int p=0; p<16; ++p) {
        CPPUNIT_ASSERT_EQUAL(
                (uint8_t)(50+p*10),
                _converter->toStoragePriority(
                        static_cast<documentapi::Priority::Value>(p)));
    }
    for (int i=0; i<256; ++i) {
        uint8_t p = i;
        if (p <= 50) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_HIGHEST,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 60) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_VERY_HIGH,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 70) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_HIGH_1,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 80) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_HIGH_2,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 90) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_HIGH_3,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 100) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_1,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 110) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_2,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 120) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_3,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 130) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_4,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 140) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_5,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 150) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_NORMAL_6,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 160) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_LOW_1,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 170) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_LOW_2,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 180) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_LOW_3,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 190) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_VERY_LOW,
                                 _converter->toDocumentPriority(p));
        } else if (p <= 200) {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_LOWEST,
                                 _converter->toDocumentPriority(p));
        } else {
            CPPUNIT_ASSERT_EQUAL(documentapi::Priority::PRI_LOWEST,
                                 _converter->toDocumentPriority(p));
        }
    }
}


void
PriorityConverterTest::testLowestPriorityIsReturnedForUnknownCode()
{
    CPPUNIT_ASSERT_EQUAL(255,
                         static_cast<int>(_converter->toStoragePriority(
                             static_cast<documentapi::Priority::Value>(123))));
}

}
