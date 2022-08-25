// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumwriter.h"
#include "docsumstate.h"
#include "docsum_field_writer_state.h"
#include "i_docsum_store_document.h"
#include "summaryfieldconverter.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.docsumwriter");

using namespace vespalib::slime::convenience;
using vespalib::Issue;

namespace search::docsummary {

uint32_t
IDocsumWriter::slime2RawBuf(const Slime & slime, RawBuf & buf)
{
    const uint32_t preUsed = buf.GetUsedLen();
    const uint32_t magic = SLIME_MAGIC_ID;
    buf.append(&magic, sizeof(magic));
    SlimeOutputRawBufAdapter adapter(buf);
    vespalib::slime::BinaryFormat::encode(slime, adapter);
    return (buf.GetUsedLen() - preUsed);
}

DynamicDocsumWriter::ResolveClassInfo
DynamicDocsumWriter::resolveClassInfo(vespalib::stringref outputClassName, uint32_t inputClassId) const
{
    DynamicDocsumWriter::ResolveClassInfo rci = resolveOutputClass(outputClassName);
    if (!rci.mustSkip && !rci.allGenerated) {
        resolveInputClass(rci, inputClassId);
    }
    return rci;
}

DynamicDocsumWriter::ResolveClassInfo
DynamicDocsumWriter::resolveOutputClass(vespalib::stringref summaryClass) const
{
    DynamicDocsumWriter::ResolveClassInfo result;
    uint32_t id = _defaultOutputClass;
    id = _resultConfig->LookupResultClassId(summaryClass, id);

    if (id != ResultConfig::NoClassID()) {
        const ResultClass *oC = _resultConfig->LookupResultClass(id);
        if (oC == nullptr) {
            Issue::report("Illegal docsum class requested: %s, using empty docsum for documents",
                          vespalib::string(summaryClass).c_str());
            result.mustSkip = true;
        } else {
            result.outputClass = oC;
            const ResultClass::DynamicInfo *rcInfo = oC->getDynamicInfo();
            if (rcInfo->_generateCnt == oC->GetNumEntries()) {
                LOG_ASSERT(rcInfo->_overrideCnt == rcInfo->_generateCnt);
                result.allGenerated = true;
            }
            result.outputClassInfo = rcInfo;
        }
    }
    result.outputClassId = id;
    return result;
}

void
DynamicDocsumWriter::resolveInputClass(ResolveClassInfo &rci, uint32_t id) const
{
    rci.inputClass = _resultConfig->LookupResultClass(id);
    if (rci.inputClass == nullptr) {
        rci.mustSkip = true;
        return;
    }
    if (rci.outputClass == nullptr) {
        LOG_ASSERT(rci.outputClassId == ResultConfig::NoClassID());
        rci.outputClassId = id;
        rci.outputClass = rci.inputClass;
        rci.outputClassInfo = rci.inputClass->getDynamicInfo();
    }
}

void
DynamicDocsumWriter::insertDocsum(const ResolveClassInfo & rci, uint32_t docid, GetDocsumsState *state,
                                  IDocsumStore *docinfos, vespalib::slime::Inserter& topInserter)
{
    if (rci.allGenerated) {
        // generate docsum entry on-the-fly
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.outputClass->GetNumEntries(); ++i) {
            const ResConfigEntry *resCfg = rci.outputClass->GetEntry(i);
            DocsumFieldWriter *writer = _overrideTable[resCfg->_enumValue];
            if (! writer->isDefaultValue(docid, state)) {
                const Memory field_name(resCfg->_bindname.data(), resCfg->_bindname.size());
                ObjectInserter inserter(docsum, field_name);
                writer->insertField(docid, nullptr, state, resCfg->_type, inserter);
            }
        }
    } else {
        // look up docsum entry
        auto doc = docinfos->getMappedDocsum(docid);
        // insert docsum blob
        vespalib::slime::Cursor & docsum = topInserter.insertObject();
        for (uint32_t i = 0; i < rci.outputClass->GetNumEntries(); ++i) {
            const ResConfigEntry *outCfg = rci.outputClass->GetEntry(i);
            DocsumFieldWriter *writer = _overrideTable[outCfg->_enumValue];
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

DynamicDocsumWriter::DynamicDocsumWriter( ResultConfig *config, KeywordExtractor *extractor)
    : _resultConfig(config),
      _keywordExtractor(extractor),
      _defaultOutputClass(ResultConfig::NoClassID()),
      _numClasses(config->GetNumResultClasses()),
      _numEnumValues(config->GetFieldNameEnum().GetNumEntries()),
      _numFieldWriterStates(0),
      _classInfoTable(nullptr),
      _overrideTable(nullptr)
{
    LOG_ASSERT(config != nullptr);
    _classInfoTable = new ResultClass::DynamicInfo[_numClasses];
    _overrideTable  = new DocsumFieldWriter*[_numEnumValues];

    uint32_t i = 0;
    for (ResultConfig::iterator it(config->begin()), mt(config->end()); it != mt; it++, i++) {
        _classInfoTable[i]._overrideCnt = 0;
        _classInfoTable[i]._generateCnt = 0;
        it->setDynamicInfo(&(_classInfoTable[i]));
    }
    LOG_ASSERT(i == _numClasses);

    for (i = 0; i < _numEnumValues; i++)
        _overrideTable[i] = nullptr;
}


DynamicDocsumWriter::~DynamicDocsumWriter()
{
    delete _resultConfig;
    delete _keywordExtractor;

    delete [] _classInfoTable;

    for (uint32_t i = 0; i < _numEnumValues; i++)
        delete _overrideTable[i];
    delete [] _overrideTable;

}

bool
DynamicDocsumWriter::SetDefaultOutputClass(uint32_t classID)
{
    const ResultClass *resClass = _resultConfig->LookupResultClass(classID);

    if (resClass == nullptr ||
        _defaultOutputClass != ResultConfig::NoClassID())
    {
        if (resClass == nullptr) {
            LOG(warning, "cannot set default output docsum class to %d; class not defined", classID);
        } else if (_defaultOutputClass != ResultConfig::NoClassID()) {
            LOG(warning, "cannot set default output docsum class to %d; value already set", classID);
        }
        return false;
    }
    _defaultOutputClass = classID;
    return true;
}


bool
DynamicDocsumWriter::Override(const char *fieldName, DocsumFieldWriter *writer)
{
    uint32_t fieldEnumValue = _resultConfig->GetFieldNameEnum().Lookup(fieldName);

    if (fieldEnumValue >= _numEnumValues ||
        _overrideTable[fieldEnumValue] != nullptr)
    {

        if (fieldEnumValue >= _numEnumValues) {
            LOG(warning, "cannot override docsum field '%s'; undefined field name", fieldName);
        } else if (_overrideTable[fieldEnumValue] != nullptr) {
            LOG(warning, "cannot override docsum field '%s'; already overridden", fieldName);
        }
        delete writer;
        return false;
    }

    writer->setIndex(fieldEnumValue);
    _overrideTable[fieldEnumValue] = writer;
    if (writer->setFieldWriterStateIndex(_numFieldWriterStates)) {
        ++_numFieldWriterStates;
    }

    for (auto & result_class : *_resultConfig) {

        if (result_class.GetIndexFromEnumValue(fieldEnumValue) >= 0) {
            ResultClass::DynamicInfo *info = result_class.getDynamicInfo();
            info->_overrideCnt++;
            if (writer->IsGenerated())
                info->_generateCnt++;
        }
    }

    return true;
}


void
DynamicDocsumWriter::InitState(IAttributeManager & attrMan, GetDocsumsState *state)
{
    state->_kwExtractor = _keywordExtractor;
    state->_attrCtx = attrMan.createContext();
    state->_attributes.resize(_numEnumValues);
    state->_fieldWriterStates.resize(_numFieldWriterStates);
    for (size_t i(0); i < state->_attributes.size(); i++) {
        const DocsumFieldWriter *fw = _overrideTable[i];
        if (fw) {
            const vespalib::string & attributeName = fw->getAttributeName();
            if (!attributeName.empty()) {
                state->_attributes[i] = state->_attrCtx->getAttribute(attributeName);
            }
        }
    }
}


uint32_t
DynamicDocsumWriter::WriteDocsum(uint32_t docid, GetDocsumsState *state, IDocsumStore *docinfos, search::RawBuf *target)
{
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    ResolveClassInfo rci = resolveClassInfo(state->_args.getResultClassName(), docinfos->getSummaryClassId());
    insertDocsum(rci, docid, state, docinfos, inserter);
    return slime2RawBuf(slime, *target);
}

}
