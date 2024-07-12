// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/stringmap.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/featureset.h>
#include <algorithm>

namespace search::fef { class FeatureResolver; }

namespace streaming {

/**
 * This class is used to store hits and MatchData objects for the m best hits.
 **/
class HitCollector : public vsm::IDocSumCache
{
private:
    using TermFieldMatchData = search::fef::TermFieldMatchData;
    using MatchData = search::fef::MatchData;
    using FeatureResolver = search::fef::FeatureResolver;
    class Hit
    {
    public:
        Hit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & matchData,
            double score, const void * sortData, size_t sortDataLen);
        Hit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & matchData, double score)
            : Hit(std::move(doc), docId, matchData, score, nullptr, 0)
        { }
        ~Hit();
        Hit(const Hit &) = delete;
        Hit & operator = (const Hit &) = delete;
        Hit(Hit && rhs) noexcept = default;
        Hit & operator = (Hit && rhs) noexcept = default;
        search::DocumentIdT getDocId() const noexcept { return _docid; }
        const vsm::StorageDocument & getDocument() const noexcept { return *_document; }
        const std::vector<TermFieldMatchData> &getMatchData() const noexcept { return _matchData; }
        search::feature_t getRankScore() const noexcept { return _score; }
        const vespalib::string & getSortBlob() const noexcept { return _sortBlob; }
        bool operator < (const Hit & b) const noexcept { return getDocId() < b.getDocId(); }
        int cmpDocId(const Hit & b) const noexcept { return getDocId() - b.getDocId(); }
        int cmpRank(const Hit & b) const noexcept {
            return (getRankScore() > b.getRankScore()) ?
                -1 : ((getRankScore() < b.getRankScore()) ? 1 : cmpDocId(b));
        }
        int cmpSort(const Hit & b) const noexcept {
            auto min_size = std::min(_sortBlob.size(), b._sortBlob.size());
            int diff = (min_size != 0u) ? memcmp(_sortBlob.data(), b._sortBlob.data(), min_size) : 0;
            if (diff == 0) {
                diff = (_sortBlob.size() == b._sortBlob.size()) ? 0 : ((_sortBlob.size() < b._sortBlob.size()) ? -1 : 1);
            }
            return (diff == 0) ? cmpDocId(b) : diff;
        }

        void dropDocument() noexcept { _document.reset(); }

    private:
        uint32_t _docid;
        double   _score;
        vsm::StorageDocument::SP        _document;
        std::vector<TermFieldMatchData> _matchData;
        vespalib::string                _sortBlob;
    };
    using HitVector = std::vector<Hit>;
    using Lids = std::vector<uint32_t>;
    HitVector _hits;
    Lids      _heap;
    bool      _use_sort_blob;

    Lids bestLids() const;
    bool addHitToHeap(uint32_t index) const;
    bool addHit(Hit && hit);
    void make_heap();
    void pop_heap();
    void push_heap();
    class RankComparator {
    public:
        explicit RankComparator(const HitVector & hits) noexcept : _hits(hits) {}
        bool operator() (uint32_t lhs, uint32_t rhs) const noexcept {
            return _hits[lhs].cmpRank(_hits[rhs]) < 0;
        }
    private:
        const HitVector & _hits;
    };
    class SortComparator {
    public:
        explicit SortComparator(const HitVector & hits) noexcept : _hits(hits) {}
        bool operator() (uint32_t lhs, uint32_t rhs) const noexcept {
            return _hits[lhs].cmpSort(_hits[rhs]) < 0;
        }
    private:
        const HitVector & _hits;
    };

public:
    using UP = std::unique_ptr<HitCollector>;

    struct IRankProgram {
        virtual ~IRankProgram() = default;
        virtual void run(uint32_t docid, const std::vector<TermFieldMatchData> &matchData) = 0;
    };

    HitCollector(size_t wantedHits, bool use_sort_blob);
    ~HitCollector() override;

    size_t numHits() const noexcept { return _hits.size(); }
    size_t numHitsOnHeap() const noexcept { return _heap.size(); }

    const vsm::Document & getDocSum(const search::DocumentIdT & docId) const override;

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
    bool addHit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & data, double score);

    /**
     * Adds a hit to this hit collector.
     * Make sure that the hits are added in increasing local docId order.
     * If you add a nullptr document you should not use getDocSum() or fillSearchResult(),
     * as these functions expect valid documents.
     *
     * @param doc   The document that is a hit.
     * @param data  The match data for the hit.
     * @param sortData The buffer of the sortdata.
     * @param sortDataLen The length of the sortdata.
     * @return true if the document was added to the heap
     **/
    bool addHit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & data,
                double score, const void * sortData, size_t sortDataLen);

    /**
     * Fills the given search result with the m best hits from the hit heap.
     **/
    void fillSearchResult(vdslib::SearchResult & searchResult, vespalib::FeatureValues&& match_features) const;
    void fillSearchResult(vdslib::SearchResult & searchResult) const;

    /**
     * Extract features from the hits stored in the hit heap.
     * Note that this method will calculate any additional features.
     *
     * @return features for all hits on the heap.
     * @param rankProgram the rank program used to calculate all features.
     * @param resolver   feature resolver, gives feature names and values
     **/
    vespalib::FeatureSet::SP getFeatureSet(IRankProgram &rankProgram,
                                           const FeatureResolver &resolver,
                                           const search::StringStringMap &feature_rename_map) const;

    vespalib::FeatureSet::SP getFeatureSet(IRankProgram &rankProgram,
                                           search::DocumentIdT docId,
                                           const FeatureResolver &resolver,
                                           const search::StringStringMap &feature_rename_map);

    vespalib::FeatureValues get_match_features(IRankProgram& rank_program,
                                               const FeatureResolver& resolver,
                                               const search::StringStringMap& feature_rename_map) const;
};

} // namespace streaming
