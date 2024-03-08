// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/asciistream.h>

struct Foo : public vespalib::Printable {
    int val;
    std::string other;

    Foo(int v, std::string o) : val(v), other(o) {}

    void print(std::ostream& out, bool verbose = false,
               const std::string& indent = "") const override
    {
        out << "Foo(val = " << val;
        if (verbose) {
            out << ", other:\n" << indent << "  " << other;
        } else {
            out << ", other size " << other.size();
        }
        out << ")";
    }
};

struct Bar : public Foo {
    int i;

    Bar(int j, int v, std::string o) : Foo(v, o), i(j) {}

    void print(std::ostream& out, bool verbose = false,
               const std::string& indent = "") const override
    {
        out << "Bar(" << i << ")";
        if (verbose) {
            out << " : ";
            Foo::print(out, verbose, indent + "  ");
        }
    }
};

struct AsciiFoo : public vespalib::AsciiPrintable {
    int val;

    AsciiFoo(int v) : val(v) {}

    void print(vespalib::asciistream& out,
               const PrintProperties& p) const override
    {
        if (p.verbose()) {
            out << "AsciiFoo(" << val << ")";
        } else {
            out << val;
        }
    }
};

struct AsciiBar : public vespalib::AsciiPrintable {
    AsciiFoo _foo;

    AsciiBar(int v) : _foo(v) {}

    void print(vespalib::asciistream& out,
               const PrintProperties& p) const override
    {
        if (p.verbose()) {
            out << "AsciiBar() {"
                << "\n" << p.indent(1);
            _foo.print(out, p.indentedCopy());
            out << "\n" << p.indent() << "}";
        } else {
            out << _foo;
        }
    }
};

TEST(PrintableTest, test_simple)
{
    Foo foo(3, "myval");
    Bar bar(7, 3, "otherval");

    EXPECT_EQ("Foo(val = 3, other size 5)", foo.toString());
    EXPECT_EQ("Foo(val = 3, other size 5)", foo.toString(false, "  "));
    EXPECT_EQ("Foo(val = 3, other:\n"
              "  myval)", foo.toString(true));
    EXPECT_EQ("Foo(val = 3, other:\n"
              "    myval)", foo.toString(true, "  "));

    std::ostringstream ost;
    ost << foo;
    EXPECT_EQ("Foo(val = 3, other size 5)", ost.str());

    EXPECT_EQ("Bar(7)", bar.toString());
    EXPECT_EQ("Bar(7)", bar.toString(false, "  "));
    EXPECT_EQ("Bar(7) : Foo(val = 3, other:\n"
              "    otherval)", bar.toString(true));
    EXPECT_EQ("Bar(7) : Foo(val = 3, other:\n"
              "      otherval)", bar.toString(true, "  "));
}

TEST(PrintableTest, test_ascii_variant)
{
    AsciiFoo foo(19);

    EXPECT_EQ("19", foo.toString());
    EXPECT_EQ("AsciiFoo(19)",
              foo.toString(vespalib::AsciiPrintable::VERBOSE));
    {
        vespalib::asciistream as;
        as << foo;
        EXPECT_EQ("19", as.str());

        std::ostringstream ost;
        ost << foo;
        EXPECT_EQ("19", ost.str());
    }

    AsciiBar bar(3);
    EXPECT_EQ("3", bar.toString());
    EXPECT_EQ("AsciiBar() {\n"
              "  AsciiFoo(3)\n"
              "}", bar.toString(vespalib::AsciiPrintable::VERBOSE));
    {
        vespalib::asciistream as;
        as << bar;
        EXPECT_EQ("3", as.str());

        std::ostringstream ost;
        ost << bar;
        EXPECT_EQ("3", ost.str());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
