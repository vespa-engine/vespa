// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <iostream>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/documentapi/messagebus/replymerger.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/getdocumentreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>

using namespace documentapi;

class Test : public vespalib::TestApp
{
    static void assertReplyErrorsMatch(const mbus::Reply& r,
                                       const std::vector<mbus::Error>& errors);
public:
    int Main() override;

    void mergingGenericRepliesWithNoErrorsPicksFirstReply();
    void mergingSingleReplyWithOneErrorReturnsEmptyReplyWithError();
    void mergingSingleReplyWithMultipleErrorsReturnsEmptyReplyWithAllErrors();
    void mergingMultipleRepliesWithMultipleErrorsReturnsEmptyReplyWithAllErrors();
    void returnIgnoredReplyWhenAllRepliesHaveOnlyIgnoredErrors();
    void successfulReplyTakesPrecedenceOverIgnoredReplyWhenNoErrors();
    void nonIgnoredErrorTakesPrecedence();
    void returnRemoveDocumentReplyWhereDocWasFound();
    void returnFirstRemoveDocumentReplyIfNoDocsWereFound();
    void returnUpdateDocumentReplyWhereDocWasFound();
    void returnGetDocumentReplyWhereDocWasFound();
    void mergingZeroRepliesReturnsDefaultEmptyReply();
};

TEST_APPHOOK(Test);

void
Test::mergingGenericRepliesWithNoErrorsPicksFirstReply()
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
    EXPECT_EQUAL(0u, ret.getSuccessfulReplyIndex());
}

void
Test::mergingSingleReplyWithOneErrorReturnsEmptyReplyWithError()
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

void
Test::mergingSingleReplyWithMultipleErrorsReturnsEmptyReplyWithAllErrors()
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

void
Test::mergingMultipleRepliesWithMultipleErrorsReturnsEmptyReplyWithAllErrors()
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

void
Test::returnIgnoredReplyWhenAllRepliesHaveOnlyIgnoredErrors()
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

void
Test::successfulReplyTakesPrecedenceOverIgnoredReplyWhenNoErrors()
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
    EXPECT_EQUAL(1u, ret.getSuccessfulReplyIndex());
}

void
Test::nonIgnoredErrorTakesPrecedence()
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

void
Test::returnRemoveDocumentReplyWhereDocWasFound()
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
    ASSERT_EQUAL(1u, ret.getSuccessfulReplyIndex());
}

void
Test::returnFirstRemoveDocumentReplyIfNoDocsWereFound()
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
    ASSERT_EQUAL(0u, ret.getSuccessfulReplyIndex());
}

void
Test::returnUpdateDocumentReplyWhereDocWasFound()
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
    ASSERT_EQUAL(1u, ret.getSuccessfulReplyIndex());
}

void
Test::returnGetDocumentReplyWhereDocWasFound()
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
    ASSERT_EQUAL(1u, ret.getSuccessfulReplyIndex());
}

void
Test::assertReplyErrorsMatch(const mbus::Reply& r,
                             const std::vector<mbus::Error>& errors)
{
    ASSERT_EQUAL(r.getNumErrors(), errors.size());
    for (size_t i = 0; i < errors.size(); ++i) {
        ASSERT_EQUAL(errors[i].getCode(), r.getError(i).getCode());
        ASSERT_EQUAL(errors[i].getMessage(), r.getError(i).getMessage());
    }
}

void
Test::mergingZeroRepliesReturnsDefaultEmptyReply()
{
    ReplyMerger merger;
    ReplyMerger::Result ret(merger.mergedReply());
    ASSERT_FALSE(ret.isSuccessful());
    ASSERT_TRUE(ret.hasGeneratedReply());
    std::unique_ptr<mbus::Reply> gen(ret.releaseGeneratedReply());
    ASSERT_TRUE(dynamic_cast<mbus::EmptyReply*>(gen.get()) != 0);
    assertReplyErrorsMatch(*gen, {});
}

#ifdef RUN_TEST
#  error Someone defined RUN_TEST already! Oh no!
#endif
#define RUN_TEST(f) \
  std::cerr << "running test case '" #f "'\n"; \
  f(); TEST_FLUSH();

int
Test::Main()
{
    TEST_INIT("replymerger_test");

    RUN_TEST(mergingGenericRepliesWithNoErrorsPicksFirstReply);
    RUN_TEST(mergingSingleReplyWithOneErrorReturnsEmptyReplyWithError);
    RUN_TEST(mergingSingleReplyWithMultipleErrorsReturnsEmptyReplyWithAllErrors);
    RUN_TEST(mergingMultipleRepliesWithMultipleErrorsReturnsEmptyReplyWithAllErrors);
    RUN_TEST(returnIgnoredReplyWhenAllRepliesHaveOnlyIgnoredErrors);
    RUN_TEST(successfulReplyTakesPrecedenceOverIgnoredReplyWhenNoErrors);
    RUN_TEST(nonIgnoredErrorTakesPrecedence);
    RUN_TEST(returnRemoveDocumentReplyWhereDocWasFound);
    RUN_TEST(returnFirstRemoveDocumentReplyIfNoDocsWereFound);
    RUN_TEST(returnUpdateDocumentReplyWhereDocWasFound);
    RUN_TEST(returnGetDocumentReplyWhereDocWasFound);
    RUN_TEST(mergingZeroRepliesReturnsDefaultEmptyReply);

    TEST_DONE();
}
