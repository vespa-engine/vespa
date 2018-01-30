// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell


#include "messages52test.h"
#include <vespa/documentapi/documentapi.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/datatype/documenttype.h>

using document::DocumentTypeRepo;

namespace {

document::Document::SP
createDoc(const DocumentTypeRepo &repo, const string &type_name, const string &id)
{
    return document::Document::SP(new document::Document(
                    *repo.getDocumentType(type_name),
                    document::DocumentId(id)));
}

}

static const int MESSAGE_BASE_LENGTH = 5;

Messages52Test::Messages52Test()
{
    // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
    // version 5.2. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.

    putTest(DocumentProtocol::MESSAGE_PUTDOCUMENT, TEST_METHOD(Messages52Test::testPutDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_REMOVEDOCUMENT, TEST_METHOD(Messages52Test::testRemoveDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_UPDATEDOCUMENT, TEST_METHOD(Messages52Test::testUpdateDocumentMessage));
}

bool
Messages52Test::testPutDocumentMessage()
{
    auto doc = createDoc(getTypeRepo(), "testdoc", "doc:scheme:");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH +
                 41u +
                 serializedLength(msg.getCondition().getSelection()),
                 serialize("PutDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(routableUp.get() != nullptr)) {
            auto & deserializedMsg = static_cast<PutDocumentMessage &>(*routableUp);

            EXPECT_EQUAL(msg.getDocument().getType().getName(), deserializedMsg.getDocument().getType().getName());
            EXPECT_EQUAL(msg.getDocument().getId().toString(), deserializedMsg.getDocument().getId().toString());
            EXPECT_EQUAL(msg.getTimestamp(), deserializedMsg.getTimestamp());
            EXPECT_EQUAL(67u, deserializedMsg.getApproxSize());
            EXPECT_EQUAL(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
        }
    }

    return true;
}

bool
Messages52Test::testRemoveDocumentMessage()
{
    RemoveDocumentMessage msg(document::DocumentId("doc:scheme:"));

    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(16) + serializedLength(msg.getCondition().getSelection()), serialize("RemoveDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routablePtr = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);

        if (EXPECT_TRUE(routablePtr.get() != nullptr)) {
            auto & ref = static_cast<RemoveDocumentMessage &>(*routablePtr);
            EXPECT_EQUAL(string("doc:scheme:"), ref.getDocumentId().toString());
            EXPECT_EQUAL(msg.getCondition().getSelection(), ref.getCondition().getSelection());
        }
    }
    return true;
}

bool
Messages52Test::testUpdateDocumentMessage()
{
    const DocumentTypeRepo & repo = getTypeRepo();
    const document::DocumentType & docType = *repo.getDocumentType("testdoc");

    auto docUpdate = std::make_shared<document::DocumentUpdate>(docType, document::DocumentId("doc:scheme:"));

    docUpdate->addFieldPathUpdate(document::FieldPathUpdate::CP(
        new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));

    UpdateDocumentMessage msg(docUpdate);
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 89u + serializedLength(msg.getCondition().getSelection()), serialize("UpdateDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);

        if (EXPECT_TRUE(routableUp.get() != nullptr)) {
            auto & deserializedMsg = static_cast<UpdateDocumentMessage &>(*routableUp);
            EXPECT_EQUAL(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
            EXPECT_EQUAL(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
            EXPECT_EQUAL(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
            EXPECT_EQUAL(115u, deserializedMsg.getApproxSize());
            EXPECT_EQUAL(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
        }
    }
    return true;
}
