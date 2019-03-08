// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <set>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/datatypes.h>
#include <limits>
#include "i_document_remove_listener.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/document/annotation/span.h>

namespace search
{

namespace memoryindex
{

class IOrderedDocumentInserter;
class DocumentRemover;

class FieldInverter : public IDocumentRemoveListener
{
public:
    class PosInfo
    {
    public:
        uint32_t _wordNum;      // XXX: Initially word reference
        uint32_t _docId;
        uint32_t _elemId;
        uint32_t _wordPos;
        uint32_t _elemRef;  // Offset in _elems

        static constexpr uint32_t _elemRemoved =
            std::numeric_limits<uint32_t>::max();

        PosInfo()
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
                uint32_t wordPos, uint32_t elemRef)
            : _wordNum(wordRef),
              _docId(docId),
              _elemId(elemId),
              _wordPos(wordPos),
              _elemRef(elemRef)
        {
        }


        PosInfo(uint32_t wordRef,
                uint32_t docId)
            : _wordNum(wordRef),
              _docId(docId),
              _elemId(_elemRemoved),
              _wordPos(0),
              _elemRef(0)
        {
        }

        bool
        removed() const
        {
            return _elemId == _elemRemoved;
        }

        bool
        operator<(const PosInfo &rhs) const
        {
            if (_wordNum != rhs._wordNum)
                return _wordNum < rhs._wordNum;
            if (_docId != rhs._docId)
                return _docId < rhs._docId;
            if (_elemId != rhs._elemId) {
                if (removed() != rhs.removed())
                    return removed() && !rhs.removed();
                return _elemId < rhs._elemId;
            }
            return _wordPos < rhs._wordPos;
        }
    };

private:
    FieldInverter(const FieldInverter &) = delete;
    FieldInverter(const FieldInverter &&) = delete;
    FieldInverter &operator=(const FieldInverter &) = delete;
    FieldInverter &operator=(const FieldInverter &&) = delete;

    typedef vespalib::Array<char> WordBuffer;

    class ElemInfo
    {
    public:
        int32_t _weight;
        uint32_t _len;

        ElemInfo(int32_t weight)
            : _weight(weight),
              _len(0u)
        {
        }

        void
        setLen(uint32_t len)
        {
            _len = len;
        }
    };

    typedef std::vector<ElemInfo> ElemInfoVec;

    typedef std::vector<PosInfo> PosInfoVec;

    class CompareWordRef
    {
        const char *const _wordBuffer;

    public:
        CompareWordRef(const WordBuffer &wordBuffer)
            : _wordBuffer(&wordBuffer[0])
        {
        }

        const char *
        getWord(uint32_t wordRef) const
        {
            return &_wordBuffer[static_cast<size_t>(wordRef) << 2];
        }

        bool
        operator()(const uint32_t lhs, const uint32_t rhs) const
        {
            return strcmp(getWord(lhs), getWord(rhs)) < 0;
        }
    };

    /*
     * Range in _positions vector used to represent a document put.
     */
    class PositionRange
    {
        uint32_t _start;
        uint32_t _len;

    public:
        PositionRange(uint32_t start, uint32_t len)
            : _start(start),
              _len(len)
        {
        }

        bool
        operator<(const PositionRange &rhs) const
        {
            if (_start != rhs._start) {
                return _start < rhs._start;
            }
            return _len < rhs._len;
        }

        uint32_t getStart() const { return _start; }
        uint32_t getLen() const   { return _len; }
    };

    // Current field state.
    uint32_t                       _fieldId;   // current field id
    uint32_t                       _elem;      // current element
    uint32_t                       _wpos;      // current word pos
    uint32_t                       _docId;
    uint32_t                       _oldPosSize;

    const index::Schema           &_schema;

    WordBuffer                     _words;
    ElemInfoVec                    _elems;
    PosInfoVec                     _positions;
    index::DocIdAndPosOccFeatures  _features;
    std::vector<uint32_t>          _elementWordRefs;
    std::vector<uint32_t>          _wordRefs;

    typedef std::pair<document::Span, const document::FieldValue *> SpanTerm;
    typedef std::vector<SpanTerm> SpanTermVector;
    SpanTermVector                      _terms;

    // info about aborted and pending documents.
    std::vector<PositionRange>      _abortedDocs;
    std::map<uint32_t, PositionRange> _pendingDocs;
    std::vector<uint32_t>             _removeDocs;

    void
    invertNormalDocTextField(const document::FieldValue &val);

public:
    /**
     * Start a new element
     *
     * @param weight        element weight
     */
    void
    startElement(int32_t weight);

    /**
     * End an element.
     */
    void
    endElement();

private:
    /**
     * Save field value as word in word buffer.
     *
     * @param word      word to be saved
     * @param len       length of word to be saved.
     *
     * @return          word reference
     */
    VESPA_DLL_LOCAL uint32_t
    saveWord(const vespalib::stringref word);

