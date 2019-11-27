// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
class DocsumContext : public search::docsummary::GetDocsumsStateCallback {
private:
    const search::engine::DocsumRequest  & _request;
    search::docsummary::IDocsumWriter    & _docsumWriter;
    search::docsummary::IDocsumStore     & _docsumStore;
    std::shared_ptr<matching::Matcher>     _matcher;
    matching::ISearchContext             & _searchCtx;
    search::attribute::IAttributeContext & _attrCtx;
    search::IAttributeManager            & _attrMgr;
    search::docsummary::GetDocsumsState    _docsumState;
    matching::SessionManager             & _sessionMgr;

    void initState();
    search::engine::DocsumReply::UP createReply();
    std::unique_ptr<vespalib::Slime> createSlimeReply();

public:
    typedef std::unique_ptr<DocsumContext> UP;

    DocsumContext(const search::engine::DocsumRequest & request,
                  search::docsummary::IDocsumWriter & docsumWriter,
                  search::docsummary::IDocsumStore & docsumStore,
                  std::shared_ptr<matching::Matcher> matcher,
                  matching::ISearchContext & searchCtx,
                  search::attribute::IAttributeContext & attrCtx,
                  search::IAttributeManager & attrMgr,
                  matching::SessionManager & sessionMgr);

    search::engine::DocsumReply::UP getDocsums();

    // Implements GetDocsumsStateCallback
    void FillSummaryFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment * env) override;
    void FillRankFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment * env) override;
    void ParseLocation(search::docsummary::GetDocsumsState * state) override;
    std::unique_ptr<search::MatchingElements> fill_matching_elements(const search::StructFieldMapper &struct_field_mapper) override;
};

} // namespace proton

