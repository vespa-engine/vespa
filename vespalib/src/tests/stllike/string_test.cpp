// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/string.h>
#include <algorithm>

using namespace vespalib;

TEST("testStringInsert") {
    string s("first string ");
    string a;
    EXPECT_TRUE("first string " == a.insert(0, s));
    EXPECT_EQUAL(string("first first string string "), a.insert(6, s));
    EXPECT_EQUAL(2*s.size(), a.size());
    EXPECT_TRUE(string("first first string string ") == s.insert(6, s));
}

TEST("testStringIterator") {
    string s("abcabccba");
    std::replace(s.begin(), s.end(), 'a','z');
    EXPECT_TRUE(s == "zbczbccbz");
}

TEST("test iterator assignment") {
    std::vector<char> empty;
    string s(empty.begin(), empty.end());
    EXPECT_TRUE(strstr(s.c_str(), "mumbo jumbo.") == nullptr);
}

namespace {

template <typename S>
void assign(S &lhs, const S &rhs) __attribute__((noinline));

template <typename S>
void
assign(S &lhs, const S &rhs)
{
    lhs = rhs;
}

}


TEST("test self assignment of small string") {
    const char * text = "abc";
    string s(text);
    const char * addr(reinterpret_cast<const char *>(&s));
    EXPECT_TRUE((addr < s.c_str()) && (s.c_str() < addr + sizeof(s)));
    assign(s, s);
    EXPECT_EQUAL(text, s);
}

TEST("test self assignment of big string") {
    const char * text = "abcbcdefghijklmnopqrstuvwxyz-abcbcdefghijklmnopqrstuvwxyz";
    string s(text);
    const char * addr(reinterpret_cast<const char *>(&s));
    EXPECT_TRUE((addr > s.c_str()) || (s.c_str() > addr + sizeof(s)));
    assign(s, s);
    EXPECT_EQUAL(text, s);
}

void verify_move_constructor(string org) {
    string copy(org);
    EXPECT_EQUAL(org, copy);
    string moved_into(std::move(copy));
    EXPECT_EQUAL(org, moved_into);
    EXPECT_NOT_EQUAL(org, copy);
    EXPECT_EQUAL(string(), copy);
}

void verify_move_operator(string org) {
    string copy(org);
    EXPECT_EQUAL(org, copy);
    string moved_into_short("short movable string");
    EXPECT_LESS(moved_into_short.size(), string().capacity());
    EXPECT_NOT_EQUAL(org, moved_into_short);
    moved_into_short = std::move(copy);
    EXPECT_EQUAL(org, moved_into_short);
    EXPECT_NOT_EQUAL(org, copy);
    EXPECT_EQUAL(string(), copy);

    string moved_into_long("longer movable string than the 47 bytes that can be held in the short string optimization.");
    EXPECT_GREATER(moved_into_long.size(), string().capacity());
    EXPECT_NOT_EQUAL(org, moved_into_long);
    moved_into_long = std::move(moved_into_short);
    EXPECT_EQUAL(org, moved_into_long);
    EXPECT_NOT_EQUAL(org, moved_into_short);
    EXPECT_EQUAL(string(), moved_into_short);
}

void verify_move(string org) {
    verify_move_constructor(org);
    verify_move_operator(org);
}

TEST("test move constructor") {
    TEST_DO(verify_move("short string"));
    TEST_DO(verify_move("longer string than the 47 bytes that can be held in the short string optimization."));
}

TEST("testStringAlloc") {
    fprintf(stderr, "... testing allocations\n");
    string a("abcde");

    for (int i=0; i<99999; i++) {
        a.append("12345");
    }
    EXPECT_TRUE(a.size() == 5u*100000);
    EXPECT_TRUE(a.capacity() > a.size());
    EXPECT_TRUE(a.capacity() < 2*a.size());

    string foo;
    EXPECT_EQUAL(64ul, sizeof(foo));

    small_string<112> bar;
    EXPECT_EQUAL(128ul, sizeof(bar));

    string reset;
    for (int i=0; i<100; i++) {
        reset.append("12345");
    }
    EXPECT_EQUAL(500u, reset.size());
    EXPECT_EQUAL(511u, reset.capacity());
    reset.reserve(2000);
    EXPECT_EQUAL(500u, reset.size());
    EXPECT_EQUAL(2000u, reset.capacity());
    reset.reset();
    EXPECT_EQUAL(0u, reset.size());
    EXPECT_EQUAL(47u, reset.capacity());

    TEST_FLUSH();
}

