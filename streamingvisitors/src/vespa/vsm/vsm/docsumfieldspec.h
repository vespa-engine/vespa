// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/common/document.h>
#include <vespa/vsm/config/vsm-cfif.h>

namespace search::docsummary { class SlimeFillerFilter; }

namespace vsm {

/**
 * This class contains the specifications for how to generate a summary field.
 **/
class DocsumFieldSpec {
public:
    using FieldPath = document::FieldPath;
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
        FieldIdentifier(FieldIdentifier &&) noexcept;
        FieldIdentifier & operator=(FieldIdentifier &&) noexcept;
        FieldIdentifier(const FieldIdentifier &) = delete;
        FieldIdentifier & operator=(const FieldIdentifier &) = delete;
        ~FieldIdentifier();
        FieldIdT getId() const { return _id; }
        const FieldPath & getPath() const { return _path; }
    };

    typedef std::vector<FieldIdentifier> FieldIdentifierVector;

private:
    bool                        _struct_or_multivalue; // property of the output field
    VsmsummaryConfig::Fieldmap::Command  _command;
    FieldIdentifier             _outputField;
    FieldIdentifierVector       _inputFields;
    std::unique_ptr<search::docsummary::SlimeFillerFilter> _filter;

public:
    DocsumFieldSpec();
    DocsumFieldSpec(VsmsummaryConfig::Fieldmap::Command command);
    DocsumFieldSpec(DocsumFieldSpec&&) noexcept;
    ~DocsumFieldSpec();

    bool is_struct_or_multivalue() const noexcept { return _struct_or_multivalue; }
    void set_struct_or_multivalue(bool struct_or_multivalue) { _struct_or_multivalue = struct_or_multivalue; }

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
    void setOutputField(FieldIdentifier outputField) { _outputField = std::move(outputField); }
    const FieldIdentifierVector & getInputFields() const { return _inputFields; }
    FieldIdentifierVector & getInputFields() { return _inputFields; }
    void set_filter(std::unique_ptr<search::docsummary::SlimeFillerFilter> filter);
    const search::docsummary::SlimeFillerFilter *get_filter() const noexcept;
};

}

