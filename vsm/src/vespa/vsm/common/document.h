// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/base.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <map>

namespace vespalib {
    class asciistream;
}

namespace vsm {

/// Type to identify fields in documents.
typedef unsigned int FieldIdT;
/// A type to represent a list of FieldIds.
typedef std::vector<FieldIdT> FieldIdTList;
/// A type to represent all the fields contained in all the indexs.
typedef vespalib::hash_map<vespalib::string, FieldIdTList> IndexFieldMapT;
/// A type to represent all the fields contained in all the indexs in an all the document types.
typedef vespalib::hash_map<vespalib::string, IndexFieldMapT> DocumentTypeIndexFieldMapT;
/// A type to represent a map from fieldname to fieldid.
typedef std::map<vespalib::string, FieldIdT> StringFieldIdTMapT;

class StringFieldIdTMap
{
 public:
  enum { npos=0xFFFFFFFF };
  StringFieldIdTMap();
  FieldIdT fieldNo(const vespalib::string & fName) const;
  void add(const vespalib::string & s);
  void add(const vespalib::string & s, FieldIdT fNo);
  const StringFieldIdTMapT & map() const { return _map; }
  size_t highestFieldNo() const;
  friend vespalib::asciistream & operator << (vespalib::asciistream & os, const StringFieldIdTMap & f);
 private:
  StringFieldIdTMapT _map;
};

typedef vespalib::stringref FieldRef;

/**
  This is the base class representing a document. It gives a document some
  basic properties. A document is a collection of fields, together with a
  document id and a time stamp.
*/
class Document
{
 public:
  Document(size_t maxFieldCount) : _docId(0), _fieldCount(maxFieldCount) { }
  Document(search::DocumentIdT doc, size_t maxFieldCount) : _docId(doc), _fieldCount(maxFieldCount) { }
  virtual ~Document();
  const search::DocumentIdT & getDocId()        const { return _docId; }
  size_t getFieldCount()                        const { return _fieldCount; }
  void setDocId(const search::DocumentIdT & v)        { _docId = v; }
  virtual const document::FieldValue * getField(FieldIdT fId) const = 0;
  /**
   Returns true, if not possible to set.
   */
  virtual bool setField(FieldIdT fId, document::FieldValue::UP fv) = 0;
 private:
  search::DocumentIdT _docId;
  const size_t        _fieldCount;
};

}

