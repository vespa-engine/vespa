// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/query.h>
#include <vespa/vsm/common/document.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>

namespace vsm {

typedef size_t termcount_t;
typedef size_t termsize_t;

#if defined(COLLECT_CHAR_STAT)
  #define NEED_CHAR_STAT(a) { a; }
#else
  #define NEED_CHAR_STAT(a)
#endif

typedef ucs4_t cmptype_t;
typedef vespalib::Array<cmptype_t> SearcherBuf;
typedef std::shared_ptr<SearcherBuf> SharedSearcherBuf;
typedef std::vector<char> CharVector;

class FieldSearcherBase
{
protected:
    search::QueryTermList _qtl;
private:
    CharVector    _qtlFastBuffer;
protected:
    FieldSearcherBase();
    FieldSearcherBase(const FieldSearcherBase & org);
    virtual ~FieldSearcherBase(void);
    FieldSearcherBase & operator = (const FieldSearcherBase & org);
    void prepare(const search::QueryTermList & qtl);
    size_t          _qtlFastSize;
    search::v16qi  *_qtlFast;
};

class FieldSearcher : public FieldSearcherBase, public search::Object
{
public:
    enum MatchType {
        REGULAR,
        PREFIX,
        SUBSTRING,
        SUFFIX,
        EXACT
    };

    FieldSearcher(const FieldIdT & fId, bool defaultPrefix=false);
    ~FieldSearcher();
    bool search(const StorageDocument & doc);
    virtual void prepare(search::QueryTermList & qtl, const SharedSearcherBuf & buf);
    const FieldIdT & field()         const { return _field; }
    void field(const FieldIdT & v)         { _field = v; prepareFieldId(); }
    bool prefix()                    const { return _matchType == PREFIX; }
    bool substring()                 const { return _matchType == SUBSTRING; }
    bool suffix()                    const { return _matchType == SUFFIX; }
    bool exact()                     const { return _matchType == EXACT; }
    void setMatchType(MatchType mt)        { _matchType = mt; }
    static void init();
    static search::byte fold(search::byte c)               { return _foldLowCase[c]; }
    static search::byte iswordchar(search::byte c)         { return _wordChar[c]; }
    static search::byte isspace(search::byte c)            { return ! iswordchar(c); }
    static size_t countWords(const FieldRef & f);
    unsigned pureUsAsciiCount()      const { return _pureUsAsciiCount; }
    unsigned pureUsAsciiFieldCount() const { return _pureUsAsciiFieldCount; }
    unsigned anyUtf8Count()          const { return _anyUtf8Count; }
    unsigned anyUtf8FieldCount()     const { return _anyUtf8FieldCount; }
    unsigned badUtf8Count()          const { return _badUtf8Count; }
    unsigned zeroCount()             const { return _zeroCount; }
    unsigned utf8Count(size_t sz)    const { return _utf8Count[1+sz]; }
    const unsigned * utf8Count()     const { return _utf8Count; }
    int32_t getCurrentWeight()       const { return _currentElementWeight; }
    void addStat(const FieldSearcher & toAdd);
    void zeroStat();
    FieldSearcher & maxFieldLength(uint32_t maxFieldLength_) { _maxFieldLength = maxFieldLength_; return *this; }
    size_t maxFieldLength() const { return _maxFieldLength; }

private:
    class IteratorHandler : public document::fieldvalue::IteratorHandler {
    private:
        FieldSearcher & _searcher;

        void onPrimitive(uint32_t fid, const Content & c) override;
        void onCollectionStart(const Content & c) override;
        void onStructStart(const Content & c) override;

    public:
        IteratorHandler(FieldSearcher & searcher) : _searcher(searcher) {}
    };
    friend class IteratorHandler; // to allow calls to onValue();

    void prepareFieldId();
    void setCurrentWeight(int32_t weight) { _currentElementWeight = weight; }
    bool onSearch(const StorageDocument & doc);
    virtual void onValue(const document::FieldValue & fv) = 0;
    FieldIdT      _field;
    MatchType     _matchType;
    unsigned      _maxFieldLength;
    int32_t       _currentElementWeight; // Contains the weight of the current item being evaluated.
    /// Number of bytes in blocks containing pure us-ascii
    unsigned _pureUsAsciiCount;
    /// Number of blocks containing pure us-ascii
    unsigned _pureUsAsciiFieldCount;
    /// Number of bytes in blocks containing any non us-ascii
    unsigned _anyUtf8Count;
    /// Number of blocks containing any non us-ascii
    unsigned _anyUtf8FieldCount;
protected:
    /// Number of terms searched.
    unsigned _words;
    /// Number of utf8 bytes by utf8 size.
    unsigned _utf8Count[6];
    unsigned _badUtf8Count;
    unsigned _zeroCount;
protected:
    void addPureUsAsciiField(size_t sz) { _pureUsAsciiCount += sz; _pureUsAsciiFieldCount++;; }
    void addAnyUtf8Field(size_t sz)     { _anyUtf8Count += sz; _anyUtf8FieldCount++; }
    /**
     * Adds a hit to the given query term.
     * For each call to onValue() a batch of words are processed, and the position is local to this batch.
     **/
    void addHit(search::QueryTerm & qt, uint32_t pos) const {
        qt.add(_words + pos, field(), getCurrentWeight());
    }
public:
    static search::byte _foldLowCase[256];
    static search::byte _wordChar[256];
};

typedef search::ObjectContainer<FieldSearcher> FieldSearcherContainer;
typedef std::vector<FieldSearcherContainer> FieldIdTSearcherMapT;

class FieldIdTSearcherMap : public FieldIdTSearcherMapT
{
public:
    void prepare(const DocumentTypeIndexFieldMapT & difm, const SharedSearcherBuf & searcherBuf, search::Query & query);
};

}
