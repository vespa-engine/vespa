// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/matchengine/imatchhandler.h>

namespace proton {

class DocumentDB;

class MatchHandlerProxy : public IMatchHandler
{
private:
    std::shared_ptr<DocumentDB> _documentDB;
public:
    MatchHandlerProxy(const std::shared_ptr<DocumentDB> &documentDB);
    virtual~MatchHandlerProxy();

    std::unique_ptr<SearchReply>
    match(const ISearchHandler::SP &searchHandler, const SearchRequest &req, ThreadBundle &threadBundle) const override;
};

} // namespace proton

