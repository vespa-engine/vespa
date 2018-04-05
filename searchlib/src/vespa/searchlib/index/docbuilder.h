// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "doctypebuilder.h"
#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib { namespace tensor { class Tensor; } }
namespace search {
namespace index {

VESPA_DEFINE_EXCEPTION(DocBuilderError, vespalib::Exception);

/**
 * Builder class used to generate a search document that corresponds
 * to an index schema.
 **/
class DocBuilder
{
public:
    typedef DocBuilderError Error;

private:
    /**
     * Base class for handling the construction of a field.
     **/
    class FieldHandle {
    public:
        typedef std::shared_ptr<FieldHandle> SP;
    protected:
        const Schema::Field & _sfield;
        document::FieldValue::UP _value;
        document::FieldValue::UP _element;
    public:
        FieldHandle(const document::Field & dfield, const Schema::Field & field);
        virtual ~FieldHandle() {}
        virtual void startElement(int32_t weight) { (void) weight; throw Error("Function not supported"); }
        virtual void endElement() { throw Error("Function not supported"); }
        virtual void addStr(const vespalib::string & val) { (void) val; throw Error("Function not supported"); }

        virtual void addSpace() {
            throw Error("Function not supported");
        }

        virtual void addNoWordStr(const vespalib::string & val) {
            (void) val;
            throw Error("Function not supported");
        }

        virtual void addTokenizedString(const vespalib::string &val, bool urlMode) {
            (void) val;
            (void) urlMode;
            throw Error("Function not supported");
        }

        virtual void addSpan(size_t start, size_t len) {
            (void) start;
            (void) len;
            throw Error("Function not supported");
        }

        virtual void addSpan() {
            throw Error("Function not supported");
        }

        virtual void addSpaceTokenAnnotation() {
            throw Error("Function not supported");
        }

        virtual void addNumericTokenAnnotation() {
            throw Error("Function not supported");
        }

        virtual void addAlphabeticTokenAnnotation() {
            throw Error("Function not supported");
        }

        virtual void addTermAnnotation() {
            throw Error("Function not supported");
        }

        virtual void addTermAnnotation(const vespalib::string &val) {
            (void) val;
            throw Error("Function not supported");
        }

        virtual void addInt(int64_t val) { (void) val; throw Error("Function not supported"); }
        virtual void addFloat(double val) { (void) val; throw Error("Function not supported"); }
        virtual void addPredicate(std::unique_ptr<vespalib::Slime>) {
            throw Error("Function not supported");
        }
        virtual void addTensor(std::unique_ptr<vespalib::tensor::Tensor>) {
            throw Error("Function not supported");
        }
        const document::FieldValue::UP & getValue() const { return _value; }
        const Schema::Field & getField() const { return _sfield; }

        virtual void onEndElement() {}
        virtual void onEndField() {}

        virtual void setAutoAnnotate(bool autoAnnotate) {
            (void) autoAnnotate;
            throw Error("Function not supported");
        }

        virtual void setAutoSpace(bool autoSpace) {
            (void) autoSpace;
            throw Error("Function not supported");
        }

        virtual void addPosition(int32_t xpos, int32_t ypos) {
            (void) xpos;
            (void) ypos;
            throw Error("Function not supported");
        }

        virtual void addRaw(const void *buf, size_t len) {
            (void) buf;
            (void) len;
            throw Error("Function not supported");
        }

        virtual void startSubField(const vespalib::string &subField) {
            (void) subField;
            throw Error("Function not supported");
        }

        virtual void endSubField() {
            throw Error("Function not supported");
        }
    };

    /**
     * Class that can handle multi value fields.
     **/
    class CollectionFieldHandle : public FieldHandle {
    private:
        int32_t _elementWeight;
    public:
        CollectionFieldHandle(const document::Field & dfield, const Schema::Field & sfield);
        void startElement(int32_t weight) override;
        void endElement() override;
    };

    /**
     * Class for handling the construction of the content of an index field.
     **/
    class IndexFieldHandle : public CollectionFieldHandle
    {
        vespalib::string _str; // adjusted as word comes along
        size_t _strSymbols; // symbols in string, assuming UTF8
        document::SpanList *_spanList; // owned by _spanTree
        document::SpanTree::UP _spanTree;
        const document::SpanNode *_lastSpan;
        size_t _spanStart;  // start of span
        bool _autoAnnotate; // Add annotation when adding strings
        bool _autoSpace;    // Add space before strings
        bool _skipAutoSpace;    // one shot skip of adding space
        bool _uriField;     // URI handling (special struct case)
        vespalib::string _subField;
        const document::FixedTypeRepo & _repo;

        void append(const vespalib::string &val);

    public:
        IndexFieldHandle(const document::FixedTypeRepo & repo,
                         const document::Field &dfield,
                         const Schema::Field &sfield);

