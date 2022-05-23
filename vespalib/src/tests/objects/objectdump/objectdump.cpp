// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/objects/visit.hpp>

#define CID_Base 10000000
#define CID_Foo  10000001
#define CID_Bar  10000002
#define CID_Baz  10000003

using vespalib::ObjectVisitor;
using vespalib::IdentifiablePtr;

struct Base : public vespalib::Identifiable
{
    DECLARE_IDENTIFIABLE(Base);
    virtual Base *clone() const { return new Base(*this); }
};
IMPLEMENT_IDENTIFIABLE(Base, vespalib::Identifiable);

struct Baz : public Base
{
    DECLARE_IDENTIFIABLE(Baz);
    Baz *clone() const override { return new Baz(*this); }
};
IMPLEMENT_IDENTIFIABLE(Baz, Base);

struct Bar : public Base
{
    DECLARE_IDENTIFIABLE(Bar);
    bool             _bool;
    int8_t           _int8;
    uint8_t          _uint8;
    int16_t          _int16;
    uint16_t         _uint16;
    int32_t          _int32;
    uint32_t         _uint32;
    int64_t          _int64;
    uint64_t         _uint64;
    float            _float;
    double           _double;
    vespalib::string _string;
    Bar() : _bool(true), _int8(-1), _uint8(1), _int16(-2), _uint16(2),
            _int32(-4), _uint32(4), _int64(-8), _uint64(8),
            _float(2.5), _double(2.75), _string("bla bla") {}

    Bar *clone() const override { return new Bar(*this); }

    void visitMembers(ObjectVisitor &v) const override {
        visit(v, "_bool", _bool);
        visit(v, "_int8", _int8);
        visit(v, "_uint8", _uint8);
        visit(v, "_int16", _int16);
        visit(v, "_uint16", _uint16);
        visit(v, "_int32", _int32);
        visit(v, "_uint32", _uint32);
        visit(v, "_int64", _int64);
        visit(v, "_uint64", _uint64);
        visit(v, "_float", _float);
        visit(v, "_double", _double);
        visit(v, "_string", _string);
        visit(v, "info", "a dummy string");
        visit(v, "(const char*)0", (const char*)0);
    }
};
IMPLEMENT_IDENTIFIABLE(Bar, Base);

struct Foo : public Base
{
    DECLARE_IDENTIFIABLE(Foo);
    Bar              _objMember;
    Baz              _objMember2;
    Baz             *_objPtr;
    std::vector<Bar> _list;
    std::vector<IdentifiablePtr<Base> > _list2;

    Foo();
    ~Foo();
    Foo *clone() const override { return new Foo(*this); }
    void visitMembers(ObjectVisitor &v) const override;
};

Foo::~Foo() { }
Foo::Foo()
        : _objMember(), _objMember2(), _objPtr(0), _list(), _list2()
{
    _list.push_back(Bar());
    _list.push_back(Bar());
    _list.push_back(Bar());
    _list2.push_back(Bar());
    _list2.push_back(Baz());
}

void
Foo::visitMembers(ObjectVisitor &v) const {
    visit(v, "_objMember", _objMember);
    visit(v, "_objMember2", _objMember2);
    visit(v, "_objPtr", _objPtr);
    visit(v, "_list", _list);
    visit(v, "_list2", _list2);
}

IMPLEMENT_IDENTIFIABLE(Foo, Base);

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("objectdump_test");
    Foo foo;
    fprintf(stderr, "%s", foo.asString().c_str());
    TEST_DONE();
}
