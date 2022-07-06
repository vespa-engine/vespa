// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumfieldwriter.h"
#include "i_docsum_store_document.h"
#include "idocsumenvironment.h"
#include "docsumstate.h"
#include "summaryfieldconverter.h"
#include <vespa/searchlib/common/documentlocations.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/data/slime/slime.h>

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

EmptyDFW::EmptyDFW() = default;

EmptyDFW::~EmptyDFW() = default;

void
EmptyDFW::insertField(uint32_t, GetDocsumsState *, ResType, vespalib::slime::Inserter &target)
{
    // insert explicitly-empty field?
    // target.insertNix();
    (void)target;
}

//--------------------------------------------------------------------------

CopyDFW::CopyDFW()
    : _inputFieldEnumValue(static_cast<uint32_t>(-1)),
      _input_field_name()
{
}

CopyDFW::~CopyDFW() = default;

bool
CopyDFW::Init(const ResultConfig & config, const char *inputField)
{
    _inputFieldEnumValue = config.GetFieldNameEnum().Lookup(inputField);
    _input_field_name = inputField;

    if (_inputFieldEnumValue >= config.GetFieldNameEnum().GetNumEntries()) {
        LOG(warning, "no docsum format contains field '%s'; copied fields will be empty", inputField);
    }

    for (const auto & result_class : config) {
        const ResConfigEntry *entry = result_class.GetEntry(result_class.GetIndexFromEnumValue(_inputFieldEnumValue));

        if (entry != nullptr && !entry->_not_present &&
            !IsRuntimeCompatible(entry->_type, RES_INT) &&
            !IsRuntimeCompatible(entry->_type, RES_DOUBLE) &&
            !IsRuntimeCompatible(entry->_type, RES_INT64) &&
            !IsRuntimeCompatible(entry->_type, RES_STRING) &&
            !IsRuntimeCompatible(entry->_type, RES_DATA)) {

            LOG(warning, "cannot use docsum field '%s' as input to copy; type conflict with result class %d (%s)",
                inputField, result_class.GetClassID(), result_class.GetClassName());
            return false;
        }
    }
    return true;
}

void
CopyDFW::insertField(uint32_t /*docid*/, GeneralResult *gres, GetDocsumsState *, ResType type,
                     vespalib::slime::Inserter &target)
{
    int idx = gres->GetClass()->GetIndexFromEnumValue(_inputFieldEnumValue);
    ResEntry *entry = gres->GetPresentEntry(idx);

    if (entry == nullptr) {
        const auto* document = gres->get_document();
        if (document != nullptr) {
            document->insert_summary_field(_input_field_name, target);
        }
    } else if (IsRuntimeCompatible(entry->_type, type)) {
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
        case RES_BOOL: {
            target.insertBool(entry->_intval != 0);
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

        case RES_JSONSTRING: {
            uint32_t    len;
            const char *spt;
            // resolve field
            entry->_resolve_field(&spt, &len);
            if (len != 0) {
                // note: 'JSONSTRING' really means 'structured data'
                vespalib::Slime input_field_as_slime;
                size_t d = vespalib::slime::BinaryFormat::decode(vespalib::Memory(spt, len), input_field_as_slime);
                if (d != len) {
                    LOG(warning, "could not decode %u bytes: %zu bytes decoded", len, d);
                }
                if (d != 0) {
                    inject(input_field_as_slime.get(), target);
                }
            }
            break; }
        case RES_FEATUREDATA:
        case RES_LONG_STRING:
        case RES_STRING: {
            uint32_t    len;
            const char *spt;
            // resolve field
            entry->_resolve_field(&spt, &len);
            vespalib::Memory value(spt, len);
            target.insertString(value);
            break; }

        case RES_TENSOR:
        case RES_LONG_DATA:
        case RES_DATA: {
            uint32_t    len;
            const char *dpt;
            // resolve field
            entry->_resolve_field(&dpt, &len);
            vespalib::Memory value(dpt, len);
            target.insertData(value);
            break; }
        }
    }
}

}
