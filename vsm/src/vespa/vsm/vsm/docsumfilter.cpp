// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumfilter.h"
#include "slimefieldwriter.h"
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
        if (fv.getClass().inherits(document::LiteralFieldValueB::classId)) {
            const document::LiteralFieldValueB & lfv = static_cast<const document::LiteralFieldValueB &>(fv);
            vespalib::stringref s = lfv.getValueRef();
            addToPacker(s.c_str(), s.size());
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
            if (buf.first != NULL) {
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
               if (!fieldPathMap[field].empty()) {
                   // skip the element that correspond to the start field value
                   spec.setOutputField(DocsumFieldSpec::FieldIdentifier
                                       (field, FieldPath(fieldPathMap[field].begin() + 1,
                                                         fieldPathMap[field].end())));
               } else {
                   spec.setOutputField(DocsumFieldSpec::FieldIdentifier(field, FieldPath()));
               }
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
                if (!fieldPathMap[field].empty()) {
                    FieldPath relPath(fieldPathMap[field].begin() + 1,
                                      fieldPathMap[field].end());
                    LOG(debug, "map[%u] -> %zu elements", field, fieldPathMap[field].end() - fieldPathMap[field].begin());
                    // skip the element that correspond to the start field value
                    spec.getInputFields().push_back(DocsumFieldSpec::FieldIdentifier
                                                    (field, FieldPath(fieldPathMap[field].begin() + 1,
                                                                      fieldPathMap[field].end())));
                } else {
                    LOG(debug, "map[%u] empty", field);
                    spec.getInputFields().push_back(DocsumFieldSpec::FieldIdentifier(field, FieldPath()));
                }
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
    if (fv == NULL) {
        return NULL;
    }
    switch (command) {
    case VsmsummaryConfig::Fieldmap::FLATTENJUNIPER:
        if (_snippetModifiers != NULL) {
            FieldModifier * mod = _snippetModifiers->getModifier(fId);
            if (mod != NULL) {
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
    _packer(tools.get() ? tools->getResultConfig() : NULL),
    _flattenWriter(),
    _snippetModifiers(NULL),
    _cachedValue(),
    _emptyFieldPath()
{ }

DocsumFilter::~DocsumFilter() =default;

void DocsumFilter::init(const FieldMap & fieldMap, const FieldPathMapT & fieldPathMap)
{
    if (_tools.get()) {
        const ResultClass *resClass = _tools->getResultClass();
        const std::vector<DocsumTools::FieldSpec> & inputSpecs = _tools->getFieldSpecs();
        if (resClass != NULL) {
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
        LOG(warning,
            "Unknown docsum field type: %s",
            ResultConfig::GetResTypeName(type));
        packer.AddEmpty(); // unhandled output type
        break;
    }
}


void
DocsumFilter::writeSlimeField(const DocsumFieldSpec & fieldSpec,
                              const Document & docsum,
                              ResultPacker & packer)
{
    if (fieldSpec.getCommand() == VsmsummaryConfig::Fieldmap::NONE) {
        const DocsumFieldSpec::FieldIdentifier & fieldId = fieldSpec.getOutputField();
        const document::FieldValue * fv = docsum.getField(fieldId.getId());
        if (fv != NULL) {
            LOG(debug, "writeSlimeField: About to write field '%d' as Slime: field value = '%s'",
                fieldId.getId(), fv->toString().c_str());
            SlimeFieldWriter writer;
            if (! fieldSpec.hasIdentityMapping()) {
                writer.setInputFields(fieldSpec.getInputFields());
            }
            writer.convert(*fv);
            const vespalib::stringref out = writer.out();
            packer.AddLongString(out.data(), out.size());
        } else {
            LOG(debug, "writeSlimeField: Field value not set for field '%d'", fieldId.getId());
            packer.AddEmpty();
        }
    } else {
        LOG(debug, "writeSlimeField: Cannot handle this command");
        packer.AddEmpty();
    }
}

void
DocsumFilter::writeFlattenField(const DocsumFieldSpec & fieldSpec,
                                const Document & docsum,
                                ResultPacker & packer)
{
    if (fieldSpec.getCommand() == VsmsummaryConfig::Fieldmap::NONE) {
        LOG(debug, "writeFlattenField: Cannot handle command NONE");
        packer.AddEmpty();
        return;
    }

    if (fieldSpec.getResultType() != RES_LONG_STRING &&
        fieldSpec.getResultType() != RES_STRING)
    {
        LOG(debug, "writeFlattenField: Can only handle result types STRING and LONG_STRING");
        packer.AddEmpty();
        return;
    }

    switch (fieldSpec.getCommand()) {
    case VsmsummaryConfig::Fieldmap::FLATTENJUNIPER:
        _flattenWriter.setSeparator("\x1E"); // record separator (same as juniper uses)
        break;
    default:
        break;
    }
    const DocsumFieldSpec::FieldIdentifierVector & inputFields = fieldSpec.getInputFields();
    for (size_t i = 0; i < inputFields.size(); ++i) {
        const DocsumFieldSpec::FieldIdentifier & fieldId = inputFields[i];
        bool modified = false;
        const document::FieldValue * fv = getFieldValue(fieldId, fieldSpec.getCommand(), docsum, modified);
        if (fv != NULL) {
            LOG(debug, "writeFlattenField: About to flatten field '%d' with field value (%s) '%s'",
                fieldId.getId(), modified ? "modified" : "original", fv->toString().c_str());
            if (modified) {
                fv->iterateNested(_emptyFieldPath, _flattenWriter);
            } else {
                fv->iterateNested(fieldId.getPath(), _flattenWriter);
            }
        } else {
            LOG(debug, "writeFlattenField: Field value not set for field '%d'", fieldId.getId());
        }
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
        packer.AddByte(0); // byte fields are unsigned so we have no 'notdefined' value.
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
    if (resClass == NULL) {
        return DocsumStoreValue(NULL, 0);
    }

    const Document & doc = _docsumCache->getDocSum(id);

    _packer.Init(resClass->GetClassID());
    for (FieldSpecList::iterator it(_fields.begin()), end = _fields.end(); it != end; ++it) {
        ResType type = it->getResultType();
        if (type == RES_JSONSTRING || type == RES_XMLSTRING) {
            // this really means 'structured data'
            writeSlimeField(*it, doc, _packer);
        } else {
            if (it->getInputFields().size() == 1 && it->getCommand() == VsmsummaryConfig::Fieldmap::NONE) {
                const DocsumFieldSpec::FieldIdentifier & fieldId = it->getInputFields()[0];
                const document::FieldValue * field = doc.getField(fieldId.getId());
                if (field != NULL) {
                    writeField(*field, fieldId.getPath(), type, _packer);
                } else {
                    writeEmpty(type, _packer); // void input
                }
            } else if (it->getInputFields().size() == 0 && it->getCommand() == VsmsummaryConfig::Fieldmap::NONE) {
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
        return DocsumStoreValue(buf, buflen);
    } else {
        return DocsumStoreValue(NULL, 0);
    }
}

}