TEST("testStringCompare") {
    fprintf(stderr, "... testing comparison\n");
    string abc("abc");
    string abb("abb");
    string abd("abd");

    string a5("abcde");

    std::string other("abc");

    EXPECT_TRUE(abc == "abc");
    EXPECT_TRUE(abc == other);
    EXPECT_TRUE(!(abc == "aaa"));
    EXPECT_TRUE(!(abc == "a"));
    EXPECT_TRUE(!(abc == "abcde"));
    EXPECT_TRUE(!(abc == abb));
    EXPECT_TRUE(!(abc == a5));

    EXPECT_TRUE(abc != abd);
    EXPECT_TRUE(abc != "aaa");
    EXPECT_TRUE(abc != "a");
    EXPECT_TRUE(abc != a5);
    EXPECT_TRUE(!(abc != abc));
    EXPECT_TRUE(!(abc != other));

    EXPECT_TRUE(abc < abd);
    EXPECT_TRUE(abb < abc);
    EXPECT_TRUE(abc < a5);
    EXPECT_TRUE(abc.compare(abd) < 0);
    EXPECT_TRUE(abd.compare(abc) > 0);
    EXPECT_TRUE(abc.compare(abc) == 0);

    TEST_FLUSH();
}

TEST("testString") {
    fprintf(stderr, "... testing basic functionality\n");
    string a;
    EXPECT_EQUAL(sizeof(a), 48 + sizeof(uint32_t)*2 + sizeof(char *));
    EXPECT_EQUAL(0u, a.size());
    a.append("a");
    EXPECT_EQUAL(1u, a.size());
    EXPECT_TRUE(strcmp("a", a.c_str()) == 0);
    a.append("b");
    EXPECT_EQUAL(2u, a.size());
    EXPECT_TRUE(strcmp("ab", a.c_str()) == 0);
    string b(a);
    EXPECT_EQUAL(2u, a.size());
    EXPECT_TRUE(strcmp("ab", a.c_str()) == 0);
    EXPECT_EQUAL(2u, b.size());
    EXPECT_TRUE(strcmp("ab", b.c_str()) == 0);
    string c("dfajsg");
    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);
    b = c;
    EXPECT_EQUAL(6u, b.size());
    EXPECT_TRUE(strcmp("dfajsg", b.c_str()) == 0);

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    TEST_FLUSH();

    std::string::size_type exp = std::string::npos;
    std::string::size_type act = vespalib::string::npos;
    EXPECT_EQUAL(exp, act);
    std::string::size_type idx = a.find('a');
    EXPECT_EQUAL(0u, idx);
    idx = a.find('b');
    EXPECT_EQUAL(1u, idx);
    idx = a.find('x');
    EXPECT_EQUAL(std::string::npos, idx);
    EXPECT_EQUAL(1u, a.find('b', 1));
    EXPECT_EQUAL(std::string::npos, a.find('b', 2));
    // causes warning:
    EXPECT_TRUE(vespalib::string::npos == idx);

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    TEST_FLUSH();

    string slow;
    for (int i = 0; i < 9; i++) {
        EXPECT_EQUAL(i*5u, slow.size());
        slow.append("abcde");
        EXPECT_EQUAL(sizeof(slow) - 17u, slow.capacity());
    }

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    EXPECT_EQUAL(45u, slow.size());
    EXPECT_EQUAL(47u, slow.capacity());
    slow.append("1");
    EXPECT_EQUAL(46u, slow.size());
    slow.append("1");
    EXPECT_EQUAL(47u, slow.size());
    EXPECT_EQUAL(47u, slow.capacity());
    slow.append("1");
    EXPECT_EQUAL(48u, slow.size());
    EXPECT_EQUAL(63u, slow.capacity());

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);


    string fast;
    fast.append(slow);

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    EXPECT_EQUAL(48u, fast.size());
    EXPECT_EQUAL(63u, fast.capacity());
    fast.append(slow);

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    EXPECT_EQUAL(48u*2, fast.size());
    EXPECT_EQUAL(127u, fast.capacity());
    fast.append(slow);

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    EXPECT_EQUAL(48u*3, fast.size());
    EXPECT_EQUAL(255u, fast.capacity());
    fast.append(slow);
    EXPECT_EQUAL(48u*4, fast.size());
    EXPECT_EQUAL(255u, fast.capacity());

    EXPECT_EQUAL(6u, c.size());
    EXPECT_TRUE(strcmp("dfajsg", c.c_str()) == 0);

    std::istringstream is("test streng");
    string test, streng;
    is >> test >> streng;
    EXPECT_EQUAL(test, "test");
    EXPECT_EQUAL(streng, "streng");
    std::ostringstream os;
    os << test << streng;
    EXPECT_EQUAL(os.str(), "teststreng");

    {
        string s("abcabca");
        EXPECT_EQUAL(string::npos, s.find('g'));
        EXPECT_EQUAL(string::npos, s.rfind('g'));
        EXPECT_EQUAL(0u, s.find('a'));
        EXPECT_EQUAL(6u, s.rfind('a'));
        EXPECT_EQUAL(1u, s.find('b'));
        EXPECT_EQUAL(4u, s.rfind('b'));
        EXPECT_EQUAL(2u, s.find("ca"));
        EXPECT_EQUAL(5u, s.rfind("ca"));
        EXPECT_EQUAL(0u, s.find("ab"));
        EXPECT_EQUAL(3u, s.rfind("ab"));
    }
    {
        stringref s("abcabca");
        EXPECT_EQUAL(string::npos, s.find('g'));
        EXPECT_EQUAL(string::npos, s.rfind('g'));
        EXPECT_EQUAL(0u, s.find('a'));
        EXPECT_EQUAL(6u, s.rfind('a'));
        EXPECT_EQUAL(1u, s.find('b'));
        EXPECT_EQUAL(4u, s.rfind('b'));
        EXPECT_EQUAL(2u, s.find("ca"));
        EXPECT_EQUAL(5u, s.rfind("ca"));
        EXPECT_EQUAL(0u, s.find("ab"));
        EXPECT_EQUAL(3u, s.rfind("ab"));
        stringref s2("abc");
        EXPECT_EQUAL(2u, s2.rfind('c'));
        EXPECT_EQUAL(1u, s2.rfind('b'));
        EXPECT_EQUAL(0u, s2.rfind('a'));
        EXPECT_EQUAL(string::npos, s2.rfind('d'));
    }

    EXPECT_EQUAL("a" + stringref("b"), string("ab"));
    EXPECT_EQUAL("a" + string("b"), string("ab"));
    EXPECT_EQUAL(string("a") + string("b"), string("ab"));
    EXPECT_EQUAL(string("a") + stringref("b"), string("ab"));
    EXPECT_EQUAL(string("a") + "b", string("ab"));
    EXPECT_EQUAL(stringref("a") + stringref("b"), string("ab"));

    // Test std::string conversion of empty string
    stringref sref;
    std::string stdString(sref);
    EXPECT_TRUE(strcmp("", sref.data()) == 0);
    stdString = "abc";
    stringref sref2(stdString);
    EXPECT_TRUE(stdString.c_str() == sref2.data());
    EXPECT_TRUE(stdString == sref2);
    EXPECT_TRUE(sref2 == stdString);
    {
        string s;
        s = std::string("cba");
        EXPECT_TRUE("cba" == s);
        s = sref2;
        EXPECT_TRUE("abc" == s);
        string s2;
        s2.swap(s);
        EXPECT_TRUE(s.empty());
        EXPECT_TRUE("abc" == s2);
    }
    {
        EXPECT_EQUAL(string("abc"), string("abcd", 3));
        EXPECT_EQUAL(string("abc"), string(stringref("abc")));
    }
    {
        string s("abc");
        EXPECT_EQUAL(string("a"), s.substr(0,1));
        EXPECT_EQUAL(string("b"), s.substr(1,1));
        EXPECT_EQUAL(string("c"), s.substr(2,1));
        EXPECT_EQUAL(string("abc"), s.substr(0));
        EXPECT_EQUAL(string("bc"), s.substr(1));
        EXPECT_EQUAL(string("c"), s.substr(2));
    }
    {
        stringref s("abc");
        EXPECT_EQUAL(string("a"), s.substr(0,1));
        EXPECT_EQUAL(string("b"), s.substr(1,1));
        EXPECT_EQUAL(string("c"), s.substr(2,1));
        EXPECT_EQUAL(string("abc"), s.substr(0));
        EXPECT_EQUAL(string("bc"), s.substr(1));
        EXPECT_EQUAL(string("c"), s.substr(2));
    }

    {
        string s(" A very long string that is longer than what fits on the stack so that it will be initialized directly on the heap");
        EXPECT_TRUE( ! s.empty());
        EXPECT_TRUE(s.length() > sizeof(s));
    }

    TEST_FLUSH();
}

