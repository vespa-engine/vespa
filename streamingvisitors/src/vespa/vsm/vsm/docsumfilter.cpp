// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumfilter.h"
#include "slimefieldwriter.h"
#include <vespa/searchsummary/docsummary/check_undefined_value_visitor.h>
#include <vespa/searchsummary/docsummary/i_docsum_store_document.h>
#include <vespa/searchsummary/docsummary/summaryfieldconverter.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.docsumfilter");

using namespace search::docsummary;


namespace {

class Handler : public document::fieldvalue::IteratorHandler {
public:
};

struct IntResultHandler : public Handler {
    int32_t value;
    IntResultHandler() : value(0) {}
    void onPrimitive(uint32_t, const Content & c) override {
        value = c.getValue().getAsInt();
    }
};

struct LongResultHandler : public Handler {
    int64_t value;
    LongResultHandler() : value(0) {}
    void onPrimitive(uint32_t, const Content & c) override {
        value = c.getValue().getAsLong();
    }
};

struct FloatResultHandler : public Handler {
    float value;
    FloatResultHandler() : value(0) {}
    void onPrimitive(uint32_t, const Content & c) override {
        value = c.getValue().getAsFloat();
    }
};

struct DoubleResultHandler : public Handler {
    double value;
    DoubleResultHandler() : value(0) {}
    void onPrimitive(uint32_t, const Content & c) override {
        value = c.getValue().getAsDouble();
    }
};

class StringResultHandler : public Handler {
private:
    ResType        _type;
    ResultPacker & _packer;
    void addToPacker(const char * buf, size_t len) {
        switch (_type) {
        case RES_STRING:
            _packer.AddString(buf, len);
            break;
        case RES_LONG_STRING:
            _packer.AddLongString(buf, len);
            break;
        default:
            break;
        }
    }

public:
    StringResultHandler(ResType t, ResultPacker & p) : _type(t), _packer(p) {}
    void onPrimitive(uint32_t, const Content & c) override {
        const document::FieldValue & fv = c.getValue();
        if (fv.isLiteral()) {
            const document::LiteralFieldValueB & lfv = static_cast<const document::LiteralFieldValueB &>(fv);
            vespalib::stringref s = lfv.getValueRef();
            addToPacker(s.data(), s.size());
        } else {
            vespalib::string s = fv.toString();
            addToPacker(s.c_str(), s.size());
        }
    }
};

class RawResultHandler : public Handler {
private:
    ResType        _type;
    ResultPacker & _packer;

public:
    RawResultHandler(ResType t, ResultPacker & p) : _type(t), _packer(p) {}
    void onPrimitive(uint32_t, const Content & c) override {
        const document::FieldValue & fv = c.getValue();
        try {
            std::pair<const char *, size_t> buf = fv.getAsRaw();
            if (buf.first != nullptr) {
                switch (_type) {
                case RES_DATA:
                    _packer.AddData(buf.first, buf.second);
                    break;
                case RES_LONG_DATA:
                    _packer.AddLongData(buf.first, buf.second);
                    break;
                default:
                    break;
                }
            }
        } catch (document::InvalidDataTypeConversionException & e) {
            LOG(warning, "RawResultHandler: Could not get field value '%s' as raw. Skipping writing this field", fv.toString().c_str());
            _packer.AddEmpty();
        }
    }
};


}


namespace vsm {

namespace {

/**
 * Class providing access to a document retrieved from an IDocsumStore
 * (vsm::DocsumFilter). VSM specific transforms might be applied when
 * accessing some fields.
 **/
class DocsumStoreVsmDocument : public IDocsumStoreDocument
{
    DocsumFilter&             _docsum_filter;
    const ResultClass&        _result_class;
    const Document&           _vsm_document;
    const document::Document* _document;

