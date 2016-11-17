// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace proton {

class DocumentDB;

class SearchHandlerProxy : public ISearchHandler
{
private:
    std::shared_ptr<DocumentDB> _documentDB;
public:
    SearchHandlerProxy(const std::shared_ptr<DocumentDB> &documentDB);

    virtual~SearchHandlerProxy();
    std::unique_ptr<DocsumReply> getDocsums(const DocsumRequest & request) override;
    std::unique_ptr<SearchReply> match(const ISearchHandler::SP &searchHandler, const SearchRequest &req, ThreadBundle &threadBundle) const override;
};

} // namespace proton

