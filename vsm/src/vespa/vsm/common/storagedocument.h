// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/vsm/common/document.h>

namespace vsm {

typedef vespalib::CloneablePtr<document::FieldValue> FieldValueContainer;
typedef document::FieldPath FieldPath; // field path to navigate a field value
typedef std::vector<FieldPath> FieldPathMapT; // map from field id to field path
typedef std::shared_ptr<FieldPathMapT> SharedFieldPathMap;

class StorageDocument : public Document
{
 public:
  typedef vespalib::LinkedPtr<StorageDocument> SP;
  class SubDocument {
  public:
      SubDocument() :
          _fieldValue(NULL)
      { }
      SubDocument(document::FieldValue * fv, FieldPath::const_iterator it, FieldPath::const_iterator mt) :
          _fieldValue(fv),
          _it(it),
          _mt(mt)
      { }
      const document::FieldValue * getFieldValue() const { return _fieldValue; }
      void setFieldValue(document::FieldValue * fv) { _fieldValue = fv; }
      FieldPath::const_iterator begin() const { return _it; }
      FieldPath::const_iterator end()   const { return _mt; }
      void swap(SubDocument & rhs);
  private:
      document::FieldValue    * _fieldValue;
      FieldPath::const_iterator _it;
      FieldPath::const_iterator _mt;
  };
  StorageDocument(const SharedFieldPathMap & fim);
  StorageDocument(const document::Document & doc);
  StorageDocument(document::Document::UP doc);
  virtual ~StorageDocument();
  void init();
  const document::Document & docDoc()         const { return *_doc; }
  void fieldPathMap(const SharedFieldPathMap & fim) { _fieldMap = fim; }
  const SharedFieldPathMap & fieldPathMap()   const { return _fieldMap; }
  bool valid()                                const { return _doc.get() != NULL; }
  const SubDocument & getComplexField(FieldIdT fId) const;
  virtual const document::FieldValue * getField(FieldIdT fId) const;
  virtual bool setField(FieldIdT fId, document::FieldValue::UP fv);
  void saveCachedFields();
 private:
  typedef vespalib::CloneablePtr<document::Document> DocumentContainer;
  DocumentContainer      _doc;
  SharedFieldPathMap     _fieldMap;
  mutable std::vector<SubDocument> _cachedFields;
  mutable std::vector<document::FieldValue::UP> _backedFields;
};

}

