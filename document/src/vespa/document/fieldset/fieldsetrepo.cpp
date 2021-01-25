// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsetrepo.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".document.fieldset.fieldsetrepo");

using vespalib::StringTokenizer;

namespace document {

namespace {

FieldSet::SP
parseSpecialValues(vespalib::stringref name)
{
    if ((name.size() == 4) && (name[1] == 'i') && (name[2] == 'd') && (name[3] == ']')) {
        return std::make_shared<DocIdOnly>();
    } else if ((name.size() == 5) && (name[1] == 'a') && (name[2] == 'l') && (name[3] == 'l') && (name[4] == ']')) {
        return std::make_shared<AllFields>();
    } else if ((name.size() == 6) && (name[1] == 'n') && (name[2] == 'o') && (name[3] == 'n') && (name[4] == 'e') && (name[5] == ']')) {
        return std::make_shared<NoFields>();
    } else if ((name.size() == 7) && (name[1] == 'd') && (name[2] == 'o') && (name[3] == 'c') && (name[4] == 'i') && (name[5] == 'd') && (name[6] == ']')) {
        return std::make_shared<DocIdOnly>();
    } else {
        throw vespalib::IllegalArgumentException(
                "The only special names (enclosed in '[]') allowed are "
                "id, all, none, not '" + name + "'.");
    }
}

FieldSet::SP
parseFieldCollection(const DocumentTypeRepo& repo,
                     vespalib::stringref docType,
                     vespalib::stringref fieldNames)
{
    const DocumentType* typePtr = repo.getDocumentType(docType);
    if (!typePtr) {
        throw vespalib::IllegalArgumentException("Unknown document type " + docType);
    }
    const DocumentType& type(*typePtr);

    StringTokenizer tokenizer(fieldNames, ",");
    Field::Set::Builder builder;
    for (const auto & token : tokenizer) {
        const DocumentType::FieldSet * fs = type.getFieldSet(token);
        if (fs) {
            for (const auto & fieldName : fs->getFields()) {
                builder.add(&type.getField(fieldName));
            }
        } else {
            builder.add(&type.getField(token));
        }
    }
    return std::make_shared<FieldCollection>(type, builder.build());
}

}

FieldSet::SP
FieldSetRepo::parse(const DocumentTypeRepo& repo, vespalib::stringref str)
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
        case FieldSet::Type::FIELD:
            return static_cast<const Field&>(fieldSet).getName();
        case FieldSet::Type::SET: {
            const auto & collection = static_cast<const FieldCollection&>(fieldSet);

            vespalib::asciistream stream;
            stream << collection.getDocumentType().getName() << ":";
            bool first = true;
            for (const Field * field : collection.getFields()) {
                if (first) {
                    first = false;
                } else {
                    stream << ",";
                }
                stream << field->getName();
            }

            return stream.str();
        }
        case FieldSet::Type::ALL:
            return AllFields::NAME;
        case FieldSet::Type::NONE:
            return NoFields::NAME;
        case FieldSet::Type::DOCID:
            return DocIdOnly::NAME;
        default:
            return "";
    }
}


FieldSetRepo::FieldSetRepo(const DocumentTypeRepo& repo)
    : _doumentTyperepo(repo),
      _configuredFieldSets()
{
    repo.forEachDocumentType(*DocumentTypeRepo::makeLambda([&](const DocumentType &type) {
        configureDocumentType(type);
    }));
}
FieldSetRepo::~FieldSetRepo() = default;

void
FieldSetRepo::configureDocumentType(const DocumentType & documentType) {
    for (const auto & entry : documentType.getFieldSets()) {
        vespalib::string fieldSetName(documentType.getName());
        fieldSetName.append(':').append(entry.first);
        try {
            auto fieldset = parse(_doumentTyperepo, fieldSetName);
            _configuredFieldSets[fieldSetName] = std::move(fieldset);
        } catch (const FieldNotFoundException & ex) {
            // Just silently skip it so error handling can be done when you can return proper error to user.
        }
    }
}
FieldSet::SP
FieldSetRepo::getFieldSet(vespalib::stringref fieldSetString) const {
    auto found = _configuredFieldSets.find(fieldSetString);
    if (found != _configuredFieldSets.end()) {
        return found->second;
    }
    return parse(_doumentTyperepo, fieldSetString);
}

}

