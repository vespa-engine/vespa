// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/thread_bundle.h>

namespace search::engine {
    class SearchRequest;
    class SearchReply;
    class DocsumRequest;
    class DocsumReply;
}

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
    using ThreadBundle = vespalib::ThreadBundle;
public:
    typedef std::shared_ptr<ISearchHandler> SP;

    ISearchHandler(const ISearchHandler &) = delete;
    ISearchHandler & operator = (const ISearchHandler &) = delete;
    virtual ~ISearchHandler() = default;

    /**
     * @return Use the request and produce the document summary result.
     */
    virtual std::unique_ptr<DocsumReply> getDocsums(const DocsumRequest & request) = 0;

    virtual std::unique_ptr<SearchReply>
    match(const SearchRequest &req, ThreadBundle &threadBundle) const = 0;
};

} // namespace proton

