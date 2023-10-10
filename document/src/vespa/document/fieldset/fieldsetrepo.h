// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldset/fieldset.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/datatype/documenttype.h>

namespace document {

class DocumentTypeRepo;

/**
 * Class that has configuration for all document field sets.
 * Responsible for parsing field set strings using a documenttype.
 */
class FieldSetRepo
{
public:
    FieldSetRepo(const DocumentTypeRepo& repo);
    ~FieldSetRepo();

    FieldSet::SP getFieldSet(vespalib::stringref fieldSetString) const;

    static FieldSet::SP parse(const DocumentTypeRepo& repo, vespalib::stringref fieldSetString);
    static vespalib::string serialize(const FieldSet& fs);
private:
    void configureDocumentType(const DocumentType & documentType);
    const DocumentTypeRepo & _doumentTyperepo;
    vespalib::hash_map<vespalib::string, FieldSet::SP> _configuredFieldSets;
};

}


