// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/object_value.h>
#include <vespa/vespalib/data/slime/array_value.h>
#include <vespa/vespalib/data/slime/strfmt.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/symbol_table.h>
#include <vespa/vespalib/data/slime/basic_value.h>
#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP("slime_test");

using namespace vespalib::slime::convenience;

TEST("print sizes") {
    using vespalib::slime::SymbolTable;
    using vespalib::slime::Value;
    using vespalib::slime::NixValue;
    using vespalib::slime::BasicBoolValue;
    using vespalib::slime::BasicLongValue;
    using vespalib::slime::BasicDoubleValue;
    using vespalib::slime::BasicStringValue;
    using vespalib::slime::BasicDataValue;
    using vespalib::slime::ArrayValue;
    using vespalib::slime::ObjectValue;

    const char *pattern = "size of %s: %5u\n";
    fprintf(stderr, pattern, "Slime             ", sizeof(Slime));
    fprintf(stderr, pattern, "SymbolTable       ", sizeof(SymbolTable));
    fprintf(stderr, pattern, "Type              ", sizeof(Type));
    fprintf(stderr, pattern, "TypeType<n>       ", sizeof(vespalib::slime::BOOL));
    fprintf(stderr, pattern, "Value             ", sizeof(Value));
    fprintf(stderr, pattern, "NixValue          ", sizeof(NixValue));
    fprintf(stderr, pattern, "BasicBoolValue    ", sizeof(BasicBoolValue));
    fprintf(stderr, pattern, "BasicLongValue    ", sizeof(BasicLongValue));
    fprintf(stderr, pattern, "BasicDoubleValue  ", sizeof(BasicDoubleValue));
    fprintf(stderr, pattern, "BasicStringValue  ", sizeof(BasicStringValue));
    fprintf(stderr, pattern, "BasicDataValue    ", sizeof(BasicDataValue));
    fprintf(stderr, pattern, "ArrayValue        ", sizeof(ArrayValue));
    fprintf(stderr, pattern, "ObjectValue       ", sizeof(ObjectValue));
    EXPECT_EQUAL(sizeof(Value), sizeof(void*)); // ensure single vtable
}

TEST("test type ids") {
    EXPECT_EQUAL(0u, vespalib::slime::NIX::ID);
    EXPECT_EQUAL(1u, vespalib::slime::BOOL::ID);
    EXPECT_EQUAL(2u, vespalib::slime::LONG::ID);
    EXPECT_EQUAL(3u, vespalib::slime::DOUBLE::ID);
    EXPECT_EQUAL(4u, vespalib::slime::STRING::ID);
    EXPECT_EQUAL(5u, vespalib::slime::DATA::ID);
    EXPECT_EQUAL(6u, vespalib::slime::ARRAY::ID);
    EXPECT_EQUAL(7u, vespalib::slime::OBJECT::ID);
}

TEST("test empty") {
    Slime slime;
    for (int i = 0; i < 2; ++i) {
        Cursor &cur = (i == 0 ?
                       slime.get() :        // i = 0 -> empty object
                       *vespalib::slime::NixValue::invalid()); // i = 1 -> invalid cursor
        if (i == 0) {
            EXPECT_TRUE(cur.valid());
        } else {
            EXPECT_TRUE(!cur.valid());
        }
        EXPECT_EQUAL(vespalib::slime::NIX::ID, cur.type().getId());
        EXPECT_EQUAL(0u, cur.children());
        EXPECT_EQUAL(0u, cur.entries());
        EXPECT_EQUAL(0u, cur.fields());
        EXPECT_EQUAL(cur.asBool(), false);
        EXPECT_EQUAL(cur.asLong(), 0L);
        EXPECT_EQUAL(cur.asDouble(), 0.0);
        {
            Memory expect;
            Memory actual = cur.asString();
            EXPECT_EQUAL(expect.data, actual.data);
            EXPECT_EQUAL(expect.size, actual.size);
        }
        {
            Memory expect;
            Memory actual = cur.asData();
            EXPECT_EQUAL(expect.data, actual.data);
            EXPECT_EQUAL(expect.size, actual.size);
        }
        EXPECT_TRUE(!cur[0].valid());         // ARRAY
        EXPECT_TRUE(!cur["foo"].valid());     // OBJECT
        EXPECT_TRUE(!cur[Symbol(5)].valid()); // OBJECT
    }
}

