// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/objects/identifiable.hpp>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/objectoperation.h>

using namespace vespalib;

#define CID_Foo  60000005
#define CID_Bar  60000010

struct Foo : public Identifiable
{
    typedef IdentifiablePtr<Foo> CP;
    std::vector<CP> nodes;

    DECLARE_IDENTIFIABLE(Foo);
    virtual Foo *clone() const { return new Foo(*this); }
    void selectMembers(const ObjectPredicate &p, ObjectOperation &o) override {
        for (uint32_t i = 0; i < nodes.size(); ++i) {
            nodes[i]->select(p, o);
        }
    }
};
IMPLEMENT_IDENTIFIABLE(Foo, Identifiable);

struct Bar : public Foo
{
    int value;

    DECLARE_IDENTIFIABLE(Bar);
    Bar() : value(0) {}
    Bar(int v) { value = v; }
    Bar *clone() const override { return new Bar(*this); }
};
IMPLEMENT_IDENTIFIABLE(Bar, Identifiable);

struct ObjectType : public ObjectPredicate
{
    uint32_t cid;
    ObjectType(uint32_t id) : cid(id) {}
    bool check(const Identifiable &obj) const override {
        return (obj.getClass().id() == cid);
    }
};

struct ObjectCollect : public ObjectOperation
{
    std::vector<Identifiable*> nodes;
    ~ObjectCollect() override;
    void execute(Identifiable &obj) override {
        nodes.push_back(&obj);
    }
};

ObjectCollect::~ObjectCollect() = default;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("objectselection_test");
    {
        Foo f1;
        Foo f2;
        Foo f3;
        Bar b1(1);
        Bar b2(2);
        Bar b3(3);
        Bar b4(4);
        f2.nodes.push_back(b1);
        f2.nodes.push_back(b2);
        f3.nodes.push_back(b3);
        f3.nodes.push_back(b4);
        f1.nodes.push_back(f2);
        f1.nodes.push_back(f3);

        ObjectType predicate(Bar::classId);
        ObjectCollect operation;
        f1.select(predicate, operation);
        ASSERT_TRUE(operation.nodes.size() == 4);
        ASSERT_TRUE(operation.nodes[0]->getClass().id() == Bar::classId);
        ASSERT_TRUE(operation.nodes[1]->getClass().id() == Bar::classId);
        ASSERT_TRUE(operation.nodes[2]->getClass().id() == Bar::classId);
        ASSERT_TRUE(operation.nodes[3]->getClass().id() == Bar::classId);
        ASSERT_TRUE(((Bar*)operation.nodes[0])->value == 1);
        ASSERT_TRUE(((Bar*)operation.nodes[1])->value == 2);
        ASSERT_TRUE(((Bar*)operation.nodes[2])->value == 3);
        ASSERT_TRUE(((Bar*)operation.nodes[3])->value == 4);
    }
    TEST_DONE();
}
