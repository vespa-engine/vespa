// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/vsm/docsumfieldspec.h>

namespace vsm {

/**
 * This class is used to write a field value as a json string.
 * If only a subset of the field value should be written this subset
 * is specified using the setInputFields() function.
 **/
class JSONDocsumWriter
{
private:
    vespalib::JSONStringer _output;
    const DocsumFieldSpec::FieldIdentifierVector * _inputFields;
    FieldPath _currPath;

    void traverseRecursive(const document::FieldValue & fv);
    bool explorePath();

public:
    JSONDocsumWriter();

    /**
     * Specifies the subset of the field value that should be written.
     **/
    void setInputFields(const DocsumFieldSpec::FieldIdentifierVector & inputFields) { _inputFields = &inputFields; }

    /**
     * Writes the given field value using the underlying JSONStringer.
     **/
    void write(const document::FieldValue & fv);

    /**
     * Returns the result as a string.
     **/
    vespalib::string getResult() { return _output.toString(); }

    /**
     * Clears this instance such that it is ready to write a new field value.
     **/
    void clear();
};

}

