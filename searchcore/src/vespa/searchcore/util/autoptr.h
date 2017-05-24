// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

template <class T>
class FastS_AutoPtr
{
private:
    FastS_AutoPtr(const FastS_AutoPtr &);
    FastS_AutoPtr& operator=(const FastS_AutoPtr &);

    T *_val;
    void Clean() {
        if (_val != NULL) {
            delete _val;
            _val = NULL;
        }
    }
public:
    FastS_AutoPtr() : _val(NULL) { }
    explicit FastS_AutoPtr(T *val)
        : _val(val)
    {
    }
    ~FastS_AutoPtr() { Clean(); }
    void Set(T *val) { Clean(); _val = val; }
    T *Get() const { return _val; }
    T *HandOver() { T *ret = _val; _val = NULL; return ret; }
    void Drop() {
        if (_val != NULL) {
            delete _val;
            _val = NULL;
        }
    }
};


template <class T>
class FastS_AutoRefCntPtr
{
private:
    FastS_AutoRefCntPtr(const FastS_AutoRefCntPtr &);
    FastS_AutoRefCntPtr& operator=(const FastS_AutoRefCntPtr &);

    T *_val;
    void Clean() {
        if (_val != NULL)
            _val->subRef();
    }
public:
    FastS_AutoRefCntPtr() : _val(NULL) { }
    explicit FastS_AutoRefCntPtr(T *val) {_val = val; }
    ~FastS_AutoRefCntPtr() { Clean(); }
    void Set(T *val) { Clean(); _val = val; }
    void SetDup(T *val) {
        Clean();
        if (val != NULL)
            val->addRef();
        _val = val;
    }
    T *Get() const { return _val; }
    T *GetDup() {
        if (_val != NULL)
            _val->addRef();
        return _val;
    }
    T *HandOver() { T *ret = _val; _val = NULL; return ret; }
    void Drop() {
        if (_val != NULL) {
            _val->subRef();
            _val = NULL;
        }
    }
};


class FastS_AutoCharPtr
{
private:
    FastS_AutoCharPtr(const FastS_AutoCharPtr &);
    FastS_AutoCharPtr& operator=(const FastS_AutoCharPtr &);

    char *_val;
    void Clean() {
        if (_val != NULL)
            free(_val);
    }
public:
    FastS_AutoCharPtr()
        : _val(NULL)
    {
    }
    explicit FastS_AutoCharPtr(char *val)
        : _val(val)
    {
    }
    ~FastS_AutoCharPtr() { Clean(); }
    void Set(char *val) { Clean(); _val = val; }
    char *Get() const { return _val; }
    char *HandOver()  { char *ret = _val; _val = NULL; return ret; }
    void Drop() {
        if (_val != NULL) {
            free(_val);
            _val = NULL;
        }
    }
};

