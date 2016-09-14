// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace proton {

class EmptySearchView : public ISearchHandler
{
public:
    typedef std::shared_ptr<EmptySearchView> SP;

    EmptySearchView(void);

    /**
     * Implements ISearchHandler
     */
    virtual search::engine::DocsumReply::UP
    getDocsums(const search::engine::DocsumRequest & req);

    virtual search::engine::SearchReply::UP
    match(const ISearchHandler::SP &searchHandler,
          const search::engine::SearchRequest &req,
          vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton

