// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "docsumfieldspec.h"
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/document/fieldvalue/fieldvalues.h>

namespace vespalib::slime { class Inserter; }

namespace vsm {

/**
 * This class is used to write a field value as slime binary data.
 * If only a subset of the field value should be written this subset
 * is specified using the setInputFields() function.
 **/
class SlimeFieldWriter
{
private:
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
     * Insert the given field value
     **/
    void insert(const document::FieldValue & fv, vespalib::slime::Inserter& inserter);

    void clear() {
        _inputFields = nullptr;
        _currPath.clear();
    }
};

}
