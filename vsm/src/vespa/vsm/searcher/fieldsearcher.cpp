// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fieldsearcher.h"
#include <vespa/vsm/vsm/fieldsearchspec.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.searcher.fieldsearcher");

using search::byte;
using search::QueryTerm;
using search::QueryTermList;
using search::v16qi;
using search::Query;

namespace vsm {

class force
{
 public:
  force() { FieldSearcher::init(); }
};

static force __forceInit;

byte FieldSearcher::_foldLowCase[256];
byte FieldSearcher::_wordChar[256];

FieldSearcherBase::FieldSearcherBase() :
    _qtl(),
    _qtlFastBuffer(),
    _qtlFastSize(0),
    _qtlFast(NULL)
{
}

FieldSearcherBase::FieldSearcherBase(const FieldSearcherBase & org) :
    _qtl(),
    _qtlFastBuffer(),
    _qtlFastSize(0),
    _qtlFast(NULL)
{
    prepare(org._qtl);
}

FieldSearcherBase::~FieldSearcherBase()
{
}

FieldSearcherBase & FieldSearcherBase::operator = (const FieldSearcherBase & org)
{
    if (this != &org) {
        prepare(org._qtl);
    }
    return *this;
}

void FieldSearcherBase::prepare(const QueryTermList & qtl)
{
    _qtl = qtl;
    _qtlFastBuffer.resize(sizeof(*_qtlFast)*(_qtl.size()+1), 0x13);
    _qtlFast = reinterpret_cast<v16qi *>(reinterpret_cast<unsigned long>(&_qtlFastBuffer[0]+15) & ~0xf);
    _qtlFastSize = 0;
    for(QueryTermList::iterator it=_qtl.begin(), mt=_qtl.end(); it != mt; it++) {
        const QueryTerm & qt = **it;
        memcpy(&_qtlFast[_qtlFastSize++], qt.getTerm(), std::min(size_t(16), qt.termLen()));
    }
}

FieldSearcher::FieldSearcher(const FieldIdT & fId, bool defaultPrefix) :
    FieldSearcherBase(),
    Object(),
    _field(fId),
    _matchType(defaultPrefix ? PREFIX : REGULAR),
    _maxFieldLength(0x100000),
    _currentElementId(0),
    _currentElementWeight(1),
    _pureUsAsciiCount(0),
    _pureUsAsciiFieldCount(0),
    _anyUtf8Count(0),
    _anyUtf8FieldCount(0),
    _words(0),
    _badUtf8Count(0),
    _zeroCount(0)
{
    zeroStat();
}

FieldSearcher::~FieldSearcher() = default;

bool FieldSearcher::search(const StorageDocument & doc)
{
    for(QueryTermList::iterator it=_qtl.begin(), mt=_qtl.end(); it != mt; it++) {
        QueryTerm & qt = **it;
        QueryTerm::FieldInfo & fInfo = qt.getFieldInfo(field());
        fInfo.setHitOffset(qt.getHitList().size());
    }
    onSearch(doc);
    for(QueryTermList::iterator it=_qtl.begin(), mt=_qtl.end(); it != mt; it++) {
        QueryTerm & qt = **it;
        QueryTerm::FieldInfo & fInfo = qt.getFieldInfo(field());
        fInfo.setHitCount(qt.getHitList().size() - fInfo.getHitOffset());
        fInfo.setFieldLength(_words);
    }
    _words = 0;
    return true;
}

void FieldSearcher::prepare(QueryTermList & qtl, const SharedSearcherBuf & UNUSED_PARAM(buf))
{
    FieldSearcherBase::prepare(qtl);
    prepareFieldId();
}

size_t FieldSearcher::countWords(const FieldRef & f)
{
    size_t words = 0;
    const char * n = f.c_str();
    const char * e = n + f.size();
    for( ; n < e; ++n) {
        for (; isspace(*n) && (n<e); ++n);
        const char * m = n;
        for (; iswordchar(*n) && (n<e); ++n);
        if (n > m) {
            words++;
        }
    }
    return words;
}

void FieldSearcher::prepareFieldId()
{
    for(QueryTermList::iterator it=_qtl.begin(), mt=_qtl.end(); it != mt; it++) {
        QueryTerm & qt = **it;
        qt.resizeFieldId(field());
    }
}

void FieldSearcher::addStat(const FieldSearcher & toAdd)
{
    _pureUsAsciiCount += toAdd._pureUsAsciiCount;
    _pureUsAsciiFieldCount += toAdd._pureUsAsciiFieldCount;
    _anyUtf8Count += toAdd._anyUtf8Count;
    _anyUtf8FieldCount += toAdd._anyUtf8FieldCount;
    _badUtf8Count += toAdd._badUtf8Count;
    _zeroCount += toAdd._zeroCount;
    for (size_t i=0; i<NELEMS(_utf8Count); i++) { _utf8Count[i] += toAdd._utf8Count[i]; }
}

void FieldSearcher::zeroStat()
{
    _pureUsAsciiCount = 0;
    _pureUsAsciiFieldCount = 0;
    _anyUtf8Count = 0;
    _anyUtf8FieldCount = 0;
    _badUtf8Count = 0;
    _zeroCount = 0;
    for (size_t i=0; i<NELEMS(_utf8Count); i++) { _utf8Count[i] = 0; }
}

void FieldSearcher::init()
{
    for (unsigned i = 0; i < NELEMS(_foldLowCase); i++) {
        _foldLowCase[i] = 0;
        _wordChar[i] = 0;
    }
    for (int i = 'A'; i <= 'Z'; i++) {
        _wordChar[i] = 0xFF;
        _foldLowCase[i] = i | 0x20;
    }
    for (int i = 'a'; i <= 'z'; i++) {
        _wordChar[i] = 0xFF;
        _foldLowCase[i] = i;
    }
    for (int i = '0'; i <= '9'; i++) {
        _wordChar[i] = 0xFF;
        _foldLowCase[i] = i;
    }
    for (int i = 0xC0; i <= 0xFF; i++) {
        _wordChar[i] = 0xFF;
    }
    _wordChar[0xd7] = 0;
    _wordChar[0xf7] = 0;

    if (1) /* _doAccentRemoval */ {
        _foldLowCase[0xc0] = 'a';
        _foldLowCase[0xc1] = 'a';
        _foldLowCase[0xc2] = 'a';
        _foldLowCase[0xc3] = 'a';  // A tilde
        _foldLowCase[0xc7] = 'c';
        _foldLowCase[0xc8] = 'e';
        _foldLowCase[0xc9] = 'e';
        _foldLowCase[0xca] = 'e';
        _foldLowCase[0xcb] = 'e';
        _foldLowCase[0xcc] = 'i';  // I grave
        _foldLowCase[0xcd] = 'i';
        _foldLowCase[0xce] = 'i';
        _foldLowCase[0xcf] = 'i';
        _foldLowCase[0xd3] = 'o';
        _foldLowCase[0xd4] = 'o';
        _foldLowCase[0xda] = 'u';
        _foldLowCase[0xdb] = 'u';

        _foldLowCase[0xe0] = 'a';
        _foldLowCase[0xe1] = 'a';
        _foldLowCase[0xe2] = 'a';
        _foldLowCase[0xe3] = 'a'; // a tilde
        _foldLowCase[0xe7] = 'c';
        _foldLowCase[0xe8] = 'e';
        _foldLowCase[0xe9] = 'e';
        _foldLowCase[0xea] = 'e';
        _foldLowCase[0xeb] = 'e';
        _foldLowCase[0xec] = 'i'; // i grave
        _foldLowCase[0xed] = 'i';
        _foldLowCase[0xee] = 'i';
        _foldLowCase[0xef] = 'i';
        _foldLowCase[0xf3] = 'o';
        _foldLowCase[0xf4] = 'o';
        _foldLowCase[0xfa] = 'u';
        _foldLowCase[0xfb] = 'u';
    }
}

void FieldIdTSearcherMap::prepare(const DocumentTypeIndexFieldMapT & difm, const SharedSearcherBuf & searcherBuf, Query & query)
{
    QueryTermList qtl;
    query.getLeafs(qtl);
    vespalib::string tmp;
    for (FieldIdTSearcherMap::iterator it = begin(), mt = end(); it != mt; it++) {
        QueryTermList onlyInIndex;
        FieldIdT fid = (*it)->field();
        for (QueryTermList::iterator qt = qtl.begin(), mqt = qtl.end(); qt != mqt; qt++) {
            QueryTerm * q = *qt;
            for (DocumentTypeIndexFieldMapT::const_iterator dt(difm.begin()), dmt(difm.end()); dt != dmt; dt++) {
                const IndexFieldMapT & fim = dt->second;
                IndexFieldMapT::const_iterator found = fim.find(FieldSearchSpecMap::stripNonFields(q->index()));
                if (found != fim.end()) {
                    const FieldIdTList & index = found->second;
                    if ((find(index.begin(), index.end(), fid) != index.end()) && (find(onlyInIndex.begin(), onlyInIndex.end(), q) == onlyInIndex.end())) {
                        onlyInIndex.push_back(q);
                    }
                } else {
                    LOG(debug, "Could not find the requested index=%s in the index config map. Query does not fit search definition.", q->index().c_str());
                }
            }
        }
        /// Should perhaps do a unique on onlyInIndex
        (*it)->prepare(onlyInIndex, searcherBuf);
        if (logger.wants(ns_log::Logger::spam)) {
            char tmpBuf[16];
            sprintf(tmpBuf,"%d", fid);
            tmp += tmpBuf;
            tmp += ", ";
        }
    }
    LOG(debug, "Will search in %s", tmp.c_str());
}

bool FieldSearcher::onSearch(const StorageDocument & doc)
{
    bool retval(true);
    size_t fNo(field());
    const StorageDocument::SubDocument & sub = doc.getComplexField(fNo);
    if (sub.getFieldValue() != NULL) {
        LOG(spam, "onSearch %s : %s", sub.getFieldValue()->getClass().name(), sub.getFieldValue()->toString().c_str());
        IteratorHandler ih(*this);
        sub.getFieldValue()->iterateNested(sub.getRange(), ih);
    }
    return retval;
}

void
FieldSearcher::IteratorHandler::onPrimitive(uint32_t, const Content & c)
{
    LOG(spam, "onPrimitive: field value '%s'", c.getValue().toString().c_str());
    _searcher.setCurrentWeight(c.getWeight());
    _searcher.setCurrentElementId(getArrayIndex());
    _searcher.onValue(c.getValue());
}

void
FieldSearcher::IteratorHandler::onCollectionStart(const Content & c)
{
    const document::FieldValue & fv = c.getValue();
    LOG(spam, "onCollectionStart: field value '%s'", fv.toString().c_str());
    if (fv.inherits(document::ArrayFieldValue::classId)) {
        const document::ArrayFieldValue & afv = static_cast<const document::ArrayFieldValue &>(fv);
        LOG(spam, "onCollectionStart: Array size = '%zu'", afv.size());
    } else if (fv.inherits(document::WeightedSetFieldValue::classId)) {
        const document::WeightedSetFieldValue & wsfv = static_cast<const document::WeightedSetFieldValue &>(fv);
        LOG(spam, "onCollectionStart: WeightedSet size = '%zu'", wsfv.size());
    }
}

void
FieldSearcher::IteratorHandler::onStructStart(const Content & c)
{
    LOG(spam, "onStructStart: field value '%s'", c.getValue().toString().c_str());
}


}
