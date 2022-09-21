// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::docsummary {

class IDocsumStoreDocument;

/**
 * Interface used to fetch docsum specific abstract of documents based on local document id.
 **/
class IDocsumStore
{
public:
    using UP = std::unique_ptr<IDocsumStore>;

    virtual ~IDocsumStore() = default;

    /**
     * Get a docsum specific abstract of the document for the given local document id.
     **/
    virtual std::unique_ptr<const IDocsumStoreDocument> get_document(uint32_t docid) = 0;
};

}
