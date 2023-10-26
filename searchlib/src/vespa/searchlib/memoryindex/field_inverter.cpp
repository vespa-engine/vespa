// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_inverter.h"
#include "ordered_field_index_inserter.h"
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/util/url.h>
#include <vespa/vespalib/datastore/aligner.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <stdexcept>

namespace search::memoryindex {

using document::Annotation;
using document::ArrayFieldValue;
using document::DataType;
using document::Document;
using document::DocumentType;
using document::Field;
using document::FieldValue;
using document::IntFieldValue;
using document::Span;
using document::StringFieldValue;
using document::StructFieldValue;
using document::WeightedSetFieldValue;
using index::DocIdAndPosOccFeatures;
using index::Schema;
using search::index::schema::CollectionType;
using search::linguistics::TokenExtractor;
using search::util::URL;
using vespalib::make_string;
using vespalib::datastore::Aligner;

void
FieldInverter::processAnnotations(const StringFieldValue &value, const Document& doc)
{
    _terms.clear();
    auto span_trees = value.getSpanTrees();
    vespalib::stringref text = value.getValueRef();
    _token_extractor.extract(_terms, span_trees, text, &doc);
    auto it  = _terms.begin();
    auto ite = _terms.end();
    for (; it != ite; ) {
        auto it_begin = it;
        for (; it != ite && it->span == it_begin->span; ++it) {
            uint32_t wordRef = saveWord(it->word);
            add(wordRef);
        }
        stepWordPos();
    }
}

void
FieldInverter::reset()
{
    _words.clear();
    _elems.clear();
    _positions.clear();
    _wordRefs.resize(1);
    _pendingDocs.clear();
    _abortedDocs.clear();
    _removeDocs.clear();
    _oldPosSize = 0u;
}

struct WordRefRadix {
    uint32_t operator () (const uint64_t v) { return v >> 32; }
};

void
FieldInverter::sortWords()
{
    assert(_wordRefs.size() > 1);

    // Make a dictionary for words.
    { // Use radix sort based on first four bytes of word, before finalizing with std::sort.
        vespalib::Array<uint64_t> firstFourBytes(_wordRefs.size());
        for (size_t i(1); i < _wordRefs.size(); i++) {
            uint64_t firstFour = ntohl(*reinterpret_cast<const uint32_t *>(getWordFromRef(_wordRefs[i])));
            firstFourBytes[i] = (firstFour << 32) | _wordRefs[i];
        }
        ShiftBasedRadixSorter<uint64_t, WordRefRadix, CompareWordRef, 24, true>::
           radix_sort(WordRefRadix(), CompareWordRef(_words), &firstFourBytes[1], firstFourBytes.size()-1, 16);
        for (size_t i(1); i < firstFourBytes.size(); i++) {
            _wordRefs[i] = firstFourBytes[i] & 0xffffffffl;
        }
    }
    // Populate word numbers in word buffer and mapping from
    // word numbers to word reference.
    // TODO: shrink word buffer to only contain unique words
    auto w(_wordRefs.begin() + 1);
    auto we(_wordRefs.end());
    uint32_t wordNum = 1;   // First valid word number
    const char *lastWord = getWordFromRef(*w);
    updateWordNum(*w, wordNum);
    for (++w; w != we; ++w) {
        const char *word = getWordFromRef(*w);
        int cmpres = strcmp(lastWord, word);
        assert(cmpres <= 0);
        if (cmpres < 0) {
            ++wordNum;
            _wordRefs[wordNum] = *w;
            lastWord = word;
        }
        updateWordNum(*w, wordNum);
    }
    assert(_wordRefs.size() >= wordNum + 1);
    _wordRefs.resize(wordNum + 1);
    // Replace initial word reference by word number.
    for (auto &p : _positions) {
        p._wordNum = getWordNum(p._wordNum);
    }
}

void
FieldInverter::startElement(int32_t weight)
{
    _elems.push_back(ElemInfo(weight)); // Fill in length later
}

void
FieldInverter::endElement()
{
    _elems.back().setLen(_wpos);
    _wpos = 0;
    ++_elem;
}

uint32_t
FieldInverter::saveWord(vespalib::stringref word)
{
    const size_t wordsSize = _words.size();
    // assert((wordsSize & 3) == 0); // Check alignment
    const size_t unpadded_size = wordsSize + 4 + word.size() + 1;
    const size_t fullyPaddedSize = Aligner<4>::align(unpadded_size);
    _words.reserve(vespalib::roundUp2inN(fullyPaddedSize));
    _words.resize(fullyPaddedSize);

    char * buf = &_words[0] + wordsSize;
    memset(buf, 0, 4);
    memcpy(buf + 4, word.data(), word.size());
    memset(buf + 4 + word.size(), 0, fullyPaddedSize - unpadded_size + 1);

    uint32_t wordRef = (wordsSize + 4) >> 2;
    // assert(wordRef != 0);
    _wordRefs.push_back(wordRef);
    return wordRef;
}

void
FieldInverter::remove(const vespalib::stringref word, uint32_t docId)
{
    uint32_t wordRef = saveWord(word);
    _positions.emplace_back(wordRef, docId);
}

void
FieldInverter::endDoc()
{
    uint32_t field_length = 0;
    if (_elem > 0) {
        auto itr = _elems.end() - _elem;
        while (itr != _elems.end()) {
            field_length += itr->_len;
            ++itr;
        }
        itr = _elems.end() - _elem;
        while (itr != _elems.end()) {
            itr->set_field_length(field_length);
            ++itr;
        }
    }
    _calculator.add_field_length(field_length);
    uint32_t newPosSize = static_cast<uint32_t>(_positions.size());
    _pendingDocs.insert({ _docId, { _oldPosSize, newPosSize - _oldPosSize } });
    _docId = 0;
    _oldPosSize = newPosSize;
}

void
FieldInverter::addWord(vespalib::stringref word, const document::Document& doc)
{
    word = _token_extractor.sanitize_word(word, &doc);
    if (!word.empty()) {
        uint32_t wordRef = saveWord(word);
        add(wordRef);
        stepWordPos();
    }
}

void
FieldInverter::processNormalDocTextField(const StringFieldValue &field, const Document& doc)
{
    startElement(1);
    processAnnotations(field, doc);
    endElement();
}

void
FieldInverter::processNormalDocArrayTextField(const ArrayFieldValue &field, const Document& doc)
{
    uint32_t el = 0;
    uint32_t ele = field.size();
    for (;el < ele; ++el) {
        const FieldValue &elfv = field[el];
        assert(elfv.isA(FieldValue::Type::STRING));
        const auto &element = static_cast<const StringFieldValue &>(elfv);
        startElement(1);
        processAnnotations(element, doc);
        endElement();
    }
}

void
FieldInverter::processNormalDocWeightedSetTextField(const WeightedSetFieldValue &field, const Document& doc)
{
    for (const auto & el : field) {
        const FieldValue &key = *el.first;
        const FieldValue &xweight = *el.second;
        assert(key.isA(FieldValue::Type::STRING));
        assert(xweight.isA(FieldValue::Type::INT));
        const auto &element = static_cast<const StringFieldValue &>(key);
        int32_t weight = xweight.getAsInt();
        startElement(weight);
        processAnnotations(element, doc);
        endElement();
    }
}

FieldInverter::FieldInverter(const Schema &schema, uint32_t fieldId,
                             FieldIndexRemover &remover,
                             IOrderedFieldIndexInserter &inserter,
                             index::FieldLengthCalculator &calculator)
    : _fieldId(fieldId),
      _elem(0u),
      _wpos(0u),
      _docId(0),
      _oldPosSize(0),
      _schema(schema),
      _token_extractor(_schema.getIndexField(_fieldId).getName(), max_word_len),
      _words(),
      _elems(),
      _positions(),
      _features(),
      _wordRefs(1),
      _terms(),
      _abortedDocs(),
      _pendingDocs(),
      _removeDocs(),
      _remover(remover),
      _inserter(inserter),
      _calculator(calculator)
{
}

FieldInverter::~FieldInverter() = default;

void
FieldInverter::abortPendingDoc(uint32_t docId)
{
    auto itr = _pendingDocs.find(docId);
    if (itr != _pendingDocs.end()) {
        if (itr->second.getLen() != 0) {
            _abortedDocs.push_back(itr->second);
        }
        _pendingDocs.erase(itr);
    }
}

void
FieldInverter::moveNotAbortedDocs(uint32_t &dstIdx,
                                  uint32_t srcIdx,
                                  uint32_t nextTrimIdx)
{
    assert(nextTrimIdx >= srcIdx);
    uint32_t size = nextTrimIdx - srcIdx;
    if (size == 0) {
        return;
    }
    assert(dstIdx < srcIdx);
    assert(srcIdx < _positions.size());
    assert(srcIdx + size <= _positions.size());
    PosInfo *dst = &_positions[dstIdx];
    const PosInfo *src = &_positions[srcIdx];
    const PosInfo *srce = src + size;
    while (src != srce) {
        *dst = *src;
        ++dst;
        ++src;
    }
    dstIdx += size;
}

void
FieldInverter::trimAbortedDocs()
{
    if (_abortedDocs.empty()) {
        return;
    }
    std::sort(_abortedDocs.begin(), _abortedDocs.end());
    auto itrEnd = _abortedDocs.end();
    auto itr = _abortedDocs.begin();
    uint32_t dstIdx = itr->getStart();
    uint32_t srcIdx = itr->getStart() + itr->getLen();
    ++itr;
    while (itr != itrEnd) {
        moveNotAbortedDocs(dstIdx, srcIdx, itr->getStart());
        srcIdx = itr->getStart() + itr->getLen();
        ++itr;
    }
    moveNotAbortedDocs(dstIdx, srcIdx, _positions.size());
    _positions.resize(dstIdx);
    _abortedDocs.clear();
}

void
FieldInverter::invertField(uint32_t docId, const FieldValue::UP &val, const Document& doc)
{
    (void) doc;
    if (val) {
        startDoc(docId);
        invertNormalDocTextField(*val, doc);
        endDoc();
    } else {
        removeDocument(docId);
    }
}

void
FieldInverter::startDoc(uint32_t docId) {
    assert(_docId == 0);
    assert(docId != 0);
    abortPendingDoc(docId);
    _removeDocs.push_back(docId);
    _docId = docId;
    _elem = 0;
    _wpos = 0;
}

void
FieldInverter::invertNormalDocTextField(const FieldValue &val, const Document& doc)
{
    const Schema::IndexField &field = _schema.getIndexField(_fieldId);
    switch (field.getCollectionType()) {
    case CollectionType::SINGLE:
        if (val.isA(FieldValue::Type::STRING)) {
            processNormalDocTextField(static_cast<const StringFieldValue &>(val), doc);
        } else {
            throw std::runtime_error(make_string("Expected DataType::STRING, got '%s'", val.getDataType()->getName().c_str()));
        }
        break;
    case CollectionType::WEIGHTEDSET:
        if (val.isA(FieldValue::Type::WSET)) {
            const auto &wset = static_cast<const WeightedSetFieldValue &>(val);
            if (wset.getNestedType() == *DataType::STRING) {
                processNormalDocWeightedSetTextField(wset, doc);
            } else {
                throw std::runtime_error(make_string("Expected DataType::STRING, got '%s'", wset.getNestedType().getName().c_str()));
            }
        } else {
            throw std::runtime_error(make_string("Expected weighted set, got '%s'", val.className()));
        }
        break;
    case CollectionType::ARRAY:
        if (val.isA(FieldValue::Type::ARRAY)) {
            const auto &arr = static_cast<const ArrayFieldValue&>(val);
            if (arr.getNestedType() == *DataType::STRING) {
                processNormalDocArrayTextField(arr, doc);
            } else {
                throw std::runtime_error(make_string("Expected DataType::STRING, got '%s'", arr.getNestedType().getName().c_str()));
            }
        } else {
            throw std::runtime_error(make_string("Expected Array, got '%s'", val.className()));
        }
        break;
    default:
        break;
    }
}

namespace {

struct FullRadix {
    uint64_t operator () (const FieldInverter::PosInfo & p) const {
        return (static_cast<uint64_t>(p._wordNum) << 32) |
            p._docId;
    }
};

}

void
FieldInverter::applyRemoves()
{
    for (auto docId : _removeDocs) {
        _remover.remove(docId, *this);
    }
    _removeDocs.clear();
}

void
FieldInverter::push_documents_internal()
{
    trimAbortedDocs();

    if (_positions.empty()) {
        reset();
        return;             // All documents with words aborted
    }

    sortWords();

    // Sort for terms.
    ShiftBasedRadixSorter<PosInfo, FullRadix, std::less<PosInfo>, 56, true>::
        radix_sort(FullRadix(), std::less<PosInfo>(), &_positions[0], _positions.size(), 16);

    constexpr uint32_t NO_ELEMENT_ID = std::numeric_limits<uint32_t>::max();
    constexpr uint32_t NO_WORD_POS = std::numeric_limits<uint32_t>::max();
    uint32_t lastWordNum = 0;
    uint32_t lastElemId = 0;
    uint32_t lastWordPos = 0;
    uint32_t numWordIds = _wordRefs.size() - 1;
    uint32_t lastDocId = 0;
    vespalib::stringref word;
    bool emptyFeatures = true;
    uint32_t last_field_length = 0;

    _inserter.rewind();

    for (auto &i : _positions) {
        assert(i._wordNum <= numWordIds);
        (void) numWordIds;
        if (lastWordNum != i._wordNum || lastDocId != i._docId) {
            if (!emptyFeatures) {
                _features.set_num_occs(_features.word_positions().size());
                _inserter.add(lastDocId, _features);
                emptyFeatures = true;
            }
            if (lastWordNum != i._wordNum) {
                lastWordNum = i._wordNum;
                word = getWordFromNum(lastWordNum);
                _inserter.setNextWord(word);
            }
            lastDocId = i._docId;
            if (i.removed()) {
                _inserter.remove(lastDocId);
                continue;
            }
        }
        if (emptyFeatures) {
            if (!i.removed()) {
                emptyFeatures = false;
                _features.clear(lastDocId);
                lastElemId = NO_ELEMENT_ID;
                lastWordPos = NO_WORD_POS;
                const ElemInfo &elem = _elems[i._elemRef];
                _features.set_field_length(elem.get_field_length());
                last_field_length = elem.get_field_length();
            } else {
                continue; // ignore dup remove
            }
        } else {
            // removes must come before non-removes
            assert(!i.removed());
            const ElemInfo &elem = _elems[i._elemRef];
            assert(last_field_length == elem.get_field_length());
        }
        const ElemInfo &elem = _elems[i._elemRef];
        if (i._wordPos != lastWordPos || i._elemId != lastElemId) {
            _features.addNextOcc(i._elemId, i._wordPos,
                                 elem._weight, elem._len);
            lastElemId = i._elemId;
            lastWordPos = i._wordPos;
        } else {
            // silently ignore duplicate annotations
        }
    }

    if (!emptyFeatures) {
        _features.set_num_occs(_features.word_positions().size());
        _inserter.add(lastDocId, _features);
    }
    _inserter.flush();
    _inserter.commit();
    reset();
}

void
FieldInverter::pushDocuments()
{
    try {
        push_documents_internal();
    } catch (vespalib::OverflowException &e) {
        const Schema::IndexField &field = _schema.getIndexField(_fieldId);
        vespalib::asciistream s;
        s << "FieldInverter::pushDocuments(), caught exception for field " << field.getName();
        throw vespalib::OverflowException(s.c_str(), e);
    }
}

}

