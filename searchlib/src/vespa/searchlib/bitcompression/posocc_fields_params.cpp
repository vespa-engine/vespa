// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posocc_fields_params.h"
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".posocc_fields_params");

using search::index::SchemaUtil;
using vespalib::GenericHeader;

namespace search::bitcompression {

PosOccFieldsParams::PosOccFieldsParams()
    : _numFields(0u),
      _fieldParams(nullptr),
      _params()
{
}

PosOccFieldsParams::PosOccFieldsParams(const PosOccFieldsParams &rhs)
    : _numFields(0u),
      _fieldParams(nullptr),
      _params(rhs._params)
{
    cacheParamsRef();
}

PosOccFieldsParams &
PosOccFieldsParams::operator=(const PosOccFieldsParams &rhs)
{
    assertCachedParamsRef();
    _params = rhs._params;
    cacheParamsRef();
    return *this;
}

void
PosOccFieldsParams::assertCachedParamsRef() const {
    assert(_numFields == _params.size());
    assert(_fieldParams == (_params.empty() ? nullptr : &_params[0]));
}


bool
PosOccFieldsParams::operator==(const PosOccFieldsParams &rhs) const
{
    return _params == rhs._params;
}


void
PosOccFieldsParams::getParams(PostingListParams &params) const
{
    assertCachedParamsRef();
    assert(_numFields == 1u); // Only single field for now
    params.set("numFields", _numFields);
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < _numFields; ++field) {
        _fieldParams[field].getParams(params, field);
    }
}


void
PosOccFieldsParams::setParams(const PostingListParams &params)
{
    assertCachedParamsRef();
    uint32_t numFields = _numFields;
    params.get("numFields", numFields);
    assert(numFields == 1u);
    _params.resize(numFields);
    cacheParamsRef();
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < numFields; ++field) {
        _params[field].setParams(params, field);
    }
}


void
PosOccFieldsParams::setSchemaParams(const Schema &schema,
                                    const uint32_t indexId)
{
    assertCachedParamsRef();
    SchemaUtil::IndexIterator i(schema, indexId);
    assert(i.isValid());
    _params.resize(1u);
    cacheParamsRef();
    const Schema::IndexField &field = schema.getIndexField(indexId);
    if (!SchemaUtil::validateIndexField(field)) {
        LOG_ABORT("should not be reached");
    }
    _params[0].setSchemaParams(schema, indexId);
}


void
PosOccFieldsParams::readHeader(const vespalib::GenericHeader &header,
               const vespalib::string &prefix)
{
    vespalib::string numFieldsKey(prefix + "numFields");
    assertCachedParamsRef();
    uint32_t numFields = header.getTag(numFieldsKey).asInteger();
    assert(numFields == 1u);
    _params.resize(numFields);
    cacheParamsRef();
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < numFields; ++field) {
        vespalib::asciistream as;
        as << prefix << "field[" << field << "].";
        vespalib::string subPrefix(as.str());
        _params[field].readHeader(header, subPrefix);
    }
}


void
PosOccFieldsParams::writeHeader(vespalib::GenericHeader &header,
                                const vespalib::string &prefix) const
{
    vespalib::string numFieldsKey(prefix + "numFields");
    assertCachedParamsRef();
    assert(_numFields == 1u);
    header.putTag(GenericHeader::Tag(numFieldsKey, _numFields));
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < _numFields; ++field) {
        vespalib::asciistream as;
        as << prefix << "field[" << field << "].";
        vespalib::string subPrefix(as.str());
        _params[field].writeHeader(header, subPrefix);
    }
}

void
PosOccFieldsParams::set_field_length_info(const index::FieldLengthInfo &field_length_info)
{
    assert(!_params.empty());
    _params.front().set_field_length_info(field_length_info);
}

}
