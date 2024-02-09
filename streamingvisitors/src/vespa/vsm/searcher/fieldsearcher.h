// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/vsm/common/document.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/array.h>
#include <utility>

namespace search::fef { class IQueryEnvironment; }

namespace vsm {

using termcount_t = size_t;
using termsize_t = size_t;

using ucs4_t = uint32_t;
using cmptype_t = ucs4_t;
using SearcherBuf = vespalib::Array<cmptype_t>;
using SharedSearcherBuf = std::shared_ptr<SearcherBuf>;

class FieldSearcherBase
{
public:
    FieldSearcherBase & operator = (const FieldSearcherBase & org) = delete;
protected:
    FieldSearcherBase() noexcept;
    FieldSearcherBase(const FieldSearcherBase & org);
    virtual ~FieldSearcherBase();
    void prepare(const search::streaming::QueryTermList & qtl);

    search::streaming::QueryTermList _qtl;
};

class FieldSearcher : public FieldSearcherBase
{
public:
    using Normalizing = search::Normalizing;
    enum MatchType {
        REGULAR,
        PREFIX,
        SUBSTRING,
        SUFFIX,
        EXACT,
    };

    explicit FieldSearcher(FieldIdT fId) noexcept : FieldSearcher(fId, false) {}
    FieldSearcher(FieldIdT fId, bool defaultPrefix) noexcept;
    ~FieldSearcher() override;
    [[nodiscard]] virtual std::unique_ptr<FieldSearcher> duplicate() const = 0;
    bool search(const StorageDocument & doc);
    virtual void prepare(search::streaming::QueryTermList& qtl, const SharedSearcherBuf& buf,
                         const vsm::FieldPathMapT& field_paths, search::fef::IQueryEnvironment& query_env);

    FieldIdT field()             const noexcept { return _field; }
    bool prefix()                const noexcept { return _matchType == PREFIX; }
    bool substring()             const noexcept { return _matchType == SUBSTRING; }
    bool suffix()                const noexcept { return _matchType == SUFFIX; }
    bool exact()                 const noexcept { return _matchType == EXACT; }
    Normalizing normalize_mode() const noexcept { return _normalize_mode; }
    MatchType match_type()       const noexcept { return _matchType; }
    void match_type(MatchType mt)         noexcept { _matchType = mt; }
    void normalize_mode(Normalizing mode) noexcept { _normalize_mode = mode; }
    void field(FieldIdT v)                noexcept { _field = v; prepareFieldId(); }
    static void init();
    static search::byte fold(search::byte c)               { return _foldLowCase[c]; }
    static search::byte iswordchar(search::byte c)         { return _wordChar[c]; }
    static search::byte isspace(search::byte c)            { return ! iswordchar(c); }
    static size_t countWords(const FieldRef & f);
    int32_t currentWeight()       const { return _currentElementWeight; }
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
        explicit IteratorHandler(FieldSearcher & searcher) noexcept : _searcher(searcher) {}
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
    Normalizing   _normalize_mode;
    unsigned      _maxFieldLength;
    uint32_t      _currentElementId;
    int32_t       _currentElementWeight; // Contains the weight of the current item being evaluated.
    std::vector<std::pair<search::streaming::QueryTerm*, uint32_t>> _element_length_fixups;
protected:
    /// Number of terms searched.
    unsigned      _words;
    /// Number of utf8 bytes by utf8 size.
    unsigned      _badUtf8Count;
    /**
     * Adds a hit to the given query term.
     * For each call to onValue() a batch of words are processed, and the position is local to this batch.
     **/
    void addHit(search::streaming::QueryTerm & qt, uint32_t pos) {
        _element_length_fixups.emplace_back(&qt, qt.add(field(), _currentElementId, _currentElementWeight, pos));
    }
    void set_element_length(uint32_t element_length);
public:
    static search::byte _foldLowCase[256];
    static search::byte _wordChar[256];
};

using FieldSearcherContainer = std::unique_ptr<FieldSearcher>;
using FieldIdTSearcherMapT = std::vector<FieldSearcherContainer>;

class FieldIdTSearcherMap : public FieldIdTSearcherMapT
{
    void prepare_term(const DocumentTypeIndexFieldMapT& difm, search::streaming::QueryTerm* qt, FieldIdT fid, vespalib::hash_set<const void*>& seen, search::streaming::QueryTermList& onlyInIndex);
public:
    void prepare(const DocumentTypeIndexFieldMapT& difm, const SharedSearcherBuf& searcherBuf,
                 search::streaming::Query& query, const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env);
};

}
