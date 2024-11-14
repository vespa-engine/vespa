// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_index.h"
#include "document_inverter.h"
#include "document_inverter_collection.h"
#include "document_inverter_context.h"
#include "field_index_collection.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.memory_index");

using document::ArrayFieldValue;
using document::WeightedSetFieldValue;

namespace search {

using index::FieldLengthInfo;
using index::IFieldLengthInspector;
using index::IndexBuilder;
using index::Schema;
using index::SchemaUtil;
using query::FuzzyTerm;
using query::LocationTerm;
using query::NearestNeighborTerm;
using query::Node;
using query::NumberTerm;
using query::PredicateQuery;
using query::PrefixTerm;
using query::RangeTerm;
using query::RegExpTerm;
using query::StringTerm;
using query::SubstringTerm;
using query::SuffixTerm;
using queryeval::Blueprint;
using queryeval::CreateBlueprintVisitorHelper;
using queryeval::EmptyBlueprint;
using queryeval::FieldSpec;
using queryeval::IRequestContext;
using queryeval::Searchable;
using vespalib::ISequencedTaskExecutor;
using vespalib::slime::Cursor;

}

namespace search::memoryindex {

MemoryIndex::MemoryIndex(const Schema& schema,
                         const IFieldLengthInspector& inspector,
                         ISequencedTaskExecutor& invertThreads,
                         ISequencedTaskExecutor& pushThreads)
    : _schema(schema),
      _invertThreads(invertThreads),
      _pushThreads(pushThreads),
      _fieldIndexes(std::make_unique<FieldIndexCollection>(_schema, inspector)),
      _inverter_context(std::make_unique<DocumentInverterContext>(_schema, _invertThreads, _pushThreads, *_fieldIndexes)),
      _inverters(std::make_unique<DocumentInverterCollection>(*_inverter_context, 4)),
      _frozen(false),
      _maxDocId(0), // docId 0 is reserved
      _numDocs(0),
      _lock(),
      _hiddenFields(schema.getNumIndexFields(), false),
      _prunedSchema(),
      _indexedDocs(0),
      _staticMemoryFootprint(getMemoryUsage().allocatedBytes())
{
}

MemoryIndex::~MemoryIndex() = default;

void
MemoryIndex::insertDocument(uint32_t docId, const document::Document &doc, const OnWriteDoneType& on_write_done)
{
    if (_frozen) {
        LOG(warning, "Memory index frozen: ignoring insert of document '%s'(%u): '%s'",
            doc.getId().toString().c_str(), docId, doc.toString().c_str());
        return;
    }
    updateMaxDocId(docId);
    auto& inverter = _inverters->get_active_inverter();
    inverter.invertDocument(docId, doc, on_write_done);
    if (_indexedDocs.insert(docId).second) {
        incNumDocs();
    }
}

void
MemoryIndex::removeDocuments(LidVector lids)
{
    if (_frozen) {
        LOG(warning, "Memory index frozen: ignoring remove of %lu documents", lids.size());
        return;
    }
    for (uint32_t lid : lids) {

        if (_indexedDocs.find(lid) != _indexedDocs.end()) {
            _indexedDocs.erase(lid);
            decNumDocs();
        }
    }
    auto& inverter = _inverters->get_active_inverter();
    inverter.removeDocuments(std::move(lids));
}

void
MemoryIndex::commit(const OnWriteDoneType& on_write_done)
{
    auto& inverter = _inverters->get_active_inverter();
    inverter.pushDocuments(on_write_done);
    _inverters->switch_active_inverter();
}

void
MemoryIndex::freeze()
{
    _frozen = true;
}

void
MemoryIndex::dump(IndexBuilder &indexBuilder)
{
    _fieldIndexes->dump(indexBuilder);
}

namespace {

/**
 * Determines the correct Blueprint to use.
 **/
class CreateBlueprintVisitor : public CreateBlueprintVisitorHelper {
private:
    const FieldSpec &_field;
    const uint32_t   _fieldId;
    FieldIndexCollection &_fieldIndexes;

public:
    CreateBlueprintVisitor(Searchable &searchable,
                           const IRequestContext & requestContext,
                           const FieldSpec &field,
                           uint32_t fieldId,
                           FieldIndexCollection &fieldIndexes)
        : CreateBlueprintVisitorHelper(searchable, field, requestContext),
          _field(field),
          _fieldId(fieldId),
          _fieldIndexes(fieldIndexes) {}

    template <class TermNode>
    void visitTerm(TermNode &n) {
        const std::string termStr = queryeval::termAsString(n);
        LOG(debug, "searching for '%s' in '%s'",
            termStr.c_str(), _field.getName().c_str());
        IFieldIndex* fieldIndex = _fieldIndexes.getFieldIndex(_fieldId);
        setResult(fieldIndex->make_term_blueprint(termStr, _field, _fieldId));
    }

    void not_supported(Node &) {}

