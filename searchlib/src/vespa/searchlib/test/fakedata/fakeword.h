// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/rand48.h>
#include <vector>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/diskindex/fieldreader.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>

namespace search::fakedata {


/**
 * General representation of a faked word, containing all features used
 * by any of the candidate posting list formats.
 */
class FakeWord
{
public:
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;

    class DocWordPosFeature
    {
    public:
        uint32_t _elementId;
        uint32_t _wordPos;
        int32_t _elementWeight;
        uint32_t _elementLen;

        bool operator<(const DocWordPosFeature &rhs) const {
            if (_elementId != rhs._elementId)
                return _elementId < rhs._elementId;
            return _wordPos < rhs._wordPos;
        }

        DocWordPosFeature();
        ~DocWordPosFeature();
    };

    using DocWordPosFeatureList = std::vector<DocWordPosFeature>;

    class DocWordCollapsedFeature
    {
    public:
        uint32_t _field_len;
        uint32_t _num_occs;

        DocWordCollapsedFeature();
        ~DocWordCollapsedFeature();
    };

    class DocWordFeature
    {
    public:
        uint32_t _docId;
        DocWordCollapsedFeature _collapsedDocWordFeatures;
        uint32_t _positions;
        uint32_t _accPositions; // accumulated positions for previous words

        DocWordFeature();
        ~DocWordFeature();
    };

    using DocWordFeatureList = std::vector<DocWordFeature>;

    class Randomizer
    {
    public:
        uint32_t _random;
        int32_t _ref;

        Randomizer() : _random(0), _ref(0) {}

        bool operator<(const Randomizer &rhs) const {
            if (_random != rhs._random)
                return _random < rhs._random;
            return _ref < rhs._ref;
        }

        bool operator==(const Randomizer &rhs) const {
            return _random == rhs._random && _ref == rhs._ref;
        }

        bool isExtra() const { return _ref < 0; }

        bool isRemove() const { return isExtra() && (_ref & 1) == 0; }
        uint32_t extraIdx() const { return (~_ref) >> 1; }
    };

    class RandomizedWriter
    {
    public:
        virtual~RandomizedWriter();

        virtual void add(uint32_t wordIdx, index::DocIdAndFeatures &features) = 0;

        virtual void remove(uint32_t wordIdx, uint32_t docId) = 0;
    };

    class RandomizedReader
    {
        Randomizer _r;
        const FakeWord *_fw;
        uint32_t _wordIdx;
        bool _valid;
        std::vector<Randomizer>::const_iterator _ri;
        std::vector<Randomizer>::const_iterator _re;
        index::DocIdAndPosOccFeatures _features;
    public:
        RandomizedReader();
        void read();

        void
        write(RandomizedWriter &writer)
        {
            const FakeWord::DocWordFeature &d = _fw->getDocWordFeature(_r);
            if (_r.isRemove()) {
                writer.remove(_wordIdx, d._docId);
            } else {
                const DocWordPosFeature *p = _fw->getDocWordPosFeature(_r, d);
                FakeWord::setupFeatures(d, p, _features);
                writer.add(_wordIdx, _features);
            }
        }

        bool isValid() const { return _valid; }

        bool operator<(const RandomizedReader &rhs) const
        {
            if (_r < rhs._r)
                return true;
            if (!(_r == rhs._r))
                return false;
            return _wordIdx < rhs._wordIdx;
        }

        void setup(const FakeWord *fw, uint32_t wordIdx);
    };

    DocWordFeatureList _postings;
    DocWordPosFeatureList _wordPosFeatures;
    DocWordFeatureList _extraPostings;
    DocWordPosFeatureList _extraWordPosFeatures;
    std::vector<Randomizer> _randomizer;
    uint32_t _docIdLimit;   // Documents in index
    std::string _name;
    const PosOccFieldsParams &_fieldsParams;
    uint32_t _packedIndex;