TEST("require that vespalib::string can append characters (non-standard)") {
    char c = 'x';
    vespalib::string str;
    str.append(c);
    str.append(c);
    str.append(c);
    EXPECT_EQUAL(str, "xxx");
}

TEST("require that vespalib::append_from_reserved gives uninitialized data (non-standard)") {
    vespalib::string str;
    str.reserve(8);
    char *s = &str[0];
    s[0] = 'x';
    s[1] = 'x';
    s[2] = 'x';
    str.append_from_reserved(3);
    EXPECT_EQUAL(3u, str.size());
    EXPECT_EQUAL(str, "xxx");
    s[3] = 'y';
    s[4] = 'y';
    s[5] = 'y';
    str.append_from_reserved(3);
    EXPECT_EQUAL(6u, str.size());
    EXPECT_EQUAL(str, "xxxyyy");
}

TEST("require that vespalib::resize works") {
    vespalib::string s("abcdefghijk");
    EXPECT_EQUAL(11u, s.size());
    s.resize(5);
    EXPECT_EQUAL(5u, s.size());
    EXPECT_EQUAL("abcde", s);
    s.resize(7, 'X');
    EXPECT_EQUAL(7u, s.size());
    EXPECT_EQUAL("abcdeXX", s);
    EXPECT_EQUAL(47u, s.capacity());
    s.resize(50, 'Y');
    EXPECT_EQUAL(50u, s.size());
    EXPECT_EQUAL("abcdeXXYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY", s);
}

