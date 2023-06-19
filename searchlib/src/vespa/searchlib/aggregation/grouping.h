// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "groupinglevel.h"
#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/vespalib/util/clock.h>

namespace search {
    class BitVector;
    struct IDocumentMetaStore;
}

namespace search::aggregation {

/**
 * This class represents a top-level grouping request.
 **/
class Grouping : public vespalib::Identifiable
{
public:
    using GroupingLevelList = std::vector<GroupingLevel>;
    using UP = std::unique_ptr<Grouping>;

private:
    uint32_t                 _id;         // client id for this grouping
    bool                     _valid;      // is this grouping object valid?
    bool                     _all;        // if true, group all document, not just hits (streaming only)
    int64_t                  _topN;       // hits to process per search node
    uint32_t                 _firstLevel; // first processing level this iteration (levels before considered frozen)
    uint32_t                 _lastLevel;  // last processing level this iteration
    GroupingLevelList        _levels;     // grouping parameters per level
    Group                    _root;       // the grouping tree
    const vespalib::Clock   *_clock;      // An optional clock to be used for timeout handling.
    vespalib::steady_time    _timeOfDoom; // Used if clock is specified. This is time when request expires.

    bool hasExpired() const { return _clock->getTimeNS() > _timeOfDoom; }
    void aggregateWithoutClock(const RankedHit * rankedHit, unsigned int len);
    void aggregateWithClock(const RankedHit * rankedHit, unsigned int len);
    void postProcess();
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, Grouping);
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    Grouping() noexcept;
    Grouping(const Grouping &);
    Grouping & operator = (const Grouping &);
    Grouping(Grouping &&) noexcept = default;
    Grouping & operator = (Grouping &&) noexcept = default;
    ~Grouping() override;

    Grouping unchain() const { return *this; }

    Grouping &setId(unsigned int i)             { _id = i;                  return *this; }
    Grouping &invalidate()                      { _valid = false;           return *this; }
    Grouping &setAll(bool v)                    { _all = v;                 return *this; }
    Grouping &setTopN(int64_t v)                { _topN = v;                return *this; }
    Grouping &setFirstLevel(unsigned int level) { _firstLevel = level;      return *this; }
    Grouping &setLastLevel(unsigned int level)  { _lastLevel = level;       return *this; }
    Grouping &addLevel(GroupingLevel && level)  { _levels.push_back(std::move(level)); return *this; }
    Grouping &setRoot(const Group &root_)       { _root = root_;            return *this; }
    Grouping &setClock(const vespalib::Clock * clock) { _clock = clock; return *this; }
    Grouping &setTimeOfDoom(vespalib::steady_time timeOfDoom) { _timeOfDoom = timeOfDoom; return *this; }

    unsigned int getId()     const noexcept { return _id; }
    bool valid()             const noexcept { return _valid; }
    bool getAll()            const noexcept { return _all; }
    int64_t getTopN()        const noexcept { return _topN; }
    size_t getMaxN(size_t n) const noexcept { return std::min(n, static_cast<size_t>(getTopN())); }
    uint32_t getFirstLevel() const noexcept { return _firstLevel; }
    uint32_t getLastLevel()  const noexcept { return _lastLevel; }
    const GroupingLevelList &getLevels() const noexcept { return _levels; }
    const Group &getRoot()   const noexcept { return _root; }
    bool needResort() const;

    GroupingLevelList &levels() noexcept { return _levels; }
    Group &root() noexcept { return _root; }

    void selectMembers(const vespalib::ObjectPredicate &predicate,
                       vespalib::ObjectOperation &operation) override;

    void merge(Grouping & b);
    void mergePartial(const Grouping & b);
    void postMerge();
    void preAggregate(bool isOrdered);
    void prune(const Grouping & b);
    void aggregate(DocId from, DocId to);
    void aggregate(const RankedHit * rankedHit, unsigned int len);
    void aggregate(const RankedHit * rankedHit, unsigned int len, const BitVector * bVec);
    void aggregate(DocId docId, HitRank rank = 0);
    void aggregate(const document::Document & doc, HitRank rank = 0);
    void convertToGlobalId(const IDocumentMetaStore &metaStore);
    void postAggregate();
    void sortById();
    void cleanTemporary();
    void configureStaticStuff(const expression::ConfigureStaticParams & params);
    void cleanupAttributeReferences();
};

}
