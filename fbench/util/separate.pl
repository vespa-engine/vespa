#!/usr/bin/perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

$sepcol = shift;

if ($sepcol eq "") {
  die qq{usage: separate.pl <sepcol>
       Separate a tabular numeric file into chunks using a blank
       line whenever the value in column 'sepcol' changes.
};
}

$oldval = -2;
$newval = -2;

while (<>) {
  if (/^#/) {
    print;
  } else {
    chomp;
    @vals = split;
    $newval = $vals[$sepcol];
    if ($newval != $oldval) {
      print "\n";
      $oldval = $newval;
    }
    print "@vals\n";
  }
}
