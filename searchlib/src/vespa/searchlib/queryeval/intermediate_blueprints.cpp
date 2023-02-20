// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprints.h"
#include "andnotsearch.h"
#include "andsearch.h"
#include "orsearch.h"
#include "nearsearch.h"
#include "ranksearch.h"
#include "sourceblendersearch.h"
#include "termwise_blueprint_helper.h"
#include "isourceselector.h"
#include "field_spec.hpp"
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>

namespace search::queryeval {

//-----------------------------------------------------------------------------

namespace {

template <typename CombineType>
size_t lookup_create_source(std::vector<std::unique_ptr<CombineType> > &sources, uint32_t child_source, uint32_t docid_limit) {
    for (size_t i = 0; i < sources.size(); ++i) {
        if (sources[i]->getSourceId() == child_source) {
            return i;
        }
    }
    sources.push_back(std::unique_ptr<CombineType>(new CombineType()));
    sources.back()->setSourceId(child_source);
    sources.back()->setDocIdLimit(docid_limit);
    return (sources.size() - 1);
}

template <typename CombineType>
void optimize_source_blenders(IntermediateBlueprint &self, size_t begin_idx) {
    std::vector<size_t> source_blenders;
    SourceBlenderBlueprint *reference = nullptr;
    for (size_t i = begin_idx; i < self.childCnt(); ++i) {
        if (self.getChild(i).isSourceBlender()) {
            auto *child = static_cast<SourceBlenderBlueprint *>(&self.getChild(i));
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
            assert(blender_up->isSourceBlender());
            auto *blender = static_cast<SourceBlenderBlueprint *>(blender_up.get());
            while (blender->childCnt() > 0) {
                Blueprint::UP child_up = blender->removeChild(blender->childCnt() - 1);
                size_t source_idx = lookup_create_source(sources, child_up->getSourceId(), self.get_docid_limit());
                sources[source_idx]->addChild(std::move(child_up));
            }
        }
        assert(blender_up->isSourceBlender());
        auto *top = static_cast<SourceBlenderBlueprint *>(blender_up.get());
        while (!sources.empty()) {
            top->addChild(std::move(sources.back()));
            sources.pop_back();
        }
        blender_up = Blueprint::optimize(std::move(blender_up));
        self.addChild(std::move(blender_up));
    }
}

void
need_normal_features_for_children(const IntermediateBlueprint &blueprint, fef::MatchData &md)
{
    for (size_t i = 0; i < blueprint.childCnt(); ++i) {
        const Blueprint::State &cs = blueprint.getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            auto *tfmd = cs.field(j).resolve(md);
            if (tfmd != nullptr) {
                tfmd->setNeedNormalFeatures(true);
            }
        }
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
    if (getChild(0).isAndNot()) {
        auto *child = static_cast<AndNotBlueprint *>(&getChild(0));
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
    if ( !(getParent() && getParent()->isAndNot()) ) {
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
AndNotBlueprint::sort(Children &children) const
{
    if (children.size() > 2) {
        std::sort(children.begin() + 1, children.end(), TieredGreaterEstimate());
    }
}

bool
AndNotBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndNotBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                          bool strict, search::fef::MatchData &md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = (helper.first_termwise == 0)
                               ? AndNotSearch::create(helper.get_termwise_children(), termwise_strict)
                               : OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return AndNotSearch::create(std::move(rearranged), strict);
    }
    return AndNotSearch::create(std::move(sub_searches), strict);
}

SearchIterator::UP
AndNotBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_andnot_filter(get_children(), strict, constraint);
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
        if (getChild(i).isAnd()) {
            auto *child = static_cast<AndBlueprint *>(&getChild(i));
            while (child->childCnt() > 0) {
                addChild(child->removeChild(0));
            }
            removeChild(i--);
        }
    }
    if ( !(getParent() && getParent()->isAnd()) ) {
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
AndBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredLessEstimate());
}

bool
AndBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                       bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    std::unique_ptr<AndSearch> search;
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = AndSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        } else {
            search = AndSearch::create(std::move(rearranged), strict, helper.termwise_unpack);
        }
    } else {
        search = AndSearch::create(std::move(sub_searches), strict, unpack_info);
    }
    search->estimate(getState().estimate().estHits);
    return search;
}

SearchIterator::UP
AndBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_and_filter(get_children(), strict, constraint);
}

double
AndBlueprint::computeNextHitRate(const Blueprint & child, double hitRate) const {
    return hitRate * child.hit_ratio();
}