        void addStr(const vespalib::string & val) override;
        void addSpace() override;
        void addNoWordStr(const vespalib::string & val) override;
        void addTokenizedString(const vespalib::string &val, bool urlMode) override;
        void addSpan(size_t start, size_t len) override;
        void addSpan() override;
        void addSpaceTokenAnnotation() override;
        void addNumericTokenAnnotation() override;
        void addAlphabeticTokenAnnotation() override;
        void addTermAnnotation() override;
        void addTermAnnotation(const vespalib::string &val) override;
        void onEndElement() override;
        void onEndField() override;
        void startAnnotate();
        void setAutoAnnotate(bool autoAnnotate) override;
        void setAutoSpace(bool autoSpace) override;
        void startSubField(const vespalib::string &subField) override;
        void endSubField() override;
    };

    /**
     * Class for handling the construction of the content of an attribute field.
     **/
    class AttributeFieldHandle : public CollectionFieldHandle
    {
    public:
        AttributeFieldHandle(const document::Field & dfield, const Schema::Field & sfield);
        void addStr(const vespalib::string & val) override;
        void addInt(int64_t val) override;
        void addFloat(double val) override;
        void addPredicate(std::unique_ptr<vespalib::Slime> val) override;
        void addTensor(std::unique_ptr<vespalib::tensor::Tensor> val) override;
        void addPosition(int32_t xpos, int32_t ypos) override;
    };

    /**
     * Class for handling the construction of the content of a summary field.
     **/
    class SummaryFieldHandle : public CollectionFieldHandle {
    public:
        SummaryFieldHandle(const document::Field & dfield, const Schema::Field & sfield);
        void addStr(const vespalib::string & val) override;
        void addInt(int64_t val) override;
        void addFloat(double val) override;
        void addRaw(const void *buf, size_t len) override;
    };

    /**
     * Class for handling the construction of a document (set of fields).
     **/
    class DocumentHandle {
    public:
        typedef std::shared_ptr<DocumentHandle> SP;
    private:
        const document::DocumentType * _type;
        document::Document *const _doc;
        FieldHandle::SP _fieldHandle;
        document::FixedTypeRepo _repo;
    public:
        DocumentHandle(document::Document &doc, const vespalib::string & docId);
        const FieldHandle::SP & getFieldHandle() const { return _fieldHandle; }
        void startIndexField(const Schema::Field & sfield) {
            _fieldHandle.reset(new IndexFieldHandle(_repo, _type->getField(sfield.getName()), sfield));
        }
        void startAttributeField(const Schema::Field & sfield) {
            _fieldHandle.reset(new AttributeFieldHandle(_type->getField(sfield.getName()), sfield));
        }
        void startSummaryField(const Schema::Field & sfield) {
            _fieldHandle.reset(new SummaryFieldHandle(_type->getField(sfield.getName()), sfield));
        }
        void endField() {
            _fieldHandle->onEndField();
            _doc->setValue(_type->getField(_fieldHandle->getField().getName()), *_fieldHandle->getValue());
            _fieldHandle.reset(static_cast<FieldHandle *>(NULL));
        }
        void endDocument(const document::Document::UP & doc) {
            (void) doc;
        }
    };

    const Schema & _schema;
    document::DocumenttypesConfig _doctypes_config;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const document::DocumentType &_docType;
    document::Document::UP _doc;    // the document we are about to generate

    DocumentHandle::SP _handleDoc;   // handle for all fields
    DocumentHandle   * _currDoc;    // the current document handle

public:
    DocBuilder(const Schema & schema);
    ~DocBuilder();

    DocBuilder & startDocument(const vespalib::string & docId);
    document::Document::UP endDocument();

    DocBuilder & startIndexField(const vespalib::string & name);
    DocBuilder & startAttributeField(const vespalib::string & name);
    DocBuilder & startSummaryField(const vespalib::string & name);
    DocBuilder & endField();
    DocBuilder & startElement(int32_t weight = 1);
    DocBuilder & endElement();
    DocBuilder & addStr(const vespalib::string & val);
    DocBuilder & addSpace();
    DocBuilder & addNoWordStr(const vespalib::string & val);
    DocBuilder & addInt(int64_t val);
    DocBuilder & addFloat(double val);
    DocBuilder & addPredicate(std::unique_ptr<vespalib::Slime> val);
    DocBuilder & addTensor(std::unique_ptr<vespalib::tensor::Tensor> val);
    DocBuilder &addTokenizedString(const vespalib::string &val);
    DocBuilder &addUrlTokenizedString(const vespalib::string &val);
    DocBuilder &addSpan(size_t start, size_t len);
    DocBuilder &addSpan();
    DocBuilder &addSpaceTokenAnnotation();
    DocBuilder &addNumericTokenAnnotation();
    DocBuilder &addAlphabeticTokenAnnotation();
    DocBuilder &addTermAnnotation();
    DocBuilder &addTermAnnotation(const vespalib::string &val);
    DocBuilder &setAutoAnnotate(bool autoAnnotate);
    DocBuilder &setAutoSpace(bool autoSpace);
    DocBuilder &addPosition(int32_t xpos, int32_t ypos);
    DocBuilder &addRaw(const void *buf, size_t len);
    DocBuilder &startSubField(const vespalib::string &subField);
    DocBuilder &endSubField();
    static bool hasAnnotations() { return true; }

    const document::DocumentType &getDocumentType() const { return _docType; }
    const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepo() const { return _repo; }
    document::DocumenttypesConfig getDocumenttypesConfig() const { return _doctypes_config; }
};

} // namespace search::index
} // namespace search
