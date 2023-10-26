// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/strfmt.h>

using namespace vespalib::slime::convenience;

struct SrcFixture {
    Slime empty;
    Slime nix_value;
    Slime bool_value;
    Slime long_value;
    Slime double_value;
    Slime string_value;
    Slime data_value;
    Slime array_value;
    Slime object_value;
    SrcFixture() {
        nix_value.setNix();
        bool_value.setBool(true);
        long_value.setLong(10);
        double_value.setDouble(20.0);
        string_value.setString("string");
        data_value.setData("data");
        Cursor &arr = array_value.setArray();
        arr.addLong(1);
        arr.addLong(2);
        arr.addLong(3);
        Cursor &obj = object_value.setObject();
        obj.setLong("a", 1);
        obj.setLong("b", 2);
        obj.setLong("c", 3);
    }
};

struct DstFixture {
    Slime slime1;
    Slime slime2;
    Slime slime3;
    Slime slime4;
    Slime slime5;
    Slime slime6;
    Slime slime7;
    Slime slime8;
    Slime slime9;
    DstFixture();
    ~DstFixture();
};

DstFixture::DstFixture() { }
DstFixture::~DstFixture() { }

TEST_FF("inject into slime", SrcFixture(), DstFixture()) {
    EXPECT_TRUE(f1.empty.get().valid()); // explicit nix

    inject(f1.empty.get(), SlimeInserter(f2.slime1));
    inject(f1.nix_value.get(), SlimeInserter(f2.slime2));
    inject(f1.bool_value.get(), SlimeInserter(f2.slime3));
    inject(f1.long_value.get(), SlimeInserter(f2.slime4));
    inject(f1.double_value.get(), SlimeInserter(f2.slime5));
    inject(f1.string_value.get(), SlimeInserter(f2.slime6));
    inject(f1.data_value.get(), SlimeInserter(f2.slime7));
    inject(f1.array_value.get(), SlimeInserter(f2.slime8));
    inject(f1.object_value.get(), SlimeInserter(f2.slime9));

    EXPECT_EQUAL(f1.empty.get().toString(), f2.slime1.get().toString());
    EXPECT_EQUAL(f1.nix_value.get().toString(), f2.slime2.get().toString());
    EXPECT_EQUAL(f1.bool_value.get().toString(), f2.slime3.get().toString());
    EXPECT_EQUAL(f1.long_value.get().toString(), f2.slime4.get().toString());
    EXPECT_EQUAL(f1.double_value.get().toString(), f2.slime5.get().toString());
    EXPECT_EQUAL(f1.string_value.get().toString(), f2.slime6.get().toString());
    EXPECT_EQUAL(f1.data_value.get().toString(), f2.slime7.get().toString());
    EXPECT_EQUAL(f1.array_value.get().toString(), f2.slime8.get().toString());
    EXPECT_EQUAL(f1.object_value.get(), f2.slime9.get());
}

TEST_FF("inject into array", SrcFixture(), DstFixture()) {
    f2.slime1.setArray();
    inject(f1.empty.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.nix_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.bool_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.long_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.double_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.string_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.data_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.array_value.get(), ArrayInserter(f2.slime1.get()));
    inject(f1.object_value.get(), ArrayInserter(f2.slime1.get()));

    EXPECT_EQUAL(f1.empty.get().toString(), f2.slime1.get()[0].toString());
    EXPECT_EQUAL(f1.nix_value.get().toString(), f2.slime1.get()[1].toString());
    EXPECT_EQUAL(f1.bool_value.get().toString(), f2.slime1.get()[2].toString());
    EXPECT_EQUAL(f1.long_value.get().toString(), f2.slime1.get()[3].toString());
    EXPECT_EQUAL(f1.double_value.get().toString(), f2.slime1.get()[4].toString());
    EXPECT_EQUAL(f1.string_value.get().toString(), f2.slime1.get()[5].toString());
    EXPECT_EQUAL(f1.data_value.get().toString(), f2.slime1.get()[6].toString());
    EXPECT_EQUAL(f1.array_value.get().toString(), f2.slime1.get()[7].toString());
    EXPECT_EQUAL(f1.object_value.get(), f2.slime1.get()[8]);
}