    void
    fakeup(search::BitVector &bitmap,
           vespalib::Rand48 &rnd,
           DocWordFeatureList &postings,
           DocWordPosFeatureList &wordPosFeatures);

    void
    fakeupTemps(vespalib::Rand48 &rnd,
                uint32_t docIdLimit,
                uint32_t tempWordDocs);

    void setupRandomizer(vespalib::Rand48 &rnd);

    const DocWordFeature &
    getDocWordFeature(const Randomizer &r) const
    {
        if (r.isExtra()) {
            assert(r.extraIdx() < _extraPostings.size());
            return _extraPostings[r.extraIdx()];
        }
        assert(static_cast<uint32_t>(r._ref) < _postings.size());
        return _postings[r._ref];
    }

    const
    DocWordPosFeature *
    getDocWordPosFeature(const Randomizer &r, const DocWordFeature &d) const
    {
        if (r.isExtra()) {
            assert(d._accPositions + d._positions <=
                   _extraWordPosFeatures.size());
            return &_extraWordPosFeatures[d._accPositions];
        }
        assert(d._accPositions + d._positions <=
               _wordPosFeatures.size());
        return &_wordPosFeatures[d._accPositions];
    }

    static void
    setupFeatures(const DocWordFeature &d,
                  const DocWordPosFeature *p,
                  index::DocIdAndPosOccFeatures &features)
    {
        unsigned int positions = d._positions;
        features.clear(d._docId);
        for (unsigned int t = 0; t < positions; ++t) {
            features.addNextOcc(p->_elementId, p->_wordPos,
                                p->_elementWeight, p->_elementLen);
            ++p;
        }
        features.set_field_length(d._collapsedDocWordFeatures._field_len);
        features.set_num_occs(d._collapsedDocWordFeatures._num_occs);
    }

public:

    FakeWord(uint32_t docIdLimit,
             const std::vector<uint32_t> & docIds,
             const std::string &name,
             const PosOccFieldsParams &fieldsParams,
             uint32_t packedIndex);

    FakeWord(uint32_t docIdLimit,
             uint32_t wordDocs,
             uint32_t tempWordDocs,
             const std::string &name,
             vespalib::Rand48 &rnd,
             const PosOccFieldsParams &fieldsParams,
             uint32_t packedIndex);

    FakeWord(uint32_t docIdLimit,
             uint32_t wordDocs,
             uint32_t tempWordDocs,
             const std::string &name,
             const FakeWord &otherWord,
             size_t overlapDocs,
             vespalib::Rand48 &rnd,
             const PosOccFieldsParams &fieldsParams,
             uint32_t packedIndex);

    ~FakeWord();

    bool
    validate(search::queryeval::SearchIterator *iterator,
             const fef::TermFieldMatchDataArray &matchData,
             uint32_t stride,
             bool unpack_normal_features,
             bool unpack_interleaved_features,
             bool verbose) const;

    bool
    validate(search::queryeval::SearchIterator *iterator,
             const fef::TermFieldMatchDataArray &matchData,
             bool unpack_normal_features,
             bool unpack_interleaved_features,
             bool verbose) const;

    bool validate(search::queryeval::SearchIterator *iterator,
                  bool verbose) const;

    bool
    validate(search::diskindex::FieldReader &fieldReader,
             uint32_t wordNum,
             const fef::TermFieldMatchDataArray &matchData,
             bool decode_interleaved_features,
             bool verbose) const;

    void validate(const std::vector<uint32_t> &docIds) const;
    void validate(const BitVector &bv) const;

    bool
    dump(search::diskindex::FieldWriter &fieldWriter,
         bool verbose) const;

    const std::string &getName() const { return _name; }
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    const PosOccFieldsParams &getFieldsParams() const { return _fieldsParams; }
    uint32_t getPackedIndex() const { return _packedIndex; }
    void addDocIdBias(uint32_t docIdBias);
};

}
