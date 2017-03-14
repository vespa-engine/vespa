// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slimefieldwriter.h"
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.slimefieldwriter");

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

} // namespace <unnamed>

using namespace vespalib::slime::convenience;


namespace vsm {

void
SlimeFieldWriter::traverseRecursive(const document::FieldValue & fv,
                                    Inserter &inserter)
{
    LOG(debug, "traverseRecursive: class(%s), fieldValue(%s), currentPath(%s)",
        fv.getClass().name(), fv.toString().c_str(), toString(_currPath).c_str());

    if (fv.getClass().inherits(document::CollectionFieldValue::classId)) {
        const document::CollectionFieldValue & cfv = static_cast<const document::CollectionFieldValue &>(fv);
        if (cfv.inherits(document::ArrayFieldValue::classId)) {
            const document::ArrayFieldValue & afv = static_cast<const document::ArrayFieldValue &>(cfv);
            Cursor &a = inserter.insertArray();
            for (size_t i = 0; i < afv.size(); ++i) {
                const document::FieldValue & nfv = afv[i];
                ArrayInserter ai(a);
                traverseRecursive(nfv, ai);
            }
        } else if (cfv.inherits(document::WeightedSetFieldValue::classId)) {
            const document::WeightedSetFieldValue & wsfv = static_cast<const document::WeightedSetFieldValue &>(cfv);
            Cursor &a = inserter.insertArray();
            Symbol isym = a.resolve("item");
            Symbol wsym = a.resolve("weight");
            for (const auto &entry : wsfv) {
                Cursor &o = a.addObject();
                const document::FieldValue & nfv = *entry.first;
                ObjectSymbolInserter oi(o, isym);
                traverseRecursive(nfv, oi);
                int weight = static_cast<const document::IntFieldValue &>(*entry.second).getValue();
                o.setLong(wsym, weight);
            }
        } else {
            LOG(warning, "traverseRecursive: Cannot handle collection field value of type '%s'",
                fv.getClass().name());
        }

    } else if (fv.getClass().inherits(document::MapFieldValue::classId)) {
        const document::MapFieldValue & mfv = static_cast<const document::MapFieldValue &>(fv);
        const document::MapDataType& mapType = static_cast<const document::MapDataType &>(*mfv.getDataType());
        Cursor &a = inserter.insertArray();
        Symbol keysym = a.resolve("key");
        Symbol valsym = a.resolve("value");
        for (const auto &entry : mfv) {
            Cursor &o = a.addObject();
            ObjectSymbolInserter ki(o, keysym);
            traverseRecursive(*entry.first, ki);
            document::FieldPathEntry valueEntry(
                    mapType, mapType.getKeyType(), mapType.getValueType(),
                    false, true);
            _currPath.push_back(valueEntry);
            ObjectSymbolInserter vi(o, valsym);
            traverseRecursive(*entry.second, vi);
            _currPath.pop_back();
        }
    } else if (fv.getClass().inherits(document::StructuredFieldValue::classId)) {
        const document::StructuredFieldValue & sfv = static_cast<const document::StructuredFieldValue &>(fv);
        Cursor &o = inserter.insertObject();
        for (const document::Field & entry : sfv) {
            // TODO: Why do we have to iterate like this?
            document::FieldPathEntry fi(sfv.getField(entry.getName()));
            _currPath.push_back(fi);
            if (explorePath()) {
                Memory keymem(entry.getName());
                ObjectInserter oi(o, keymem);
                document::FieldValue::UP fval(sfv.getValue(entry));
                traverseRecursive(*fval, oi);
            }
            _currPath.pop_back();
        }
    } else {
        if (fv.getClass().inherits(document::LiteralFieldValueB::classId)) {
            const document::LiteralFieldValueB & lfv = static_cast<const document::LiteralFieldValueB &>(fv);
            inserter.insertString(lfv.getValueRef());
        } else if (fv.getClass().inherits(document::NumericFieldValueBase::classId)) {
            switch (fv.getDataType()->getId()) {
            case document::DataType::T_BYTE:
            case document::DataType::T_SHORT:
            case document::DataType::T_INT:
            case document::DataType::T_LONG:
                inserter.insertLong(fv.getAsLong());
                break;
            case document::DataType::T_DOUBLE:
                inserter.insertDouble(fv.getAsDouble());
                break;
            case document::DataType::T_FLOAT:
                inserter.insertDouble(fv.getAsFloat());
                break;
            default:
                inserter.insertString(fv.getAsString());
            }
        } else {
            inserter.insertString(fv.toString());
        }
    }
}

bool
SlimeFieldWriter::explorePath()
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

SlimeFieldWriter::SlimeFieldWriter() :
    _rbuf(4096),
    _slime(),
    _inputFields(NULL),
    _currPath()
{
}

SlimeFieldWriter::~SlimeFieldWriter() {}

void
SlimeFieldWriter::convert(const document::FieldValue & fv)
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
    SlimeInserter inserter(_slime);
    traverseRecursive(fv, inserter);
    search::SlimeOutputRawBufAdapter adapter(_rbuf);
    vespalib::slime::BinaryFormat::encode(_slime, adapter);
}

}
