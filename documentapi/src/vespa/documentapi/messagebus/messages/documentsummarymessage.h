// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/container/documentsummary.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>

namespace documentapi {

class DocumentSummaryMessage : public VisitorMessage,
                               public vdslib::DocumentSummary {
protected:
    // Implements VisitorMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<DocumentSummaryMessage> UP;
    typedef std::shared_ptr<DocumentSummaryMessage> SP;

    /**
     * Constructs a new document message with no content.
     */
    DocumentSummaryMessage();

    /**
     * Constructs a new document message with summary comment.
     *
     * @param summary The document summary to contain.
     */
    DocumentSummaryMessage(const vdslib::DocumentSummary &summary);

    // Overrides VisitorMessage.
    uint32_t getApproxSize() const override;

    // Implements VisitorMessage.
    uint32_t getType() const override;

    string toString() const override { return "documentsummarymessage"; }
};

}

