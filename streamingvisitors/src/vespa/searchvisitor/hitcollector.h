// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vespalib/stllike/string.h>

namespace search { namespace fef { class FeatureResolver; } }

namespace storage {

/**
 * This class is used to store hits and MatchData objects for the m best hits.
 **/
class HitCollector : public vsm::IDocSumCache
{
private:
    class Hit
    {
    public:
        Hit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & matchData,
            double score, const void * sortData, size_t sortDataLen);
        Hit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & matchData, double score)
            : Hit(doc, docId, matchData, score, nullptr, 0)
        { }
        ~Hit();
        Hit(const Hit &) = delete;
        Hit & operator = (const Hit &) = delete;
        Hit(Hit && rhs) = default;
        Hit & operator = (Hit && rhs) = default;
        search::DocumentIdT getDocId() const { return _docid; }
        const vsm::StorageDocument & getDocument() const { return *_document; }
        const std::vector<search::fef::TermFieldMatchData> &getMatchData() const { return _matchData; }
        search::feature_t getRankScore() const { return _score; }
        const vespalib::string & getSortBlob() const { return _sortBlob; }
        bool operator < (const Hit & b) const { return getDocId() < b.getDocId(); }
        int cmpDocId(const Hit & b) const { return getDocId() - b.getDocId(); }
        int cmpRank(const Hit & b) const {
            return (getRankScore() > b.getRankScore()) ?
                -1 : ((getRankScore() < b.getRankScore()) ? 1 : cmpDocId(b));
        }
        int cmpSort(const Hit & b) const {
            int diff = _sortBlob.compare(b._sortBlob.c_str(), b._sortBlob.size());
            return (diff == 0) ? cmpDocId(b) : diff;
        }
        class RankComparator {
        public:
            RankComparator() {}
            bool operator() (const Hit & lhs, const Hit & rhs) const {
                return lhs.cmpRank(rhs) < 0;
            }
        };
        class SortComparator {
        public:
            SortComparator() {}
            bool operator() (const Hit & lhs, const Hit & rhs) const {
                return lhs.cmpSort(rhs) < 0;
            }
        };

    private:
        uint32_t _docid;
        double _score;
        const vsm::StorageDocument * _document;
        std::vector<search::fef::TermFieldMatchData> _matchData;
        vespalib::string _sortBlob;
    };
    typedef std::vector<Hit> HitVector;
    HitVector _hits;
    bool      _sortedByDocId; // flag for whether the hit vector is sorted on docId

    void sortByDocId();
    bool addHitToHeap(const Hit & hit) const;
    bool addHit(Hit && hit);

public:
    typedef std::unique_ptr<HitCollector> UP;

    struct IRankProgram {
        virtual ~IRankProgram() {}
        virtual void run(uint32_t docid, const std::vector<search::fef::TermFieldMatchData> &matchData) = 0;
    };

    HitCollector(size_t wantedHits);

    virtual const vsm::Document & getDocSum(const search::DocumentIdT & docId) const override;

    /**
     * Adds a hit to this hit collector.
     * Make sure that the hits are added in increasing local docId order.
     * If you add a nullptr document you should not use getDocSum() or fillSearchResult(),
     * as these functions expect valid documents.
     *
     * @param doc   The document that is a hit. Must be kept alive on the outside.
     * @param data  The match data for the hit.
     * @return true if the document was added to the heap
     **/
    bool addHit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & data, double score);

    /**
     * Adds a hit to this hit collector.
     * Make sure that the hits are added in increasing local docId order.
     * If you add a nullptr document you should not use getDocSum() or fillSearchResult(),
     * as these functions expect valid documents.
     *
     * @param doc   The document that is a hit. Must be kept alive on the outside.
     * @param data  The match data for the hit.
     * @param sortData The buffer of the sortdata.
     * @param sortDataLen The length of the sortdata.
     * @return true if the document was added to the heap
     **/
    bool addHit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & data,
                double score, const void * sortData, size_t sortDataLen);

    /**
     * Fills the given search result with the m best hits from the hit heap.
     * Invoking this method will destroy the heap property of the hit heap.
     **/
    void fillSearchResult(vdslib::SearchResult & searchResult);

    /**
     * Extract features from the hits stored in the hit heap.
     * Invoking this method will destroy the heap property of the hit heap.
     * Note that this method will calculate any additional features.
     *
     * @return features for all hits on the heap.
     * @param rankProgram the rank program used to calculate all features.
     * @param resolver   feature resolver, gives feature names and values
     **/
    search::FeatureSet::SP getFeatureSet(IRankProgram &rankProgram,
                                         const search::fef::FeatureResolver &resolver);

};

} // namespace storage

