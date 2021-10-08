#!/usr/bin/perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

while ( <STDIN> ) {
    if ( m/^indexfield\[(\d+)\]$/ ) {
	$numfields = $1;
	print "$numfields fields\n";
	next;
    }
    if ( m/^indexfield\[(\d+)\]\.(\w+) (.*)$/ ) {
	$ifield[$1]{$2} = $3;
	next;
    }
    if ( m/^fieldcollection\[(\d+)\]$/ ) {
	$numfc = $1;
    }
    if ( m/^fieldcollection\[(\d+)\]\.name (.*)$/ ) {
	$fieldcoll[$1]{name} = $2;
	next;
    }
    if ( m/^fieldcollection\[(\d+)\]\.field\[\d+\]$/ ) {
#	$fieldcoll[$1]{fields} = [ ];
	next;
    }
    if ( m/^fieldcollection\[(\d+)\]\.field\[\d+\]\.name (.*)$/ ) {
	push(@{$fieldcoll[$1]{fields}}, $2);
	next;
    }
}
for ( $f = 0; $f < $numfields; ++$f) {
    printf "indexfield %s %s %s %s %s %s\n",
    $ifield[$f]{name}, $ifield[$f]{datatype}, $ifield[$f]{collectiontype},
    $ifield[$f]{prefix}, $ifield[$f]{phrases}, $ifield[$f]{positions};
}
for ($fc = 0; $fc < $numfc; ++$fc) {
    printf "fieldcoll %s -> %s\n", $fieldcoll[$fc]{name},
    join(' ', @{$fieldcoll[$fc]{fields}});
} 
