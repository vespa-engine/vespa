// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskindex.h"
#include "disktermblueprint.h"
#include "pagedict4randread.h"
#include "fileheader.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/cache.hpp>

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
    : indexId(0u),
      wordNum(0),
      counts(),
      bitOffset(0)
{
}

DiskIndex::Key::Key() noexcept = default;
DiskIndex::Key::Key(IndexList indexes, vespalib::stringref word) noexcept :
    _word(word),
    _indexes(std::move(indexes))
{
}

DiskIndex::Key::Key(const Key &) = default;
DiskIndex::Key & DiskIndex::Key::operator = (const Key &) = default;
DiskIndex::Key::~Key() = default;

DiskIndex::DiskIndex(const vespalib::string &indexDir, size_t cacheSize)
    : _indexDir(indexDir),
      _cacheSize(cacheSize),
      _schema(),
      _postingFiles(),
      _bitVectorDicts(),
      _dicts(),
      _tuneFileSearch(),
      _cache(*this, cacheSize),
      _size(0)
{
    calculateSize();
}

DiskIndex::~DiskIndex() = default;

bool
DiskIndex::loadSchema()
{
    vespalib::string schemaName = _indexDir + "/schema.txt";
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
        vespalib::string dictName =
            _indexDir + "/" + itr.getName() + "/dictionary";
        auto dict = std::make_unique<PageDict4RandRead>();
        if (!dict->open(dictName, tuneFileSearch._read)) {
            LOG(warning, "Could not open disk dictionary '%s'", dictName.c_str());
            _dicts.clear();
            return false;
        }
        _dicts.push_back(std::move(dict));
    }
    return true;
}

bool
DiskIndex::openField(const vespalib::string &fieldDir,
                     const TuneFileSearch &tuneFileSearch)
{
    vespalib::string postingName = fieldDir + "posocc.dat.compressed";

    DiskPostingFile::SP pFile;
    BitVectorDictionary::SP bDict;
    FileHeader fileHeader;
    bool dynamicK = false;
    if (fileHeader.taste(postingName, tuneFileSearch._read)) {
        if (fileHeader.getVersion() == 1 &&
            fileHeader.getBigEndian() &&
            fileHeader.getFormats().size() == 2 &&
            fileHeader.getFormats()[0] ==
            DiskPostingFileDynamicKReal::getIdentifier() &&
            fileHeader.getFormats()[1] ==
            DiskPostingFileDynamicKReal::getSubIdentifier()) {
            dynamicK = true;
        } else if (fileHeader.getVersion() == 1 &&
                   fileHeader.getBigEndian() &&
                   fileHeader.getFormats().size() == 2 &&
                   fileHeader.getFormats()[0] ==
                   DiskPostingFileReal::getIdentifier() &&
                   fileHeader.getFormats()[1] ==
                   DiskPostingFileReal::getSubIdentifier()) {
            dynamicK = false;
        } else {
            LOG(warning,
                "Could not detect format for posocc file read %s",
                postingName.c_str());
        }
    }
    pFile.reset(dynamicK ?
                new DiskPostingFileDynamicKReal() :
                new DiskPostingFileReal());
    if (!pFile->open(postingName, tuneFileSearch._read)) {
        LOG(warning,
            "Could not open posting list file '%s'",
            postingName.c_str());
        return false;
    }

    bDict.reset(new BitVectorDictionary());
    if (!bDict->open(fieldDir, tuneFileSearch._read, BitVectorKeyScope::PERFIELD_WORDS)) {
        LOG(warning,
            "Could not open bit vector dictionary in '%s'",
            fieldDir.c_str());
        return false;
    }
    _postingFiles.push_back(pFile);
    _bitVectorDicts.push_back(bDict);
    return true;
}

bool
DiskIndex::setup(const TuneFileSearch &tuneFileSearch)
{
    if (!loadSchema() || !openDictionaries(tuneFileSearch)) {
        return false;
    }
    for (SchemaUtil::IndexIterator itr(_schema); itr.isValid(); ++itr) {
        vespalib::string fieldDir =
            _indexDir + "/" + itr.getName() + "/";
        if (!openField(fieldDir, tuneFileSearch)) {
            return false;
        }
    }
    _tuneFileSearch = tuneFileSearch;
    return true;
}

