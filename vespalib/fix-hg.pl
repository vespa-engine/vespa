#!/usr/bin/perl
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

foreach $arg ( @ARGV ) {
	$hgd = $arg;          # maybe: . "_";
	$hgd =~ s{\W}{_}g;
	$hgd =~ tr{a-z}{A-Z};
	$hgd =~ s{^_*}{H_};

# print "arg $arg header guard $hgd\n";

 	open(FOO, $arg) or die "Cannot open '$arg'\n";
	$backup = $arg . ".orig";
	rename ($arg, $backup);

        open(ARGVOUT, ">$arg") or die "cannot write to '$arg'\n";
        select(ARGVOUT);

	my $eic = 0;
	my $cnt = 1;

        while (<FOO>) {
		++$eic if m{#endif} ;
	}
	seek FOO, 0, 0;
        while (<FOO>) {
 		if ($cnt == 1 and m{^#ifndef}) {
			s{\s.*}{ $hgd};
			++$cnt;
		}
 		if ($cnt == 2 and m{^#define}) {
			s{\s.*}{ $hgd};
			++$cnt;
		}
		if ( m{#endif} ) {
			--$eic;
			if ($eic == 0) {
				s{.*#endif.*}{#endif // header guard};
			}
		}
		print;
	}
	close(FOO);
        select(STDOUT);
	close(ARGVOUT);

	unlink($backup);
}

exit 0;
