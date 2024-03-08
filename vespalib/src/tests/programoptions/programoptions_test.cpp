// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "programoptions_testutils.h"
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

namespace vespalib {

struct MyOptions : public ProgramOptions {
    bool boolOpt;
    bool boolWithDefOpt;
    int intOpt;
    uint32_t uintOpt;
    float floatOpt;
    std::string stringOpt;
    std::string argString;
    int argInt;
    std::string argOptionalString;
    std::map<std::string, std::string> properties;
    int anotherOptionalArg;

    MyOptions(int argc, const char *const *argv);
    ~MyOptions();
};

MyOptions::MyOptions(int argc, const char* const* argv)
    : ProgramOptions(argc, argv)
{
        // Required options
    addOption("uintopt u", uintOpt, "Sets an unsigned int");
        // Optional options
    addOption("b bool", boolOpt, "Enables a flag");
    addOption("boolwithdef", boolWithDefOpt, true, "If set turns to false");

    addOption("intopt i", intOpt, 5, "Sets a signed int");
    addOption("floatopt", floatOpt, 4.0f, "Sets a float\nMultiline baby");
    addOption("string s", stringOpt, std::string("ballalaika"),
              "Sets a string value. This is a very long description that "
              "should be broken down into multiple lines in some sensible "
              "way.");
    addOptionHeader("Advanced options");
    addOption("p properties", properties, "Property map");
    addHiddenIdentifiers("prop");
    setArgumentTypeName("key");
    setArgumentTypeName("value", 1);

    addArgument("argString", argString, "Required string argument.");
    addArgument("argInt", argInt, "Required int argument.");
    addArgument("argOptionalString", argOptionalString, std::string("foo"),
                "Optional string argument with a long description so we "
                "can see that it will be broken correctly.");
    addArgument("argSecondOptional", anotherOptionalArg, 3,
                "Yet another optional argument");

    setSyntaxMessage("A test program to see if this utility works.");
    setSyntaxPageMaxLeftColumnSize(25);
}

MyOptions::~MyOptions() { }

TEST(ProgramOptionsTest, test_syntax_page)
{
    AppOptions opts("myapp");
    MyOptions options(opts.getArgCount(), opts.getArguments());
    std::ostringstream actual;
    options.writeSyntaxPage(actual);

    std::string expected(
"\nA test program to see if this utility works.\n\n"
"Usage: myapp [options] <argString> <argInt> [argOptionalString] [argSecondOptional]\n\n"
"Arguments:\n"
" argString (string)      : Required string argument.\n"
" argInt (int)            : Required int argument.\n"
" argOptionalString (string)\n"
"                         : Optional string argument with a long description so\n"
"                           we can see that it will be broken correctly.\n"
"                           (optional)\n"
" argSecondOptional (int) : Yet another optional argument (optional)\n\n"
"Options:\n"
" --uintopt -u <uint>  : Sets an unsigned int (required)\n"
" -b --bool            : Enables a flag\n"
" --boolwithdef        : If set turns to false\n"
" --intopt -i <int>    : Sets a signed int (default 5)\n"
" --floatopt <float>   : Sets a float\n"
"                        Multiline baby (default 4)\n"
" --string -s <string> : Sets a string value. This is a very long description\n"
"                        that should be broken down into multiple lines in some\n"
"                        sensible way. (default \"ballalaika\")\n\n"
"Advanced options:\n"
" -p --properties <key> <value> : Property map (default empty)\n"
    );
    EXPECT_EQ(expected, actual.str());
}

TEST(ProgramOptionsTest, test_normal_usage)
{
    {
        AppOptions opts("myapp -b --uintopt 4 -s foo tit 1 tei 6");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        options.parse();
        EXPECT_EQ(true, options.boolOpt);
        EXPECT_EQ(true, options.boolWithDefOpt);
        EXPECT_EQ(5, options.intOpt);
        EXPECT_EQ(4u, options.uintOpt);
        EXPECT_NEAR(4, options.floatOpt, 0.00001);
        EXPECT_EQ("foo", options.stringOpt);
        EXPECT_EQ("tit", options.argString);
        EXPECT_EQ(1, options.argInt);
        EXPECT_EQ("tei", options.argOptionalString);
        EXPECT_EQ(0u, options.properties.size());
        EXPECT_EQ(6, options.anotherOptionalArg);
    }
    {
        AppOptions opts("myapp --uintopt 6 tit 1");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        options.parse();
        EXPECT_EQ(false, options.boolOpt);
        EXPECT_EQ(true, options.boolWithDefOpt);
        EXPECT_EQ(5, options.intOpt);
        EXPECT_EQ(6u, options.uintOpt);
        EXPECT_NEAR(4, options.floatOpt, 0.00001);
        EXPECT_EQ("ballalaika", options.stringOpt);
        EXPECT_EQ("tit", options.argString);
        EXPECT_EQ(1, options.argInt);
        EXPECT_EQ("foo", options.argOptionalString);
        EXPECT_EQ(0u, options.properties.size());
        EXPECT_EQ(3, options.anotherOptionalArg);
    }
        // Arguments coming after options.
        // (Required for nesting of short options)
    {
        AppOptions opts("myapp --uintopt --intopt 6 -8 tit 1 tei");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        options.parse();
        EXPECT_EQ(false, options.boolOpt);
        EXPECT_EQ(true, options.boolWithDefOpt);
        EXPECT_EQ(-8, options.intOpt);
        EXPECT_EQ(6u, options.uintOpt);
        EXPECT_NEAR(4, options.floatOpt, 0.00001);
        EXPECT_EQ("ballalaika", options.stringOpt);
        EXPECT_EQ("tit", options.argString);
        EXPECT_EQ(1, options.argInt);
        EXPECT_EQ("tei", options.argOptionalString);
        EXPECT_EQ(0u, options.properties.size());
    }
    {
        AppOptions opts( "myapp -uib 6 -8 --boolwithdef tit 1 tei");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        options.parse();
        EXPECT_EQ(true, options.boolOpt);
        EXPECT_EQ(false, options.boolWithDefOpt);
        EXPECT_EQ(-8, options.intOpt);
        EXPECT_EQ(6u, options.uintOpt);
        EXPECT_NEAR(4, options.floatOpt, 0.00001);
        EXPECT_EQ("ballalaika", options.stringOpt);
        EXPECT_EQ("tit", options.argString);
        EXPECT_EQ(1, options.argInt);
        EXPECT_EQ("tei", options.argOptionalString);
        EXPECT_EQ(0u, options.properties.size());
    }
        // Properties
    {
        AppOptions opts("myapp -u 6 -p foo bar --prop hmm brr tit 1 tei");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        options.parse();
        EXPECT_EQ(false, options.boolOpt);
        EXPECT_EQ(true, options.boolWithDefOpt);
        EXPECT_EQ(5, options.intOpt);
        EXPECT_EQ(6u, options.uintOpt);
        EXPECT_NEAR(4, options.floatOpt, 0.00001);
        EXPECT_EQ("ballalaika", options.stringOpt);
        EXPECT_EQ("tit", options.argString);
        EXPECT_EQ(1, options.argInt);
        EXPECT_EQ("tei", options.argOptionalString);
        EXPECT_EQ(2u, options.properties.size());
        EXPECT_EQ("bar", options.properties["foo"]);
        EXPECT_EQ("brr", options.properties["hmm"]);
    }
}

TEST(ProgramOptionsTest, test_failures)
{
        // Non-existing long option
    {
        AppOptions opts("myapp -b --uintopt 4 -s foo --none");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("Invalid option 'none'.", e.getMessage());
        }
    }
        // Non-existing short option
    {
        AppOptions opts("myapp -b --uintopt 4 -s foo -q");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("Invalid option 'q'.", e.getMessage());
        }
    }
        // Lacking option argument
    {
        AppOptions opts("myapp -b --uintopt 4 -s");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("Option 's' needs 1 arguments. Only 0 available.",
                       e.getMessage());
        }
    }
        // Out of signed ranged
    {
        AppOptions opts("myapp -b --uintopt 4 -intopt 3000000000");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("The argument '3000000000' can not be interpreted as a "
                       "number of type int.", e.getMessage());
        }
    }
        // Lacking required option
    {
        AppOptions opts("myapp -b");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("Option 'uintopt' has no default and must be set.",
                       e.getMessage());
        }
    }
        // Lacking required argument
    {
        AppOptions opts("myapp --uintopt 1 tit");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("Insufficient data is given to set required argument "
                       "'argInt'.",
                       e.getMessage());
        }
    }
        // Argument of wrong type
    {
        AppOptions opts("myapp --uintopt 1 tit en");
        MyOptions options(opts.getArgCount(), opts.getArguments());
        try{
            options.parse();
            FAIL() << "Expected exception";
        } catch (InvalidCommandLineArgumentsException& e) {
            EXPECT_EQ("The argument 'en' can not be interpreted as a number "
                       "of type int.",
                       e.getMessage());
        }
    }
}

