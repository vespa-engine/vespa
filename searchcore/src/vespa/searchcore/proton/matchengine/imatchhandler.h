// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/utility.hpp>
#include <vespa/searchcore/proton/matching/isearchcontext.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/vespalib/util/thread_bundle.h>

namespace proton {

/**
 * This interface describes a sync match operation handler. It is implemented by
 * the DocumentDB class, and used by the MatchEngine class to delegate
 * operations to the appropriate db.
 */
class IMatchHandler {
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IMatchHandler> UP;
    typedef std::shared_ptr<IMatchHandler> SP;

    /**
     * Virtual destructor to allow inheritance.
     */
    virtual ~IMatchHandler() { }

    /**
     * @return Use the request and produce the matching result.
     */
    virtual search::engine::SearchReply::UP match(
            const ISearchHandler::SP &searchHandler,
            const search::engine::SearchRequest &req,
            vespalib::ThreadBundle &threadBundle) const = 0;
};

} // namespace proton

