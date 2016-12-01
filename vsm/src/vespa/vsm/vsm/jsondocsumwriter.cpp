// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jsondocsumwriter.h"
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.jsondocsumwriter");

namespace {

vespalib::string
toString(const vsm::FieldPath & fp)
{
    vespalib::asciistream oss;
    for (size_t i = 0; i < fp.size(); ++i) {
        if (i > 0) {
            oss << ".";
        }
        oss << fp[i].getName();
    }
    return oss.str();
}

}

namespace vsm {

void
JSONDocsumWriter::traverseRecursive(const document::FieldValue & fv)
{
    LOG(debug, "traverseRecursive: class(%s), fieldValue(%s), currentPath(%s)",
        fv.getClass().name(), fv.toString().c_str(), toString(_currPath).c_str());

    if (fv.getClass().inherits(document::CollectionFieldValue::classId)) {
        const document::CollectionFieldValue & cfv = static_cast<const document::CollectionFieldValue &>(fv);
        if (cfv.inherits(document::ArrayFieldValue::classId)) {
            const document::ArrayFieldValue & afv = static_cast<const document::ArrayFieldValue &>(cfv);
            _output.beginArray();
            for (size_t i = 0; i < afv.size(); ++i) {
                const document::FieldValue & nfv = afv[i];
                traverseRecursive(nfv);
            }
            _output.endArray();
        } else if (cfv.inherits(document::WeightedSetFieldValue::classId)) {
            const document::WeightedSetFieldValue & wsfv = static_cast<const document::WeightedSetFieldValue &>(cfv);
            _output.beginArray();
            for (document::WeightedSetFieldValue::const_iterator itr = wsfv.begin(); itr != wsfv.end(); ++itr) {
                const document::FieldValue & nfv = *itr->first;
                _output.beginArray();
                traverseRecursive(nfv);
                _output.appendInt64(static_cast<const document::IntFieldValue&>(*itr->second).getValue());
                _output.endArray();
            }
            _output.endArray();
        } else {
            LOG(warning, "traverseRecursive: Cannot handle collection field value of type '%s'",
                fv.getClass().name());
        }

    } else if (fv.getClass().inherits(document::MapFieldValue::classId)) {
        const document::MapFieldValue & mfv = static_cast<const document::MapFieldValue &>(fv);
        _output.beginArray();
        for (document::MapFieldValue::const_iterator itr = mfv.begin(); itr != mfv.end(); ++itr) {
            _output.beginObject();
            _output.appendKey("key");
            traverseRecursive(*itr->first);
            _output.appendKey("value");
            const document::MapDataType& mapType = static_cast<const document::MapDataType &>(*mfv.getDataType());
            document::FieldPathEntry valueEntry(
                    mapType, mapType.getKeyType(), mapType.getValueType(),
                    false, true);
            _currPath.push_back(valueEntry);
            traverseRecursive(*itr->second);
            _currPath.pop_back();
            _output.endObject();
        }
        _output.endArray();
    } else if (fv.getClass().inherits(document::StructuredFieldValue::classId)) {
        const document::StructuredFieldValue & sfv = static_cast<const document::StructuredFieldValue &>(fv);
        _output.beginObject();
        for (document::StructuredFieldValue::const_iterator itr = sfv.begin(); itr != sfv.end(); ++itr) {
            // TODO: Why do we have to iterate like this?
            document::FieldPathEntry fi(sfv.getField(itr.field().getName()));
            _currPath.push_back(fi);
            if (explorePath()) {
                _output.appendKey(itr.field().getName());
                document::FieldValue::UP fval(sfv.getValue(itr.field()));
                traverseRecursive(*fval);
            }
            _currPath.pop_back();
        }
        _output.endObject();

    } else {
        if (fv.getClass().inherits(document::LiteralFieldValueB::classId)) {
            const document::LiteralFieldValueB & lfv = static_cast<const document::LiteralFieldValueB &>(fv);
            _output.appendString(lfv.getValueRef());
        } else if (fv.getClass().inherits(document::NumericFieldValueBase::classId)) {
            switch (fv.getDataType()->getId()) {
            case document::DataType::T_BYTE:
            case document::DataType::T_SHORT:
            case document::DataType::T_INT:
            case document::DataType::T_LONG:
                _output.appendInt64(fv.getAsLong());
                break;
            case document::DataType::T_FLOAT:
                _output.appendFloat(fv.getAsFloat());
                break;
            case document::DataType::T_DOUBLE:
                _output.appendDouble(fv.getAsFloat());
                break;
            default:
                _output.appendString(fv.getAsString());
            }
        } else {
            _output.appendString(fv.toString());
        }
    }
}

bool
JSONDocsumWriter::explorePath()
{
    if (_inputFields == NULL) {
        return true;
    }
    // find out if we should explore the current path
    for (size_t i = 0; i < _inputFields->size(); ++i) {
        const FieldPath & fp = (*_inputFields)[i].getPath();
        if (_currPath.size() <= fp.size()) {
            bool equal = true;
            for (size_t j = 0; j < _currPath.size() && equal; ++j) {
                equal = (fp[j].getName() == _currPath[j].getName());
            }
            if (equal) {
                // the current path matches one of the input field paths
                return true;
            }
        }
    }
    return false;
}

JSONDocsumWriter::JSONDocsumWriter() :
    _output(),
    _inputFields(NULL),
    _currPath()
{
}

void
JSONDocsumWriter::write(const document::FieldValue & fv)
{
    if (LOG_WOULD_LOG(debug)) {
        if (_inputFields != NULL) {
            for (size_t i = 0; i < _inputFields->size(); ++i) {
                LOG(debug, "write: input field path [%zd] '%s'", i, toString((*_inputFields)[i].getPath()).c_str());
            }
        } else {
            LOG(debug, "write: no input fields");
        }
    }
    traverseRecursive(fv);
}

void
JSONDocsumWriter::clear()
{
    _output.clear();
    _inputFields = NULL;
    _currPath.clear();
}

}