TEST(ProgramOptionsTest, test_vector_argument)
{
    AppOptions opts("myapp foo bar baz");
    std::vector<std::string> args;
    ProgramOptions options(opts.getArgCount(), opts.getArguments());
    options.addListArgument("ids", args, "Vector element");
    std::ostringstream actual;
    options.writeSyntaxPage(actual);
    std::string expected(
"\nUsage: myapp [ids...]\n\n"
"Arguments:\n"
" ids (string[]) : Vector element\n"
    );
    EXPECT_EQ(expected, actual.str());

    options.parse();
    EXPECT_EQ(3u, args.size());
    EXPECT_EQ("foo", args[0]);
    EXPECT_EQ("bar", args[1]);
    EXPECT_EQ("baz", args[2]);
}

TEST(ProgramOptionsTest, test_all_hidden_options)
{
    AppOptions opts("myapp --foo bar");
    std::string option;
    ProgramOptions options(opts.getArgCount(), opts.getArguments());
    options.addOption("", option, "Description");
    options.addHiddenIdentifiers("foo");
    std::ostringstream actual;
    options.writeSyntaxPage(actual);
    std::string expected("\nUsage: myapp\n");
    EXPECT_EQ(expected, actual.str());

    options.parse();
    EXPECT_EQ("bar", option);
}

} // vespalib

GTEST_MAIN_RUN_ALL_TESTS()
