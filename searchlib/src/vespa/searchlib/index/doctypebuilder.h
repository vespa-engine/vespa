// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::index {

/**
 * Builder for the indexingdocument document type based on an index schema.
 **/
class DocTypeBuilder {
public:
    typedef std::vector<bool> UsedFieldsMap;
    typedef std::vector<uint32_t> FieldIdVector;

    class UriField
    {
    public:
        uint32_t _all;
        uint32_t _scheme;
        uint32_t _host;
        uint32_t _port;
        uint32_t _path;
        uint32_t _query;
        uint32_t _fragment;
        uint32_t _hostname;

    private:
        static void markUsed(UsedFieldsMap &usedFields, uint32_t field);
        static bool valid(const Schema &schema, uint32_t fieldId,
                          const Schema::CollectionType &collectionType);

    public:
        UriField();

        bool broken(const Schema &schema, const Schema::CollectionType &collectionType) const;
        bool valid(const Schema &schema, const Schema::CollectionType &collectionType) const;
        void setup(const Schema &schema, const vespalib::string &field);
        void markUsed(UsedFieldsMap &usedFields) const;
    };

    typedef std::vector<UriField> UriFieldIdVector;

    class SchemaIndexFields
    {
    public:
        FieldIdVector _textFields;
        UriFieldIdVector _uriFields;

        SchemaIndexFields();
        ~SchemaIndexFields();
        void setup(const Schema &schema);
    };

private:
    const Schema &_schema;
    SchemaIndexFields _iFields;

public:
    DocTypeBuilder(const Schema & schema);
    document::DocumenttypesConfig makeConfig() const;

    static document::DocumenttypesConfig
    makeConfig(const document::DocumentType &docType);
};

}
