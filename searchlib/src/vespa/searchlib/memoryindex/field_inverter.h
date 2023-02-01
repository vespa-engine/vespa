// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_field_index_remove_listener.h"
#include <vespa/document/annotation/span.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <limits>

namespace search::index {
    class FieldLengthCalculator;
    class Schema;
}

namespace document {
    class FieldValue;
    class StringFieldValue;
    class ArrayFieldValue;
    class WeightedSetFieldValue;
}
namespace search::memoryindex {

class IOrderedFieldIndexInserter;
class FieldIndexRemover;

/**
 * Class used to invert a field for a set of documents, preparing for pushing changes into the corresponding FieldIndex.
 *
 * It creates a set of sorted {word, docId, features} tuples based on the field content of the documents,
 * and uses this when updating the posting lists of the FieldIndex.
 */
class FieldInverter : public IFieldIndexRemoveListener {
public:
    class PosInfo {
    public:
        uint32_t _wordNum;      // XXX: Initially word reference
        uint32_t _docId;
        uint32_t _elemId;
        uint32_t _wordPos;
        uint32_t _elemRef;  // Offset in _elems

        static constexpr uint32_t _elemRemoved = std::numeric_limits<uint32_t>::max();

        PosInfo() noexcept
            : _wordNum(0),
              _docId(0),
              _elemId(0),
              _wordPos(0),
              _elemRef(0)
        {
        }

        PosInfo(uint32_t wordRef,
                uint32_t docId,
                uint32_t elemId,
                uint32_t wordPos, uint32_t elemRef) noexcept
            : _wordNum(wordRef),
              _docId(docId),
              _elemId(elemId),
              _wordPos(wordPos),
              _elemRef(elemRef)
        {
        }

        PosInfo(uint32_t wordRef, uint32_t docId) noexcept
            : _wordNum(wordRef),
              _docId(docId),
              _elemId(_elemRemoved),
              _wordPos(0),
              _elemRef(0)
        {
        }

        bool removed() const { return _elemId == _elemRemoved; }

        bool operator<(const PosInfo &rhs) const {
            if (_wordNum != rhs._wordNum) {
                return _wordNum < rhs._wordNum;
            }
            if (_docId != rhs._docId) {
                return _docId < rhs._docId;
            }
            if (_elemId != rhs._elemId) {
                if (removed() != rhs.removed()) {
                    return removed() && !rhs.removed();
                }
                return _elemId < rhs._elemId;
            }
            return _wordPos < rhs._wordPos;
        }
    };

private:
    using WordBuffer = std::vector<char, vespalib::allocator_large<char>>;

    class ElemInfo {
    public:
        const int32_t _weight;
        uint32_t _len;
        uint32_t _field_length;

        ElemInfo(int32_t weight)
            : _weight(weight),
              _len(0u),
              _field_length(0u)
        {
        }

        void setLen(uint32_t len) { _len = len; }
        uint32_t get_field_length() const { return _field_length; }
        void set_field_length(uint32_t field_length) { _field_length = field_length; }
    };

    using ElemInfoVec = std::vector<ElemInfo, vespalib::allocator_large<ElemInfo>>;
    using PosInfoVec = std::vector<PosInfo, vespalib::allocator_large<PosInfo>>;

    class CompareWordRef {
        const char *const _wordBuffer;

    public:
        CompareWordRef(const WordBuffer &wordBuffer)
            : _wordBuffer(&wordBuffer[0])
        {
        }

        const char *getWord(uint32_t wordRef) const {
            return &_wordBuffer[static_cast<size_t>(wordRef) << 2];
        }

        bool operator()(const uint32_t lhs, const uint32_t rhs) const {
            return strcmp(getWord(lhs), getWord(rhs)) < 0;
        }
    };

    /*
     * Range in _positions vector used to represent a document put.
     */
    class PositionRange {
        uint32_t _start;
        uint32_t _len;

    public:
        PositionRange(uint32_t start, uint32_t len)
            : _start(start),
              _len(len)
        {
        }

        bool operator<(const PositionRange &rhs) const {
            if (_start != rhs._start) {
                return _start < rhs._start;
            }
            return _len < rhs._len;
        }

        uint32_t getStart() const { return _start; }
        uint32_t getLen() const   { return _len; }
    };

    using UInt32Vector = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;
    // Current field state.
    const uint32_t                 _fieldId;   // current field id
    uint32_t                       _elem;      // current element
    uint32_t                       _wpos;      // current word pos
    uint32_t                       _docId;
    uint32_t                       _oldPosSize;

    const index::Schema           &_schema;

    WordBuffer                     _words;
    ElemInfoVec                    _elems;
    PosInfoVec                     _positions;
    index::DocIdAndPosOccFeatures  _features;
    UInt32Vector                   _wordRefs;

