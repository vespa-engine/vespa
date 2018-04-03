// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprints.h"
#include "andnotsearch.h"
#include "andsearch.h"
#include "orsearch.h"
#include "nearsearch.h"
#include "ranksearch.h"
#include "sourceblendersearch.h"
#include "equivsearch.h"
#include "termwise_blueprint_helper.h"
#include "isourceselector.h"
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>

namespace search::queryeval {

//-----------------------------------------------------------------------------

namespace {

template <typename CombineType>
size_t lookup_create_source(std::vector<std::unique_ptr<CombineType> > &sources, uint32_t child_source) {
    for (size_t i = 0; i < sources.size(); ++i) {
        if (sources[i]->getSourceId() == child_source) {
            return i;
        }
    }
    sources.push_back(std::unique_ptr<CombineType>(new CombineType()));
    sources.back()->setSourceId(child_source);
    return (sources.size() - 1);
}

template <typename CombineType>
void optimize_source_blenders(IntermediateBlueprint &self, size_t begin_idx) {
    std::vector<size_t> source_blenders;
    SourceBlenderBlueprint *reference = nullptr;
    for (size_t i = begin_idx; i < self.childCnt(); ++i) {
        SourceBlenderBlueprint *child = dynamic_cast<SourceBlenderBlueprint *>(&self.getChild(i));
        if (child != nullptr) {
            if (reference == nullptr || reference->isCompatibleWith(*child)) {
                source_blenders.push_back(i);
                reference = child;
            }
        }
    }
    if (source_blenders.size() > 1) { // maybe 2
        Blueprint::UP blender_up;
        std::vector<std::unique_ptr<CombineType> > sources;
        while (!source_blenders.empty()) {
            blender_up = self.removeChild(source_blenders.back());
            source_blenders.pop_back();
            SourceBlenderBlueprint *blender = dynamic_cast<SourceBlenderBlueprint *>(blender_up.get());
            assert(blender != nullptr);
            while (blender->childCnt() > 0) {
                Blueprint::UP child_up = blender->removeChild(blender->childCnt() - 1);
                size_t source_idx = lookup_create_source(sources, child_up->getSourceId());
                sources[source_idx]->addChild(std::move(child_up));
            }
        }
        SourceBlenderBlueprint *top = dynamic_cast<SourceBlenderBlueprint *>(blender_up.get());
        assert(top != nullptr);
        while (!sources.empty()) {
            top->addChild(std::move(sources.back()));
            sources.pop_back();
        }
        blender_up = Blueprint::optimize(std::move(blender_up));
        self.addChild(std::move(blender_up));
    }
}

} // namespace search::queryeval::<unnamed>

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
AndNotBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    if (data.empty()) {
        return HitEstimate();
    }
    return data[0];
}

FieldSpecBaseList
AndNotBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
AndNotBlueprint::optimize_self()
{
    if (childCnt() == 0) {
        return;
    }
    AndNotBlueprint *child = dynamic_cast<AndNotBlueprint *>(&getChild(0));
    if (child != nullptr) {
        while (child->childCnt() > 1) {
            addChild(child->removeChild(1));
        }
        insertChild(1, child->removeChild(0));
        removeChild(0);
    }
    for (size_t i = 1; i < childCnt(); ++i) {
        if (getChild(i).getState().estimate().empty) {
            removeChild(i--);
        }
    }
    if (dynamic_cast<AndNotBlueprint *>(getParent()) == nullptr) {
        optimize_source_blenders<OrBlueprint>(*this, 1);
    }
}

Blueprint::UP
AndNotBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return Blueprint::UP();
}

void
AndNotBlueprint::sort(std::vector<Blueprint*> &children) const
{
    if (children.size() > 2) {
        std::sort(children.begin() + 1, children.end(), GreaterEstimate());
    }
}

