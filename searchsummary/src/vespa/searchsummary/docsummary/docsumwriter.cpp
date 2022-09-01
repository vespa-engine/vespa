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
using vespalib::slime::ObjectInserter;
using vespalib::Memory;

namespace search::docsummary {

DynamicDocsumWriter::ResolveClassInfo
DynamicDocsumWriter::resolveClassInfo(vespalib::stringref outputClassName) const
{
    DynamicDocsumWriter::ResolveClassInfo rci = resolveOutputClass(outputClassName);
    return rci;
}

DynamicDocsumWriter::ResolveClassInfo
DynamicDocsumWriter::resolveOutputClass(vespalib::stringref summaryClass) const
{
    DynamicDocsumWriter::ResolveClassInfo result;
    auto id = _resultConfig->LookupResultClassId(summaryClass);

    const ResultClass *oC = (id != ResultConfig::NoClassID()) ? _resultConfig->LookupResultClass(id) : nullptr;
    if (oC == nullptr) {
        Issue::report("Illegal docsum class requested: %s, using empty docsum for documents",
                      vespalib::string(summaryClass).c_str());
    } else {
        const ResultClass::DynamicInfo *rcInfo = oC->getDynamicInfo();
        if (rcInfo->_generateCnt == oC->GetNumEntries()) {
            LOG_ASSERT(rcInfo->_overrideCnt == rcInfo->_generateCnt);
            result.allGenerated = true;
        }
    }
    result.outputClass = oC;
    return result;
}

void
DynamicDocsumWriter::insertDocsum(const ResolveClassInfo & rci, uint32_t docid, GetDocsumsState *state,
                                  IDocsumStore *docinfos, Inserter& topInserter)
{
    if (rci.outputClass == nullptr) {
        // Use empty docsum when illegal docsum class has been requested
        return;
    }
    if (rci.allGenerated) {
        // generate docsum entry on-the-fly
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.outputClass->GetNumEntries(); ++i) {
            const ResConfigEntry *resCfg = rci.outputClass->GetEntry(i);
            const DocsumFieldWriter *writer = _overrideTable[resCfg->_enumValue].get();
            if (! writer->isDefaultValue(docid, state)) {
                const Memory field_name(resCfg->_bindname.data(), resCfg->_bindname.size());
                ObjectInserter inserter(docsum, field_name);
                writer->insertField(docid, nullptr, state, resCfg->_type, inserter);
            }
        }
    } else {
        // look up docsum entry
        auto doc = docinfos->getMappedDocsum(docid);
        if (!doc) {
            return; // Use empty docsum when document is gone
        }
        // insert docsum blob
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.outputClass->GetNumEntries(); ++i) {
            const ResConfigEntry *outCfg = rci.outputClass->GetEntry(i);
            const DocsumFieldWriter *writer = _overrideTable[outCfg->_enumValue].get();
            const Memory field_name(outCfg->_bindname.data(), outCfg->_bindname.size());
            ObjectInserter inserter(docsum, field_name);
            if (writer != nullptr) {
                if (! writer->isDefaultValue(docid, state)) {
                    writer->insertField(docid, doc.get(), state, outCfg->_type, inserter);
                }
            } else {
                if (doc) {
                    doc->insert_summary_field(outCfg->_bindname, inserter);
                }
            }
        }
    }
}

DynamicDocsumWriter::DynamicDocsumWriter(std::unique_ptr<ResultConfig> config, std::unique_ptr<KeywordExtractor> extractor)
    : _resultConfig(std::move(config)),
      _keywordExtractor(std::move(extractor)),
      _numFieldWriterStates(0),
      _classInfoTable(_resultConfig->GetNumResultClasses()),
      _overrideTable(_resultConfig->GetFieldNameEnum().GetNumEntries())
{
    uint32_t i = 0;
    for (auto & result_class : *_resultConfig) {
        result_class.setDynamicInfo(&(_classInfoTable[i++]));
    }
    LOG_ASSERT(i == _resultConfig->GetNumResultClasses());
}


DynamicDocsumWriter::~DynamicDocsumWriter() = default;

bool
DynamicDocsumWriter::Override(const char *fieldName, std::unique_ptr<DocsumFieldWriter> writer)
{
    uint32_t fieldEnumValue = _resultConfig->GetFieldNameEnum().Lookup(fieldName);

    if (fieldEnumValue >= _overrideTable.size() || _overrideTable[fieldEnumValue]) {
        if (fieldEnumValue >= _overrideTable.size()) {
            LOG(warning, "cannot override docsum field '%s'; undefined field name", fieldName);
        } else if (_overrideTable[fieldEnumValue] != nullptr) {
            LOG(warning, "cannot override docsum field '%s'; already overridden", fieldName);
        }
        return false;
    }

    writer->setIndex(fieldEnumValue);
    auto writer_ptr = writer.get();
    _overrideTable[fieldEnumValue] = std::move(writer);
    if (writer_ptr->setFieldWriterStateIndex(_numFieldWriterStates)) {
        ++_numFieldWriterStates;
    }

    for (auto & result_class : *_resultConfig) {
        if (result_class.GetIndexFromEnumValue(fieldEnumValue) >= 0) {
            ResultClass::DynamicInfo *info = result_class.getDynamicInfo();
            info->_overrideCnt++;
            if (writer_ptr->IsGenerated())
                info->_generateCnt++;
        }
    }

    return true;
}


void
DynamicDocsumWriter::InitState(const IAttributeManager & attrMan, GetDocsumsState *state)
{
    state->_kwExtractor = _keywordExtractor.get();
    state->_attrCtx = attrMan.createContext();
    state->_attributes.resize(_overrideTable.size());
    state->_fieldWriterStates.resize(_numFieldWriterStates);
    for (size_t i(0); i < state->_attributes.size(); i++) {
        const DocsumFieldWriter *fw = _overrideTable[i].get();
        if (fw) {
            const vespalib::string & attributeName = fw->getAttributeName();
            if (!attributeName.empty()) {
                state->_attributes[i] = state->_attrCtx->getAttribute(attributeName);
            }
        }
    }
}


void
DynamicDocsumWriter::WriteDocsum(uint32_t docid, GetDocsumsState *state, IDocsumStore *docinfos, Inserter& inserter)
{
    ResolveClassInfo rci = resolveClassInfo(state->_args.getResultClassName());
    insertDocsum(rci, docid, state, docinfos, inserter);
}

}
