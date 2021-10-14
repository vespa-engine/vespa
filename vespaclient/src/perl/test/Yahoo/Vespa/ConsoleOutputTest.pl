# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;

BEGIN { use_ok( 'Yahoo::Vespa::ConsoleOutput' ); }
require_ok( 'Yahoo::Vespa::ConsoleOutput' );

ok( Yahoo::Vespa::ConsoleOutput::getVerbosity() == 3,
    'Default verbosity is 3' );
ok( Yahoo::Vespa::ConsoleOutput::usingAnsiColors(),
    'Using ansi colors by default' );

use TestUtils::VespaTest;

printSpam "test\n";
isOutput('', '', "No spam at level 3");

printDebug "test\n";
isOutput('', '', "No spam at level 3");

printInfo "info test\n";
isOutput("info test\n", '', "Info at level 3");

printWarning "foo\n";
isOutput("", "\e[93mfoo\e[0m\n", "Stderr output for warning");

useColors(0);
printWarning "foo\n";
isOutput("", "foo\n", "Stderr output without ansi colors");

Yahoo::Vespa::ConsoleOutput::setVerbosity(4);
printSpam "test\n";
isOutput('', '', "No spam at level 4");

printDebug "test\n";
isOutput("debug: test\n", '', "Debug at level 4");

Yahoo::Vespa::ConsoleOutput::setVerbosity(5);
printSpam "test\n";
isOutput("spam: test\n", '', "Spam at level 5");

printInfo "info test\n";
isOutput("info: info test\n", '', "Type prefix at high verbosity");

done_testing();

exit(0);