TEST("test basic") {
    { // BOOL
        Slime slime;
        slime.setBool(true);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::slime::BOOL::ID, slime.get().type().getId());
        EXPECT_EQUAL(slime.get().asBool(), true);
    }
    { // LONG
        Slime slime;
        slime.setLong(123);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::slime::LONG::ID, slime.get().type().getId());
        EXPECT_EQUAL(123, slime.get().asLong());
    }
    { // DOUBLE
        Slime slime;
        slime.setDouble(2.5);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::slime::DOUBLE::ID, slime.get().type().getId());
        EXPECT_EQUAL(2.5, slime.get().asDouble());
    }
    { // STRING
        std::string str("string");
        Slime slime;
        slime.setString(Memory(str));
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::slime::STRING::ID, slime.get().type().getId());
        EXPECT_EQUAL(std::string("string"), slime.get().asString().make_string());
    }
    { // DATA
        std::string data("data");
        Slime slime;
        slime.setData(Memory(data));
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::slime::DATA::ID, slime.get().type().getId());
        EXPECT_EQUAL(std::string("data"), slime.get().asData().make_string());
    }
}

TEST("test array") {
    Slime slime;
    Cursor &c = slime.setArray();
    EXPECT_TRUE(slime.get().valid());
    EXPECT_EQUAL(vespalib::slime::ARRAY::ID, slime.get().type().getId());
    EXPECT_EQUAL(0u, c.children());
    EXPECT_EQUAL(0u, c.entries());
    EXPECT_EQUAL(0u, c.fields());
    c.addNix();
    c.addBool(true);
    c.addLong(5);
    c.addDouble(3.5);
    c.addString(Memory("string"));
    c.addData(Memory("data"));
    EXPECT_EQUAL(6u, c.children());
    EXPECT_EQUAL(6u, c.entries());
    EXPECT_EQUAL(0u, c.fields());
    EXPECT_TRUE(c[0].valid());
    EXPECT_EQUAL(true, c[1].asBool());
    EXPECT_EQUAL(5, c[2].asLong());
    EXPECT_EQUAL(3.5, c[3].asDouble());
    EXPECT_EQUAL(std::string("string"), c[4].asString().make_string());
    EXPECT_EQUAL(std::string("data"), c[5].asData().make_string());
    EXPECT_TRUE(!c[Symbol(5)].valid()); // not OBJECT
}

TEST("test object") {
    Slime slime;
    Cursor &c = slime.setObject();
    EXPECT_TRUE(slime.get().valid());
    EXPECT_EQUAL(vespalib::slime::OBJECT::ID, slime.get().type().getId());
    EXPECT_EQUAL(0u, c.children());
    EXPECT_EQUAL(0u, c.entries());
    EXPECT_EQUAL(0u, c.fields());
    c.setNix("a");
    c.setBool("b", true);
    c.setLong("c", 5);
    c.setDouble("d", 3.5);
    c.setString("e", Memory("string"));
    c.setData("f", Memory("data"));
    EXPECT_EQUAL(6u, c.children());
    EXPECT_EQUAL(0u, c.entries());
    EXPECT_EQUAL(6u, c.fields());
    EXPECT_TRUE(c["a"].valid());
    EXPECT_EQUAL(true, c["b"].asBool());
    EXPECT_EQUAL(5, c["c"].asLong());
    EXPECT_EQUAL(3.5, c["d"].asDouble());
    EXPECT_EQUAL(std::string("string"), c["e"].asString().make_string());
    EXPECT_EQUAL(std::string("data"), c["f"].asData().make_string());
    EXPECT_TRUE(!c[4].valid()); // not ARRAY
}

TEST("test chaining") {
    // when adding a value, a cursor for the added value is
    // returned. If the add fails for some reason, an invalid cursor
    // is returned instead.
    {
        Slime slime;
        Cursor &c = slime.setArray();
        EXPECT_EQUAL(5, c.addLong(5).asLong());
    }
    {
        Slime slime;
        Cursor &c = slime.setObject();
        EXPECT_EQUAL(5, c.setLong("a", 5).asLong());
    }
}

TEST("test proxy conversion") {
    Slime slime;
    Cursor &c = slime.setLong(10);
    Inspector &i1 = c;
    EXPECT_EQUAL(10u, i1.asLong());
    Inspector &i2 = slime.get();
    EXPECT_EQUAL(10u, i2.asLong());
    const Slime &const_slime = slime;
    Inspector &i3 = const_slime.get();
    EXPECT_EQUAL(10u, i3.asLong());
}

TEST("test nesting") {
    Slime slime;
    {
        Cursor &c1 = slime.setObject();
        {
            c1.setLong("bar", 10);
            {
                Cursor &c2 = c1.setArray("foo");
                c2.addLong(20);                 // [0]
                {
                    Cursor &c3 = c2.addObject(); // [1]
                    c3.setLong("answer", 42);
                }
            }
        }
    }
    Cursor &c = slime.get();
    EXPECT_EQUAL(10, c["bar"].asLong());
    EXPECT_EQUAL(20, c["foo"][0].asLong());
    EXPECT_EQUAL(42, c["foo"][1]["answer"].asLong());
}

