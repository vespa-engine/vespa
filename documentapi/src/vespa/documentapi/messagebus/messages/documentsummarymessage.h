// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/vdslib/container/documentsummary.h>

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
    uint32_t getApproxSize() const override;
    uint32_t getType() const override;
    string toString() const override { return "documentsummarymessage"; }
};

}
