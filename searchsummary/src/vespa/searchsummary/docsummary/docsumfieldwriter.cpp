// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumfieldwriter.h"
#include "idocsumenvironment.h"
#include "docsumstate.h"
#include <vespa/searchlib/common/documentlocations.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.docsumfieldwriter");

namespace search::docsummary {

using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;
using search::common::Location;

//--------------------------------------------------------------------------

const vespalib::string IDocsumFieldWriter::_empty("");

bool
IDocsumFieldWriter::setFieldWriterStateIndex(uint32_t)
{
    return false; // Don't need any field writer state by default
}

//--------------------------------------------------------------------------

EmptyDFW::EmptyDFW() { }


EmptyDFW::~EmptyDFW() { }

void
EmptyDFW::insertField(uint32_t /*docid*/,
                      GeneralResult *,
                      GetDocsumsState *,
                      ResType,
                      vespalib::slime::Inserter &target)
{
    // insert explicitly-empty field?
    // target.insertNix();
    (void)target;
    return;
}

//--------------------------------------------------------------------------

CopyDFW::CopyDFW()
    : _inputFieldEnumValue(static_cast<uint32_t>(-1))
{
}


CopyDFW::~CopyDFW()
{
}


bool
CopyDFW::Init(const ResultConfig & config, const char *inputField)
{
    _inputFieldEnumValue = config.GetFieldNameEnum().Lookup(inputField);

    if (_inputFieldEnumValue >= config.GetFieldNameEnum().GetNumEntries()) {
        LOG(warning, "no docsum format contains field '%s'; copied fields will be empty", inputField);
    }

    for (ResultConfig::const_iterator it(config.begin()), mt(config.end()); it != mt; it++) {
        const ResConfigEntry *entry =
            it->GetEntry(it->GetIndexFromEnumValue(_inputFieldEnumValue));

        if (entry != NULL &&
            !IsRuntimeCompatible(entry->_type, RES_INT) &&
            !IsRuntimeCompatible(entry->_type, RES_DOUBLE) &&
            !IsRuntimeCompatible(entry->_type, RES_INT64) &&
            !IsRuntimeCompatible(entry->_type, RES_STRING) &&
            !IsRuntimeCompatible(entry->_type, RES_DATA)) {

            LOG(warning, "cannot use docsum field '%s' as input to copy; type conflict with result class %d (%s)",
                inputField, it->GetClassID(), it->GetClassName());
            return false;
        }
    }
    return true;
}


void
CopyDFW::insertField(uint32_t /*docid*/,
            GeneralResult *gres,
            GetDocsumsState *state,
            ResType type,
            vespalib::slime::Inserter &target)
{
    int idx = gres->GetClass()->GetIndexFromEnumValue(_inputFieldEnumValue);
    ResEntry *entry = gres->GetEntry(idx);

    if (entry != NULL &&
        IsRuntimeCompatible(entry->_type, type))
    {
        switch (type) {
        case RES_INT: {
            uint32_t val32 = entry->_intval;
            target.insertLong(val32);
            break; }

        case RES_SHORT: {
            uint16_t val16 = entry->_intval;
            target.insertLong(val16);
            break; }

        case RES_BYTE: {
            uint8_t val8 = entry->_intval;
            target.insertLong(val8);
            break; }

        case RES_FLOAT: {
            float valfloat = entry->_doubleval;
            target.insertDouble(valfloat);
            break; }

        case RES_DOUBLE: {
            double valdouble = entry->_doubleval;
            target.insertDouble(valdouble);
            break; }

        case RES_INT64: {
            uint64_t valint64 = entry->_int64val;
            target.insertLong(valint64);
            break; }

        case RES_XMLSTRING:
        case RES_JSONSTRING:
        case RES_FEATUREDATA:
        case RES_LONG_STRING:
        case RES_STRING: {
            uint32_t    len;
            const char *spt;
            // resolve field
            entry->_resolve_field(&spt, &len, &state->_docSumFieldSpace);
            vespalib::Memory value(spt, len);
            target.insertString(value);
            break; }

        case RES_TENSOR:
        case RES_LONG_DATA:
        case RES_DATA: {
            uint32_t    len;
            const char *dpt;
            // resolve field
            entry->_resolve_field(&dpt, &len, &state->_docSumFieldSpace);
            vespalib::Memory value(dpt, len);
            target.insertData(value);
            break; }
        }
    }
}

}
