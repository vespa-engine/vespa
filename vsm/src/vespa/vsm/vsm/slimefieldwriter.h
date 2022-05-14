// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "docsumfieldspec.h"
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/util/rawbuf.h>

namespace vsm {

/**
 * This class is used to write a field value as slime binary data.
 * If only a subset of the field value should be written this subset
 * is specified using the setInputFields() function.
 **/
class SlimeFieldWriter
{
private:
    search::RawBuf  _rbuf;
    vespalib::Slime _slime;
    const DocsumFieldSpec::FieldIdentifierVector * _inputFields;
    std::vector<vespalib::string> _currPath;

    void traverseRecursive(const document::FieldValue & fv, vespalib::slime::Inserter & inserter);
    bool explorePath(vespalib::stringref candidate);

public:
    SlimeFieldWriter();
    ~SlimeFieldWriter();


    /**
     * Specifies the subset of the field value that should be written.
     **/
    void setInputFields(const DocsumFieldSpec::FieldIdentifierVector & inputFields) { _inputFields = &inputFields; }

    /**
     * Convert the given field value
     **/
    void convert(const document::FieldValue & fv);

    /**
     * Return a reference to the output binary data
     **/
    vespalib::stringref out() const {
        return vespalib::stringref(_rbuf.GetDrainPos(), _rbuf.GetUsedLen());
    }

    void clear() {
        _rbuf.Reuse();
        _inputFields = nullptr;
        _currPath.clear();
    }
};

}
