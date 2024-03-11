// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <iostream>
#include <vespa/documentapi/messagebus/replymerger.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/getdocumentreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace documentapi;

namespace {

void
assertReplyErrorsMatch(const mbus::Reply& r,
                       const std::vector<mbus::Error>& errors)
{
    ASSERT_EQ(r.getNumErrors(), errors.size());
    for (size_t i = 0; i < errors.size(); ++i) {
        ASSERT_EQ(errors[i].getCode(), r.getError(i).getCode());
        ASSERT_EQ(errors[i].getMessage(), r.getError(i).getMessage());
    }
}

}

TEST(ReplyMergerTest, merging_generic_replies_with_no_errors_picks_first_reply)
{
    mbus::EmptyReply r1;
    mbus::EmptyReply r2;
    mbus::EmptyReply r3;
    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    merger.merge(2, r3);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    EXPECT_EQ(0u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, merging_single_reply_with_one_error_returns_empty_reply_with_error)
{
    mbus::EmptyReply r1;
    std::vector<mbus::Error> errors = { mbus::Error(1234, "oh no!") };
    r1.addError(errors[0]);
    ReplyMerger merger;
    merger.merge(0, r1);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    assertReplyErrorsMatch(*gen, errors);
}

TEST(ReplyMergerTest, merging_single_reply_with_multiple_errors_returns_empty_reply_with_all_errors)
{
    mbus::EmptyReply r1;
    std::vector<mbus::Error> errors = {
        mbus::Error(1234, "oh no!"),
        mbus::Error(4567, "oh dear!")
    };
    r1.addError(errors[0]);
    r1.addError(errors[1]);
    ReplyMerger merger;
    merger.merge(0, r1);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    assertReplyErrorsMatch(*gen, errors);
}

TEST(ReplyMergerTest, merging_multiple_replies_with_multiple_errors_returns_empty_reply_with_all_errors)
{
    mbus::EmptyReply r1;
    mbus::EmptyReply r2;
    std::vector<mbus::Error> errors = {
        mbus::Error(1234, "oh no!"),
        mbus::Error(4567, "oh dear!"),
        mbus::Error(678, "omg!")
    };
    r1.addError(errors[0]);
    r1.addError(errors[1]);
    r2.addError(errors[2]);
    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    assertReplyErrorsMatch(*gen, errors);
}

TEST(ReplyMergerTest, return_ignored_reply_when_all_replies_have_only_ignored_errors)
{
    mbus::EmptyReply r1;
    mbus::EmptyReply r2;
    std::vector<mbus::Error> errors = {
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "oh no!"),
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "oh dear!"),
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "omg!")
    };
    r1.addError(errors[0]);
    r1.addError(errors[1]);
    r2.addError(errors[2]);
    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    // Only first ignore error from each reply.
    assertReplyErrorsMatch(*gen, { errors[0], errors[2] });
}

TEST(ReplyMergerTest, successful_reply_takes_precedence_over_ignored_reply_when_no_errors)
{
    mbus::EmptyReply r1;
    mbus::EmptyReply r2;
    std::vector<mbus::Error> errors = {
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "oh no!"),
    };
    r1.addError(errors[0]);
    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    EXPECT_EQ(1u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, non_ignored_error_takes_precedence)
{
    mbus::EmptyReply r1;
    mbus::EmptyReply r2;
    std::vector<mbus::Error> errors = {
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "oh no!"),
        mbus::Error(DocumentProtocol::ERROR_ABORTED, "kablammo!"),
        mbus::Error(DocumentProtocol::ERROR_MESSAGE_IGNORED, "omg!")
    };
    r1.addError(errors[0]);
    r1.addError(errors[1]);
    r2.addError(errors[2]);
    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    // All errors from replies with errors are included, not those that
    // are fully ignored.
    assertReplyErrorsMatch(*gen, { errors[0], errors[1] });
}

TEST(ReplyMergerTest, return_remove_document_reply_where_doc_was_found)
{
    RemoveDocumentReply r1;
    RemoveDocumentReply r2;
    RemoveDocumentReply r3;
    r1.setWasFound(false);
    r2.setWasFound(true);
    r3.setWasFound(false);

    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    merger.merge(2, r3);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    ASSERT_EQ(1u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, return_first_remove_document_reply_if_no_docs_were_found)
{
    RemoveDocumentReply r1;
    RemoveDocumentReply r2;
    r1.setWasFound(false);
    r2.setWasFound(false);

    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    ASSERT_EQ(0u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, return_update_document_reply_where_doc_was_found)
{
    UpdateDocumentReply r1;
    UpdateDocumentReply r2;
    UpdateDocumentReply r3;
    r1.setWasFound(false);
    r2.setWasFound(true); // return first reply
    r3.setWasFound(true);

    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    merger.merge(2, r3);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    ASSERT_EQ(1u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, return_get_document_reply_where_doc_was_found)
{
    GetDocumentReply r1;
    GetDocumentReply r2;
    GetDocumentReply r3;
    r2.setLastModified(12345ULL);

    ReplyMerger merger;
    merger.merge(0, r1);
    merger.merge(1, r2);
    merger.merge(2, r3);
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_TRUE(ret.isSuccessful());
    ASSERT_FALSE(ret.hasGeneratedReply());
    ASSERT_EQ(1u, ret.getSuccessfulReplyIndex());
}

TEST(ReplyMergerTest, merging_zero_replies_returns_default_empty_reply)
{
    ReplyMerger merger;
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    ASSERT_TRUE(dynamic_cast<mbus::EmptyReply*>(gen.get()) != 0);
    assertReplyErrorsMatch(*gen, {});
}

GTEST_MAIN_RUN_ALL_TESTS()