    static const document::Document *get_document_document(const Document& vsm_document) {
        const auto* storage_doc = dynamic_cast<const StorageDocument *>(&vsm_document);
        return (storage_doc != nullptr && storage_doc->valid()) ? &storage_doc->docDoc() : nullptr;
    }
    static const ResultClass& get_result_class(const DocsumFilter& docsum_filter) {
        auto result_class = docsum_filter.getTools()->getResultClass();
        assert(result_class != nullptr);
        return *result_class;
    }
public:
    DocsumStoreVsmDocument(DocsumFilter& docsum_filter, const Document& vsm_document);
    ~DocsumStoreVsmDocument() override;
    DocsumStoreFieldValue get_field_value(const vespalib::string& field_name) const override;
    JuniperInput get_juniper_input(const vespalib::string& field_name) const override;
    void insert_summary_field(const vespalib::string& field_name, vespalib::slime::Inserter& inserter) const override;
    void insert_document_id(vespalib::slime::Inserter& inserter) const override;
};

DocsumStoreVsmDocument::DocsumStoreVsmDocument(DocsumFilter& docsum_filter, const Document& vsm_document)
    : _docsum_filter(docsum_filter),
      _result_class(get_result_class(docsum_filter)),
      _vsm_document(vsm_document),
      _document(get_document_document(vsm_document))
{
}

DocsumStoreVsmDocument::~DocsumStoreVsmDocument() = default;

DocsumStoreFieldValue
DocsumStoreVsmDocument::get_field_value(const vespalib::string& field_name) const
{
    if (_document != nullptr) {
        auto entry_idx = _result_class.GetIndexFromName(field_name.c_str());
        if (entry_idx >= 0) {
            assert((uint32_t) entry_idx < _result_class.GetNumEntries());
            return _docsum_filter.get_summary_field(entry_idx, _vsm_document);
        }
        const document::Field & field = _document->getField(field_name);
        auto value(field.getDataType().createFieldValue());
        if (value) {
            if (_document->getValue(field, *value)) {
                return DocsumStoreFieldValue(std::move(value));
            }
        }
    }
    return {};
}

JuniperInput
DocsumStoreVsmDocument::get_juniper_input(const vespalib::string& field_name) const
{
    // Markup for juniper has already been added due to FLATTENJUNIPER command in vsm summary config.
    return JuniperInput(get_field_value(field_name));
}

void
DocsumStoreVsmDocument::insert_summary_field(const vespalib::string& field_name, vespalib::slime::Inserter& inserter) const
{
    if (_document != nullptr) {
        auto entry_idx = _result_class.GetIndexFromName(field_name.c_str());
        if (entry_idx >= 0) {
            assert((uint32_t) entry_idx < _result_class.GetNumEntries());
            _docsum_filter.insert_summary_field(entry_idx, _vsm_document, inserter);
            return;
        }
        const document::Field & field = _document->getField(field_name);
        auto value(field.getDataType().createFieldValue());
        if (value) {
            if (_document->getValue(field, *value)) {
                SummaryFieldConverter::insert_summary_field(*value, inserter);
            }
        }
    }
}

void
DocsumStoreVsmDocument::insert_document_id(vespalib::slime::Inserter& inserter) const
{
    if (_document) {
        auto id = _document->getId().toString();
        vespalib::Memory id_view(id.data(), id.size());
        inserter.insertString(id_view);
    }
}

}

FieldPath
copyPathButFirst(const FieldPath & rhs) {
    // skip the element that correspond to the start field value
    FieldPath path;
    if ( ! rhs.empty()) {
        for (auto it = rhs.begin() + 1; it != rhs.end(); ++it) {
            path.push_back(std::make_unique<document::FieldPathEntry>(**it));
        }
    }
    return path;
}

void
DocsumFilter::prepareFieldSpec(DocsumFieldSpec & spec, const DocsumTools::FieldSpec & toolsSpec,
                               const FieldMap & fieldMap, const FieldPathMapT & fieldPathMap)
{
    { // setup output field
        const vespalib::string & name = toolsSpec.getOutputName();
        LOG(debug, "prepareFieldSpec: output field name '%s'", name.c_str());
        FieldIdT field = fieldMap.fieldNo(name);
        if (field != FieldMap::npos) {
            if (field < fieldPathMap.size()) {
                spec.setOutputField(DocsumFieldSpec::FieldIdentifier(field, copyPathButFirst(fieldPathMap[field])));
            } else {
                LOG(warning, "Could not find a field path for field '%s' with id '%d'", name.c_str(), field);
                spec.setOutputField(DocsumFieldSpec::FieldIdentifier(field, FieldPath()));
            }
        } else {
            LOG(warning, "Could not find output summary field '%s'", name.c_str());
        }
    }
    // setup input fields
    for (size_t i = 0; i < toolsSpec.getInputNames().size(); ++i) {
        const vespalib::string & name = toolsSpec.getInputNames()[i];
        LOG(debug, "prepareFieldSpec: input field name '%s'", name.c_str());
        FieldIdT field = fieldMap.fieldNo(name);
        if (field != FieldMap::npos) {
            if (field < fieldPathMap.size()) {
                LOG(debug, "field %u < map size %zu", field, fieldPathMap.size());
                spec.getInputFields().push_back(DocsumFieldSpec::FieldIdentifier(field, copyPathButFirst(fieldPathMap[field])));
            } else {
                LOG(warning, "Could not find a field path for field '%s' with id '%d'", name.c_str(), field);
                spec.getInputFields().push_back(DocsumFieldSpec::FieldIdentifier(field, FieldPath()));
            }
            if (_highestFieldNo <= field) {
                _highestFieldNo = field + 1;
            }
        } else {
            LOG(warning, "Could not find input summary field '%s'", name.c_str());
        }
    }
}

const document::FieldValue *
DocsumFilter::getFieldValue(const DocsumFieldSpec::FieldIdentifier & fieldId,
                            VsmsummaryConfig::Fieldmap::Command command,
                            const Document & docsum, bool & modified)
{
    FieldIdT fId = fieldId.getId();
    const document::FieldValue * fv = docsum.getField(fId);
    if (fv == nullptr) {
        return nullptr;
    }
    switch (command) {
    case VsmsummaryConfig::Fieldmap::Command::FLATTENJUNIPER:
        if (_snippetModifiers != nullptr) {
            FieldModifier * mod = _snippetModifiers->getModifier(fId);
            if (mod != nullptr) {
                _cachedValue = mod->modify(*fv, fieldId.getPath());
                modified = true;
                return _cachedValue.get();
            }
        }
        [[fallthrough]];
    default:
        return fv;
    }
}


DocsumFilter::DocsumFilter(const DocsumToolsPtr &tools, const IDocSumCache & docsumCache) :
    _docsumCache(&docsumCache),
    _tools(tools),
    _fields(),
    _highestFieldNo(0),
    _packer(tools ? tools->getResultConfig() : nullptr),
    _flattenWriter(),
    _snippetModifiers(nullptr),
    _cachedValue(),
    _emptyFieldPath()
{ }

DocsumFilter::~DocsumFilter() =default;

void DocsumFilter::init(const FieldMap & fieldMap, const FieldPathMapT & fieldPathMap)
{
    if (_tools.get()) {
        const ResultClass *resClass = _tools->getResultClass();
        const std::vector<DocsumTools::FieldSpec> & inputSpecs = _tools->getFieldSpecs();
        if (resClass != nullptr) {
            uint32_t entryCnt = resClass->GetNumEntries();
            assert(entryCnt == inputSpecs.size());
            for (uint32_t i = 0; i < entryCnt; ++i) {
                const ResConfigEntry &entry = *resClass->GetEntry(i);
                const DocsumTools::FieldSpec & toolsSpec = inputSpecs[i];
                _fields.push_back(DocsumFieldSpec(entry._type, toolsSpec.getCommand()));
                LOG(debug, "About to prepare field spec for summary field '%s'", entry._bindname.c_str());
                prepareFieldSpec(_fields.back(), toolsSpec, fieldMap, fieldPathMap);
            }
            assert(entryCnt == _fields.size());
        }
    }
}

uint32_t
DocsumFilter::getNumDocs() const
{
    return std::numeric_limits<uint32_t>::max();
}

void
DocsumFilter::writeField(const document::FieldValue & fv, const FieldPath & path, ResType type, ResultPacker & packer)
{
    switch (type) {
    case RES_INT: {
        IntResultHandler rh;
        fv.iterateNested(path, rh);
        uint32_t val = rh.value;
        packer.AddInteger(val);
        break; }
    case RES_SHORT: {
        IntResultHandler rh;
        fv.iterateNested(path, rh);
        uint16_t val = rh.value;
        packer.AddShort(val);
        break; }
    case RES_BYTE: {
        IntResultHandler rh;
        fv.iterateNested(path, rh);
        uint8_t val = rh.value;
        packer.AddByte(val);
        break; }
    case RES_BOOL: {
        IntResultHandler rh;
        fv.iterateNested(path, rh);
        uint8_t val = rh.value;
        packer.AddByte(val);
        break; }
    case RES_FLOAT: {
        FloatResultHandler rh;
        fv.iterateNested(path, rh);
        float val = rh.value;
        packer.AddFloat(val);
        break; }
    case RES_DOUBLE: {
        DoubleResultHandler rh;
        fv.iterateNested(path, rh);
        double val = rh.value;
        packer.AddDouble(val);
        break; }
    case RES_INT64: {
        LongResultHandler rh;
        fv.iterateNested(path, rh);
        uint64_t val = rh.value;
        packer.AddInt64(val);
        break; }
    case RES_STRING:
    case RES_LONG_STRING:
        {
            StringResultHandler rh(type, packer);
            // the string result handler adds the result to the packer
            fv.iterateNested(path, rh);
        }
        break;
    case RES_DATA:
    case RES_LONG_DATA:
        {
            RawResultHandler rh(type, packer);
            // the raw result handler adds the result to the packer
            fv.iterateNested(path, rh);
        }
        break;
    default:
        LOG(warning, "Unknown docsum field type: %s", ResultConfig::GetResTypeName(type));
        packer.AddEmpty(); // unhandled output type
        break;
    }
}


void
DocsumFilter::writeSlimeField(const DocsumFieldSpec & fieldSpec,
                              const Document & docsum,
                              ResultPacker & packer)
{
    if (fieldSpec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
        const DocsumFieldSpec::FieldIdentifier & fieldId = fieldSpec.getOutputField();
        const document::FieldValue * fv = docsum.getField(fieldId.getId());
        if (fv != nullptr) {
            LOG(debug, "writeSlimeField: About to write field '%d' as Slime: field value = '%s'",
                fieldId.getId(), fv->toString().c_str());
            CheckUndefinedValueVisitor check_undefined;
            fv->accept(check_undefined);
            if (!check_undefined.is_undefined()) {
                SlimeFieldWriter writer;
                if (! fieldSpec.hasIdentityMapping()) {
                    writer.setInputFields(fieldSpec.getInputFields());
                }
                writer.convert(*fv);
                const vespalib::stringref out = writer.out();
                packer.AddLongString(out.data(), out.size());
            } else {
                packer.AddEmpty();
            }
        } else {
            LOG(debug, "writeSlimeField: Field value not set for field '%d'", fieldId.getId());
            packer.AddEmpty();
        }
    } else {
        LOG(debug, "writeSlimeField: Cannot handle this command");
        packer.AddEmpty();
    }
}

bool
DocsumFilter::write_flatten_field(const DocsumFieldSpec& field_spec, const Document& doc)
{
    if (field_spec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
        LOG(debug, "write_flatten_field: Cannot handle command NONE");
        return false;
    }

    if (field_spec.getResultType() != RES_LONG_STRING && field_spec.getResultType() != RES_STRING) {
        LOG(debug, "write_flatten_field: Can only handle result types STRING and LONG_STRING");
        return false;
    }
    switch (field_spec.getCommand()) {
    case VsmsummaryConfig::Fieldmap::Command::FLATTENJUNIPER:
        _flattenWriter.setSeparator("\x1E"); // record separator (same as juniper uses)
        break;
    default:
        break;
    }
    const DocsumFieldSpec::FieldIdentifierVector & inputFields = field_spec.getInputFields();
    for (size_t i = 0; i < inputFields.size(); ++i) {
        const DocsumFieldSpec::FieldIdentifier & fieldId = inputFields[i];
        bool modified = false;
        const document::FieldValue * fv = getFieldValue(fieldId, field_spec.getCommand(), doc, modified);
        if (fv != nullptr) {
            LOG(debug, "write_flatten_field: About to flatten field '%d' with field value (%s) '%s'",
                fieldId.getId(), modified ? "modified" : "original", fv->toString().c_str());
            if (modified) {
                fv->iterateNested(_emptyFieldPath, _flattenWriter);
            } else {
                fv->iterateNested(fieldId.getPath(), _flattenWriter);
            }
        } else {
            LOG(debug, "write_flatten_field: Field value not set for field '%d'", fieldId.getId());
        }
    }
    return true;
}

void
DocsumFilter::writeFlattenField(const DocsumFieldSpec & fieldSpec,
                                const Document & docsum,
                                ResultPacker & packer)
{
    if (!write_flatten_field(fieldSpec, docsum)) {
        packer.AddEmpty();
        return;
    }
    const CharBuffer & buf = _flattenWriter.getResult();
    switch (fieldSpec.getResultType()) {
    case RES_STRING:
        packer.AddString(buf.getBuffer(), buf.getPos());
        break;
    case RES_LONG_STRING:
        packer.AddLongString(buf.getBuffer(), buf.getPos());
        break;
    default:
        break;
    }
    _flattenWriter.clear();
}


void
DocsumFilter::writeEmpty(ResType type, ResultPacker & packer)
{
    // use the 'notdefined' values when writing numeric values
    switch (type) {
    case RES_INT:
        packer.AddInteger(std::numeric_limits<int32_t>::min());
        break;
    case RES_SHORT:
        packer.AddShort(std::numeric_limits<int16_t>::min());
        break;
    case RES_BYTE:
        packer.AddByte(std::numeric_limits<int8_t>::min());
        break;
    case RES_FLOAT:
        packer.AddFloat(std::numeric_limits<float>::quiet_NaN());
        break;
    case RES_DOUBLE:
        packer.AddDouble(std::numeric_limits<double>::quiet_NaN());
        break;
    case RES_INT64:
        packer.AddInt64(std::numeric_limits<int64_t>::min());
        break;
    default:
        packer.AddEmpty();
        break;
    }
}

uint32_t
DocsumFilter::getSummaryClassId() const
{
    return _tools->getResultClass() ? _tools->getResultClass()->GetClassID() : ResultConfig::NoClassID();
}

DocsumStoreValue
DocsumFilter::getMappedDocsum(uint32_t id)
{
    const ResultClass *resClass = _tools->getResultClass();
    if (resClass == nullptr) {
        return DocsumStoreValue(nullptr, 0);
    }

    const Document & doc = _docsumCache->getDocSum(id);

    _packer.Init(resClass->GetClassID());
    uint32_t entry_idx = 0;
    for (FieldSpecList::iterator it(_fields.begin()), end = _fields.end(); it != end; ++it, ++entry_idx) {
        if (entry_idx != _packer.get_entry_idx()) {
            // Entry is not present in docsum blob
            continue;
        }
        ResType type = it->getResultType();
        if (type == RES_JSONSTRING) {
            // this really means 'structured data'
            writeSlimeField(*it, doc, _packer);
        } else {
            if (it->getInputFields().size() == 1 && it->getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
                const DocsumFieldSpec::FieldIdentifier & fieldId = it->getInputFields()[0];
                const document::FieldValue * field = doc.getField(fieldId.getId());
                if (field != nullptr) {
                    CheckUndefinedValueVisitor check_undefined;
                    field->accept(check_undefined);
                    if (!check_undefined.is_undefined()) {
                        writeField(*field, fieldId.getPath(), type, _packer);
                    } else {
                        writeEmpty(type, _packer); // void input
                    }
                } else {
                    writeEmpty(type, _packer); // void input
                }
            } else if (it->getInputFields().size() == 0 && it->getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
                LOG(spam, "0 inputfields for output field %u",  it->getOutputField().getId());
                writeEmpty(type, _packer); // no input
            } else {
                writeFlattenField(*it, doc, _packer);
            }
        }
    }

    const char *buf;
    uint32_t buflen;
    bool ok = _packer.GetDocsumBlob(&buf, &buflen);
    if (ok) {
        return DocsumStoreValue(buf, buflen, std::make_unique<DocsumStoreVsmDocument>(*this, doc));
    } else {
        return DocsumStoreValue(nullptr, 0);
    }
}

search::docsummary::DocsumStoreFieldValue
DocsumFilter::get_struct_or_multivalue_summary_field(const DocsumFieldSpec& field_spec, const Document& doc)
{
    // Filtering not yet implemented, return whole struct or multivalue field
    const DocsumFieldSpec::FieldIdentifier & fieldId = field_spec.getOutputField();
    const document::FieldValue* field_value = doc.getField(fieldId.getId());
    return DocsumStoreFieldValue(field_value);
}

search::docsummary::DocsumStoreFieldValue
DocsumFilter::get_flattened_summary_field(const DocsumFieldSpec& field_spec, const Document& doc)
{
    if (!write_flatten_field(field_spec, doc)) {
        return {};
    }
    const CharBuffer& buf = _flattenWriter.getResult();
    auto value = document::StringFieldValue::make(vespalib::stringref(buf.getBuffer(), buf.getPos()));
    _flattenWriter.clear();
    return DocsumStoreFieldValue(std::move(value));
}

search::docsummary::DocsumStoreFieldValue
DocsumFilter::get_summary_field(uint32_t entry_idx, const Document& doc)
{
    const auto& field_spec = _fields[entry_idx];
    ResType type = field_spec.getResultType();
    if (type == RES_JSONSTRING) {
        return get_struct_or_multivalue_summary_field(field_spec, doc);
    } else {
        if (field_spec.getInputFields().size() == 1 && field_spec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
            const DocsumFieldSpec::FieldIdentifier & fieldId = field_spec.getInputFields()[0];
            const document::FieldValue* field_value = doc.getField(fieldId.getId());
            return DocsumStoreFieldValue(field_value);
        } else if (field_spec.getInputFields().size() == 0 && field_spec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
            return {};
        } else {
            return get_flattened_summary_field(field_spec, doc);
        }
    }
}

void
DocsumFilter::insert_struct_or_multivalue_summary_field(const DocsumFieldSpec& field_spec, const Document& doc, vespalib::slime::Inserter& inserter)
{
    if (field_spec.getCommand() != VsmsummaryConfig::Fieldmap::Command::NONE) {
        return;
    }
    const DocsumFieldSpec::FieldIdentifier& fieldId = field_spec.getOutputField();
    const document::FieldValue* fv = doc.getField(fieldId.getId());
    if (fv == nullptr) {
        return;
    }
    CheckUndefinedValueVisitor check_undefined;
    fv->accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFieldWriter writer;
        if (! field_spec.hasIdentityMapping()) {
            writer.setInputFields(field_spec.getInputFields());
        }
        writer.insert(*fv, inserter);
    }
}

