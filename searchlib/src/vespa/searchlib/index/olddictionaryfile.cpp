// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "olddictionaryfile.h"

namespace search::index {

OldDictionaryIndexMapping::OldDictionaryIndexMapping()
    : _fieldIdToLocalId(),
      _indexNames(),
      _indexIds(),
      _washedIndexIds()
{
}


OldDictionaryIndexMapping::~OldDictionaryIndexMapping()
{
}


void
OldDictionaryIndexMapping::
setup(const Schema &schema,
      const std::vector<vespalib::string> &fieldNames)
{
    _indexIds.clear();
    _washedIndexIds.clear();
    _indexNames.clear();

    for (std::vector<vespalib::string>::const_iterator
             i = fieldNames.begin(), ie = fieldNames.end();
         i != ie;
         ++i) {
        uint32_t fieldId = schema.getIndexFieldId(*i);
        _indexIds.push_back(fieldId);
        if (fieldId != Schema::UNKNOWN_FIELD_ID)
            _washedIndexIds.push_back(fieldId);
        _indexNames.push_back(*i);
    }
    setupHelper(schema);
}


void
OldDictionaryIndexMapping::setup(const Schema &schema,
                                 const std::vector<uint32_t> &fields)
{
    _indexIds.clear();
    _washedIndexIds.clear();
    _indexNames.clear();

    uint32_t fieldId = 0;
    vespalib::string fname;
    for (std::vector<uint32_t>::const_iterator
             i = fields.begin(), ie = fields.end();
         i != ie;
         ++i, ++fieldId)
    {
        assert(*i != Schema::UNKNOWN_FIELD_ID);
        assert(*i < schema.getNumIndexFields());
        fname = schema.getIndexField(*i).getName();
        _indexIds.push_back(*i);
        _washedIndexIds.push_back(*i);
        _indexNames.push_back(fname);
    }
    setupHelper(schema);
}


void
OldDictionaryIndexMapping::setupHelper(const Schema &schema)
{
    // Create mapping to local ids

    _fieldIdToLocalId.clear();
    uint32_t localId = 0;
    vespalib::string fname;
    for (std::vector<uint32_t>::const_iterator
             i = _indexIds.begin(), ie = _indexIds.end();
         i != ie;
         ++i, ++localId)
    {
        if (*i == Schema::UNKNOWN_FIELD_ID)
            continue;		// Field on file not in current schema
        assert(*i < schema.getNumIndexFields());
        (void) schema;
        while (_fieldIdToLocalId.size() <= *i)
            _fieldIdToLocalId.push_back(noLocalId());
        assert(_fieldIdToLocalId[*i] == noLocalId());
        _fieldIdToLocalId[*i] = localId;
    }
}


OldDictionaryFileSeqRead::~OldDictionaryFileSeqRead()
{
}


OldDictionaryFileSeqWrite::~OldDictionaryFileSeqWrite()
{
}

}
