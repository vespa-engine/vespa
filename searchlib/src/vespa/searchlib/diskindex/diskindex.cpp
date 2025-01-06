// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskindex.h"
#include "disktermblueprint.h"
#include "fileheader.h"
#include "pagedict4randread.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/queryeval/create_blueprint_params.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.diskindex");

using namespace search::index;
using namespace search::query;
using namespace search::queryeval;

namespace search::diskindex {

DiskIndex::DiskIndex(const std::string &indexDir, std::shared_ptr<IPostingListCache> posting_list_cache)
    : _indexDir(indexDir),
      _schema(),
      _field_indexes(),
      _nonfield_size_on_disk(0),
      _tuneFileSearch(),
      _posting_list_cache(std::move(posting_list_cache))
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
        _field_indexes.emplace_back(itr.getIndex(), _posting_list_cache);
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

DictionaryLookupResult
DiskIndex::lookup(uint32_t index, std::string_view word)
{
    /** Only used for testing */
    if (index < _field_indexes.size()) {
        return _field_indexes[index].lookup(word);
    } else {
        return {};
    }
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

class CreateBlueprintVisitor : public CreateBlueprintVisitorHelper {
private:
    DiskIndex        &_diskIndex;
    const FieldIndex &_field_index;
    const FieldSpec  &_field;

public:
    CreateBlueprintVisitor(DiskIndex& diskIndex,
                           const IRequestContext & requestContext,
                           const FieldSpec &field,
                           uint32_t fieldId)
        : CreateBlueprintVisitorHelper(diskIndex, field, requestContext),
          _diskIndex(diskIndex),
          _field_index(_diskIndex.get_field_index(fieldId)),
          _field(field)
    {
    }

    template <class TermNode>
    void visitTerm(TermNode &n) {
        const std::string termStr = termAsString(n);
        auto lookup_result = _field_index.lookup(termStr);
        if (lookup_result.valid()) {
            double bitvector_limit = getRequestContext().get_create_blueprint_params().disk_index_bitvector_limit;
            setResult(std::make_unique<DiskTermBlueprint>
                (_field, _field_index, termStr, lookup_result, _field.isFilter(), bitvector_limit));
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
createBlueprintHelper(DiskIndex & diskIndex, const IRequestContext & requestContext,
                      const FieldSpec &field, uint32_t fieldId, const Node &term)
{
    if (fieldId != Schema::UNKNOWN_FIELD_ID) {
        CreateBlueprintVisitor visitor(diskIndex, requestContext, field, fieldId);
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
    return createBlueprintHelper(*this, requestContext, field, fieldIds[0], term);
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
    for (size_t i(0); i< fields.size(); i++) {
        const FieldSpec & field = fields[i];
        orbp->addChild(createBlueprintHelper(*this, requestContext, field, _schema.getIndexFieldId(field.getName()), term));
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

IndexStats
DiskIndex::get_stats(bool clear_disk_io_stats) const
{
    IndexStats stats;
    uint64_t size_on_disk = _nonfield_size_on_disk;
    uint32_t field_id = 0;
    for (auto& field_index : _field_indexes) {
        auto field_stats = field_index.get_stats(clear_disk_io_stats);
        size_on_disk += field_stats.size_on_disk();
        stats.add_field_stats(_schema.getIndexField(field_id).getName(), field_stats);
        ++field_id;
    }
    stats.sizeOnDisk(size_on_disk);
    return stats;
}

}