void
DocsumFilter::insert_flattened_summary_field(const DocsumFieldSpec& field_spec, const Document& doc, vespalib::slime::Inserter& inserter)
{
    if (!write_flatten_field(field_spec, doc)) {
        return;
    }
    const CharBuffer& buf = _flattenWriter.getResult();
    inserter.insertString(vespalib::Memory(buf.getBuffer(), buf.getPos()));
    _flattenWriter.clear();
}

void
DocsumFilter::insert_summary_field(uint32_t entry_idx, const Document& doc, vespalib::slime::Inserter& inserter)
{
    const auto& field_spec = _fields[entry_idx];
    ResType type = field_spec.getResultType();
    if (type == RES_JSONSTRING) {
        insert_struct_or_multivalue_summary_field(field_spec, doc, inserter);
    } else {
        if (field_spec.getInputFields().size() == 1 && field_spec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
            const DocsumFieldSpec::FieldIdentifier & fieldId = field_spec.getInputFields()[0];
            const document::FieldValue* field_value = doc.getField(fieldId.getId());
            if (field_value != nullptr) {
                SummaryFieldConverter::insert_summary_field(*field_value, inserter);
            }
        } else if (field_spec.getInputFields().size() == 0 && field_spec.getCommand() == VsmsummaryConfig::Fieldmap::Command::NONE) {
        } else {
            insert_flattened_summary_field(field_spec, doc, inserter);
        }
    }
}

}