TEST("test wrap") {
    Slime slime;
    slime.setLong(42);
    EXPECT_EQUAL(42, slime.get().asLong());
    slime.wrap("foo");
    EXPECT_EQUAL(42, slime.get()["foo"].asLong());
}

TEST("string format") {
    std::string ret = vespalib::slime::strfmt("num: %d", 5);
    EXPECT_EQUAL(ret, "num: 5");
}

TEST("cross-type number conversion") {
    Slime slime;
    slime.setArray();
    slime.get().addDouble(2.7);
    slime.get().addLong(5);
    EXPECT_EQUAL(2.7, slime.get()[0].asDouble());
    EXPECT_EQUAL(2, slime.get()[0].asLong());
    EXPECT_EQUAL(5, slime.get()[1].asLong());
    EXPECT_EQUAL(5.0, slime.get()[1].asDouble());
}

TEST("slime toString produces human-readable JSON") {
    Slime slime;
    {
        Cursor &c1 = slime.setObject();
        {
            c1.setLong("bar", 10);
            {
                Cursor &c2 = c1.setArray("foo");
                c2.addLong(20);                 // [0]
                {
                    Cursor &c3 = c2.addObject(); // [1]
                    c3.setLong("answer", 42);
                }
            }
        }
    }
    std::string expect;
    {
        vespalib::SimpleBuffer buf;
        vespalib::slime::JsonFormat::encode(slime, buf, false);
        expect = buf.get().make_string();
    }
    EXPECT_EQUAL(expect, slime.toString());
}

TEST("require that slime objects can be moved") {
    Slime obj1;
    {
        obj1.setObject().setLong("foo", 123);
    }
    EXPECT_EQUAL(123, obj1.get()["foo"].asLong());
    Slime obj2(std::move(obj1));
    EXPECT_TRUE(!obj1.get()["foo"].valid());
    EXPECT_EQUAL(123, obj2.get()["foo"].asLong());
    Slime obj3;
    obj3 = std::move(obj2);
    EXPECT_TRUE(!obj2.get()["foo"].valid());
    EXPECT_EQUAL(123, obj3.get()["foo"].asLong());
}

TEST("require that we can replace symbol table") {
    const Memory A("a");
    vespalib::slime::SymbolTable::UP symbols = std::make_unique<vespalib::slime::SymbolTable>();
    EXPECT_TRUE(symbols->lookup(A).undefined());
    symbols->insert(A);
    EXPECT_FALSE(symbols->lookup(A).undefined());
    Slime slime(Slime::Params(std::move(symbols)));
    EXPECT_FALSE(slime.lookup(A).undefined());
    symbols = Slime::reclaimSymbols(std::move(slime));
    EXPECT_FALSE(symbols->lookup(A).undefined());
}

TEST("require that slime objects can be compared") {
    EXPECT_EQUAL(Slime().setNix(), Slime().setNix());
    EXPECT_EQUAL(Slime().setBool(false), Slime().setBool(false));
    EXPECT_NOT_EQUAL(Slime().setBool(false), Slime().setBool(true));
    EXPECT_EQUAL(Slime().setLong(123), Slime().setLong(123));
    EXPECT_NOT_EQUAL(Slime().setLong(123), Slime().setLong(321));
    EXPECT_EQUAL(Slime().setDouble(123), Slime().setDouble(123));
    EXPECT_NOT_EQUAL(Slime().setDouble(123), Slime().setDouble(321));
    EXPECT_EQUAL(Slime().setString("foo"), Slime().setString("foo"));
    EXPECT_NOT_EQUAL(Slime().setString("foo"), Slime().setString("bar"));
    EXPECT_EQUAL(Slime().setData("foo"), Slime().setData("foo"));
    EXPECT_NOT_EQUAL(Slime().setData("foo"), Slime().setData("bar"));
    EXPECT_EQUAL(Slime().setArray(), Slime().setArray());
    EXPECT_EQUAL(Slime().setObject(), Slime().setObject());
    {
        Slime a;
        Cursor &arr_a = a.setArray();
        arr_a.addLong(1);
        arr_a.addLong(2);
        arr_a.addLong(3);
        Slime b;
        Cursor &arr_b = b.setArray();
        arr_b.addLong(1);
        arr_b.addLong(2);
        arr_b.addLong(3);
        EXPECT_EQUAL(a, b);
        EXPECT_EQUAL(b, a);
        arr_b.addLong(4);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
        arr_a.addLong(5);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
    }
    {
        Slime a;
        Cursor &obj_a = a.setObject();
        obj_a.setLong("foo", 1);
        obj_a.setLong("bar", 2);
        obj_a.setLong("baz", 3);
        Slime b;
        Cursor &obj_b = b.setObject();
        obj_b.setLong("foo", 1);
        obj_b.setLong("bar", 2);
        obj_b.setLong("baz", 3);
        EXPECT_EQUAL(a, b);
        EXPECT_EQUAL(b, a);
        obj_b.setLong("fox", 4);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
        obj_a.setLong("fox", 5);
        EXPECT_NOT_EQUAL(a, b);
        EXPECT_NOT_EQUAL(b, a);
    }
    EXPECT_NOT_EQUAL(Slime().setBool(false), Slime().setNix());
    EXPECT_NOT_EQUAL(Slime().setLong(123), Slime().setDouble(123));
    EXPECT_NOT_EQUAL(Slime().setData("foo"), Slime().setString("foo"));
    EXPECT_NOT_EQUAL(Slime().setArray(), Slime().setObject());
}

