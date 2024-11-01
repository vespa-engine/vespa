// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskindex.h"
#include "disktermblueprint.h"
#include "fileheader.h"
#include "pagedict4randread.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/cache.hpp>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.diskindex");

using namespace search::index;
using namespace search::query;
using namespace search::queryeval;

namespace search::diskindex {

void swap(DiskIndex::LookupResult & a, DiskIndex::LookupResult & b)
{
    a.swap(b);
}

DiskIndex::LookupResult::LookupResult() noexcept
    : DictionaryLookupResult(),
      indexId(0u)
{
}

DiskIndex::Key::Key() noexcept = default;
DiskIndex::Key::Key(IndexList indexes, std::string_view word) noexcept :
    _word(word),
    _indexes(std::move(indexes))
{
}

DiskIndex::Key::Key(const Key &) = default;
DiskIndex::Key & DiskIndex::Key::operator = (const Key &) = default;
DiskIndex::Key::~Key() = default;

DiskIndex::DiskIndex(const std::string &indexDir, std::shared_ptr<IPostingListCache> posting_list_cache, size_t dictionary_cache_size)
    : _indexDir(indexDir),
      _dictionary_cache_size(dictionary_cache_size),
      _schema(),
      _field_indexes(),
      _nonfield_size_on_disk(0),
      _tuneFileSearch(),
      _posting_list_cache(std::move(posting_list_cache)),
      _cache(*this, dictionary_cache_size)
{
    calculate_nonfield_size_on_disk();
}

DiskIndex::~DiskIndex() = default;

bool
DiskIndex::loadSchema()
{
    std::string schemaName = _indexDir + "/schema.txt";
    if (!_schema.loadFromFile(schemaName)) {
        LOG(error, "Could not open schema '%s'", schemaName.c_str());
        return false;
    }
    if (!SchemaUtil::validateSchema(_schema)) {
        LOG(error, "Could not validate schema loaded from '%s'", schemaName.c_str());
        return false;
    }
    return true;
}

bool
DiskIndex::openDictionaries(const TuneFileSearch &tuneFileSearch)
{
    for (SchemaUtil::IndexIterator itr(_schema); itr.isValid(); ++itr) {
        std::string field_dir = _indexDir + "/" + itr.getName();
        _field_indexes.emplace_back(_posting_list_cache);
        if (!_field_indexes.back().open_dictionary(field_dir, tuneFileSearch)) {
            _field_indexes.clear();
            return false;
        }
    }
    return true;
}

bool
DiskIndex::setup(const TuneFileSearch &tuneFileSearch)
{
    if (!loadSchema() ) {
        return false;
    }
    if (!openDictionaries(tuneFileSearch)) {
        return false;
    }
    for (SchemaUtil::IndexIterator itr(_schema); itr.isValid(); ++itr) {
        std::string fieldDir = _indexDir + "/" + itr.getName() + "/";
        auto& field_index = _field_indexes[itr.getIndex()];
        if (!field_index.open(fieldDir, tuneFileSearch)) {
            return false;
        }
    }
    _tuneFileSearch = tuneFileSearch;
    return true;
}

bool
DiskIndex::setup(const TuneFileSearch &tuneFileSearch, const DiskIndex &old)
{
    if (tuneFileSearch != old._tuneFileSearch) {
        return setup(tuneFileSearch);
    }
    if (!loadSchema() || !openDictionaries(tuneFileSearch)) {
        return false;
    }
    const Schema &oldSchema = old._schema;
    for (SchemaUtil::IndexIterator itr(_schema); itr.isValid(); ++itr) {
        std::string fieldDir = _indexDir + "/" + itr.getName() + "/";
        SchemaUtil::IndexSettings settings = itr.getIndexSettings();
        if (settings.hasError()) {
            return false;
        }
        auto& field_index = _field_indexes[itr.getIndex()];
        SchemaUtil::IndexIterator oItr(oldSchema, itr);
        if (!itr.hasMatchingOldFields(oldSchema) || !oItr.isValid()) {
            if (!field_index.open(fieldDir, tuneFileSearch)) {
                return false;
            }
        } else {
            auto& old_field_index = old._field_indexes[oItr.getIndex()];
            field_index.reuse_files(old_field_index);
        }
    }
    _tuneFileSearch = tuneFileSearch;
    return true;
}

DiskIndex::LookupResult
DiskIndex::lookup(uint32_t index, std::string_view word)
{
    /** Only used for testing */
    IndexList indexes;
    indexes.push_back(index);
    Key key(std::move(indexes), word);
    LookupResultVector resultV(1);
    LookupResult result;
    if ( read(key, resultV)) {
        result.swap(resultV[0]);
    }
    return result;
}

namespace {

bool
containsAll(const DiskIndex::IndexList & indexes, const DiskIndex::LookupResultVector & result)
{
    for (uint32_t index : indexes) {
        bool found(false);
        for (size_t i(0); !found && (i < result.size()); i++) {
            found = index == result[i].indexId;
        }
        if ( ! found ) {
            return false;
        }
    }
    return true;
}

DiskIndex::IndexList
unite(const DiskIndex::IndexList & indexes, const DiskIndex::LookupResultVector & result)
{
    vespalib::hash_set<uint32_t> all;
    for (uint32_t index : indexes) {
        all.insert(index);
    }
    for (const DiskIndex::LookupResult & lr : result) {
        all.insert(lr.indexId);
    }
    DiskIndex::IndexList v;
    v.reserve(all.size());
    for (uint32_t indexId : all) {
        v.push_back(indexId);
    }
    return v;
}

}

DiskIndex::LookupResultVector
DiskIndex::lookup(const std::vector<uint32_t> & indexes, std::string_view word)
{
    Key key(indexes, word);
    LookupResultVector result;
    if (_dictionary_cache_size > 0) {
        result = _cache.read(key);
        if (!containsAll(indexes, result)) {
            key = Key(unite(indexes, result), word);
            _cache.invalidate(key);
            result = _cache.read(key);
        }
    } else {
        read(key, result);
    }
    return result;
}

bool
DiskIndex::read(const Key & key, LookupResultVector & result)
{
    uint64_t wordNum(0);
    const IndexList & indexes(key.getIndexes());
    result.resize(indexes.size());
    for (size_t i(0); i < result.size(); i++) {
        LookupResult & lr(result[i]);
        lr.indexId = indexes[i];
        PostingListOffsetAndCounts offsetAndCounts;
        wordNum = 0;
        SchemaUtil::IndexIterator it(_schema, lr.indexId);
        uint32_t fieldId = it.getIndex();
        if (fieldId < _field_indexes.size()) {
            (void) _field_indexes[fieldId].get_dictionary()->lookup(key.getWord(), wordNum,offsetAndCounts);
        }
        lr.wordNum = wordNum;
        lr.counts.swap(offsetAndCounts._counts);
        lr.bitOffset = offsetAndCounts._offset;
    }
    return true;
}

index::PostingListHandle
DiskIndex::readPostingList(const LookupResult &lookupRes) const
{
    auto& field_index = _field_indexes[lookupRes.indexId];
    return field_index.read_posting_list(lookupRes);
}

BitVector::UP
DiskIndex::readBitVector(const LookupResult &lookupRes) const
{
    auto& field_index = _field_indexes[lookupRes.indexId];
    return field_index.read_bit_vector(lookupRes);
}

std::unique_ptr<search::queryeval::SearchIterator>
DiskIndex::create_iterator(const LookupResult& lookup_result,
                           const index::PostingListHandle& handle,
                           const search::fef::TermFieldMatchDataArray& tfmda) const
{
    auto& field_index = _field_indexes[lookup_result.indexId];
    return field_index.create_iterator(lookup_result, handle, tfmda);
}

namespace {

const std::vector<std::string> nonfield_file_names{
    "docsum.qcnt",
    "schema.txt",
    "schema.txt.orig",
    "selector.dat",
    "serial.dat"
};

}

void
DiskIndex::calculate_nonfield_size_on_disk()
{
    _nonfield_size_on_disk = FieldIndex::calculate_size_on_disk(_indexDir + "/", nonfield_file_names);
}

namespace {

DiskIndex::LookupResult G_nothing;

class LookupCache {
public:
    LookupCache(DiskIndex & diskIndex, const std::vector<uint32_t> & fieldIds)
        : _diskIndex(diskIndex),
          _fieldIds(fieldIds),
          _cache()
    { }
    const DiskIndex::LookupResult &
    lookup(const std::string & word, uint32_t fieldId) {
        auto it = _cache.find(word);
        if (it == _cache.end()) {
            _cache[word] = _diskIndex.lookup(_fieldIds, word);
            it = _cache.find(word);
        }
        for (const auto & result : it->second) {
            if (result.indexId == fieldId) {
                return result;
            }
        }
        return G_nothing;
    }
private:

