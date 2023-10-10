#!/usr/bin/env perl
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# input looks like:
# lowercase( 65 )= 97

my %lowercase;

my %blocks;

while (<>) {
	my ( $key, $value ) = m{lowercase. (\d+) .. (\d+)$} ;
	$lowercase{$key} = $value;

	my $b = ( $key >> 8 );
	++$blocks{$b};
}

@kl = keys %blocks;
@nkl = sort {$a <=> $b} @kl;

print STDERR "blocks: " . join(" ", @nkl) . "\n";


foreach $b ( @nkl )
{
        if ( $b == 0 ) {
	    print "unsigned char\nLowerCase::lowercase_${b}_block[256] = {\n\t";
        } else {
	    print "\nuint32_t\nLowerCase::lowercase_${b}_block[256] = {\n\t";
        }
        my $act = 0;
	for ($i = 0; $i < 256; $i++) {
		if ($act > 7) {
			print ",\n\t";
			$act = 0;
		} elsif ($act) {
			print ",";
		}
		$n = ($b << 8) + $i;
		$v = $lowercase{$n};
		if (defined $v) {
			print "\t$v";
			die "too big value in table 0: $v\n" if ($v > 255 && $b == 0);
		} else {
			print "\t$n";
		}
		++$act;
	}
	print "\n\t};\n\n";
}

{
        print "\nuint32_t\nLowerCase::lowercase_0_5_block[0x600] = {\n\t";
        my $act = 0;
	for ($i = 0; $i < 0x600; $i++) {
		if ($act > 7) {
			print ",\n\t";
			$act = 0;
		} elsif ($act) {
			print ",";
		}
		$n = $i;
		$v = $lowercase{$n};
		if (defined $v) {
			print "\t$v";
		} else {
			print "\t$n";
		}
		++$act;
	}
	print "\n\t};\n\n";
}
