// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingsession.h"

#include "groupingcontext.h"
#include "groupingmanager.h"

#include <vespa/log/log.h>
LOG_SETUP(".groupingsession");

namespace search::grouping {

using search::aggregation::AggregationResult;
using search::aggregation::Grouping;
using search::attribute::IAttributeContext;

GroupingSession::GroupingSession(const SessionId& sessionId, GroupingContext& groupingContext,
                                 const IAttributeContext& attrCtx, const document::DocumentType* documentType)
    : _sessionId(sessionId),
      _mgrContext(std::make_unique<GroupingContext>(groupingContext)),
      _groupingManager(std::make_unique<GroupingManager>(*_mgrContext)),
      _timeOfDoom(groupingContext.getTimeOfDoom()) {
    init(groupingContext, attrCtx, documentType);
}

GroupingSession::~GroupingSession() = default;

void GroupingSession::init(GroupingContext& groupingContext, const IAttributeContext& attrCtx,
                           const document::DocumentType* documentType) {
    GroupingList& requestList(groupingContext.getGroupingList());
    for (auto g : requestList) {
        // Groupings that need more than this pass to finish get a session copy: it is the one
        // that actually gets grouped/aggregated into (via _mgrContext/_groupingManager below) and
        // is cached in _groupingMap for use by later passes. The original stays behind, untouched,
        // as the element still in requestList (and hence in the request itself); that original is
        // what continueExecution() later merges the session copy's results into.
        if (!_sessionId.empty() && g->getLastLevel() < g->levels().size()) {
            auto sessionCopy = std::make_shared<Grouping>(*g);
            sessionCopy->setLastLevel(sessionCopy->levels().size());
            _groupingMap[sessionCopy->getId()] = sessionCopy;
            g = std::move(sessionCopy);
        }
        _mgrContext->addGrouping(std::move(g));
    }
    _groupingManager->init(attrCtx, documentType);
    prepareMergeTargets(requestList);
}

void GroupingSession::prepareMergeTargets(const GroupingList& requestList) {
    // The groupings we made a session copy of above are left behind in the request, where
    // continueExecution() will use them as merge target. They are not part of _mgrContext, so the
    // init() above did not touch them and their aggregation results are still default-constructed.
    // Prepare them here: they share their expression trees with the session copies, which init()
    // just configured, so the result nodes get the type the expression actually produces.
    AggregationResult::Configure aggrConf;
    for (const auto& g : requestList) {
        if (_groupingMap.contains(g->getId())) {
            g->select(aggrConf, aggrConf);
        }
    }
}

void GroupingSession::prepareThreadContextCreation(size_t num_threads) {
    if (num_threads > 1) {
        _mgrContext->serialize(); // need copy of internal modified request
    }
}

GroupingContext::UP GroupingSession::createThreadContext(size_t thread_id, const IAttributeContext& attrCtx,
                                                         const document::DocumentType* documentType) {
    auto ctx = std::make_unique<GroupingContext>(*_mgrContext);
    if (thread_id == 0) {
        for (const auto& grouping : _mgrContext->getGroupingList()) {
            ctx->addGrouping(grouping);
        }
    } else {
        ctx->deserialize(_mgrContext->getResult().peek(), _mgrContext->getResult().size());
        GroupingManager man(*ctx);
        man.init(attrCtx, documentType);
    }
    return ctx;
}

void GroupingSession::continueExecution(GroupingContext& groupingContext) {
    GroupingList& orig(groupingContext.getGroupingList());
    for (const auto& groupingPtr : orig) {
        Grouping& origGrouping(*groupingPtr);
        auto      found = _groupingMap.find(origGrouping.getId());
        if (found != _groupingMap.end()) {
            Grouping& cachedGrouping(*found->second);
            cachedGrouping.prune(origGrouping);
            origGrouping.mergePartial(cachedGrouping);
            // No use in keeping it for the next round
            if (origGrouping.getLastLevel() == cachedGrouping.getLastLevel()) {
                _groupingMap.erase(origGrouping.getId());
            }
        }
        LOG(debug, "Continue execution result: %s", origGrouping.asString().c_str());
    }
    groupingContext.serialize();
}

} // namespace search::grouping
