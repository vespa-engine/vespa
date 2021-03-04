// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "stringbase.h"
#include "integerbase.h"
#include "floatbase.h"
#include <vespa/searchlib/common/rankedhit.h>

//TODO: This one should go.
//
using search::AttributeVector;

//-----------------------------------------------------------------------------

class AttrVector
{
public:
    template <bool MULTI>
    struct Features
    {
        typedef uint32_t EnumType;
        static bool IsMultiValue() { return MULTI; }
    };
};

namespace search {

template <typename B>
class NumericDirectAttribute : public B
{
private:
    typedef typename B::EnumHandle EnumHandle;
    NumericDirectAttribute(const NumericDirectAttribute &);
    NumericDirectAttribute & operator=(const NumericDirectAttribute &);
    bool onLoad() override;
    typename B::BaseType getFromEnum(EnumHandle e) const override { return _data[e]; }
protected:
    typedef typename B::BaseType   BaseType;
    typedef typename B::DocId      DocId;
    typedef typename B::Change     Change;
    typedef typename B::largeint_t largeint_t;
    typedef typename B::Config     Config;

    NumericDirectAttribute(const vespalib::string & baseFileName, const Config & c);
    ~NumericDirectAttribute() override;

    bool findEnum(BaseType value, EnumHandle & e) const override;
    void onCommit() override;
    void onUpdateStat() override { }
    bool addDoc(DocId & ) override;

    std::vector<BaseType>   _data;
    std::vector<uint32_t>   _idx;
};

}

template <typename F, typename B>
class NumericDirectAttrVector : public search::NumericDirectAttribute<B>
{
protected:
    typedef typename B::DocId         DocId;
    typedef NumericDirectAttrVector<F, B> NumDirectAttrVec;
private:
    typedef typename B::largeint_t    largeint_t;
public:
    NumericDirectAttrVector(const vespalib::string & baseFileName);
    NumericDirectAttrVector(const vespalib::string & baseFileName, const AttributeVector::Config & c);
    largeint_t getInt(DocId doc)   const override { return static_cast<largeint_t>(getHelper(doc, 0)); }
    double getFloat(DocId doc)     const override { return getHelper(doc, 0); }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz)     const override { return getAllHelper<largeint_t, largeint_t>(doc, v, sz); }
    uint32_t get(DocId doc, double * v, uint32_t sz)         const override { return getAllHelper<double, double>(doc, v, sz); }
private:
    typedef typename B::EnumHandle    EnumHandle;
    typedef typename B::BaseType      BaseType;
    typedef typename B::Weighted      Weighted;
    typedef typename B::WeightedEnum  WeightedEnum;
    typedef typename B::WeightedInt   WeightedInt;
    typedef typename B::WeightedFloat WeightedFloat;
    BaseType get(DocId doc)        const override { return getHelper(doc, 0); }
    EnumHandle getEnum(DocId doc)  const override { return getEnumHelper(doc, 0); }
    uint32_t getAll(DocId doc, BaseType * v, uint32_t sz) const override { return getAllHelper<BaseType, BaseType>(doc, v, sz); }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override { return getAllEnumHelper(doc, e, sz); }

    uint32_t getValueCount(DocId doc) const override { return getValueCountHelper(doc); }

    uint32_t getValueCountHelper(DocId doc) const {
        if (F::IsMultiValue()) {
            return this->_idx[doc+1] - this->_idx[doc];
        } else {
            return 1;
        }
    }

    EnumHandle getEnumHelper(DocId doc, int idx) const {
        (void) doc;
        (void) idx;
        return uint32_t(-1);
    }

    BaseType getHelper(DocId doc, int idx) const {
        if (F::IsMultiValue()) {
            return this->_data[this->_idx[doc] + idx];
        } else {
            return this->_data[doc];
        }
    }
    template <typename T, typename C>
    uint32_t getAllHelper(DocId doc, T * v, uint32_t sz) const {
        uint32_t available(getValueCountHelper(doc));
        uint32_t num2Read(std::min(available, sz));
        for (uint32_t i(0); i < num2Read; i++) {
            v[i] = T(static_cast<C>(getHelper(doc, i)));
        }
        return available;
    }
    template <typename T>
    uint32_t getAllEnumHelper(DocId doc, T * v, uint32_t sz) const {
        uint32_t available(getValueCountHelper(doc));
        uint32_t num2Read(std::min(available, sz));
        for (uint32_t i(0); i < num2Read; i++) {
            v[i] = T(getEnumHelper(doc, i));
        }
        return available;
    }

    uint32_t get(DocId doc, WeightedEnum * v, uint32_t sz)  const override { return getAllEnumHelper(doc, v, sz); }
    uint32_t getAll(DocId doc, Weighted * v, uint32_t sz)   const override { return getAllHelper<Weighted, BaseType>(doc, v, sz); }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz)   const override { return getAllHelper<WeightedInt, largeint_t>(doc, v, sz); }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override { return getAllHelper<WeightedFloat, double>(doc, v, sz); }
};

