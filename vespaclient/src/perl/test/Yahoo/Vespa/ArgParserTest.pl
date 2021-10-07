# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;

BEGIN { use_ok( 'Yahoo::Vespa::ArgParser' ); }
require_ok( 'Yahoo::Vespa::ArgParser' );

BEGIN { *ArgParser:: = *Yahoo::Vespa::ArgParser:: }

use TestUtils::OutputCapturer;

TestUtils::OutputCapturer::useColors(1);

&testSyntaxPage();

TestUtils::OutputCapturer::useColors(0);

&testStringOption();
&testIntegerOption();
&testHostOption();
&testPortOption();
&testFlagOption();
&testCountOption();
&testComplexParsing();
&testArguments();

done_testing();

exit(0);

sub testSyntaxPage {
        # Empty
    ArgParser::writeSyntaxPage();
    my $expected = <<EOS;
Usage: ArgParserTest.pl
EOS
    isOutput($expected, '', 'Empty syntax page');

        # Built in only
    Yahoo::Vespa::ArgParser::registerInternalParameters();
    ArgParser::writeSyntaxPage();
    $expected = <<EOS;
Usage: ArgParserTest.pl [Options]

Options:
 -h --help     : Show this help page.
 -v            : Create more verbose output.
 -s            : Create less verbose output.
 --show-hidden : Also show hidden undocumented debug options.
EOS
    isOutput($expected, '', 'Syntax page with default args');

        # Actual example
    ArgParser::initialize();

    setProgramBinaryName("testprog");
    setProgramDescription(
            "This is a multiline description of what the program is that "
          . "should be split accordingly to look nice. For now probably hard "
          . "coded, but can later be extended to detect terminal width.");
    my $arg;
    setArgument(\$arg, "Test Arg", "This argument is not used for anything.",
                OPTION_REQUIRED);
    my $optionalArg;
    setArgument(\$arg, "Another Test Arg",
                "This argument is not used for anything either.");

    setOptionHeader("My prog headers. Also a long line just to check that it "
                  . "is also split accordingly.");
    my $stringval;
    my $flag;
    my $intval;
    setStringOption(['string', 'j'], \$stringval, "A random string");
    setFlagOption(['flag', 'f'], \$flag, "A flag option with a pretty long "
            . "description that might need to be split into multiple lines.");
    setOptionHeader("More options");
    setIntegerOption(['integer', 'i'], \$intval, "A secret integer option.",
                     OPTION_SECRET);
    Yahoo::Vespa::ArgParser::registerInternalParameters();
    ArgParser::writeSyntaxPage();
    $expected = <<EOS;
This is a multiline description of what the program is that should be split
accordingly to look nice. For now probably hard coded, but can later be
extended to detect terminal width.

Usage: testprog [Options] <Test Arg> [Another Test Arg]

Arguments:
 Test Arg         : This argument is not used for anything.
 Another Test Arg : This argument is not used for anything either.

Options:
 -h --help     : Show this help page.
 -v            : Create more verbose output.
 -s            : Create less verbose output.
 --show-hidden : Also show hidden undocumented debug options.

My prog headers. Also a long line just to check that it is also split
accordingly.
 --string -j   : A random string
 --flag -f     : A flag option with a pretty long description that might need
                 to be split into multiple lines.
EOS
    isOutput($expected, '', 'Actual syntax page example');

    ArgParser::setShowHidden(1);
    ArgParser::writeSyntaxPage();
    $expected = <<EOS;
This is a multiline description of what the program is that should be split
accordingly to look nice. For now probably hard coded, but can later be
extended to detect terminal width.

Usage: testprog [Options] <Test Arg> [Another Test Arg]

Arguments:
 Test Arg         : This argument is not used for anything.
 Another Test Arg : This argument is not used for anything either.

Options:
 -h --help     : Show this help page.
 -v            : Create more verbose output.
 -s            : Create less verbose output.
 --show-hidden : Also show hidden undocumented debug options.

My prog headers. Also a long line just to check that it is also split
accordingly.
 --string -j   : A random string
 --flag -f     : A flag option with a pretty long description that might need
                 to be split into multiple lines.

More options
 --integer -i  : A secret integer option.

 --nocolors    : Do not use ansi colors in print.
EOS
    isOutput($expected, '', 'Actual syntax page example with hidden');
}

sub setUpParseTest {
    Yahoo::Vespa::ArgParser::initialize();
}

sub parseFail {
    my ($optstring, $expectedError) = @_;
    my @args = split(/\s+/, $optstring);
    my $name = $expectedError;
    chomp $name;
    if (length $name > 40 && $name =~ /^(.{20,70}?)\./) {
        $name = $1;
    } elsif (length $name > 55 && $name =~ /^(.{40,55})\s/) {
        $name = $1;
    }
    ok( !ArgParser::parseCommandLineArguments(\@args),
        "Expected parse failure: $name");
    isOutput('', $expectedError, $name);
}

sub parseSuccess {
    my ($optstring, $testname) = @_;
    my @args = split(/\s+/, $optstring);
    ok( ArgParser::parseCommandLineArguments(\@args),
        "Expected parse success: $testname");
    isOutput('', '', $testname);
}

sub testStringOption {
    &setUpParseTest();
    my $val;
    setStringOption(['s'], \$val, 'foo');
    parseFail("-s", "Too few arguments for option 's'\.\n");
    ok( !defined $val, 'String value unset on failure' );
    parseSuccess("-s foo", "String option");
    ok( $val eq 'foo', "String value set" );
}