TEST("require that nix equality checks validity") {
    const Inspector &good_nix = *vespalib::slime::NixValue::instance();
    const Inspector &bad_nix = *vespalib::slime::NixValue::invalid();
    EXPECT_EQUAL(good_nix, good_nix);
    EXPECT_EQUAL(bad_nix, bad_nix);
    EXPECT_NOT_EQUAL(good_nix, bad_nix);
    EXPECT_NOT_EQUAL(bad_nix, good_nix);
}

TEST("require that we can resolve to symbol table from a cursor") {
    Slime slime;
    Cursor &c1 = slime.setObject();
    Cursor &c2 = c1.setArray("foo");
    Cursor &c3 = c1.setLong("bar", 5);
    Cursor &c4 = c2.addObject();
    const Memory A("a");
    const Memory B("b");
    const Memory C("c");
    const Memory D("d");
    EXPECT_TRUE(slime.lookup(A).undefined());
    EXPECT_TRUE(slime.lookup(B).undefined());
    EXPECT_TRUE(slime.lookup(C).undefined());
    EXPECT_TRUE(slime.lookup(D).undefined());

    Symbol sa = c1.resolve(A);
    Symbol sb = c2.resolve(B);
    Symbol sc = c3.resolve(C);
    Symbol sd = c4.resolve(D);
    EXPECT_TRUE(!sa.undefined());
    EXPECT_TRUE(!sb.undefined());
    EXPECT_TRUE(sc.undefined());
    EXPECT_TRUE(!sd.undefined());

    EXPECT_TRUE(!slime.lookup(A).undefined());
    EXPECT_TRUE(!slime.lookup(B).undefined());
    EXPECT_TRUE(slime.lookup(C).undefined());
    EXPECT_TRUE(!slime.lookup(D).undefined());

    EXPECT_TRUE(sa == slime.lookup(A));
    EXPECT_TRUE(sb == slime.lookup(B));
    EXPECT_TRUE(sc == slime.lookup(C));
    EXPECT_TRUE(sd == slime.lookup(D));
}

template <typename T>
void verify_cursor_ref(T &&) {
    EXPECT_TRUE((std::is_same<Cursor&,T>::value));
}

template <typename T>
void verify_inspector_ref(T &&) {
    EXPECT_TRUE((std::is_same<Inspector&,T>::value));
}

TEST("require that top-level convenience accessors work as expected for objects") {
    Slime object;
    Cursor &c = object.setObject();
    c.setLong("a", 10);
    c.setLong("b", 20);
    c.setLong("c", 30);
    Symbol sym_b = object.lookup("b");
    const Slime &const_object = object;
    TEST_DO(verify_cursor_ref(object[0]));
    TEST_DO(verify_inspector_ref(const_object[0]));
    EXPECT_EQUAL(object[0].asLong(), 0);
    EXPECT_EQUAL(object[sym_b].asLong(), 20);
    EXPECT_EQUAL(object["c"].asLong(), 30);
    EXPECT_EQUAL(const_object[0].asLong(), 0);
    EXPECT_EQUAL(const_object[sym_b].asLong(), 20);
    EXPECT_EQUAL(const_object["c"].asLong(), 30);
}

TEST("require that top-level convenience accessors work as expected for arrays") {
    Slime array;
    Cursor &c = array.setArray();
    c.addLong(10);
    c.addLong(20);
    c.addLong(30);
    Symbol sym_b(1);
    const Slime &const_array = array;
    TEST_DO(verify_cursor_ref(array[0]));
    TEST_DO(verify_inspector_ref(const_array[0]));
    EXPECT_EQUAL(array[0].asLong(), 10);
    EXPECT_EQUAL(array[sym_b].asLong(), 0);
    EXPECT_EQUAL(array["c"].asLong(), 0);
    EXPECT_EQUAL(const_array[0].asLong(), 10);
    EXPECT_EQUAL(const_array[sym_b].asLong(), 0);
    EXPECT_EQUAL(const_array["c"].asLong(), 0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