bool
DiskIndex::setup(const TuneFileSearch &tuneFileSearch,
                 const DiskIndex &old)
{
    if (tuneFileSearch != old._tuneFileSearch) {
        return setup(tuneFileSearch);
    }
    if (!loadSchema() || !openDictionaries(tuneFileSearch)) {
        return false;
    }
    const Schema &oldSchema = old._schema;
    for (SchemaUtil::IndexIterator itr(_schema); itr.isValid(); ++itr) {
        vespalib::string fieldDir =
            _indexDir + "/" + itr.getName() + "/";
        SchemaUtil::IndexSettings settings = itr.getIndexSettings();
        if (settings.hasError()) {
            return false;
        }
        SchemaUtil::IndexIterator oItr(oldSchema, itr);
        if (!itr.hasMatchingOldFields(oldSchema) || !oItr.isValid()) {
            if (!openField(fieldDir, tuneFileSearch)) {
                return false;
            }
        } else {
            uint32_t oldPacked = oItr.getIndex();
            _postingFiles.push_back(old._postingFiles[oldPacked]);
            _bitVectorDicts.push_back(old._bitVectorDicts[oldPacked]);
        }
    }
    _tuneFileSearch = tuneFileSearch;
    return true;
}

DiskIndex::LookupResult::UP
DiskIndex::lookup(uint32_t index, vespalib::stringref word)
{
    /** Only used for testing */
    IndexList indexes;
    indexes.push_back(index);
    Key key(std::move(indexes), word);
    LookupResultVector resultV(1);
    LookupResult::UP result;
    if ( read(key, resultV)) {
        result = std::make_unique<LookupResult>();
        result->swap(resultV[0]);
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
DiskIndex::lookup(const std::vector<uint32_t> & indexes, vespalib::stringref word)
{
    Key key(indexes, word);
    LookupResultVector result;
    if (_cacheSize > 0) {
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
        if (fieldId < _dicts.size()) {
            (void) _dicts[fieldId]->lookup(key.getWord(), wordNum,offsetAndCounts);
        }
        lr.wordNum = wordNum;
        lr.counts.swap(offsetAndCounts._counts);
        lr.bitOffset = offsetAndCounts._offset;
    }
    return true;
}

index::PostingListHandle::UP
DiskIndex::readPostingList(const LookupResult &lookupRes) const
{
    PostingListHandle::UP handle(new PostingListHandle());
    handle->_bitOffset = lookupRes.bitOffset;
    handle->_bitLength = lookupRes.counts._bitLength;
    SchemaUtil::IndexIterator it(_schema, lookupRes.indexId);
    handle->_file = _postingFiles[it.getIndex()].get();
    if (handle->_file == nullptr) {
        return {};
    }
    const uint32_t firstSegment = 0;
    const uint32_t numSegments = 0; // means all segments
    handle->_file->readPostingList(lookupRes.counts, firstSegment, numSegments,*handle);
    return handle;
}

BitVector::UP
DiskIndex::readBitVector(const LookupResult &lookupRes) const
{
    SchemaUtil::IndexIterator it(_schema, lookupRes.indexId);
    BitVectorDictionary * dict = _bitVectorDicts[it.getIndex()].get();
    if (dict == nullptr) {
        return {};
    }
    return dict->lookup(lookupRes.wordNum);
}

void
DiskIndex::calculateSize()
{
    search::DirectoryTraverse dirt(_indexDir.c_str());
    _size = dirt.GetTreeSize();
}

namespace {

DiskIndex::LookupResult G_nothing;

class LookupCache {
public:
    LookupCache(DiskIndex & diskIndex, const std::vector<uint32_t> & fieldIds) :
        _diskIndex(diskIndex),
        _fieldIds(fieldIds),
        _cache()
    {
    }
    const DiskIndex::LookupResult &
    lookup(const vespalib::string & word, uint32_t fieldId) {
        Cache::const_iterator it = _cache.find(word);
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

    using Cache = vespalib::hash_map<vespalib::string, DiskIndex::LookupResultVector>;
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
        const vespalib::string termStr = termAsString(n);
        const DiskIndex::LookupResult & lookupRes = _cache.lookup(termStr, _fieldId);
        if (lookupRes.valid()) {
            bool useBitVector = _field.isFilter();
            setResult(std::make_unique<DiskTermBlueprint>(_field, _diskIndex, termStr, std::make_unique<DiskIndex::LookupResult>(lookupRes), useBitVector));
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
DiskIndex::get_field_length_info(const vespalib::string& field_name) const
{
    uint32_t fieldId = _schema.getIndexFieldId(field_name);
    if (fieldId != Schema::UNKNOWN_FIELD_ID) {
        return _postingFiles[fieldId]->get_field_length_info();
    } else {
        return FieldLengthInfo();
    }
}

}
