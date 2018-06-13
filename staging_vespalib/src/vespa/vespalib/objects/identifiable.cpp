// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "identifiable.hpp"
#include <cassert>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>
#include <algorithm>
#include <vespa/vespalib/objects/nbostream.h>
#include "objectdumper.h"
#include "visit.h"
#include "objectpredicate.h"
#include "objectoperation.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/stllike/hash_set.hpp>


namespace vespalib {

namespace {

class Register {
public:
    using RuntimeClass = Identifiable::RuntimeClass;
    Register();
    ~Register();
    bool append(RuntimeClass * c);
    bool erase(RuntimeClass * c);
    const RuntimeClass * classFromId(unsigned id) const;
    const RuntimeClass * classFromName(const char * name) const;
    const char * id2Name(unsigned id) const;
    unsigned name2Id(const char * name) const;
    bool empty() const { return _listById.empty(); }
private:
    struct GetId   { uint32_t operator() (const RuntimeClass * f) const { return f->id(); } };
    struct HashId  { size_t operator() (const RuntimeClass * f) const { return f->id(); } };
    struct EqualId { bool operator() (const RuntimeClass * a, const RuntimeClass * b) const { return a->id() == b->id(); } };
    struct GetName   { const char * operator() (const RuntimeClass * f) const { return f->name(); } };
    struct HashName  { size_t operator() (const RuntimeClass * f) const { return hashValue(f->name()); } };
    struct EqualName { bool operator() (const RuntimeClass * a, const RuntimeClass * b) const { return strcmp(a->name(), b->name()) == 0; } };
    typedef hash_set<RuntimeClass *, HashId, EqualId> IdList;
    typedef hash_set<RuntimeClass *, HashName, EqualName> NameList;
    IdList   _listById;
    NameList _listByName;
};

Register::Register() :
    _listById(),
    _listByName()
{ }

Register::~Register() = default;

bool Register::erase(Identifiable::RuntimeClass * c)
{
    _listById.erase(c);
    _listByName.erase(c);
    return true;
}

bool Register::append(Identifiable::RuntimeClass * c)
{
    bool ok((_listById.find(c) == _listById.end()) && ((_listByName.find(c) == _listByName.end())));
    if (ok) {
        _listById.insert(c);
        _listByName.insert(c);
    }
    return ok;
}

const Identifiable::RuntimeClass * Register::classFromId(unsigned id) const
{
    IdList::const_iterator it(_listById.find<uint32_t, GetId, hash<uint32_t>, std::equal_to<uint32_t> >(id));
    return  (it != _listById.end()) ? *it : NULL;
}

const Identifiable::RuntimeClass * Register::classFromName(const char *name) const
{
    NameList::const_iterator it(_listByName.find<const char *, GetName, hash<const char *>, std::equal_to<const char *> >(name));
    return  (it != _listByName.end()) ? *it : NULL;
}

Register * _register = nullptr;

}

Identifiable::ILoader  * Identifiable::_classLoader = nullptr;
FieldBase Identifiable::hasObjectField("hasObject");
FieldBase Identifiable::sizeField("size");
FieldBase Identifiable::classIdField("classId");
FieldBase Identifiable::objectField("object");

IMPLEMENT_IDENTIFIABLE(Identifiable, Identifiable);

const Identifiable::RuntimeClass *
Identifiable::classFromId(unsigned id) {
    return _register->classFromId(id);
}

const Identifiable::RuntimeClass *
Identifiable::classFromName(const char * name) {
    return _register->classFromName(name);
}

Identifiable::RuntimeClass::RuntimeClass(RuntimeInfo * info_) :
    _rt(info_)
{
    if (_rt->_factory) {
        Identifiable::UP tmp(create());
        assert(id() == tmp->getClass().id());
        //printf("Class %s has typeinfo %s\n", name(), typeid(*tmp).name());
        for (const RuntimeInfo * curr = _rt; curr && curr != curr->_base; curr = curr->_base) {
            //printf("\tinherits %s : typeinfo = %s\n", curr->_name, curr->_typeId().name());
            if ( ! curr->_tryCast(tmp.get()) ) {
                throw std::runtime_error(make_string("(%s, %s) is not a baseclass of (%s, %s)", curr->_name, curr->_typeId().name(), name(), typeid(*tmp).name()));
            }
        }
    }
    if (_register == NULL) {
        _register = new Register();
    }
    if (! _register->append(this)) {
        const RuntimeClass * old = _register->classFromId(id());
        throw std::runtime_error(make_string("Duplicate Identifiable object(%s, %s, %d) being registered. Choose a unique id. Object (%s, %s, %d) is using it.", name(), info(), id(), old->name(), old->info(), old->id()));
    }
}

Identifiable::RuntimeClass::~RuntimeClass()
{
    if ( ! _register->erase(this) ) {
        assert(0);
    }
    if (_register->empty()) {
        delete _register;
        _register = NULL;
    }
}

bool Identifiable::RuntimeClass::inherits(unsigned cid) const
{
    const RuntimeInfo *cur;
    for (cur = _rt; (cur != &Identifiable::_RTInfo) && cid != cur->_id; cur = cur->_base) { }
    return (cid == cur->_id);
}

class SortById : public std::binary_function<const Identifiable::RuntimeClass *, const Identifiable::RuntimeClass *, bool> {
public:
    bool operator() (const Identifiable::RuntimeClass * x, const Identifiable::RuntimeClass * y) const {
        return x->id() < y->id();
    }
};

class SortByName : public std::binary_function<const Identifiable::RuntimeClass *, const Identifiable::RuntimeClass *, bool> {
public:
    bool operator() (const Identifiable::RuntimeClass * x, const Identifiable::RuntimeClass * y) const {
        return strcmp(x->name(), y->name()) < 0;
    }
};

Serializer & operator << (Serializer & os, const Identifiable & obj)
{
    os.put(Identifiable::classIdField, obj.getClass().id());
    obj.serialize(os);
    return os;
}

nbostream & operator << (nbostream & os, const Identifiable & obj)
{
    NBOSerializer nos(os);
    nos << obj;
    return os;
}

nbostream & operator >> (nbostream & is, Identifiable & obj)
{
    NBOSerializer nis(is);
    nis >> obj;
    return is;
}

Deserializer & operator >> (Deserializer & os, Identifiable & obj)
{
    uint32_t cid(0);
    os.get(Identifiable::classIdField, cid);
    if (cid == obj.getClass().id()) {
        obj.deserialize(os);
    } else {
        throw std::runtime_error(make_string("Failed deserializing %s : Received cid %d(%0x) != %d(%0x)",
                                                       obj.getClass().name(),
                                                       cid, cid,
                                                       obj.getClass().id(), obj.getClass().id()));
        //Should mark as failed
    }
    return os;
}

Identifiable::UP Identifiable::create(Deserializer & is)
{
    uint32_t cid(0);
    is.get(classIdField, cid);
    UP obj;
    const Identifiable::RuntimeClass *rtc = Identifiable::classFromId(cid);
    if (rtc == NULL) {
        if ((_classLoader != NULL) && _classLoader->hasClass(cid)) {
            _classLoader->loadClass(cid);
            rtc = Identifiable::classFromId(cid);
            if (rtc == NULL) {
                throw std::runtime_error(make_string("Failed loading class for Identifiable with classId %d(%0x)", cid, cid));
            }
        }
    }
    if (rtc != NULL) {
        obj.reset(rtc->create());
        if (obj.get()) {
            obj->deserialize(is);
        } else {
            throw std::runtime_error(
                    make_string("Failed deserializing an Identifiable for classId %d(%0x). "
                                "It is abstract, so it can not be instantiated. Does it need to be abstract ?",
                                cid, cid));
        }
    } else {
        throw std::runtime_error(make_string("Failed deserializing an Identifiable with unknown classId %d(%0x)", cid, cid));
    }
    return obj;
}

string
Identifiable::getNativeClassName() const
{
    return vespalib::getClassName(*this);
}

string
Identifiable::asString() const
{
    ObjectDumper dumper;
    visit(dumper, "", this);
    return dumper.toString();
}

int Identifiable::onCmp(const Identifiable& b) const
{
    int diff(0);
    nbostream as, bs;
    NBOSerializer nas(as), nbs(bs);
    nas << *this;
    nbs << b;
    size_t minLength(std::min(as.size(), bs.size()));
    if (minLength > 0) {
        diff = memcmp(as.c_str(), bs.c_str(), minLength);
    }
    if (diff == 0) {
        diff = as.size() - bs.size();
    }
    return diff;
}

void
Identifiable::visitMembers(ObjectVisitor &visitor) const
{
    visitor.visitNotImplemented();
}

void
Identifiable::select(const ObjectPredicate &predicate, ObjectOperation &operation)
{
    if (predicate.check(*this)) {
        operation.execute(*this);
    } else {
        selectMembers(predicate, operation);
    }
}

void
Identifiable::selectMembers(const ObjectPredicate &predicate, ObjectOperation &operation)
{
    (void) predicate;
    (void) operation;
}

Serializer & Identifiable::serialize(Serializer & os) const
{
    return os.put(objectField, *this);
}

Deserializer & Identifiable::deserialize(Deserializer & is)
{
    return is.get(objectField, *this);
}

Serializer & Identifiable::onSerialize(Serializer & os) const
{
    return os;
}

Deserializer & Identifiable::onDeserialize(Deserializer & is)
{
    return is;
}

}
