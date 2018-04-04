// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsetrepo.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/repo/documenttyperepo.h>

using vespalib::StringTokenizer;

namespace document {

namespace {

FieldSet::UP
parseSpecialValues(const vespalib::stringref & name)
{
    FieldSet::UP fs;
    if ((name.size() == 4) && (name[1] == 'i') && (name[2] == 'd') && (name[3] == ']')) {
        fs.reset(new DocIdOnly());
    } else if ((name.size() == 5) && (name[1] == 'a') && (name[2] == 'l') && (name[3] == 'l') && (name[4] == ']')) {
        fs.reset(new AllFields());
    } else if ((name.size() == 6) && (name[1] == 'n') && (name[2] == 'o') && (name[3] == 'n') && (name[4] == 'e') && (name[5] == ']')) {
        fs.reset(new NoFields());
    } else if ((name.size() == 8) && (name[1] == 'h') && (name[2] == 'e') && (name[3] == 'a') && (name[4] == 'd') && (name[5] == 'e') && (name[6] == 'r') && (name[7] == ']')) {
        fs.reset(new HeaderFields());
    } else if ((name.size() == 7) && (name[1] == 'd') && (name[2] == 'o') && (name[3] == 'c') && (name[4] == 'i') && (name[5] == 'd') && (name[6] == ']')) {
        fs.reset(new DocIdOnly());
    } else if ((name.size() == 6) && (name[1] == 'b') && (name[2] == 'o') && (name[3] == 'd') && (name[4] == 'y') && (name[5] == ']')) {
        fs.reset(new BodyFields());
    } else {
        throw vespalib::IllegalArgumentException(
                "The only special names (enclosed in '[]') allowed are "
                "id, all, none, header, body, not '" + name + "'.");
    }
    return fs;
}

FieldSet::UP
parseFieldCollection(const DocumentTypeRepo& repo,
                     const vespalib::stringref & docType,
                     const vespalib::stringref & fieldNames)
{
    const DocumentType* typePtr = repo.getDocumentType(docType);
    if (!typePtr) {
        throw vespalib::IllegalArgumentException(
                "Unknown document type " + docType);
    }
    const DocumentType& type(*typePtr);

    StringTokenizer tokenizer(fieldNames, ",");
    FieldCollection::UP collection(new FieldCollection(type));

    for (uint32_t i = 0; i < tokenizer.size(); ++i) {
        const DocumentType::FieldSet * fs = type.getFieldSet(tokenizer[i]);
        if (fs) {
            for (DocumentType::FieldSet::Fields::const_iterator it(fs->getFields().begin()), mt(fs->getFields().end()); it != mt; it++) {
                collection->insert(type.getField(*it));
            }
        } else {
            collection->insert(type.getField(tokenizer[i]));
        }
    }

    return FieldSet::UP(collection.release());
}

}

FieldSet::UP
FieldSetRepo::parse(const DocumentTypeRepo& repo, const vespalib::stringref & str)
{
    if (str[0] == '[') {
        return parseSpecialValues(str);
    } else {
        StringTokenizer tokenizer(str, ":");
        if (tokenizer.size() != 2) {
            throw vespalib::IllegalArgumentException(
                    "The field set list must consist of a document type, "
                    "then a colon (:), then a comma-separated list of field names");
        }

        return parseFieldCollection(repo, tokenizer[0], tokenizer[1]);
    }
}

vespalib::string
FieldSetRepo::serialize(const FieldSet& fieldSet)
{
    switch (fieldSet.getType()) {
    case FieldSet::FIELD:
        return static_cast<const Field&>(fieldSet).getName();
        break;
    case FieldSet::SET:
    {
        const FieldCollection& collection = static_cast<const FieldCollection&>(fieldSet);

        vespalib::asciistream stream;
        stream << collection.getDocumentType().getName() << ":";
        for (Field::Set::const_iterator iter = collection.getFields().begin();
             iter != collection.getFields().end();
             ++iter) {
            if (iter != collection.getFields().begin()) {
                stream << ",";
            }

            stream << (*iter)->getName();
        }

        return stream.str();
    }
    case FieldSet::ALL:
        return "[all]";
    case FieldSet::NONE:
        return "[none]";
    case FieldSet::HEADER:
        return "[header]";
    case FieldSet::BODY:
        return "[body]";
    case FieldSet::DOCID:
        return "[docid]";
    default:
        return "";
    }
}

}