TEST("require that you can format a number into a vespalib::string easily") {
    vespalib::string str = vespalib::stringify(0);
    EXPECT_EQUAL(str, "0");
    EXPECT_EQUAL(vespalib::stringify(1), "1");
    EXPECT_EQUAL(vespalib::stringify(123), "123");
    EXPECT_EQUAL(vespalib::stringify(123456789), "123456789");
    EXPECT_EQUAL(vespalib::stringify(987654321uLL), "987654321");
    EXPECT_EQUAL(vespalib::stringify(18446744073709551615uLL), "18446744073709551615");
}

TEST("require that contains works") {
    vespalib::string s("require that contains works");
    EXPECT_TRUE(contains(s, "require"));
    EXPECT_TRUE(contains(s, "require that contains work"));
    EXPECT_TRUE(contains(s, "require that contains works"));
    EXPECT_TRUE(contains(s, "equire"));
    EXPECT_TRUE(contains(s, "ks"));
    EXPECT_FALSE(contains(s, "not in there"));
}

TEST("require that starts_with works") {
    vespalib::string s("require that starts_with works");
    EXPECT_TRUE(starts_with(s, "require"));
    EXPECT_TRUE(starts_with(s, "require that starts_with work"));
    EXPECT_TRUE(starts_with(s, "require that starts_with works"));
    EXPECT_FALSE(starts_with(s, "equire"));
    EXPECT_FALSE(starts_with(s, "not in there"));
}

TEST("require that ends_with works") {
    vespalib::string s("require that ends_with works");
    EXPECT_FALSE(ends_with(s, "require"));
    EXPECT_TRUE(ends_with(s, "works"));
    EXPECT_TRUE(ends_with(s, "equire that ends_with works"));
    EXPECT_TRUE(ends_with(s, "require that ends_with works"));
    EXPECT_FALSE(ends_with(s, "work"));
    EXPECT_FALSE(ends_with(s, "not in there"));
}

TEST("test that small_string::pop_back works") {
    vespalib::string s("string");
    EXPECT_EQUAL(s.size(), 6u);
    s.pop_back();
    EXPECT_EQUAL(s.size(), 5u);
    EXPECT_EQUAL(s, string("strin"));
    EXPECT_NOT_EQUAL(s, string("string"));
    s.pop_back();
    EXPECT_EQUAL(s, string("stri"));
}


TEST("test that operator<() works with stringref versus string") {
    vespalib::stringref sra("a");
    vespalib::string sa("a");
    vespalib::stringref srb("b");
    vespalib::string sb("b");
    EXPECT_FALSE(sra < sra);
    EXPECT_FALSE(sra < sa);
    EXPECT_TRUE(sra < srb);
    EXPECT_TRUE(sra < sb);
    EXPECT_FALSE(sa < sra);
    EXPECT_FALSE(sa < sa);
    EXPECT_TRUE(sa < srb);
    EXPECT_TRUE(sa < sb);
    EXPECT_FALSE(srb < sra);
    EXPECT_FALSE(srb < sa);
    EXPECT_FALSE(srb < srb);
    EXPECT_FALSE(srb < sb);
    EXPECT_FALSE(sb < sra);
    EXPECT_FALSE(sb < sa);
    EXPECT_FALSE(sb < srb);
    EXPECT_FALSE(sb < sb);
}

TEST("test that empty_string is shared and empty") {
    EXPECT_TRUE(&empty_string() == &empty_string());
    EXPECT_EQUAL(empty_string(), "");
}

TEST("starts_with has expected semantics for small_string") {
    vespalib::string a("foobar");
    EXPECT_TRUE(a.starts_with(""));
    EXPECT_TRUE(a.starts_with("foo"));
    EXPECT_TRUE(a.starts_with("foobar"));
    EXPECT_FALSE(a.starts_with("foobarf"));
    EXPECT_FALSE(a.starts_with("oobar"));
}

TEST("starts_with has expected semantics for stringref") {
    vespalib::string a("foobar");
    vespalib::stringref ar(a);
    EXPECT_TRUE(ar.starts_with(""));
    EXPECT_TRUE(ar.starts_with("foo"));
    EXPECT_TRUE(ar.starts_with("foobar"));
    EXPECT_FALSE(ar.starts_with("foobarf"));
    EXPECT_FALSE(ar.starts_with("oobar"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
