// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/datatype.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>

namespace search::memoryindex {

class FieldInverter;

class UrlFieldInverter {
    FieldInverter *_all;
    FieldInverter *_scheme;
    FieldInverter *_host;
    FieldInverter *_port;
    FieldInverter *_path;
    FieldInverter *_query;
    FieldInverter *_fragment;
    FieldInverter *_hostname;

    index::schema::CollectionType _collectionType;

    void startDoc(uint32_t docId);

    void endDoc();

    void startElement(int32_t weight);

    void endElement();

    void processUrlField(const document::FieldValue &url_field);

    void processUrlOldStyle(const vespalib::string &s);

    void processArrayUrlField(const document::ArrayFieldValue &field);

    void processWeightedSetUrlField(const document::WeightedSetFieldValue &field);

    void invertUrlField(const document::FieldValue &field);
public:
    UrlFieldInverter(index::schema::CollectionType collectionType,
                     FieldInverter *all,
                     FieldInverter *scheme,
                     FieldInverter *host,
                     FieldInverter *port,
                     FieldInverter *path,
                     FieldInverter *query,
                     FieldInverter *fragment,
                     FieldInverter *hostname);

    void invertField(uint32_t docId, const document::FieldValue::UP &field);
    void removeDocument(uint32_t docId);

    void applyRemoves();
    void pushDocuments();
};

}
