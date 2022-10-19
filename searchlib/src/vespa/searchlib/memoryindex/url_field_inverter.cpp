// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "url_field_inverter.h"
#include "field_inverter.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/searchlib/util/url.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.url_field_inverter");

namespace search::memoryindex {

namespace {

static vespalib::string HOSTNAME_BEGIN("StArThOsT");
static vespalib::string HOSTNAME_END("EnDhOsT");

static size_t
lowercaseToken(vespalib::string &dest, const char *src, size_t srcSize)
{
    dest.clear();
    dest.reserve(8 + srcSize);

    vespalib::Utf8Reader r(src, srcSize);
    vespalib::Utf8Writer w(dest);

    using vespalib::LowerCase;

    while (r.hasMore()) {
        uint32_t i = r.getChar(vespalib::Utf8::BAD);
        if (i != vespalib::Utf8::BAD) {
            w.putChar(LowerCase::convert(i));
        }
    }
    return dest.size();
}

}

using document::ArrayFieldValue;
using document::DataType;
using document::FieldValue;
using document::IntFieldValue;
using document::SpanTree;
using document::StringFieldValue;
using document::StructFieldValue;
using document::WeightedSetFieldValue;
using search::index::Schema;
using search::index::schema::CollectionType;
using search::util::URL;
using vespalib::make_string;

void
UrlFieldInverter::startDoc(uint32_t docId)
{
    _all->startDoc(docId);
    _scheme->startDoc(docId);
    _host->startDoc(docId);
    _port->startDoc(docId);
    _path->startDoc(docId);
    _query->startDoc(docId);
    _fragment->startDoc(docId);
    _hostname->startDoc(docId);
}

void
UrlFieldInverter::endDoc()
{
    _all->endDoc();
    _scheme->endDoc();
    _host->endDoc();
    _port->endDoc();
    _path->endDoc();
    _query->endDoc();
    _fragment->endDoc();
    _hostname->endDoc();
}

void
UrlFieldInverter::startElement(int32_t weight)
{
    _all->startElement(weight);
    _scheme->startElement(weight);
    _host->startElement(weight);
    _port->startElement(weight);
    _path->startElement(weight);
    _query->startElement(weight);
    _fragment->startElement(weight);
    _hostname->startElement(weight);
}

void
UrlFieldInverter::endElement()
{
    _all->endElement();
    _scheme->endElement();
    _host->endElement();
    _port->endElement();
    _path->endElement();
    _query->endElement();
    _fragment->endElement();
    _hostname->endElement();
}

void
UrlFieldInverter::processUrlField(const FieldValue &url_field)
{
    assert(url_field.isA(FieldValue::Type::STRING));
    const vespalib::string &url_str =
        static_cast<const StringFieldValue &>(url_field).getValue();
    processUrlOldStyle(url_str);
    return;
}

void
UrlFieldInverter::processUrlOldStyle(const vespalib::string &s)
{
    URL url(reinterpret_cast<const unsigned char *>(s.data()), s.size());

    _hostname->addWord(HOSTNAME_BEGIN);

    vespalib::string lowToken;
    const unsigned char *t;
    URL::URL_CONTEXT url_context;
    while ((t = url.GetToken(url_context))) {
        const char *token = reinterpret_cast<const char *>(t);
        size_t tokenLen = strlen(token);
        tokenLen = lowercaseToken(lowToken, token, tokenLen);
        token = lowToken.c_str();
        vespalib::stringref tokenRef(token, tokenLen);
        switch (url_context) {
        case URL::URL_SCHEME:
            _scheme->addWord(tokenRef);
            _all->addWord(tokenRef);
            break;
        case URL::URL_HOST:
        case URL::URL_DOMAIN:
        case URL::URL_MAINTLD:
            _host->addWord(tokenRef);
            _hostname->addWord(tokenRef);
            _all->addWord(tokenRef);
            break;
        case URL::URL_PORT:
            if (strcmp(token, "80") && strcmp(token, "443")) {
                _port->addWord(tokenRef);
                _all->addWord(tokenRef);
            }
            break;
        case URL::URL_PATH:
        case URL::URL_FILENAME:
        case URL::URL_EXTENSION:
        case URL::URL_PARAMS:
            _path->addWord(tokenRef);
            _all->addWord(tokenRef);
            break;
        case URL::URL_QUERY:
            _query->addWord(tokenRef);
            _all->addWord(tokenRef);
            break;
        case URL::URL_FRAGMENT:
            _fragment->addWord(tokenRef);
            _all->addWord(tokenRef);
            break;
        case URL::URL_ADDRESS:
            _all->addWord(tokenRef);
            break;
        default:
            LOG(warning, "Ignoring unknown Uri token '%s'.", token);
        }
    }
    _hostname->addWord(HOSTNAME_END);
}

void
UrlFieldInverter::processArrayUrlField(const ArrayFieldValue &field)
{
    for (uint32_t el(0), ele(field.size());el < ele; ++el) {
        const FieldValue &element = field[el];
        startElement(1);
        processUrlField(element);
        endElement();
    }
}

void
UrlFieldInverter::processWeightedSetUrlField(const WeightedSetFieldValue &field)
{
    for (const auto & el : field) {
        const FieldValue &key = *el.first;
        const FieldValue &xweight = *el.second;
        assert(xweight.isA(FieldValue::Type::INT));
        int32_t weight = xweight.getAsInt();
        startElement(weight);
        processUrlField(key);
        endElement();
    }
}

namespace {

bool
isUriType(const DataType &type)
{
    return type == *DataType::STRING || type == *DataType::URI;
}

}

void
UrlFieldInverter::invertUrlField(const FieldValue &val)
{
    switch (_collectionType) {
    case CollectionType::SINGLE:
        if (isUriType(*val.getDataType())) {
            startElement(1);
            processUrlField(val);
            endElement();
        } else {
            throw std::runtime_error(make_string("Expected URI field, got '%s'", val.getDataType()->getName().c_str()));
        }
        break;
    case CollectionType::WEIGHTEDSET: {
        assert(val.isA(FieldValue::Type::WSET));
        const auto &wset = static_cast<const WeightedSetFieldValue &>(val);
        if (isUriType(wset.getNestedType())) {
            processWeightedSetUrlField(wset);
        } else {
            throw std::runtime_error(
                    make_string("Expected wset of URI struct, got '%s'", wset.getNestedType().getName().c_str()));
        }
        break;
    }
    case CollectionType::ARRAY: {
        assert(val.isA(FieldValue::Type::ARRAY));
        const auto &arr = static_cast<const ArrayFieldValue &>(val);
        if (isUriType(arr.getNestedType())) {
            processArrayUrlField(arr);
        } else {
            throw std::runtime_error(
                    make_string("Expected array of URI struct, got '%s' (%s)", arr.getNestedType().getName().c_str(),
                                arr.getNestedType().toString(true).c_str()));
        }
        break;
    }
    default:
        break;
    }
}

void
UrlFieldInverter::invertField(uint32_t docId, const FieldValue::UP &val)
{
    if (val) {
        startDoc(docId);
        invertUrlField(*val);
        endDoc();
    } else {
        removeDocument(docId);
    }
}

void
UrlFieldInverter::removeDocument(uint32_t docId)
{
    _all->removeDocument(docId);
    _scheme->removeDocument(docId);
    _host->removeDocument(docId);
    _port->removeDocument(docId);
    _path->removeDocument(docId);
    _query->removeDocument(docId);
    _fragment->removeDocument(docId);
    _hostname->removeDocument(docId);
}

void
UrlFieldInverter::applyRemoves()
{
    _all->applyRemoves();
    _scheme->applyRemoves();
    _host->applyRemoves();
    _port->applyRemoves();
    _path->applyRemoves();
    _query->applyRemoves();
    _fragment->applyRemoves();
    _hostname->applyRemoves();
}

void
UrlFieldInverter::pushDocuments()
{
    _all->pushDocuments();
    _scheme->pushDocuments();
    _host->pushDocuments();
    _port->pushDocuments();
    _path->pushDocuments();
    _query->pushDocuments();
    _fragment->pushDocuments();
    _hostname->pushDocuments();
}

UrlFieldInverter::UrlFieldInverter(index::schema::CollectionType collectionType,
                                   FieldInverter *all,
                                   FieldInverter *scheme,
                                   FieldInverter *host,
                                   FieldInverter *port,
                                   FieldInverter *path,
                                   FieldInverter *query,
                                   FieldInverter *fragment,
                                   FieldInverter *hostname)
    : _all(all),
      _scheme(scheme),
      _host(host),
      _port(port),
      _path(path),
      _query(query),
      _fragment(fragment),
      _hostname(hostname),
      _collectionType(collectionType)
{
}

}