//-----------------------------------------------------------------------------

OrBlueprint::~OrBlueprint() = default;

Blueprint::HitEstimate
OrBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return sat_sum(data, get_docid_limit());
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
        if (getChild(i).isOr()) {
            auto *child = static_cast<OrBlueprint *>(&getChild(i));
            while (child->childCnt() > 0) {
                addChild(child->removeChild(0));
            }
            removeChild(i--);
        } else if (getChild(i).getState().estimate().empty) {
            removeChild(i--);
        }
    }
    if ( !(getParent() && getParent()->isOr()) ) {
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
OrBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredGreaterEstimate());
}

bool
OrBlueprint::inheritStrict(size_t) const
{
    return true;
}

SearchIterator::UP
OrBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                      bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return OrSearch::create(std::move(rearranged), strict, helper.termwise_unpack);
    }
    return OrSearch::create(std::move(sub_searches), strict, unpack_info);
}

SearchIterator::UP
OrBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_or_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------
WeakAndBlueprint::~WeakAndBlueprint() = default;

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
WeakAndBlueprint::sort(Children &) const
{
    // order needs to stay the same as _weights
}

bool
WeakAndBlueprint::inheritStrict(size_t) const
{
    return true;
}

bool
WeakAndBlueprint::always_needs_unpack() const
{
    return true;
}

SearchIterator::UP
WeakAndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                           bool strict, search::fef::MatchData &) const
{
    WeakAndSearch::Terms terms;
    assert(sub_searches.size() == childCnt());
    assert(_weights.size() == childCnt());
    for (size_t i = 0; i < sub_searches.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        terms.push_back(wand::Term(sub_searches[i].release(),
                                   _weights[i],
                                   getChild(i).getState().estimate().estHits));
    }
    return WeakAndSearch::create(terms, _n, strict);
}

SearchIterator::UP
WeakAndBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict, constraint);
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
NearBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredLessEstimate());
}

bool
NearBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
NearBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md, strict);
}

SearchIterator::UP
NearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    return SearchIterator::UP(new NearSearch(std::move(sub_searches), tfmda, _window, strict));
}

SearchIterator::UP
NearBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict, constraint);
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
ONearBlueprint::sort(Children &children) const
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
ONearBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md, strict);
}

SearchIterator::UP
ONearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                         bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    // could sort sub_searches here
    // but then strictness inheritance would also need to be fixed
    return SearchIterator::UP(new ONearSearch(std::move(sub_searches), tfmda, _window, strict));
}

SearchIterator::UP
ONearBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict, constraint);
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
RankBlueprint::sort(Children &children) const
{
    (void)children;
}

bool
RankBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
RankBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (unpack_info.unpackAll()) {
        return RankSearch::create(std::move(sub_searches), strict);
    } else {
        MultiSearch::Children require_unpack;
        require_unpack.reserve(sub_searches.size());
        require_unpack.push_back(std::move(sub_searches[0]));
        for (size_t i(1); i < sub_searches.size(); i++) {
            if (unpack_info.needUnpack(i)) {
                require_unpack.push_back(std::move(sub_searches[i]));
            } else {
                sub_searches[i].reset();
            }
        }
        if (require_unpack.size() == 1) {
            return SearchIterator::UP(std::move(require_unpack[0]));
        } else {
            return RankSearch::create(std::move(require_unpack), strict);
        }
    }
}

SearchIterator::UP
RankBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_first_child_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

SourceBlenderBlueprint::SourceBlenderBlueprint(const ISourceSelector &selector)
    : _selector(selector)
{
}

SourceBlenderBlueprint::~SourceBlenderBlueprint() = default;

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
SourceBlenderBlueprint::sort(Children &) const
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
SourceBlenderBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                                 bool strict, search::fef::MatchData &) const
{
    SourceBlenderSearch::Children children;
    assert(sub_searches.size() == childCnt());
    for (size_t i = 0; i < sub_searches.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        children.push_back(SourceBlenderSearch::Child(sub_searches[i].release(),
                                                      getChild(i).getSourceId()));
        assert(children.back().sourceId != 0xffffffff);
    }
    return SourceBlenderSearch::create(_selector.createIterator(),
                                       children, strict);
}

SearchIterator::UP
SourceBlenderBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict, constraint);
}

bool
SourceBlenderBlueprint::isCompatibleWith(const SourceBlenderBlueprint &other) const
{
    return (&_selector == &other._selector);
}

//-----------------------------------------------------------------------------

}