    void visit(LocationTerm &n)  override { visitTerm(n); }
    void visit(PrefixTerm &n)    override { visitTerm(n); }
    void visit(RangeTerm &n)     override { visitTerm(n); }
    void visit(StringTerm &n)    override { visitTerm(n); }
    void visit(SubstringTerm &n) override { visitTerm(n); }
    void visit(SuffixTerm &n)    override { visitTerm(n); }
    void visit(RegExpTerm &n)    override { visitTerm(n); }
    void visit(FuzzyTerm &n)    override { visitTerm(n); }
    void visit(PredicateQuery &n) override { not_supported(n); }
    void visit(NearestNeighborTerm &n) override { not_supported(n); }

    void visit(NumberTerm &n) override {
        handleNumberTermAsText(n);
    }

};

} // namespace search::memoryindex::<unnamed>

Blueprint::UP
MemoryIndex::createBlueprint(const IRequestContext & requestContext,
                             const FieldSpec &field,
                             const Node &term)
{
    uint32_t fieldId = _schema.getIndexFieldId(field.getName());
    if (fieldId == Schema::UNKNOWN_FIELD_ID || _hiddenFields[fieldId]) {
        return std::make_unique<EmptyBlueprint>(field);
    }
    CreateBlueprintVisitor visitor(*this, requestContext, field, fieldId, *_fieldIndexes);
    const_cast<Node &>(term).accept(visitor);
    return visitor.getResult();
}

std::unique_ptr<queryeval::Blueprint>
MemoryIndex::createBlueprint(const queryeval::IRequestContext & requestContext,
                             const queryeval::FieldSpecList &fields,
                             const query::Node &term)
{
    return queryeval::Searchable::createBlueprint(requestContext, fields, term);
}

vespalib::MemoryUsage
MemoryIndex::getMemoryUsage() const
{
    vespalib::MemoryUsage usage;
    usage.merge(_fieldIndexes->getMemoryUsage());
    return usage;
}

SearchableStats
MemoryIndex::get_stats() const
{
    auto stats = _fieldIndexes->get_stats(_schema);
    stats.docsInMemory(getNumDocs());
    return stats;
}

uint64_t
MemoryIndex::getNumWords() const {
    return _fieldIndexes->getNumUniqueWords();
}

void
MemoryIndex::pruneRemovedFields(const Schema &schema)
{
    std::lock_guard lock(_lock);
    if (_prunedSchema.get() == nullptr) {
        auto newSchema = Schema::intersect(_schema, schema);
        if (_schema == *newSchema) {
            return;
        }
        _prunedSchema = std::move(newSchema);
    } else {
        auto newSchema = Schema::intersect(*_prunedSchema, schema);
        if (*_prunedSchema == *newSchema) {
            return;
        }
        _prunedSchema = std::move(newSchema);
    }
    SchemaUtil::IndexIterator i(_schema);
    for (; i.isValid(); ++i) {
        uint32_t packedIndex = i.getIndex();
        assert(packedIndex < _hiddenFields.size());
        SchemaUtil::IndexIterator wi(*_prunedSchema, i);
        _hiddenFields[packedIndex] = !wi.isValid();
    }
}

std::shared_ptr<const Schema>
MemoryIndex::getPrunedSchema() const
{
    std::lock_guard lock(_lock);
    return _prunedSchema;
}

FieldLengthInfo
MemoryIndex::get_field_length_info(const std::string& field_name) const
{
    uint32_t field_id = _schema.getIndexFieldId(field_name);
    if (field_id != Schema::UNKNOWN_FIELD_ID) {
        return _fieldIndexes->get_calculator(field_id).get_info();
    }
    return FieldLengthInfo();
}

namespace {

void
fields_to_slime(const std::vector<uint32_t>& field_ids, const Schema& schema, Cursor& array)
{
    for (uint32_t field_id : field_ids) {
        assert(field_id < schema.getIndexFields().size());
        const auto& field = schema.getIndexField(field_id);
        array.addString(field.getName());
    }
}

void
write_context_to_slime(const BundledFieldsContext& ctx, const Schema& schema, Cursor& object)
{
    object.setLong("executor_id", ctx.get_id().getId());
    auto& fields = object.setArray("fields");
    fields_to_slime(ctx.get_fields(), schema, fields);
    fields_to_slime(ctx.get_uri_all_field_ids(), schema, fields);
}

}

void
MemoryIndex::insert_write_context_state(Cursor& object) const
{
    auto& invert = object.setArray("invert");
    for (const auto& ctx : _inverter_context->get_invert_contexts()) {
        auto& ctx_obj = invert.addObject();
        write_context_to_slime(ctx, _schema, ctx_obj);
    }
    auto& push = object.setArray("push");
    for (const auto& ctx : _inverter_context->get_push_contexts()) {
        auto& ctx_obj = push.addObject();
        write_context_to_slime(ctx, _schema, ctx_obj);
    }
}

}
