// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "no_loaded_vector.h"
#include "enum_store_loaders.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/changevector.h>
#include <vespa/searchlib/attribute/i_enum_store.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>
#include <vespa/vespalib/regex/regex.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <optional>

namespace search {

/**
 * Helper class for search context when scanning string fields
 * It handles different search settings like prefix, regex and cased/uncased.
 */
class StringSearchHelper {
public:
    StringSearchHelper(QueryTermUCS4 & qTerm, bool cased);
    ~StringSearchHelper();
    bool isMatch(const char *src) const;
    bool isPrefix() const { return _isPrefix; }
    bool isRegex() const { return _isRegex; }
    bool isCased() const { return _isCased; }
    const vespalib::Regex & getRegex() const { return _regex; }
private:
    vespalib::Regex                _regex;
    union {
        const ucs4_t *_ucs4;
        const char   *_char;
    }                              _term;
    uint32_t                       _termLen;
    bool                           _isPrefix;
    bool                           _isRegex;
    bool                           _isCased;
};

class ReaderBase;

/**
 * Base class for all string attributes.
 */
class StringAttribute : public AttributeVector
{
public:
    using EnumIndex = IEnumStore::Index;
    using EnumVector = IEnumStore::EnumVector;
    using LoadedValueType = const char*;
    using LoadedVector = NoLoadedVector;
    using OffsetVector = vespalib::Array<uint32_t>;
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
    bool applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust) override;
    bool findEnum(const char * value, EnumHandle & e) const override = 0;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override = 0;
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
    ~StringAttribute() override;
    static const char * defaultValue() { return ""; }
    using Change = ChangeTemplate<StringChangeData>;
    using ChangeVector = ChangeVectorT<Change>;
    using EnumEntryType = const char*;
    ChangeVector _changes;
    Change _defaultValue;
    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    bool onAddDoc(DocId doc) override;

    vespalib::MemoryUsage getChangeVectorMemoryUsage() const override;
private:
    virtual void load_posting_lists(LoadedVector& loaded);
    virtual void load_enum_store(LoadedVector& loaded);
    virtual void fillValues(LoadedVector & loaded);

    virtual void load_enumerated_data(ReaderBase &attrReader, enumstore::EnumeratedPostingsLoader& loader, size_t num_values);
    virtual void load_enumerated_data(ReaderBase &attrReader, enumstore::EnumeratedLoader& loader);
    virtual void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader);

    largeint_t getInt(DocId doc)  const override { return strtoll(get(doc), nullptr, 0); }
    double getFloat(DocId doc)    const override;
    const char * getString(DocId doc, char * v, size_t sz) const override { (void) v; (void) sz; return get(doc); }

    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;

protected:
    class StringSearchContext : public SearchContext {
    public:
        StringSearchContext(QueryTermSimpleUP qTerm, const StringAttribute & toBeSearched);
        ~StringSearchContext() override;
    protected:
        bool valid() const override;
        const QueryTermUCS4 * queryTerm() const override;
        bool isMatch(const char *src) const { return _helper.isMatch(src); }
        bool isPrefix() const { return _helper.isPrefix(); }
        bool isRegex() const { return _helper.isRegex(); }
        bool isCased() const { return _helper.isCased(); }
        const vespalib::Regex & getRegex() const { return _helper.getRegex(); }

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
        int32_t findNextMatch(vespalib::ConstArrayRef<WeightedT> w, int32_t elemId, const Accessor & ac, Collector & collector) const {
            for (uint32_t i(elemId); i < w.size(); i++) {
                if (isMatch(ac.get(w[i].value()))) {
                    collector.addWeight(w[i].weight());
                    return i;
                }
            }
            return -1;
        }
    private:
        std::unique_ptr<QueryTermUCS4> _queryTerm;
        StringSearchHelper             _helper;
    };
};

}