sub testIntegerOption {
    &setUpParseTest();
    my $val;
    setIntegerOption(['i'], \$val, 'foo');
    parseFail("-i", "Too few arguments for option 'i'\.\n");
    ok( !defined $val, 'Integer value unset on failure' );
    parseFail("-i foo", "Invalid value 'foo' given to integer option 'i'\.\n");
    parseFail("-i 0.5", "Invalid value '0.5' given to integer option 'i'\.\n");
    parseSuccess("-i 5", "Integer option");
    ok( $val == 5, "Integer value set" );
        # Don't allow numbers as first char in id, so this can be detected as
        # argument for integer.
    parseSuccess("-i -8", "Negative integer option");
    ok( $val == -8, "Integer value set" );
        # Test big numbers
    parseSuccess("-i 8000000000", "Big integer option");
    ok( $val / 1000000 == 8000, "Integer value set" );
    parseSuccess("-i -8000000000", "Big negative integer option");
    ok( $val / 1000000 == -8000, "Integer value set" );
}

sub testHostOption {
    &setUpParseTest();
    my $val;
    setHostOption(['h'], \$val, 'foo');
    parseFail("-h", "Too few arguments for option 'h'\.\n");
    ok( !defined $val, 'Host value unset on failure' );
    parseFail("-h 5", "Invalid host '5' given to option 'h'\. Not a valid host\n");
    parseFail("-h non.existing.host.no", "Invalid host 'non.existing.host.no' given to option 'h'\. Not a valid host\n");
    parseSuccess("-h localhost", "Host option set");
    is( $val, 'localhost', 'Host value set' );
}

sub testPortOption {
    &setUpParseTest();
    my $val;
    setPortOption(['p'], \$val, 'foo');
    parseFail("-p", "Too few arguments for option 'p'\.\n");
    ok( !defined $val, 'Host value unset on failure' );
    parseFail("-p -1", "Invalid value '-1' given to port option 'p'\. Must be an unsigned 16 bit\ninteger\.\n");
    parseFail("-p 65536", "Invalid value '65536' given to port option 'p'\. Must be an unsigned 16 bit\ninteger\.\n");
    parseSuccess("-p 65535", "Port option set");
    is( $val, 65535, 'Port value set' );
}

sub testFlagOption {
    &setUpParseTest();
    my $val;
    setFlagOption(['f'], \$val, 'foo');
    setFlagOption(['g'], \$val2, 'foo', OPTION_INVERTEDFLAG);
    parseFail("-f 3", "Unhandled argument '3'\.\n");
    parseSuccess("-f", "First flag option set");
    is( $val, 1, 'Flag value set' );
    is( $val2, 1, 'Flag value set' );
    parseSuccess("-f", "First flag option reset");
    is( $val, 1, 'Flag value set' );
    is( $val2, 1, 'Flag value set' );
    parseSuccess("-g", "Second flag option set");
    is( $val, 0, 'Flag value set' );
    is( $val2, 0, 'Flag value set' );
    parseSuccess("-fg", "Both flag options set");
    is( $val, 1, 'Flag value set' );
    is( $val2, 0, 'Flag value set' );
}

sub testCountOption {
    &setUpParseTest();
    my $val;
    setUpCountingOption(['u'], \$val, 'foo');
    setDownCountingOption(['d'], \$val, 'foo');
    parseSuccess("", "Count not set");
    ok( !defined $val, 'Count value not set if not specified' );
    parseSuccess("-u", "Counting undefined");
    is( $val, 1, 'Count value set' );
    parseSuccess("-d", "Counting undefined - down");
    is( $val, -1, 'Count value set' );
    parseSuccess("-uuuud", "Counting both ways");
    is( $val, 3, 'Count value set' );
}

sub testComplexParsing {
    &setUpParseTest();
    my $count;
    my $int;
    my $string;
    setUpCountingOption(['u', 'up'], \$count, 'foo');
    setIntegerOption(['i', 'integer'], \$int, 'bar');
    setStringOption(['s', 'string'], \$string, 'baz');
    parseSuccess("-uis 3 foo", "Complex parsing managed");
    is( $count, 1, 'count counted' );
    is( $int, 3, 'integer set' );
    is( $string, 'foo', 'string set' );
    parseSuccess("-uiusi 3 foo 5", "Complex parsing managed 2");
    is( $count, 2, 'count counted' );
    is( $int, 5, 'integer set' );
    is( $string, 'foo', 'string set' );
    parseSuccess("-s -i foo -u 3", "Complex parsing managed 3");
    is( $count, 1, 'count counted' );
    is( $int, 3, 'integer set' );
    is( $string, 'foo', 'string set' );
}

sub testArguments {
    &testOptionalArgument();
    &testRequiredArgument();
    &testRequiredArgumentAfterOptional();
}

sub testOptionalArgument {
    &setUpParseTest();
    my $val;
    setArgument(\$val, "Name", "Description");
    parseSuccess("", "Unset optional argument");
    ok( !defined $val, "Argument unset if not specified" );
    parseSuccess("myval", "Optional argument set");
    is( $val, 'myval', 'Optional argument set to correct value' );
}

sub testRequiredArgument {
    &setUpParseTest();
    my $val;
    setArgument(\$val, "Name", "Description", OPTION_REQUIRED);
    parseFail("", "Argument Name is required but not specified\.\n");
    ok( !defined $val, "Argument unset on failure" );
    parseSuccess("myval", "Required argument set");
    is( $val, 'myval', 'Required argument set to correct value' );
}

sub testRequiredArgumentAfterOptional {
    &setUpParseTest();
    my ($val, $val2);
    setArgument(\$val, "Name", "Description");
    eval {
        setArgument(\$val2, "Name2", "Description2", OPTION_REQUIRED);
    };
    like( $@, qr/Cannot add required argument after optional/,
          'Fails adding required arg after optional' );
}
