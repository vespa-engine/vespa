// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/field_length_scanner.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::index::DocIdAndFeatures;


namespace search::diskindex {


class FieldLengthScannerTest : public ::testing::Test
{
protected:
    FieldLengthScanner _scanner;
    FieldLengthScannerTest()
        : _scanner(3)
    {
    }
};

TEST_F(FieldLengthScannerTest, require_that_no_scan_gives_empty_length)
{
    EXPECT_EQ(0, _scanner.get_field_length(1));
}

TEST_F(FieldLengthScannerTest, require_that_single_length_is_registered)
{
    DocIdAndFeatures features;
    features.set_doc_id(1);
    features.elements().emplace_back(0, 1, 5);
    _scanner.scan_features(features);
    EXPECT_EQ(5u, _scanner.get_field_length(1));
}

TEST_F(FieldLengthScannerTest, require_that_duplicate_element_is_ignored)
{
    DocIdAndFeatures features;
    features.set_doc_id(1);
    features.elements().emplace_back(10, 1, 5);
    features.elements().emplace_back(100, 1, 23);
    _scanner.scan_features(features);
    EXPECT_EQ(28u, _scanner.get_field_length(1));
    _scanner.scan_features(features); // elements 10 and 100 already scanned
    EXPECT_EQ(28u, _scanner.get_field_length(1));
    features.elements()[0].setElementId(11);
    _scanner.scan_features(features); // element 100 already scanned
    EXPECT_EQ(33u, _scanner.get_field_length(1));
    features.elements()[1].setElementId(101);
    _scanner.scan_features(features); // elements 10 already scanned
    EXPECT_EQ(56u, _scanner.get_field_length(1));
}

TEST_F(FieldLengthScannerTest, require_that_documents_are_not_mixed)
{
    DocIdAndFeatures features1;
    DocIdAndFeatures features2;
    features1.set_doc_id(1);
    features1.elements().emplace_back(10, 1, 5);
    features1.elements().emplace_back(100, 1, 23);
    features2.set_doc_id(2);
    features2.elements().emplace_back(10, 1, 7);
    features2.elements().emplace_back(100, 1, 9);
    _scanner.scan_features(features1);
    _scanner.scan_features(features2);
    EXPECT_EQ(28u, _scanner.get_field_length(1));
    EXPECT_EQ(16u, _scanner.get_field_length(2));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
