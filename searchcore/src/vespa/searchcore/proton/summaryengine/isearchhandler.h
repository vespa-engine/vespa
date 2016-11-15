// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/vespalib/util/thread_bundle.h>

namespace proton {

/**
 * This interface describes a sync summary operation handler. It is
 * implemented by the DocumentDB class, and used by the SummaryEngine
 * class to delegate operations to the appropriate db.
 */
class ISearchHandler {
protected:
    ISearchHandler() = default;
    using DocsumReply = search::engine::DocsumReply;
    using SearchReply = search::engine::SearchReply;
    using SearchRequest = search::engine::SearchRequest;
    using DocsumRequest = search::engine::DocsumRequest;
public:
    typedef std::unique_ptr<ISearchHandler> UP;
    typedef std::shared_ptr<ISearchHandler> SP;

    ISearchHandler(const ISearchHandler &) = delete;
    ISearchHandler & operator = (const ISearchHandler &) = delete;
    virtual ~ISearchHandler() { }

    /**
     * @return Use the request and produce the document summary result.
     */
    virtual DocsumReply::UP getDocsums(const DocsumRequest & request) = 0;

    virtual SearchReply::UP match(
            const ISearchHandler::SP &self,
            const SearchRequest &req,
            vespalib::ThreadBundle &threadBundle) const = 0;
};

} // namespace proton