    using Cache = vespalib::hash_map<std::string, DiskIndex::LookupResultVector>;
    DiskIndex &                   _diskIndex;
    const std::vector<uint32_t> & _fieldIds;
    Cache                         _cache;
};

class CreateBlueprintVisitor : public CreateBlueprintVisitorHelper {
private:
    LookupCache      &_cache;
    DiskIndex        &_diskIndex;
    const FieldSpec  &_field;
    const uint32_t    _fieldId;

public:
    CreateBlueprintVisitor(LookupCache & cache, DiskIndex &diskIndex,
                           const IRequestContext & requestContext,
                           const FieldSpec &field,
                           uint32_t fieldId)
        : CreateBlueprintVisitorHelper(diskIndex, field, requestContext),
          _cache(cache),
          _diskIndex(diskIndex),
          _field(field),
          _fieldId(fieldId)
    {
    }

    template <class TermNode>
    void visitTerm(TermNode &n) {
        const std::string termStr = termAsString(n);
        const DiskIndex::LookupResult & lookupRes = _cache.lookup(termStr, _fieldId);
        if (lookupRes.valid()) {
            bool useBitVector = _field.isFilter();
            setResult(std::make_unique<DiskTermBlueprint>(_field, _diskIndex, termStr, lookupRes, useBitVector));
        } else {
            setResult(std::make_unique<EmptyBlueprint>(_field));
        }
    }

