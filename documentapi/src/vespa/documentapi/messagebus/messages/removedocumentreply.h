// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "writedocumentreply.h"

namespace documentapi {

class RemoveDocumentReply : public WriteDocumentReply {
private:
    bool _found;

public:
    /**
     * Convenience typedef.
     */
    using UP = std::unique_ptr<RemoveDocumentReply>;
    using SP = std::shared_ptr<RemoveDocumentReply>;

public:
    /**
     * Constructs a new reply with no content.
     */
    RemoveDocumentReply();

    /**
     * Set whether or not the document was found and removed.
     *
     * @param found True if the document was found.
     */
    void setWasFound(bool found) { _found = found; }

    /**
     * Returns whether or not the document was found and removed.
     *
     * @return True if document was found.
     */
    bool wasFound() const { return getWasFound(); }

    /**
     * Returns whether or not the document was found and removed.
     *
     * @return True if document was found.
     */
    bool getWasFound() const { return _found; }

    string toString() const override { return "removedocumentreply"; }
};

}