bool
AndNotBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndNotBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                          bool strict, search::fef::MatchData &md) const
{
    UnpackInfo unpackInfo(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpackInfo, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, subSearches, unpackInfo);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = (helper.first_termwise == 0)
                               ? SearchIterator::UP(AndNotSearch::create(helper.termwise, termwise_strict))
                               : SearchIterator::UP(OrSearch::create(helper.termwise, termwise_strict));
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        if (helper.children.size() == 1) {
            return SearchIterator::UP(helper.children.front());
        }
        return SearchIterator::UP(AndNotSearch::create(helper.children, strict));
    }
    return SearchIterator::UP(AndNotSearch::create(subSearches, strict));
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
AndBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
AndBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
AndBlueprint::optimize_self()
{
    for (size_t i = 0; i < childCnt(); ++i) {
        AndBlueprint *child = dynamic_cast<AndBlueprint *>(&getChild(i));
        if (child != nullptr) {
            while (child->childCnt() > 0) {
                addChild(child->removeChild(0));
            }
            removeChild(i--);
        }
    }
    if (dynamic_cast<AndBlueprint *>(getParent()) == nullptr) {
        optimize_source_blenders<AndBlueprint>(*this, 0);
    }
}

Blueprint::UP
AndBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return Blueprint::UP();
}

void
AndBlueprint::sort(std::vector<Blueprint*> &children) const
{
    std::sort(children.begin(), children.end(), LessEstimate());
}

bool
AndBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                         bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpackInfo(calculateUnpackInfo(md));
    AndSearch * search = 0;
    if (should_do_termwise_eval(unpackInfo, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, subSearches, unpackInfo);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = SearchIterator::UP(AndSearch::create(helper.termwise, termwise_strict));
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        if (helper.children.size() == 1) {
            return SearchIterator::UP(helper.children.front());
        } else {
            search = AndSearch::create(helper.children, strict, helper.termwise_unpack);
        }
    } else {
        search = AndSearch::create(subSearches, strict, unpackInfo);
    }
    search->estimate(getState().estimate().estHits);
    return SearchIterator::UP(search);
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
OrBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return max(data);
}

FieldSpecBaseList
OrBlueprint::exposeFields() const
{
    return mixChildrenFields();
}

void
OrBlueprint::optimize_self()
{
    for (size_t i = 0; (childCnt() > 1) && (i < childCnt()); ++i) {
        OrBlueprint *child = dynamic_cast<OrBlueprint *>(&getChild(i));
        if (child != nullptr) {
            while (child->childCnt() > 0) {
                addChild(child->removeChild(0));
            }
            removeChild(i--);
        } else if (getChild(i).getState().estimate().empty) {
            removeChild(i--);
        }
    }
    if (dynamic_cast<OrBlueprint *>(getParent()) == nullptr) {
        optimize_source_blenders<OrBlueprint>(*this, 0);
    }
}

Blueprint::UP
OrBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return Blueprint::UP();
}

void
OrBlueprint::sort(std::vector<Blueprint*> &children) const
{
    std::sort(children.begin(), children.end(), GreaterEstimate());
}

bool
OrBlueprint::inheritStrict(size_t) const
{
    return true;
}

SearchIterator::UP
OrBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                      bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpackInfo(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpackInfo, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, subSearches, unpackInfo);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = SearchIterator::UP(OrSearch::create(helper.termwise, termwise_strict));
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        if (helper.children.size() == 1) {
            return SearchIterator::UP(helper.children.front());
        }
        return SearchIterator::UP(OrSearch::create(helper.children, strict, helper.termwise_unpack));
    }
    return SearchIterator::UP(OrSearch::create(subSearches, strict, unpackInfo));
}

//-----------------------------------------------------------------------------
WeakAndBlueprint::~WeakAndBlueprint() {}

Blueprint::HitEstimate
WeakAndBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    HitEstimate childEst = max(data);
    HitEstimate myEst(_n, false);
    if (childEst < myEst) {
        return childEst;
    }
    return myEst;
}

FieldSpecBaseList
WeakAndBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
WeakAndBlueprint::sort(std::vector<Blueprint*> &) const
{
    // order needs to stay the same as _weights
}

bool
WeakAndBlueprint::inheritStrict(size_t) const
{
    return true;
}

