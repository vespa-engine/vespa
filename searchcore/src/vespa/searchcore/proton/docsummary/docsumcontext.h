// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/docsumreply.h>

namespace proton {

namespace matching {
    class Matcher;
    class ISearchContext;
    class SessionManager;
}

/**
 * The DocsumContext class is responsible for performing a docsum request and
 * creating a docsum reply.
 **/
class DocsumContext : public search::docsummary::GetDocsumsStateCallback,
                      public search::QueryNormalization
{
private:
    const search::engine::DocsumRequest  & _request;
    search::docsummary::IDocsumWriter    & _docsumWriter;
    search::docsummary::IDocsumStore     & _docsumStore;
    std::shared_ptr<matching::Matcher>     _matcher;
    matching::ISearchContext             & _searchCtx;
    search::attribute::IAttributeContext & _attrCtx;
    const search::IAttributeManager      & _attrMgr;
    search::docsummary::GetDocsumsState    _docsumState;
    matching::SessionManager             & _sessionMgr;

    void initState();
    std::unique_ptr<vespalib::Slime> createSlimeReply();

public:
    using UP = std::unique_ptr<DocsumContext>;

    DocsumContext(const search::engine::DocsumRequest & request,
                  search::docsummary::IDocsumWriter & docsumWriter,
                  search::docsummary::IDocsumStore & docsumStore,
                  std::shared_ptr<matching::Matcher> matcher,
                  matching::ISearchContext & searchCtx,
                  search::attribute::IAttributeContext & attrCtx,
                  const search::IAttributeManager & attrMgr,
                  matching::SessionManager & sessionMgr);

    search::engine::DocsumReply::UP getDocsums();

    // Implements GetDocsumsStateCallback
    void fillSummaryFeatures(search::docsummary::GetDocsumsState& state) override;
    void fillRankFeatures(search::docsummary::GetDocsumsState& state) override;
    void fill_matching_elements(search::docsummary::GetDocsumsState& state) override;
    bool is_text_matching(std::string_view index) const noexcept override;
    Normalizing normalizing_mode(std::string_view index) const noexcept override;
};

} // namespace proton