TEST_FF("inject into object", SrcFixture(), DstFixture()) {
    f2.slime1.setObject();
    inject(f1.empty.get(), ObjectInserter(f2.slime1.get(), "a"));
    inject(f1.nix_value.get(), ObjectInserter(f2.slime1.get(), "b"));
    inject(f1.bool_value.get(), ObjectInserter(f2.slime1.get(), "c"));
    inject(f1.long_value.get(), ObjectInserter(f2.slime1.get(), "d"));
    inject(f1.double_value.get(), ObjectInserter(f2.slime1.get(), "e"));
    inject(f1.string_value.get(), ObjectInserter(f2.slime1.get(), "f"));
    inject(f1.data_value.get(), ObjectInserter(f2.slime1.get(), "g"));
    inject(f1.array_value.get(), ObjectInserter(f2.slime1.get(), "h"));
    inject(f1.object_value.get(), ObjectInserter(f2.slime1.get(), "i"));

    EXPECT_EQUAL(f1.empty.get().toString(), f2.slime1.get()["a"].toString());
    EXPECT_EQUAL(f1.nix_value.get().toString(), f2.slime1.get()["b"].toString());
    EXPECT_EQUAL(f1.bool_value.get().toString(), f2.slime1.get()["c"].toString());
    EXPECT_EQUAL(f1.long_value.get().toString(), f2.slime1.get()["d"].toString());
    EXPECT_EQUAL(f1.double_value.get().toString(), f2.slime1.get()["e"].toString());
    EXPECT_EQUAL(f1.string_value.get().toString(), f2.slime1.get()["f"].toString());
    EXPECT_EQUAL(f1.data_value.get().toString(), f2.slime1.get()["g"].toString());
    EXPECT_EQUAL(f1.array_value.get().toString(), f2.slime1.get()["h"].toString());
    EXPECT_EQUAL(f1.object_value.get(), f2.slime1.get()["i"]);    
}

TEST_FF("invalid injection is ignored", SrcFixture(), DstFixture()) {
    inject(f1.array_value.get(), SlimeInserter(f2.slime1));
    EXPECT_EQUAL(3u, f2.slime1.get().entries());
    inject(f1.long_value.get(), ArrayInserter(f2.slime1.get()));
    EXPECT_EQUAL(4u, f2.slime1.get().entries());
    inject(f1.double_value.get(), ArrayInserter(f2.slime1.get()));
    EXPECT_EQUAL(5u, f2.slime1.get().entries());
    inject(f1.nix_value.get()["bogus"], ArrayInserter(f2.slime1.get()));
    EXPECT_EQUAL(5u, f2.slime1.get().entries());
}

TEST("recursive array inject") {
    Slime expect;
    {
        Cursor &arr = expect.setArray();
        arr.addLong(1);
        arr.addLong(2);
        arr.addLong(3);
        {
            Cursor &arr_cpy = arr.addArray();
            arr_cpy.addLong(1);
            arr_cpy.addLong(2);
            arr_cpy.addLong(3);
        }
    }
    Slime data;
    {
        Cursor &arr = data.setArray();
        arr.addLong(1);
        arr.addLong(2);
        arr.addLong(3);
    }
    inject(data.get(), ArrayInserter(data.get()));
    EXPECT_EQUAL(expect.toString(), data.toString());
}

TEST("recursive object inject") {
    Slime expect;
    {
        Cursor &obj = expect.setObject();
        obj.setLong("a", 1);
        obj.setLong("b", 2);
        obj.setLong("c", 3);
        {
            Cursor &obj_cpy = obj.setObject("d");
            obj_cpy.setLong("a", 1);
            obj_cpy.setLong("b", 2);
            obj_cpy.setLong("c", 3);
        }
    }
    Slime data;
    {
        Cursor &obj = data.setObject();
        obj.setLong("a", 1);
        obj.setLong("b", 2);
        obj.setLong("c", 3);
    }
    inject(data.get(), ObjectInserter(data.get(), "d"));
    EXPECT_EQUAL(expect, data);
}

TEST_MAIN() { TEST_RUN_ALL(); }