    using SpanTerm = std::pair<document::Span, const document::FieldValue *>;
    using SpanTermVector = std::vector<SpanTerm>;
    SpanTermVector                      _terms;

    // Info about aborted and pending documents.
    std::vector<PositionRange>                  _abortedDocs;
    vespalib::hash_map<uint32_t, PositionRange> _pendingDocs;
    UInt32Vector                                _removeDocs;

    FieldIndexRemover                &_remover;
    IOrderedFieldIndexInserter       &_inserter;
    index::FieldLengthCalculator     &_calculator;

    void invertNormalDocTextField(const document::FieldValue &val);

public:
    void startElement(int32_t weight);
    void endElement();

private:
    /**
     * Save the given word in the word buffer and return the word reference.
     */
    VESPA_DLL_LOCAL uint32_t saveWord(const vespalib::stringref word);

    /**
     * Save the field value as a word in the word buffer and return the word reference.
     */
    VESPA_DLL_LOCAL uint32_t saveWord(const document::FieldValue &fv);

    /**
     * Get pointer to saved word from a word reference.
     */
    const char *getWordFromRef(uint32_t wordRef) const {
        return &_words[static_cast<size_t>(wordRef) << 2];
    }

    /**
     * Get pointer to saved word from a word number.
     */
    const char *getWordFromNum(uint32_t wordNum) const {
        return getWordFromRef(_wordRefs[wordNum]);
    }

    /**
     * Get word number from word reference.
     */
    uint32_t getWordNum(uint32_t wordRef) const {
        const char *p = &_words[static_cast<size_t>(wordRef - 1) << 2];
        return *reinterpret_cast<const uint32_t *>(p);
    }

    /**
     * Update mapping from word reference to word number.
     */
    void updateWordNum(uint32_t wordRef, uint32_t wordNum) {
        char *p = &_words[static_cast<size_t>(wordRef - 1) << 2];
        *reinterpret_cast<uint32_t *>(p) = wordNum;
    }

    /**
     * Add a word reference to posting list (but don't step word pos).
     */
    void add(uint32_t wordRef) {
        _positions.emplace_back(wordRef, _docId, _elem, _wpos, _elems.size() - 1);
    }

    void stepWordPos() { ++_wpos; }

public:
    VESPA_DLL_LOCAL void
    processAnnotations(const document::StringFieldValue &value);

    void push_documents_internal();

private:
    void processNormalDocTextField(const document::StringFieldValue &field);
    void processNormalDocArrayTextField(const document::ArrayFieldValue &field);
    void processNormalDocWeightedSetTextField(const document::WeightedSetFieldValue &field);

    const index::Schema &getSchema() const { return _schema; }

    /**
     * Clear internal memory structures.
     */
    void reset();

    /**
     * Calculate word numbers and replace word references with word numbers in internal memory structures.
     */
    void sortWords();

    void moveNotAbortedDocs(uint32_t &dstIdx, uint32_t srcIdx, uint32_t nextTrimIdx);

    void trimAbortedDocs();

    /**
     * Abort a pending document that has already been inverted.
     */
    void abortPendingDoc(uint32_t docId);

public:
    /**
     * Create a new field inverter for the given fieldId, using the given schema.
     */
    FieldInverter(const index::Schema &schema, uint32_t fieldId,
                  FieldIndexRemover &remover,
                  IOrderedFieldIndexInserter &inserter,
                  index::FieldLengthCalculator &calculator);
    FieldInverter(const FieldInverter &) = delete;
    FieldInverter(const FieldInverter &&) = delete;
    FieldInverter &operator=(const FieldInverter &) = delete;
    FieldInverter &operator=(const FieldInverter &&) = delete;
    ~FieldInverter() override;

    /**
     * Apply pending removes using the given remover.
     *
     * The remover is tracking all {word, docId} tuples that should removed,
     * and forwards this to the remove() function in this class (via IFieldIndexRemoveListener interface).
     */
    void applyRemoves();

    /**
     * Push the current batch of inverted documents to the FieldIndex using the given inserter.
     */
    void pushDocuments();

    /**
     * Invert a normal text field, based on annotations.
     */
    void invertField(uint32_t docId, const std::unique_ptr<document::FieldValue> &val);

    /**
     * Setup remove of word in old version of document.
     */
    void remove(const vespalib::stringref word, uint32_t docId) override;

    void removeDocument(uint32_t docId) {
        abortPendingDoc(docId);
        _removeDocs.push_back(docId);
    }

    void startDoc(uint32_t docId);

    void endDoc();

    void addWord(const vespalib::stringref word) {
        uint32_t wordRef = saveWord(word);
        if (wordRef != 0u) {
            add(wordRef);
            stepWordPos();
        }
    }
};

}

