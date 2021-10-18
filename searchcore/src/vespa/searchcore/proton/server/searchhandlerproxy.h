// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/vespalib/util/retain_guard.h>

namespace proton {

class DocumentDB;

class SearchHandlerProxy : public ISearchHandler
{
private:
    std::shared_ptr<DocumentDB> _documentDB;
    vespalib::RetainGuard       _retainGuard;
public:
    SearchHandlerProxy(std::shared_ptr<DocumentDB> documentDB);

    ~SearchHandlerProxy() override;
    std::unique_ptr<DocsumReply> getDocsums(const DocsumRequest & request) override;
    std::unique_ptr<SearchReply> match(const SearchRequest &req, ThreadBundle &threadBundle) const override;
};

} // namespace proton

