// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>

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
    virtual bool onLoad();
    virtual typename B::BaseType getFromEnum(EnumHandle e) const { return _data[e]; }
    virtual void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const {
        for (size_t i(0); i < sz; i++) {
            e[i] = v[i];
        }
    }
protected:
    typedef typename B::BaseType   BaseType;
    typedef typename B::DocId      DocId;
    typedef typename B::Change     Change;
    typedef typename B::largeint_t largeint_t;
    typedef typename B::Config     Config;

    NumericDirectAttribute(const vespalib::string & baseFileName, const Config & c);

    virtual bool findEnum(BaseType value, EnumHandle & e) const;
    virtual void onCommit();
    virtual void onUpdateStat() { }
    virtual bool addDoc(DocId & );

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
    virtual largeint_t getInt(DocId doc)   const { return static_cast<largeint_t>(getHelper(doc, 0)); }
    virtual double getFloat(DocId doc)     const { return getHelper(doc, 0); }
    virtual uint32_t get(DocId doc, largeint_t * v, uint32_t sz)     const { return getAllHelper<largeint_t, largeint_t>(doc, v, sz); }
    virtual uint32_t get(DocId doc, double * v, uint32_t sz)         const { return getAllHelper<double, double>(doc, v, sz); }
private:
    typedef typename B::EnumHandle    EnumHandle;
    typedef typename B::BaseType      BaseType;
    typedef typename B::Weighted      Weighted;
    typedef typename B::WeightedEnum  WeightedEnum;
    typedef typename B::WeightedInt   WeightedInt;
    typedef typename B::WeightedFloat WeightedFloat;
    virtual BaseType get(DocId doc)        const { return getHelper(doc, 0); }
    virtual EnumHandle getEnum(DocId doc)  const { return getEnumHelper(doc, 0); }
    virtual uint32_t getAll(DocId doc, BaseType * v, uint32_t sz)    const { return getAllHelper<BaseType, BaseType>(doc, v, sz); }
    virtual uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const { return getAllEnumHelper(doc, e, sz); }

    virtual uint32_t getValueCount(DocId doc) const { return getValueCountHelper(doc); }
    virtual bool hasEnum2Value() const { return false; }

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

    virtual uint32_t get(DocId doc, WeightedEnum * v, uint32_t sz) const { return getAllEnumHelper(doc, v, sz); }
    virtual uint32_t getAll(DocId doc, Weighted * v, uint32_t sz)      const { return getAllHelper<Weighted, BaseType>(doc, v, sz); }
    virtual uint32_t get(DocId doc, WeightedInt * v, uint32_t sz)      const { return getAllHelper<WeightedInt, largeint_t>(doc, v, sz); }
    virtual uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz)    const { return getAllHelper<WeightedFloat, double>(doc, v, sz); }
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
protected:
    StringDirectAttribute(const vespalib::string & baseFileName, const Config & c);
    bool findEnum(const char * value, EnumHandle & e) const override;
    void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const override {
        for (size_t i(0); i < sz; i++) {
            e[i] = v[i];
        }
    }
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
    virtual const char * getString(DocId doc, char * v, size_t sz) const { (void) v; (void) sz; return getHelper(doc, 0); }
    virtual uint32_t get(DocId doc, const char ** v, uint32_t sz)  const { return getAllHelper(doc, v, sz); }
private:
    virtual uint32_t get(DocId doc, vespalib::string * v, uint32_t sz)  const { return getAllHelper(doc, v, sz); }
    virtual uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const { return getAllEnumHelper(doc, e, sz); }
    virtual const char * get(DocId doc)  const { return getHelper(doc, 0); }
    virtual EnumHandle getEnum(DocId doc)  const { return getEnumHelper(doc, 0); }
    virtual uint32_t getValueCount(DocId doc) const { return getValueCountHelper(doc); }
    virtual uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz)  const { return getAllEnumHelper(doc, e, sz); }
    virtual uint32_t get(DocId doc, WeightedString * v, uint32_t sz)    const { return getAllHelper(doc, v, sz); }
    virtual uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const { return getAllHelper(doc, v, sz); }
    virtual bool hasEnum2Value() const { return true; }

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

