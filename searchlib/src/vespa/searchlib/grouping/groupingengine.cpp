// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingengine.h"
#include "groupandcollectengine.h"
#include <cassert>

namespace search {

using namespace aggregation;
using namespace expression;

namespace grouping {

GroupingEngine::GroupingEngine(Grouping & request) :
    _request(request),
    _levels(),
    _rootRequestLevel()
{
    const Grouping::GroupingLevelList & gll(request.getLevels());
    assert(request.getLastLevel() <= gll.size());
    bool collectLastLevel(request.getLastLevel() == gll.size());
    _levels.resize(request.getLastLevel() + ((gll.size()==request.getLastLevel()) ? 0 : 1) + 1); // 1 for inclusive, 1 for artificial root
    GroupEngine * nextEngine(NULL);
    for (size_t i(_levels.size()); i-- > 1; ) {
        const GroupingLevel & l = gll[i-1];
        if (i > request.getFirstLevel()) {
            if ((i-1) == request.getLastLevel()) {
                if (collectLastLevel) {
                    _levels[i] = new GroupAndCollectEngine(&l, i, nextEngine, false);
                } else {
                    _levels[i] = new GroupEngine(&l, i, nextEngine, false);
                }
            } else {
                _levels[i] = new GroupAndCollectEngine(&l, i, nextEngine, false);
            }
        } else {
            // This should be a frozen level
            if (i == request.getFirstLevel()) {
                _levels[i] = new GroupAndCollectEngine(&l, i, nextEngine, true);
            } else {
                _levels[i] = new GroupEngine(&l, i, nextEngine, true);
            }
        }
        nextEngine = _levels[i];
    }

    fillRootRequest(request.getRoot());
    if (0 >= request.getFirstLevel()) {
        _levels[0] = new GroupAndCollectEngine(&_rootRequestLevel, 0, nextEngine, true);
    } else {
        _levels[0] = new GroupEngine(&_rootRequestLevel, 0, nextEngine, true);
    }
    preFillEngines(request.getRoot(), request.getFirstLevel());
}

void
GroupingEngine::preFillEngines(const Group & r, size_t levels)
{
    if (_levels.size() > levels) {
        _levels[0]->preFillEngine(r, levels);
    }
}

void
GroupingEngine::fillRootRequest(const Group & r)
{
    _rootRequestLevel.setMaxGroups(1).setPresicion(1).freeze();
    for (size_t i(0), m(r.getAggrSize()); i < m; i++) {
        _rootRequestLevel.addResult(ExpressionNode::UP(r.getAggregationResult(i).clone()));
    }
}

GroupingEngine::~GroupingEngine()
{
    for (size_t i(0); i < _levels.size(); i++) {
        delete _levels[i];
        _levels[i] = 0;
    }
}

void
GroupingEngine::aggregate(const RankedHit * rankedHit, unsigned int len)
{
    _request.preAggregate( ! _request.needResort());
    if ( ! _levels.empty() ) {
        len = _request.getMaxN(len);
        for (size_t i(0); i < len; i++) {
            const RankedHit & r(rankedHit[i]);
            _levels[0]->group(r.getDocId(), r.getRank());
        }
    }
    _request.postAggregate();
}

Group::UP
GroupingEngine::createResult() const
{
    return _levels[0]->getRootGroup();
}

void GroupingEngine::merge(const GroupingEngine & b)
{
    _levels[0]->merge(*b._levels[0]);
}

}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_grouping_groupingengine() {}