    /**
     * Save field value as word in word buffer.
     *
     * @param fv        field value containing word to be stored
     *
     * @return          word reference
     */
    VESPA_DLL_LOCAL uint32_t
    saveWord(const document::FieldValue &fv);

    /**
     * Get pointer to saved word from a word reference.
     *
     * @param wordRef       word reference
     *
     * @return          saved word
     */
    const char *
    getWordFromRef(uint32_t wordRef) const
    {
        return &_words[static_cast<size_t>(wordRef) << 2];
    }

    /**
     * Get pointer to saved word from a word number
     *
     * @param wordNum       word number
     *
     * @return          saved word
     */
    const char *
    getWordFromNum(uint32_t wordNum) const
    {
        return getWordFromRef(_wordRefs[wordNum]);
    }

    /**
     * Get word number from word reference
     *
     * @param wordRef       word reference
     *
     * @return          word number
     */
    uint32_t
    getWordNum(uint32_t wordRef) const
    {
        const char *p = &_words[static_cast<size_t>(wordRef - 1) << 2];
        return *reinterpret_cast<const uint32_t *>(p);
    }

    /**
     * Update mapping from word reference to word number
     *
     * @param wordRef       word reference
     * @param wordNum       word number
     */
    void
    updateWordNum(uint32_t wordRef, uint32_t wordNum)
    {
        char *p = &_words[static_cast<size_t>(wordRef - 1) << 2];
        *reinterpret_cast<uint32_t *>(p) = wordNum;
    }

    /**
     * Add a word reference to posting list.  Don't step word pos.
     *
     *
     * @param wordRef       word reference
     */
    void
    add(uint32_t wordRef) {
        _positions.emplace_back(wordRef, _docId, _elem,
                                _wpos, _elems.size() - 1);
    }

    void
    stepWordPos()
    {
        ++_wpos;
    }

public:
    VESPA_DLL_LOCAL void
    processAnnotations(const document::StringFieldValue &value);

private:
    void
    processNormalDocTextField(const document::StringFieldValue &field);

    void
    processNormalDocArrayTextField(const document::ArrayFieldValue &field);

    void
    processNormalDocWeightedSetTextField(const document::WeightedSetFieldValue &field);

    /**
     * Obtain the schema used by this index.
     *
     * @return schema used by this index
     */
    const index::Schema &
    getSchema() const
    {
        return _schema;
    }

    /**
     * Clear internal memory structures.
     */
    void
    reset();

    /**
     * Calculate word numbers and replace word references with word
     * numbers in internal memory structures.
     */
    void
    sortWords();

    void
    moveNotAbortedDocs(uint32_t &dstIdx, uint32_t srcIdx, uint32_t nextTrimIdx);

    void
    trimAbortedDocs();

    /*
     * Abort a pending document that has already been inverted.
     *
     * @param docId            local id for document
     *
     */
    void
    abortPendingDoc(uint32_t docId);

public:
    /**
     * Create a new memory index based on the given schema.
     *
     * @param schema the index schema to use
     * @param schema the field to be inverted
     */
    FieldInverter(const index::Schema &schema, uint32_t fieldId);

    /*
     * Apply pending removes.
     *
     * @param remover    document remover
     */
    void
    applyRemoves(DocumentRemover &remover);

    /**
     * Push inverted documents to memory index structure.
     *
     * Temporary restriction: Currently only one document at a time is
     * supported.
     *
     * @param inserter  ordered document inserter
     */
    void
    pushDocuments(IOrderedDocumentInserter &inserter);

    /*
     * Invert a normal text field, based on annotations.
     */
    void
    invertField(uint32_t docId, const document::FieldValue::UP &val);

    /*
     * Setup remove of word in old version of document.
     */
    virtual void
    remove(const vespalib::stringref word, uint32_t docId) override;

    void
    removeDocument(uint32_t docId)
    {
        abortPendingDoc(docId);
        _removeDocs.push_back(docId);
    }

    void
    startDoc(uint32_t docId)
    {
        assert(_docId == 0);
        assert(docId != 0);
        abortPendingDoc(docId);
        _removeDocs.push_back(docId);
        _docId = docId;
        _elem = 0;
        _wpos = 0;
    }

    void
    endDoc()
    {
        uint32_t newPosSize = static_cast<uint32_t>(_positions.size());
        _pendingDocs.insert({ _docId,
                                 { _oldPosSize, newPosSize - _oldPosSize } });
        _docId = 0;
        _oldPosSize = newPosSize;
    }

    void
    addWord(const vespalib::stringref word)
    {
        uint32_t wordRef = saveWord(word);
        if (wordRef != 0u) {
            add(wordRef);
            stepWordPos();
        }
    }
};

} // namespace memoryindex

} // namespace search

