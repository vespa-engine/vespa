// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupengine.h"
#include <vespa/searchlib/expression/nullresultnode.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <cassert>

using namespace search::expression;
using namespace search::aggregation;

namespace search::grouping {

GroupEngine::GroupEngine(const GroupingLevel * request, size_t level, GroupEngine * nextEngine, bool frozen) :
    Collect(request->getGroupPrototype()),
    _request(request),
    _nextEngine(nextEngine),
    _idByteSize(0),
    _ids(),
    _idScratch(),
    _rank(),
    _groupBacking(),
    _level(level),
    _frozen(frozen)
{
    if ((request != NULL) && (level > 0)) {
        _idScratch.reset(request->getExpression().getResult()->clone());
    } else {
        _idScratch.reset(new NullResultNode());
    }
    _idByteSize = _idScratch->getRawByteSize();
}

GroupEngine::~GroupEngine()
{
    if (_idByteSize) {
        for (size_t i(0), m(_ids.size()/_idByteSize); i < m; i++) {
            _idScratch->destroy(&_ids[getIdBase(GroupRef(i))]);
        }
    }
    for (size_t i(0), m(_groupBacking.size()); i < m; i++) {
        delete _groupBacking[i];
    }
}

GroupRef GroupEngine::group(Children & children, uint32_t docId, double rank)
{
    const ExpressionTree &selector = _request->getExpression();
    if (!selector.execute(docId, rank)) {
        throw std::runtime_error("Does not know how to handle failed select statements");
    }
    const ResultNode &selectResult = *selector.getResult();
    Children::iterator found = children.find(selectResult);
    GroupRef gr;
    if (found == children.end()) {
        if (_request->allowMoreGroups(children.size())) {
            gr = createGroup(selectResult);
            _rank.push_back(rank);
            children.insert(gr);
        } else {
            return gr;
        }
    } else {
        gr = *found;
    }

    if (_nextEngine != NULL) {
        _nextEngine->group(*_groupBacking[gr], docId, rank);
    }

    return gr;
}

void GroupEngine::group(uint32_t docId, double rank)
{
    if (_nextEngine != NULL) {
        _nextEngine->group(*_groupBacking[0], docId, rank);
    }
}

void GroupEngine::merge(Children &, const GroupEngine &)
{
}

void GroupEngine::merge(const GroupEngine & b)
{
    if (_nextEngine != NULL) {
        _nextEngine->merge(*_groupBacking[0], *b._nextEngine);
    }
}

std::unique_ptr<GroupEngine::Children>
GroupEngine::createChildren() {
    return std::unique_ptr<Children>(new Children(0, GroupHash(*this), GroupEqual(*this)));
}

#if 0
int GroupEngine::cmpRank(GroupRef a, GroupRef b) const
{
#if 0
    return cmpAggr(a, b);
#else
#if 0
    int diff(cmpAggr(a, b));
    return diff
               ? diff
               : ((_rank[a] > _rank[b])
                   ? -1
                   : ((_rank[a] < _rank[b]) ? 1 : 0));
#else
    return (_rank[a] > _rank[b])
               ? -1
               : ((_rank[a] < _rank[b]) ? 1 : 0);
#endif
#endif
}
#endif

GroupRef GroupEngine::createGroup(const search::expression::ResultNode & v)
{
    GroupRef gr(_idByteSize ? _ids.size()/_idByteSize : 0);
    _ids.resize(getIdBase(GroupRef(gr + 1)));
    uint8_t * base(&_ids[getIdBase(gr)]);
    v.create(base);
    v.encode(base);
    if (_nextEngine != NULL) {
        _groupBacking.push_back(_nextEngine->createChildren().release());
    }
    return gr;
}

GroupRef
GroupEngine::createFullGroup(const search::expression::ResultNode & v)
{
    GroupRef gr(GroupEngine::createGroup(v));
    createCollectors(gr);
    return gr;
}

namespace {
class RadixAccess {
public:
    RadixAccess(const uint64_t * v) : _radix(v) { }
    uint64_t operator () (size_t i) const { return _radix[i]; }
private:
    const uint64_t * _radix;
};
}

Group::UP GroupEngine::getGroup(GroupRef ref) const
{
    Group::UP p(new Group(_request->getGroupPrototype()));
    Group & g(*p);
    g.setId(getGroupId(ref));
    g.setRank(_rank[ref]);
    if (_nextEngine != NULL) {
        const Children & ch(*_groupBacking[ref]);
        std::vector<GroupRef> v(ch.size());
        {
            size_t i(0);
            for (Children::const_iterator it(ch.begin()), mt(ch.end()); it != mt; it++) {
                v[i++] = *it;
            }
        }
        uint64_t maxN(_nextEngine->_request->getPrecision());
        if (maxN < v.size()) {
#if 0
            std::sort(v.begin(), v.end(), GroupRankLess(*_nextEngine));
#else
            size_t radixSorted;
            if (_nextEngine->hasSpecifiedOrder()) {
                uint64_t * radixCache = new uint64_t[v.size()];
                if (_nextEngine->isPrimarySortKeyAscending()) {
                    for (size_t i(0); i < v.size(); i++) {
                        radixCache[i] = _nextEngine->radixAggrAsc(GroupRef(i));
                    }
                } else {
                    for (size_t i(0); i < v.size(); i++) {
                        radixCache[i] = _nextEngine->radixAggrDesc(GroupRef(i));
                    }
                }
                radixSorted = ShiftBasedRadixSorter<GroupRef, RadixAccess, GroupRankLess, 56>::
                    radix_sort(RadixAccess(radixCache), GroupRankLess(*_nextEngine), &v[0], v.size(), 16, maxN);
                delete [] radixCache;
            } else {
                radixSorted = ShiftBasedRadixSorter<GroupRef, GroupRankRadix, GroupRankLess, 56>::
                    radix_sort(GroupRankRadix(*_nextEngine), GroupRankLess(*_nextEngine), &v[0], v.size(), 16, maxN);
            }
            assert(radixSorted >= maxN);
            assert(radixSorted <= v.size());
            v.resize(radixSorted);
            std::sort(v.begin(), v.end(), GroupRankLess(*_nextEngine));
#endif
            v.resize(maxN);
        }
        std::sort(v.begin(), v.end(), GroupIdLess(*_nextEngine));
        for (size_t i(0); i < v.size(); i++) {
            g.addChild(_nextEngine->getGroup(v[i]));
        }
    }
    getCollectors(ref, g);
    return p;
}

GroupRef
GroupEngine::preFillEngine(const Group & r, size_t depth)
{
    GroupRef gr;
    if (depth >= _level) {
        gr = (r.hasId())
                 ? createFullGroup(r.getId())
                 : createFullGroup(NullResultNode());
        _rank.push_back(r.getRank());
        if (_nextEngine != NULL) {
            Children & ch(*_groupBacking[gr]);
            for (size_t i(0), m(r.getChildrenSize()); i < m; i++) {
                GroupRef tmp = _nextEngine->preFillEngine(r.getChild(i), depth);
                if (tmp.valid()) {
                    ch.insert(tmp);
                }
            }
        }
        preFill(gr, r);
    }
    return gr;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_grouping_groupengine() {}
