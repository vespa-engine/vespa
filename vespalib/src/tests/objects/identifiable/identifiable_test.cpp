// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "namedobject.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/identifiable.hpp>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;

class IdentifiableTest : public ::testing::Test {
protected:
    IdentifiableTest();
    ~IdentifiableTest() override;
    template <typename T>
    void testStream(const T & a);
    template <typename T>
    void testSerializer(const T & a);
};

IdentifiableTest::IdentifiableTest() = default;
IdentifiableTest::~IdentifiableTest() = default;

#define CID_Abstract  0x700000
#define CID_A  0x700001
#define CID_B  0x700002
#define CID_C  0x700003

class Abstract : public Identifiable
{
public:
    DECLARE_IDENTIFIABLE_ABSTRACT(Abstract);
    ~Abstract() override = default;
    virtual void someAbstractVirtualMethod() = 0;
};

class A : public Abstract
{
public:
    DECLARE_IDENTIFIABLE(A);
    A() { }
    void someAbstractVirtualMethod() override { };
};

class B : public A
{
public:
    DECLARE_IDENTIFIABLE(B);
    B() { }
};

class C : public Identifiable
{
private:
    int _value;

public:
    DECLARE_IDENTIFIABLE(C);
    C() : _value(0) {}
    C(int value) : _value(value) {}
    C *clone() const { return new C(*this); }
    virtual int cmp(const Identifiable &rhs) const {
        int result(cmpClassId(rhs));
        if (result == 0) {
            result = _value - static_cast<const C &>(rhs)._value;
        }
        return result;
    }
};

IMPLEMENT_IDENTIFIABLE_ABSTRACT(Abstract, Identifiable);
IMPLEMENT_IDENTIFIABLE(A, Abstract);
IMPLEMENT_IDENTIFIABLE(B, A);
IMPLEMENT_IDENTIFIABLE(C, Identifiable);

TEST_F(IdentifiableTest, test_named_object)
{
    NamedObject a("first"), b("second");;
    nbostream os;
    NBOSerializer nos(os);
    nos << a << b;
    EXPECT_EQ(27u,os.size());
    Identifiable::UP o1;
    o1 = Identifiable::create(nos);
    EXPECT_EQ(14u, os.size());
    ASSERT_TRUE(o1->inherits(NamedObject::classId));
    ASSERT_TRUE(o1->getClass().id() == NamedObject::classId);
    EXPECT_TRUE(static_cast<const NamedObject &>(*o1).getName() == "first");
    o1 = Identifiable::create(nos);
    EXPECT_EQ(0u, os.size());
    ASSERT_TRUE(o1->inherits(NamedObject::classId));
    ASSERT_TRUE(o1->getClass().id() == NamedObject::classId);
    EXPECT_TRUE(static_cast<const NamedObject &>(*o1).getName() == "second");
}

template <typename T>
void IdentifiableTest::testStream(const T & a)
{
    nbostream s;
    s << a;
    T b;
    s >> b;
    EXPECT_TRUE(s.empty());
    EXPECT_EQ(a, b);
    EXPECT_EQ(nbostream::ok, s.state());
    EXPECT_TRUE(s.good());
}

template <typename T>
void IdentifiableTest::testSerializer(const T & a)
{
    nbostream t;
    NBOSerializer s(t);
    s << a;
    T b;
    s >> b;
    EXPECT_TRUE(s.getStream().empty());
    EXPECT_EQ(a, b);
    EXPECT_EQ(nbostream::ok, s.getStream().state());
}

TEST_F(IdentifiableTest, test_nbo_serializer)
{
    testSerializer(true);
    testSerializer(false);
    testSerializer(static_cast<int8_t>('a'));
    testSerializer(static_cast<uint8_t>(156));
    testSerializer(static_cast<int16_t>(156));
    testSerializer(static_cast<int32_t>(156));
    testSerializer(static_cast<int64_t>(156));
    testSerializer(static_cast<uint16_t>(156));
    testSerializer(static_cast<uint32_t>(156));
    testSerializer(static_cast<uint64_t>(156));
    testSerializer(static_cast<float>(156));
    testSerializer(static_cast<double>(156));
    testSerializer(std::string("abcdefgh"));
}

TEST_F(IdentifiableTest, test_nbo_stream)
{
    testStream(true);
    testStream(false);
    testStream('a');
    testStream(static_cast<unsigned char>(156));
    testStream(static_cast<int16_t>(156));
    testStream(static_cast<int32_t>(156));
    testStream(static_cast<int64_t>(156));
    testStream(static_cast<uint16_t>(156));
    testStream(static_cast<uint32_t>(156));
    testStream(static_cast<uint64_t>(156));
    testStream(static_cast<float>(156));
    testStream(static_cast<double>(156));
    testStream(std::string("abcdefgh"));
    testStream(std::string("abcdefgh"));
    {
        nbostream s(4);
        EXPECT_EQ(4u, s.capacity());
        s << "abcdef";
        EXPECT_EQ(nbostream::ok, s.state());
        EXPECT_EQ(10u, s.size());
        EXPECT_EQ(16u, s.capacity());
        EXPECT_EQ(0, strncmp(s.data() + 4, "abcdef", 6));
    }
    {
        nbostream s(8);
        EXPECT_EQ(0u, s.size());
        EXPECT_EQ(8u, s.capacity());
        const char * prev = s.data();
        s << "ABCD";
        EXPECT_EQ(8u, s.size());
        EXPECT_EQ(8u, s.capacity());
        EXPECT_EQ(prev, s.data());
        s << "A long string that will cause resizing";
        EXPECT_EQ(50u, s.size());
        EXPECT_EQ(64u, s.capacity());
        EXPECT_NE(prev, s.data());
    }
    {
        nbostream s(8);
        EXPECT_EQ(0u, s.size());
        EXPECT_EQ(8u, s.capacity());
        const char * prev = s.data();
        s << "ABCD";
        EXPECT_EQ(8u, s.size());
        EXPECT_EQ(8u, s.capacity());
        EXPECT_EQ(prev, s.data());
        s.reserve(50);
        EXPECT_NE(prev, s.data());
        EXPECT_EQ(8u, s.size());
        EXPECT_EQ(64u, s.capacity());
        prev = s.data();
        s << "A long string that will cause resizing";
        EXPECT_EQ(50u, s.size());
        EXPECT_EQ(64u, s.capacity());
        EXPECT_EQ(prev, s.data());
    }
    {
        nbostream s;
        s << int64_t(9);
        EXPECT_EQ(8u, s.size());
        EXPECT_EQ(0u, s.rp());
        int64_t a(7), b(1);
        s >> a;
        EXPECT_EQ(0u, s.size());
        EXPECT_EQ(8u, s.rp());
        EXPECT_TRUE(s.empty());
        EXPECT_TRUE(s.good());
        EXPECT_EQ(9, a);
        try {
            s >> b;
            EXPECT_TRUE(false);
        } catch (const IllegalStateException & e) {
            EXPECT_EQ("Stream failed bufsize(1024), readp(8), writep(8)", e.getMessage());
        }
        EXPECT_EQ(0u, s.size());
        EXPECT_EQ(8u, s.rp());
        EXPECT_TRUE(s.empty());
        EXPECT_FALSE(s.good());
        EXPECT_EQ(1, b);
        EXPECT_EQ(nbostream::eof, s.state());
    }
}

TEST_F(IdentifiableTest, test_identifiable)
{
    A a;
    B b;

    const Identifiable::RuntimeClass & rtcA = a.getClass();
    EXPECT_EQ(rtcA.id(), static_cast<unsigned int>(A::classId));
    EXPECT_EQ(strcmp(rtcA.name(), "A"), 0);

    const Identifiable::RuntimeClass & rtcB = b.getClass();
    EXPECT_EQ(rtcB.id(), static_cast<unsigned int>(B::classId));
    EXPECT_EQ(strcmp(rtcB.name(), "B"), 0);

    const Identifiable::RuntimeClass * rt(Identifiable::classFromId(0x1ab76245));
    ASSERT_TRUE(rt == NULL);
    rt = Identifiable::classFromId(Abstract::classId);
    ASSERT_TRUE(rt != NULL);
    Identifiable * u = rt->create();
    ASSERT_TRUE(u == NULL);
    rt = Identifiable::classFromId(A::classId);
    ASSERT_TRUE(rt != NULL);
    rt = Identifiable::classFromId(B::classId);
    ASSERT_TRUE(rt != NULL);

    Identifiable * o = rt->create();
    ASSERT_TRUE(o != NULL);

    const Identifiable::RuntimeClass & rtc = o->getClass();
    ASSERT_TRUE(rtc.id() == B::classId);
    ASSERT_TRUE(strcmp(rtc.name(), "B") == 0);
    ASSERT_TRUE(o->inherits(B::classId));
    ASSERT_TRUE(o->inherits(A::classId));
    ASSERT_TRUE(o->inherits(Abstract::classId));
    ASSERT_TRUE(o->inherits(Identifiable::classId));
    ASSERT_TRUE(o->getClass().id() == B::classId);
    nbostream os;
    NBOSerializer nos(os);
    nos << *o;
    EXPECT_EQ(os.size(), 4u);
    Identifiable::UP o2 = Identifiable::create(nos);
    EXPECT_TRUE(os.empty());
    ASSERT_TRUE(o->inherits(B::classId));
    ASSERT_TRUE(o->getClass().id() == B::classId);
    delete o;

    rt = Identifiable::classFromName("NotBNorA");
    ASSERT_TRUE(rt == NULL);
    rt = Identifiable::classFromName("B");
    ASSERT_TRUE(rt != NULL);
    o = rt->create();
    ASSERT_TRUE(o != NULL);
    const Identifiable::RuntimeClass & rtc2 = o->getClass();
    ASSERT_TRUE(rtc2.id() == B::classId);
    ASSERT_TRUE(strcmp(rtc2.name(), "B") == 0);
    ASSERT_TRUE(o->inherits(B::classId));
    ASSERT_TRUE(o->inherits(A::classId));
    ASSERT_TRUE(o->inherits(Abstract::classId));
    ASSERT_TRUE(o->inherits(Identifiable::classId));
    ASSERT_TRUE(o->getClass().id() == B::classId);
    delete o;

    IdentifiablePtr<C> c0(NULL);
    IdentifiablePtr<C> c1(new C(10));
    IdentifiablePtr<C> c2(new C(20));

    EXPECT_LT(c0.cmp(c1), 0);
    EXPECT_EQ(c0.cmp(c0), 0);
    EXPECT_GT(c1.cmp(c0), 0);

    EXPECT_LT(c1.cmp(c2), 0);
    EXPECT_EQ(c1.cmp(c1), 0);
    EXPECT_GT(c2.cmp(c1), 0);
}

TEST_F(IdentifiableTest, require_that_identifiable_cast_can_cast_pointers)
{
    A a;
    B b;
    EXPECT_TRUE(Identifiable::cast<A *>(&a));
    EXPECT_TRUE(Identifiable::cast<A *>(&b));
    EXPECT_TRUE(!Identifiable::cast<B *>(&a));
    EXPECT_TRUE(Identifiable::cast<B *>(&b));
    EXPECT_TRUE(Identifiable::cast<Abstract *>(&a));
    EXPECT_TRUE(Identifiable::cast<Abstract *>(&b));
}

TEST_F(IdentifiableTest, require_that_identifiable_cast_can_cast_references)
{
    A a;
    B b;
    try {
        // These should not throw.
        Identifiable::cast<A &>(a);
        Identifiable::cast<A &>(b);
        Identifiable::cast<B &>(b);
        Identifiable::cast<Abstract &>(a);
        Identifiable::cast<Abstract &>(b);
    } catch (std::bad_cast &e) {
        FAIL() << e.what();
    }
    EXPECT_THROW(Identifiable::cast<B &>(a), std::bad_cast);
}

GTEST_MAIN_RUN_ALL_TESTS()
