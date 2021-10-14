// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/vsm/common/document.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/config/vsm-cfif.h>

namespace vsm {

/**
 * This class contains the specifications for how to generate a summary field.
 **/
class DocsumFieldSpec {
public:
    /**
     * This class contains a field id and a field path (to navigate a field value).
     **/
    class FieldIdentifier {
    private:
        FieldIdT  _id;
        FieldPath _path;

    public:
        FieldIdentifier();
        FieldIdentifier(FieldIdT id, FieldPath path);
        FieldIdT getId() const { return _id; }
        const FieldPath & getPath() const { return _path; }
    };

    typedef std::vector<FieldIdentifier> FieldIdentifierVector;

private:
    search::docsummary::ResType _resultType;
    VsmsummaryConfig::Fieldmap::Command  _command;
    FieldIdentifier             _outputField;
    FieldIdentifierVector       _inputFields;

public:
    DocsumFieldSpec();
    DocsumFieldSpec(search::docsummary::ResType resultType, VsmsummaryConfig::Fieldmap::Command command);

    /**
     * Returns the result type for the summary field.
     **/
    search::docsummary::ResType getResultType() const { return _resultType; }

    /**
     * Returns the command specifying how to transform input fields into output summary field.
     **/
    VsmsummaryConfig::Fieldmap::Command getCommand() const { return _command; }

    /**
     * Returns whether the input field and output field are identical.
     **/
    bool hasIdentityMapping() const {
        return _inputFields.size() == 1 && _outputField.getId() == _inputFields[0].getId();
    }

    const FieldIdentifier & getOutputField() const { return _outputField; }
    void setOutputField(const FieldIdentifier & outputField) { _outputField = outputField; }
    const FieldIdentifierVector & getInputFields() const { return _inputFields; }
    FieldIdentifierVector & getInputFields() { return _inputFields; }
};

}

