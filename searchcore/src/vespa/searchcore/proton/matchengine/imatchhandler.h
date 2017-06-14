// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/vespalib/util/thread_bundle.h>

namespace searche {
namespace engine {
    class SearchRequest;
    class SearchReply;
}
}

namespace proton {

/**
 * This interface describes a sync match operation handler. It is implemented by
 * the DocumentDB class, and used by the MatchEngine class to delegate
 * operations to the appropriate db.
 */
class IMatchHandler {
protected:
    using SearchReply = search::engine::SearchReply;
    using SearchRequest = search::engine::SearchRequest;
    using ThreadBundle = vespalib::ThreadBundle;
    IMatchHandler() = default;
public:
    IMatchHandler(const IMatchHandler &) = delete;
    IMatchHandler & operator = (const IMatchHandler &) = delete;
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IMatchHandler> UP;
    typedef std::shared_ptr<IMatchHandler> SP;

    virtual ~IMatchHandler() { }

    /**
     * @return Use the request and produce the matching result.
     */
    virtual std::unique_ptr<SearchReply>
    match(const ISearchHandler::SP &searchHandler, const SearchRequest &req, ThreadBundle &threadBundle) const = 0;
};

} // namespace proton

