// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingsession.h"
#include "groupingmanager.h"
#include "groupingcontext.h"

#include <vespa/log/log.h>
LOG_SETUP(".groupingsession");

namespace search::grouping {

using search::aggregation::Group;
using search::aggregation::Grouping;
using search::aggregation::GroupingLevel;
using search::attribute::IAttributeContext;

GroupingSession::GroupingSession(const SessionId &sessionId,
                                 GroupingContext & groupingContext,
                                 const IAttributeContext &attrCtx)
    : _sessionId(sessionId),
      _mgrContext(std::make_unique<GroupingContext>(groupingContext)),
      _groupingManager(std::make_unique<GroupingManager>(*_mgrContext)),
      _timeOfDoom(groupingContext.getTimeOfDoom())
{
    init(groupingContext, attrCtx);
}

GroupingSession::~GroupingSession() = default;

using search::expression::ExpressionNode;
using search::expression::AttributeNode;
using search::expression::ConfigureStaticParams;
using search::aggregation::Grouping;
using search::aggregation::GroupingLevel;

void
GroupingSession::init(GroupingContext & groupingContext, const IAttributeContext &attrCtx)
{
    GroupingList & sessionList(groupingContext.getGroupingList());
    for (size_t i = 0; i < sessionList.size(); ++i) {
        GroupingPtr g(sessionList[i]);
        // Make internal copy of those we want to keep for another pass
        if (!_sessionId.empty() && g->getLastLevel() < g->levels().size()) {
            auto gp = std::make_shared<Grouping>(*g);
            gp->setLastLevel(gp->levels().size());
            _groupingMap[gp->getId()] = gp;
            g = std::move(gp);
        }
        _mgrContext->addGrouping(std::move(g));
    }
    _groupingManager->init(attrCtx);
}

void
GroupingSession::prepareThreadContextCreation(size_t num_threads)
{
    if (num_threads > 1) {
        _mgrContext->serialize(); // need copy of internal modified request
    }
}

GroupingContext::UP
GroupingSession::createThreadContext(size_t thread_id, const IAttributeContext &attrCtx)
{
    auto ctx = std::make_unique<GroupingContext>(*_mgrContext);
    if (thread_id == 0) {
        for (const auto & grouping : _mgrContext->getGroupingList()) {
            ctx->addGrouping(grouping);
        }
    } else {
        ctx->deserialize(_mgrContext->getResult().peek(), _mgrContext->getResult().size());
        GroupingManager man(*ctx);
        man.init(attrCtx);
    }
    return ctx;
}

void
GroupingSession::continueExecution(GroupingContext & groupingContext)
{
    GroupingList &orig(groupingContext.getGroupingList());
    for (const auto & groupingPtr : orig) {
        Grouping &origGrouping(*groupingPtr);
        auto found = _groupingMap.find(origGrouping.getId());
        if (found != _groupingMap.end()) {
            Grouping &cachedGrouping(*found->second);
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

}
