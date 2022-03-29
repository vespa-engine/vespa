// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/update/removefieldpathupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>

#include <vespa/document/update/tensor_remove_update.h>
#include <vespa/document/update/tensor_modify_update.h>
#include <vespa/document/update/tensor_add_update.h>
#include <vespa/document/update/tensor_partial_update.h>
#include <vespa/document/util/feed_reject_helper.h>

#include <gtest/gtest.h>
#include <gmock/gmock.h>


namespace document {

TEST(DocumentRejectTest, requireThatFixedSizeFieldValuesAreDetected) {
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::BoolFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::ByteFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::ShortFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::IntFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::LongFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::FloatFieldValue()));
    EXPECT_TRUE(FeedRejectHelper::isFixedSizeSingleValue(document::DoubleFieldValue()));

    EXPECT_FALSE(FeedRejectHelper::isFixedSizeSingleValue(document::StringFieldValue()));
    EXPECT_FALSE(FeedRejectHelper::isFixedSizeSingleValue(document::RawFieldValue()));
    EXPECT_FALSE(FeedRejectHelper::isFixedSizeSingleValue(document::PredicateFieldValue()));
    EXPECT_FALSE(FeedRejectHelper::isFixedSizeSingleValue(document::ReferenceFieldValue()));

    document::ArrayDataType intArrayType(*document::DataType::INT);
    EXPECT_FALSE(FeedRejectHelper::isFixedSizeSingleValue(document::ArrayFieldValue(intArrayType)));
}

TEST(DocumentRejectTest, requireThatClearRemoveTensorRemoveAndArtithmeticUpdatesIgnoreFeedRejection) {
    EXPECT_FALSE(FeedRejectHelper::mustReject(ClearValueUpdate()));
    EXPECT_FALSE(FeedRejectHelper::mustReject(RemoveValueUpdate(StringFieldValue::make())));
    EXPECT_FALSE(FeedRejectHelper::mustReject(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 5.0)));
    EXPECT_FALSE(FeedRejectHelper::mustReject(TensorRemoveUpdate(std::make_unique<TensorFieldValue>())));
}

TEST(DocumentRejectTest, requireThatAddMapTensorModifyAndTensorAddUpdatesWillBeRejected) {
    EXPECT_TRUE(FeedRejectHelper::mustReject(AddValueUpdate(std::make_unique<IntFieldValue>())));
    EXPECT_TRUE(FeedRejectHelper::mustReject(MapValueUpdate(std::make_unique<IntFieldValue>(), std::make_unique<ClearValueUpdate>())));
    EXPECT_TRUE(FeedRejectHelper::mustReject(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE,
                                                                std::make_unique<TensorFieldValue>())));
    EXPECT_TRUE(FeedRejectHelper::mustReject(TensorAddUpdate(std::make_unique<TensorFieldValue>())));
}

TEST(DocumentRejectTest, requireThatAssignUpdatesWillBeRejectedBasedOnTheirContent) {
    EXPECT_FALSE(FeedRejectHelper::mustReject(AssignValueUpdate(std::make_unique<IntFieldValue>())));
    EXPECT_TRUE(FeedRejectHelper::mustReject(AssignValueUpdate(StringFieldValue::make())));
}

}
