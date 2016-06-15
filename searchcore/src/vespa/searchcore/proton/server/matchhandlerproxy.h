// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/matchengine/imatchhandler.h>
#include "documentdb.h"

namespace proton {

class MatchHandlerProxy : public boost::noncopyable,
                          public IMatchHandler
{
private:
    DocumentDB::SP _documentDB;
public:
    MatchHandlerProxy(const DocumentDB::SP &documentDB);

    virtual
    ~MatchHandlerProxy(void);

    /**
     * Implements IMatchHandler.
     */
    virtual search::engine::SearchReply::UP
    match(const ISearchHandler::SP &searchHandler,
          const search::engine::SearchRequest &req,
          vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton

