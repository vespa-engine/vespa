// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/polymorphicarrays.h>

using namespace vespalib;

class A {
public:
    virtual ~A() = default;
    virtual void assign(const A & rhs) { (void) rhs; assert(false); }  // Required by the primitive array.
    virtual A * clone() const { assert(false); return nullptr; }       // Required for the complex array.

    // For testing
    virtual bool operator == (const A & rhs) const = 0;
    virtual void print(std::ostream & os) const = 0;
};

class Primitive : public A
{
public:
    Primitive(size_t v=11) noexcept : _v(v) { }
    size_t value() const { return _v; }
    bool operator == (const A & rhs) const override {
       return dynamic_cast<const Primitive &>(rhs).value() == value();
    }
    void assign(const A & rhs) override {
        _v = dynamic_cast<const Primitive &>(rhs).value();
    }
    void print(std::ostream & os) const override {
        os << _v;
    }
private:
    size_t _v;
};


class Complex : public A
{
public:
    Complex(size_t v=11) noexcept : _v(v) { }
    size_t value() const { return _v; }
    bool operator == (const A & rhs) const override {
       return dynamic_cast<const Complex &>(rhs).value() == value();
    }
    Complex * clone() const override {
        return new Complex(_v); 
    }
    void print(std::ostream & os) const override {
        os << _v;
    }
private:
    size_t _v;
};

std::ostream & operator << (std::ostream & os, const A & v) {
    v.print(os);
    return os;
}


template <typename T>
void
verifyArray(IArrayT<A> & array)
{
    EXPECT_EQUAL(0u, array.size());
    for (size_t i(0); i < 10; i++) {
        array.push_back(T(i));
    }
    EXPECT_EQUAL(10u, array.size());
    for (size_t i(0); i < 10; i++) {
        EXPECT_EQUAL(T(i), array[i]);
    }
    IArrayT<A>::UP copy(array.clone());
    array.clear();
    EXPECT_EQUAL(0u, array.size());

    for (size_t i(0); i < copy->size(); i++) {
        array.push_back((*copy)[i]);
    }

    array.resize(19);
    EXPECT_EQUAL(19u, array.size());
    for (size_t i(0); i < 10; i++) {
        EXPECT_EQUAL(T(i), array[i]);
    }
    for (size_t i(10); i < array.size(); i++) {
        EXPECT_EQUAL(T(11), array[i]);
    }
    array.resize(13);
    EXPECT_EQUAL(13u, array.size());
    for (size_t i(0); i < 10; i++) {
        EXPECT_EQUAL(T(i), array[i]);
    }
    for (size_t i(10); i < array.size(); i++) {
        EXPECT_EQUAL(T(11), array[i]);
    }
    dynamic_cast<T &>(array[1]) = T(17);
    EXPECT_EQUAL(T(0), array[0]);
    EXPECT_EQUAL(T(17), array[1]);
    EXPECT_EQUAL(T(2), array[2]);
}


TEST("require that primitive arrays conforms") {
    PrimitiveArrayT<Primitive, A> a;
    verifyArray<Primitive>(a); 
    EXPECT_EQUAL(7u, a[7].value());
}

class Factory : public ComplexArrayT<A>::Factory
{
public:
    A * create() override { return new Complex(); }
    Factory * clone() const override { return new Factory(*this); }
};

TEST("require that complex arrays conforms") {
    ComplexArrayT<A> a(Factory::UP(new Factory()));
    verifyArray<Complex>(a); 
}

TEST_MAIN() { TEST_RUN_ALL(); }
