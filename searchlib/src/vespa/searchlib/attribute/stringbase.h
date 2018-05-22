// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/searchlib/attribute/enumstorebase.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>
#include <vespa/searchlib/attribute/loadedstringvalue.h>
#include <vespa/searchlib/attribute/changevector.h>

namespace search {

class StringEntryType;
class ReaderBase;

class StringAttribute : public AttributeVector
{
public:
    typedef vespalib::Array<uint32_t> OffsetVector;
    typedef const char *                  LoadedValueType;
    typedef EnumStoreBase::Index          EnumIndex;
    typedef EnumStoreBase::IndexVector    EnumIndexVector;
    typedef EnumStoreBase::EnumVector     EnumVector;
    typedef attribute::LoadedStringVector LoadedVector;
public:
    DECLARE_IDENTIFIABLE_ABSTRACT(StringAttribute);
    bool append(DocId doc, const vespalib::string & v, int32_t weight) {
        return AttributeVector::append(_changes, doc, StringChangeData(v), weight);
    }
    template<typename Accessor>
    bool append(DocId doc, Accessor & ac) {
        return AttributeVector::append(_changes, doc, ac);
    }
    bool remove(DocId doc, const vespalib::string & v, int32_t weight) {
        return AttributeVector::remove(_changes, doc, StringChangeData(v), weight);
    }
    bool update(DocId doc, const vespalib::string & v) {
        return AttributeVector::update(_changes, doc, StringChangeData(v));
    }
    bool apply(DocId doc, const ArithmeticValueUpdate & op);
    bool applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust) override;
    bool findEnum(const char * value, EnumHandle & e) const override = 0;
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override;
    uint32_t get(DocId doc, double * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override;
    uint32_t clearDoc(DocId doc) override;
    largeint_t getDefaultValue() const override { return 0; }
    static size_t countZero(const char * bt, size_t sz);
    static void generateOffsets(const char * bt, size_t sz, OffsetVector & offsets);
    virtual const char * getFromEnum(EnumHandle e) const = 0;
    virtual const char *get(DocId doc) const = 0;
protected:
    StringAttribute(const vespalib::string & name);
    StringAttribute(const vespalib::string & name, const Config & c);
    ~StringAttribute();
    static const char * defaultValue() { return ""; }
    typedef ChangeTemplate<StringChangeData> Change;
    typedef ChangeVectorT< Change > ChangeVector;
    typedef StringEntryType EnumEntryType;
    ChangeVector _changes;
    Change _defaultValue;
    bool onLoad() override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    virtual bool onAddDoc(DocId doc) override;

    virtual MemoryUsage getChangeVectorMemoryUsage() const override;
private:
    typedef attribute::LoadedStringVectorReal LoadedVectorR;
    virtual void fillPostings(LoadedVector & loaded);
    virtual void fillEnum(LoadedVector & loaded);
    virtual void fillValues(LoadedVector & loaded);

    virtual void fillEnum0(const void *src, size_t srcLen, EnumIndexVector &eidxs);
    virtual void fillEnumIdx(ReaderBase &attrReader, const EnumIndexVector &eidxs, attribute::LoadedEnumAttributeVector &loaded);
    virtual void fillEnumIdx(ReaderBase &attrReader, const EnumIndexVector &eidxs, EnumVector &enumHist);
    virtual void fillPostingsFixupEnum(const attribute::LoadedEnumAttributeVector &loaded);
    virtual void fixupEnumRefCounts(const EnumVector &enumHist);

    largeint_t getInt(DocId doc)  const override { return strtoll(get(doc), NULL, 0); }
    double getFloat(DocId doc)    const override;
    const char * getString(DocId doc, char * v, size_t sz) const override { (void) v; (void) sz; return get(doc); }

    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;

    template <typename T>
    void loadAllAtOnce(T & loaded, LoadedBufferUP dataBuffer, uint32_t numDocs, ReaderBase & attrReader, bool hasWeight, bool hasIdx);

    class StringSearchContext : public SearchContext {
    public:
        StringSearchContext(QueryTermSimpleUP qTerm, const StringAttribute & toBeSearched);
        ~StringSearchContext() override;
    private:
        bool                        _isPrefix;
        bool                        _isRegex;
    protected:
        bool valid() const override;

        const QueryTermBase & queryTerm() const override;
        bool isMatch(const char *src) const {
            if (__builtin_expect(isRegex(), false)) {
                return getRegex()->match(src);
            }
            vespalib::Utf8ReaderForZTS u8reader(src);
            uint32_t j = 0;
            uint32_t val;
            for (;; ++j) {
                val = u8reader.getChar();
                val = vespalib::LowerCase::convert(val);
                if (_termUCS4[j] == 0 || _termUCS4[j] != val) {
                    break;
                }
            }
            return (_termUCS4[j] == 0 && (val == 0 || isPrefix()));
        }
        class CollectHitCount {
        public:
            CollectHitCount() : _hitCount(0) { }
            void addWeight(int32_t w) {
                (void) w;
                _hitCount++;
            }
            int32_t getWeight() const { return _hitCount; }
            bool hasMatch() const { return _hitCount != 0; }
        private:
            uint32_t _hitCount;
        };
        class CollectWeight {
        public:
            CollectWeight() : _hitCount(0), _weight(0) { }
            void addWeight(int32_t w) {
                _weight += w;
                _hitCount++;
            }
            int32_t getWeight() const { return _weight; }
            bool hasMatch() const { return _hitCount != 0; }
        private:
            uint32_t _hitCount;
            int32_t  _weight;
        };

        template<typename WeightedT, typename Accessor, typename Collector>
        int32_t collectMatches(vespalib::ConstArrayRef<WeightedT> w, int32_t elemId, const Accessor & ac, Collector & collector) const {
            int firstMatch = -1;
            for (uint32_t i(elemId); i < w.size(); i++) {
                if (isMatch(ac.get(w[i].value()))) {
                    collector.addWeight(w[i].weight());
                    if (firstMatch == -1) {
                        firstMatch = i;
                    }
                }
            }
            return firstMatch;
        }


        int32_t onCmp(DocId docId, int32_t elementId, int32_t & weight) const override;
        int32_t onCmp(DocId docId, int32_t elementId) const override;

        bool isPrefix() const { return _isPrefix; }
        bool  isRegex() const { return _isRegex; }
        QueryTermSimpleUP         _queryTerm;
        std::vector<ucs4_t>       _termUCS4;
        const vespalib::Regexp * getRegex() const { return _regex.get(); }
    private:
        WeightedConstChar * getBuffer() const {
            if (_buffer == nullptr) {
                _buffer = new WeightedConstChar[_bufferLen];
            }
            return _buffer;
        }
        unsigned                    _bufferLen;
        mutable WeightedConstChar * _buffer;
        std::unique_ptr<vespalib::Regexp>   _regex;
    };
    SearchContext::UP getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
};

}

