// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include "documentdb.h"

namespace proton {

class SearchHandlerProxy : public boost::noncopyable,
                           public ISearchHandler
{
private:
    DocumentDB::SP _documentDB;
public:
    SearchHandlerProxy(const DocumentDB::SP &documentDB);

    virtual
    ~SearchHandlerProxy(void);

    /**
     * Implements ISearchHandler.
     */
    virtual search::engine::DocsumReply::UP
    getDocsums(const search::engine::DocsumRequest & request);

    /**
     * @return Use the request and produce the matching result.
     */
    virtual search::engine::SearchReply::UP match(
            const ISearchHandler::SP &searchHandler,
            const search::engine::SearchRequest &req,
            vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton

