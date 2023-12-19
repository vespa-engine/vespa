// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/vsm/common/document.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vespalib/util/array.h>

namespace search::fef { class IQueryEnvironment; }

namespace vsm {

using termcount_t = size_t;
using termsize_t = size_t;

using ucs4_t = uint32_t;
using cmptype_t = ucs4_t;
using SearcherBuf = vespalib::Array<cmptype_t>;
using SharedSearcherBuf = std::shared_ptr<SearcherBuf>;
using CharVector = std::vector<char>;

class FieldSearcherBase
{
protected:
    search::streaming::QueryTermList _qtl;
private:
    CharVector    _qtlFastBuffer;
protected:
    FieldSearcherBase() noexcept;
    FieldSearcherBase(const FieldSearcherBase & org);
    virtual ~FieldSearcherBase();
    FieldSearcherBase & operator = (const FieldSearcherBase & org);
    void prepare(const search::streaming::QueryTermList & qtl);
    size_t          _qtlFastSize;
    search::v16qi  *_qtlFast;
};

class FieldSearcher : public FieldSearcherBase
{
public:
    enum MatchType {
        REGULAR,
        PREFIX,
        SUBSTRING,
        SUFFIX,
        EXACT
    };

    explicit FieldSearcher(FieldIdT fId) noexcept : FieldSearcher(fId, false) {}
    FieldSearcher(FieldIdT fId, bool defaultPrefix) noexcept;
    ~FieldSearcher() override;
    virtual std::unique_ptr<FieldSearcher> duplicate() const = 0;
    bool search(const StorageDocument & doc);
    virtual void prepare(search::streaming::QueryTermList& qtl,
                         const SharedSearcherBuf& buf,
                         const vsm::FieldPathMapT& field_paths,
                         search::fef::IQueryEnvironment& query_env);

    FieldIdT field()                 const { return _field; }
    void field(FieldIdT v)                 { _field = v; prepareFieldId(); }
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
    int32_t getCurrentWeight()       const { return _currentElementWeight; }
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
        explicit IteratorHandler(FieldSearcher & searcher) : _searcher(searcher) {}
    };
    friend class IteratorHandler; // to allow calls to onValue();

    void prepareFieldId();
    void setCurrentWeight(int32_t weight) { _currentElementWeight = weight; }
    void setCurrentElementId(int32_t weight) { _currentElementId = weight; }
    bool onSearch(const StorageDocument & doc);
    virtual void onValue(const document::FieldValue & fv) = 0;
    virtual void onStructValue(const document::StructFieldValue &) { }
    FieldIdT      _field;
    MatchType     _matchType;
    unsigned      _maxFieldLength;
    uint32_t      _currentElementId;
    int32_t       _currentElementWeight; // Contains the weight of the current item being evaluated.
protected:
    /// Number of terms searched.
    unsigned _words;
    /// Number of utf8 bytes by utf8 size.
    unsigned _badUtf8Count;
    unsigned _zeroCount;
protected:
    /**
     * Adds a hit to the given query term.
     * For each call to onValue() a batch of words are processed, and the position is local to this batch.
     **/
    void addHit(search::streaming::QueryTerm & qt, uint32_t pos) const {
        qt.add(_words + pos, field(), _currentElementId, getCurrentWeight());
    }
public:
    static search::byte _foldLowCase[256];
    static search::byte _wordChar[256];
};

using FieldSearcherContainer = std::unique_ptr<FieldSearcher>;
using FieldIdTSearcherMapT = std::vector<FieldSearcherContainer>;

class FieldIdTSearcherMap : public FieldIdTSearcherMapT
{
public:
    void prepare(const DocumentTypeIndexFieldMapT& difm,
                 const SharedSearcherBuf& searcherBuf,
                 search::streaming::Query& query,
                 const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env);
};

}