    void visit(NumberTerm &n) override {
        handleNumberTermAsText(n);
    }

    void not_supported(Node &) {}

    void visit(LocationTerm &n)  override { visitTerm(n); }
    void visit(PrefixTerm &n)    override { visitTerm(n); }
    void visit(RangeTerm &n)     override { visitTerm(n); }
    void visit(StringTerm &n)    override { visitTerm(n); }
    void visit(SubstringTerm &n) override { visitTerm(n); }
    void visit(SuffixTerm &n)    override { visitTerm(n); }
    void visit(RegExpTerm &n)    override { visitTerm(n); }
    void visit(PredicateQuery &n) override { not_supported(n); }
    void visit(NearestNeighborTerm &n) override { not_supported(n); }
    void visit(FuzzyTerm &n)    override { visitTerm(n); }
};

Blueprint::UP
createBlueprintHelper(LookupCache & cache, DiskIndex & diskIndex, const IRequestContext & requestContext,
                      const FieldSpec &field, uint32_t fieldId, const Node &term)
{
    if (fieldId != Schema::UNKNOWN_FIELD_ID) {
        CreateBlueprintVisitor visitor(cache, diskIndex, requestContext, field, fieldId);
        const_cast<Node &>(term).accept(visitor);
        return visitor.getResult();
    }
    return std::make_unique<EmptyBlueprint>(field);
}

}

Blueprint::UP
DiskIndex::createBlueprint(const IRequestContext & requestContext, const FieldSpec &field, const Node &term)
{
    std::vector<uint32_t> fieldIds;
    fieldIds.push_back(_schema.getIndexFieldId(field.getName()));
    LookupCache cache(*this, fieldIds);
    return createBlueprintHelper(cache, *this, requestContext, field, fieldIds[0], term);
}

Blueprint::UP
DiskIndex::createBlueprint(const IRequestContext & requestContext, const FieldSpecList &fields, const Node &term)
{
    if (fields.empty()) {
        return std::make_unique<EmptyBlueprint>();
    }

    std::vector<uint32_t> fieldIds;
    fieldIds.reserve(fields.size());
    for (size_t i(0); i< fields.size(); i++) {
        const FieldSpec & field = fields[i];
        uint32_t fieldId = _schema.getIndexFieldId(field.getName());
        if (fieldId != Schema::UNKNOWN_FIELD_ID) {
            fieldIds.push_back(_schema.getIndexFieldId(field.getName()));
        }
    }
    auto orbp = std::make_unique<OrBlueprint>();
    LookupCache cache(*this, fieldIds);
    for (size_t i(0); i< fields.size(); i++) {
        const FieldSpec & field = fields[i];
        orbp->addChild(createBlueprintHelper(cache, *this, requestContext, field, _schema.getIndexFieldId(field.getName()), term));
    }
    if (orbp->childCnt() == 1) {
        return orbp->removeChild(0);
    } else {
        return orbp;
    }
}

FieldLengthInfo
DiskIndex::get_field_length_info(const std::string& field_name) const
{
    uint32_t fieldId = _schema.getIndexFieldId(field_name);
    if (fieldId != Schema::UNKNOWN_FIELD_ID) {
        return _field_indexes[fieldId].get_field_length_info();
    } else {
        return {};
    }
}

SearchableStats
DiskIndex::get_stats() const
{
    SearchableStats stats;
    uint64_t size_on_disk = _nonfield_size_on_disk;
    uint32_t field_id = 0;
    for (auto& field_index : _field_indexes) {
        auto field_stats = field_index.get_stats();
        size_on_disk += field_stats.size_on_disk();
        stats.add_field_stats(_schema.getIndexField(field_id).getName(), field_stats);
        ++field_id;
    }
    stats.sizeOnDisk(size_on_disk);
    return stats;
}

}