SearchIterator::UP
WeakAndBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                           bool strict, search::fef::MatchData &) const
{
    WeakAndSearch::Terms terms;
    assert(subSearches.size() == childCnt());
    assert(_weights.size() == childCnt());
    for (size_t i = 0; i < subSearches.size(); ++i) {
        terms.push_back(wand::Term(subSearches[i],
                                   _weights[i],
                                   getChild(i).getState().estimate().estHits));
    }
    return SearchIterator::UP(WeakAndSearch::create(terms, _n, strict));
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
NearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
NearBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
NearBlueprint::sort(std::vector<Blueprint*> &children) const
{
    std::sort(children.begin(), children.end(), LessEstimate());
}

bool
NearBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
NearBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                        bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    return SearchIterator::UP(new NearSearch(subSearches, tfmda, _window, strict));
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
ONearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
ONearBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
ONearBlueprint::sort(std::vector<Blueprint*> &children) const
{
    // ordered near cannot sort children here
    (void)children;
}

bool
ONearBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
ONearBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                         bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    // could sort subSearches here
    // but then strictness inheritance would also need to be fixed
    return SearchIterator::UP(new ONearSearch(subSearches, tfmda, _window, strict));
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
RankBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    if (data.empty()) {
        return HitEstimate();
    }
    return data[0];
}

FieldSpecBaseList
RankBlueprint::exposeFields() const
{
    return FieldSpecBaseList();
}

void
RankBlueprint::optimize_self()
{
    for (size_t i = 1; i < childCnt(); ++i) {
        if (getChild(i).getState().estimate().empty) {
            removeChild(i--);
        }
    }
    optimize_source_blenders<OrBlueprint>(*this, 1);
}

Blueprint::UP
RankBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return Blueprint::UP();
}

void
RankBlueprint::sort(std::vector<Blueprint*> &children) const
{
    (void)children;
}

bool
RankBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
RankBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                        bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpackInfo(calculateUnpackInfo(md));
    if (unpackInfo.unpackAll()) {
        return SearchIterator::UP(RankSearch::create(subSearches, strict));
    } else {
        MultiSearch::Children requireUnpack;
        requireUnpack.reserve(subSearches.size());
        requireUnpack.push_back(subSearches[0]);
        for (size_t i(1); i < subSearches.size(); i++) {
            if (unpackInfo.needUnpack(i)) {
                requireUnpack.push_back(subSearches[i]);
            } else {
                delete subSearches[i];
            }
        }
        if (requireUnpack.size() == 1) {
            return SearchIterator::UP(requireUnpack[0]);
        } else {
            return SearchIterator::UP(RankSearch::create(requireUnpack, strict));
        }
    }
}

//-----------------------------------------------------------------------------

SourceBlenderBlueprint::SourceBlenderBlueprint(const ISourceSelector &selector)
    : _selector(selector)
{
}

Blueprint::HitEstimate
SourceBlenderBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return max(data);
}

FieldSpecBaseList
SourceBlenderBlueprint::exposeFields() const
{
    return mixChildrenFields();
}

void
SourceBlenderBlueprint::sort(std::vector<Blueprint*> &) const
{
}

bool
SourceBlenderBlueprint::inheritStrict(size_t) const
{
    return true;
}

class FindSource : public Blueprint::IPredicate
{
public:
    FindSource(uint32_t sourceId) : _sourceId(sourceId) { }
    bool check(const Blueprint & bp) const override { return bp.getSourceId() == _sourceId; }
private:
    uint32_t _sourceId;
};

ssize_t
SourceBlenderBlueprint::findSource(uint32_t sourceId) const
{
    ssize_t index(-1);
    FindSource fs(sourceId);
    IndexList list = find(fs);
    if ( ! list.empty()) {
        index = list.front();
    }
    return index;
}

SearchIterator::UP
SourceBlenderBlueprint::createIntermediateSearch(const MultiSearch::Children &subSearches,
                                                 bool strict, search::fef::MatchData &) const
{
    SourceBlenderSearch::Children children;
    assert(subSearches.size() == childCnt());
    for (size_t i = 0; i < subSearches.size(); ++i) {
        children.push_back(SourceBlenderSearch::Child(subSearches[i],
                                                      getChild(i).getSourceId()));
        assert(children.back().sourceId != 0xffffffff);
    }
    return SearchIterator::UP(SourceBlenderSearch::create(_selector.createIterator(),
                                                  children, strict));
}

bool
SourceBlenderBlueprint::isCompatibleWith(const SourceBlenderBlueprint &other) const
{
    return (&_selector == &other._selector);
}

//-----------------------------------------------------------------------------

}
