// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/hashmap.h>

class Test : public vespalib::TestApp
{
public:
    void testInt();
    void testString();
    void testHashValue();
    int Main() override;
};



int
Test::Main()
{
    TEST_INIT("hashmap_test");
    srandom(1);
    testInt();
    TEST_FLUSH();
    testString();
    TEST_FLUSH();
    testHashValue();
    TEST_DONE();
}


void
Test::testHashValue()
{
    const char * s("abcdefghi");
    EXPECT_EQUAL(7045194595191919248ul, vespalib::hashValue(s));
    EXPECT_EQUAL(vespalib::hashValue(s), vespalib::hashValue(s, strlen(s)));
    EXPECT_NOT_EQUAL(vespalib::hashValue(s), vespalib::hashValue(s, strlen(s)-1));
}

void
Test::testInt()
{
    vespalib::HashMap<int> map(-1, 5);

    {
        vespalib::HashMap<int>::Iterator it = map.iterator();
        EXPECT_TRUE(!it.valid());
    }

    EXPECT_TRUE(map.size() == 0);
    EXPECT_TRUE(map.isEmpty());
    EXPECT_TRUE(map.buckets() >= 5);

    EXPECT_TRUE(map.set("one", 1)   == -1);
    EXPECT_TRUE(map.set("two", 2)   == -1);
    EXPECT_TRUE(map.set("three", 3) == -1);
    EXPECT_TRUE(map.set("four", 4)  == -1);

    {
        vespalib::HashMap<int>::Iterator it = map.iterator();
        EXPECT_TRUE(it.valid());
        EXPECT_TRUE(map[it.key()] == it.value());
        it.next();
        EXPECT_TRUE(it.valid());
        EXPECT_TRUE(map[it.key()] == it.value());
        it.next();
        EXPECT_TRUE(it.valid());
        EXPECT_TRUE(map[it.key()] == it.value());
        it.next();
        EXPECT_TRUE(it.valid());
        EXPECT_TRUE(map[it.key()] == it.value());
        it.next();
        EXPECT_TRUE(!it.valid());
    }

    EXPECT_TRUE(map.size() == 4);
    EXPECT_TRUE(!map.isEmpty());
    EXPECT_TRUE(map.get("one")   ==  1);
    EXPECT_TRUE(map.get("two")   ==  2);
    EXPECT_TRUE(map.get("three") ==  3);
    EXPECT_TRUE(map.get("four")  ==  4);
    EXPECT_TRUE(map.get("five")  == -1);

    EXPECT_TRUE(map.set("one", 11) == 1);
    EXPECT_TRUE(map.get("one") == 11);
    EXPECT_TRUE(map.size() == 4);

    EXPECT_TRUE(map["one"]   == 11);
    EXPECT_TRUE(map["two"]   ==  2);
    EXPECT_TRUE(map["three"] ==  3);
    EXPECT_TRUE(map["four"]  ==  4);
    EXPECT_TRUE(map["five"]  == -1);
    EXPECT_TRUE(map.size() == 4);

    map.set("1", 1);
    map.set("2", 2);
    map.set("3", 3);
    map.set("4", 4);
    map.set("5", 5);
    map.set("6", 6);
    map.set("7", 7);
    map.set("8", 8);
    map.set("9", 9);
    map.set("10", 10);
    map.set("11", 11);
    map.set("12", 12);
    map.set("13", 13);
    map.set("14", 14);
    map.set("15", 15);
    map.set("16", 16);
    map.set("17", 17);
    map.set("18", 18);
    map.set("19", 19);
    map.set("20", 20);
    EXPECT_TRUE(map.size() == 24);
    EXPECT_TRUE(map.remove("5")  == 5);
    EXPECT_TRUE(map.remove("10") == 10);
    EXPECT_TRUE(map.remove("15") == 15);
    EXPECT_TRUE(map.remove("20") == 20);
    EXPECT_TRUE(map.size() == 20);

    EXPECT_TRUE(map["1"]  ==  1);
    EXPECT_TRUE(map["2"]  ==  2);
    EXPECT_TRUE(map["3"]  ==  3);
    EXPECT_TRUE(map["4"]  ==  4);
    EXPECT_TRUE(map["5"]  == -1);
    EXPECT_TRUE(map["6"]  ==  6);
    EXPECT_TRUE(map["7"]  ==  7);
    EXPECT_TRUE(map["8"]  ==  8);
    EXPECT_TRUE(map["9"]  ==  9);
    EXPECT_TRUE(map["10"] == -1);
    EXPECT_TRUE(map["11"] == 11);
    EXPECT_TRUE(map["12"] == 12);
    EXPECT_TRUE(map["13"] == 13);
    EXPECT_TRUE(map["14"] == 14);
    EXPECT_TRUE(map["15"] == -1);
    EXPECT_TRUE(map["16"] == 16);
    EXPECT_TRUE(map["17"] == 17);
    EXPECT_TRUE(map["18"] == 18);
    EXPECT_TRUE(map["19"] == 19);
    EXPECT_TRUE(map["20"] == -1);

    EXPECT_TRUE(map.remove("bogus1") == -1);
    EXPECT_TRUE(map.remove("bogus2") == -1);
    EXPECT_TRUE(map.remove("bogus3") == -1);
    EXPECT_TRUE(map.size() == 20);

    map.clear();
    {
        vespalib::HashMap<int>::Iterator it = map.iterator();
        EXPECT_TRUE(!it.valid());
    }
    EXPECT_TRUE(map.size() == 0);
    EXPECT_TRUE(map.isEmpty());
    EXPECT_TRUE(map.get("one")   == -1);
    EXPECT_TRUE(map.get("two")   == -1);
    EXPECT_TRUE(map.get("three") == -1);
}

void
Test::testString()
{
    using std::string;
    vespalib::HashMap<string> map("");

    map.set("a", "a");
    map.set("b", string("b"));
    const string c("c");
    map.set("c", c);
    string d = "d";
    map.set("d", d);
    string e;
    e = "e";
    map.set("e", e);

    EXPECT_TRUE(map.size() == 5);
    EXPECT_TRUE(map.isSet("a"));
    EXPECT_TRUE(map.isSet("b"));
    EXPECT_TRUE(map.isSet("c"));
    EXPECT_TRUE(map.isSet("d"));
    EXPECT_TRUE(map.isSet("e"));
    EXPECT_TRUE(map.get("a") != "");
    EXPECT_TRUE(map.get("b") != "");
    EXPECT_TRUE(map.get("c") != "");
    EXPECT_TRUE(map.get("d") != "");
    EXPECT_TRUE(map.get("e") != "");
    EXPECT_TRUE(map.get("a") == "a");
    EXPECT_TRUE(map.get("b") == "b");
    EXPECT_TRUE(map.get("c") == "c");
    EXPECT_TRUE(map.get("d") == "d");
    EXPECT_TRUE(map.get("e") == "e");
    EXPECT_TRUE(!map.isSet("x"));
    EXPECT_TRUE(map.get("x") == "");
}

TEST_APPHOOK(Test)