//-----------------------------------------------------------------------------

namespace search {
class StringDirectAttribute : public StringAttribute
{
private:
    StringDirectAttribute(const StringDirectAttribute &);
    StringDirectAttribute & operator=(const StringDirectAttribute &);
    void onSave(IAttributeSaveTarget & saveTarget) override;
    bool onLoad() override;
    const char * getFromEnum(EnumHandle e) const override { return &_buffer[e]; }
    const char * getStringFromEnum(EnumHandle e) const override { return &_buffer[e]; }
    SearchContext::UP getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
protected:
    StringDirectAttribute(const vespalib::string & baseFileName, const Config & c);
    ~StringDirectAttribute() override;
    bool findEnum(const char * value, EnumHandle & e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *) const override;
    void onCommit() override;
    void onUpdateStat() override { }
    bool addDoc(DocId & ) override;

protected:
    std::vector<char>        _buffer;
    OffsetVector             _offsets;
    std::vector<uint32_t>    _idx;
};

}

template <typename F>
class StringDirectAttrVector : public search::StringDirectAttribute
{

public:
    StringDirectAttrVector(const vespalib::string & baseFileName);
    StringDirectAttrVector(const vespalib::string & baseFileName, const Config & c);
    const char * getString(DocId doc, char * v, size_t sz) const override {
        (void) v; (void) sz; return getHelper(doc, 0);
    }
    uint32_t get(DocId doc, const char ** v, uint32_t sz)  const override {
        return getAllHelper(doc, v, sz);
    }
private:
    uint32_t get(DocId doc, vespalib::string * v, uint32_t sz)  const override { return getAllHelper(doc, v, sz); }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override { return getAllEnumHelper(doc, e, sz); }
    const char * get(DocId doc)  const override { return getHelper(doc, 0); }
    EnumHandle getEnum(DocId doc)  const override { return getEnumHelper(doc, 0); }
    uint32_t getValueCount(DocId doc) const override { return getValueCountHelper(doc); }
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz)  const override { return getAllEnumHelper(doc, e, sz); }
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz)    const override { return getAllHelper(doc, v, sz); }
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override { return getAllHelper(doc, v, sz); }

    uint32_t getValueCountHelper(DocId doc) const {
        if (F::IsMultiValue()) {
            return this->_idx[doc+1] - this->_idx[doc];
        } else {
            return 1;
        }
    }

    EnumHandle getEnumHelper(DocId doc, int idx) const {
        if (F::IsMultiValue()) {
            return  this->_offsets[this->_idx[doc] + idx];
        } else {
            return this->_offsets[doc];
        }
        return uint32_t(-1);
    }

    const char *getHelper(DocId doc, int idx) const {
        if (F::IsMultiValue()) {
            return & this->_buffer[this->_offsets[this->_idx[doc] + idx]];
        } else if (idx == 0) {
            return & this->_buffer[this->_offsets[doc]];
        }
        return NULL;
    }
    template <typename T>
    uint32_t getAllHelper(DocId doc, T * v, uint32_t sz) const
    {
        uint32_t available(getValueCountHelper(doc));
        uint32_t num2Read(std::min(available, sz));
        for (uint32_t i(0); i < num2Read; i++) {
            v[i] = T(getHelper(doc, i));
        }
        return available;
    }
    template <typename T>
    uint32_t getAllEnumHelper(DocId doc, T * v, uint32_t sz) const
    {
        uint32_t available(getValueCountHelper(doc));
        uint32_t num2Read(std::min(available, sz));
        for (uint32_t i(0); i < num2Read; i++) {
            v[i] = T(getEnumHelper(doc, i));
        }
        return available;
    }
};

