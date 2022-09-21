// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumwriter.h"
#include "docsumstate.h"
#include "docsum_field_writer_state.h"
#include "i_docsum_store_document.h"
#include "keywordextractor.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/data/slime/inserter.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.docsumwriter");

using vespalib::Issue;
using vespalib::Memory;
using vespalib::slime::ObjectInserter;

namespace search::docsummary {

DynamicDocsumWriter::ResolveClassInfo
DynamicDocsumWriter::resolveClassInfo(vespalib::stringref class_name,
                                      const vespalib::hash_set<vespalib::string>& fields) const
{
    DynamicDocsumWriter::ResolveClassInfo result;
    auto id = _resultConfig->lookupResultClassId(class_name);

    const auto* res_class = (id != ResultConfig::noClassID()) ? _resultConfig->lookupResultClass(id) : nullptr;
    if (res_class == nullptr) {
        Issue::report("Illegal docsum class requested: %s, using empty docsum for documents",
                      vespalib::string(class_name).c_str());
    } else {
        result.all_fields_generated = res_class->all_fields_generated(fields);
    }
    result.res_class = res_class;
    return result;
}

void
DynamicDocsumWriter::insertDocsum(const ResolveClassInfo & rci, uint32_t docid, GetDocsumsState& state,
                                  IDocsumStore &docinfos, Inserter& topInserter)
{
    if (rci.res_class == nullptr) {
        // Use empty docsum when illegal docsum class has been requested
        return;
    }
    if (rci.all_fields_generated) {
        // generate docsum entry on-the-fly
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.res_class->getNumEntries(); ++i) {
            const ResConfigEntry *resCfg = rci.res_class->getEntry(i);
            const DocsumFieldWriter *writer = resCfg->writer();
            if (state._args.need_field(resCfg->name()) && ! writer->isDefaultValue(docid, state)) {
                const Memory field_name(resCfg->name().data(), resCfg->name().size());
                ObjectInserter inserter(docsum, field_name);
                writer->insertField(docid, nullptr, state, inserter);
            }
        }
    } else {
        // look up docsum entry
        auto doc = docinfos.get_document(docid);
        if (!doc) {
            return; // Use empty docsum when document is gone
        }
        // insert docsum blob
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.res_class->getNumEntries(); ++i) {
            const ResConfigEntry *outCfg = rci.res_class->getEntry(i);
            if (!state._args.need_field(outCfg->name())) {
                continue;
            }
            const DocsumFieldWriter *writer = outCfg->writer();
            const Memory field_name(outCfg->name().data(), outCfg->name().size());
            ObjectInserter inserter(docsum, field_name);
            if (writer != nullptr) {
                if (! writer->isDefaultValue(docid, state)) {
                    writer->insertField(docid, doc.get(), state, inserter);
                }
            } else {
                if (doc) {
                    doc->insert_summary_field(outCfg->name(), inserter);
                }
            }
        }
    }
}

DynamicDocsumWriter::DynamicDocsumWriter(std::unique_ptr<ResultConfig> config, std::unique_ptr<KeywordExtractor> extractor)
    : _resultConfig(std::move(config)),
      _keywordExtractor(std::move(extractor))
{
}


DynamicDocsumWriter::~DynamicDocsumWriter() = default;

void
DynamicDocsumWriter::initState(const IAttributeManager & attrMan, GetDocsumsState& state, const ResolveClassInfo& rci)
{
    state._kwExtractor = _keywordExtractor.get();
    state._attrCtx = attrMan.createContext();
    auto result_class = rci.res_class;
    if (result_class == nullptr) {
        return;
    }
    size_t num_entries = result_class->getNumEntries();
    state._attributes.resize(num_entries);
    state._fieldWriterStates.resize(result_class->get_num_field_writer_states());
    for (size_t i(0); i < num_entries; i++) {
        const DocsumFieldWriter *fw = result_class->getEntry(i)->writer();
        if (fw) {
            const vespalib::string & attributeName = fw->getAttributeName();
            if (!attributeName.empty()) {
                state._attributes[i] = state._attrCtx->getAttribute(attributeName);
            }
        }
    }
}

}
