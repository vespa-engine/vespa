# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# That that all perl files use strict and warnings
# 

use Test::More;
use TestUtils::VespaTest;

use strict;
use warnings;

my @dirs = (
    '../bin',
    '../lib',
    'Yahoo/Vespa/Mocks'
);

my $checkdirs = join(' ', @dirs);

my @files = `find $checkdirs -name \\*.pm -or -name \\*.pl`;
chomp @files;

printTest "Checking " . (scalar @files) . " files for includes.\n";

foreach my $file (@files) {
    ok( system("cat $file | grep 'use strict;' >/dev/null") == 0,
        "$file use strict" );
    ok( system("cat $file | grep 'use warnings;' >/dev/null") == 0,
        "$file use warnings" );
}

done_testing();

exit(0);
