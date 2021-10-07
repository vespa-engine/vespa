#!/usr/bin/env perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This script generates multi-argument functions for sending some events.

require 5.006_001;
use warnings;
use strict;

die "Usage: $0 <event-name> <event-version> <number of functions> <name>...\n"
  unless $#ARGV > 2;

my $event = $ARGV[0];
my $event_version = $ARGV[1];
my $noof_fns = $ARGV[2];

my @variables = @ARGV;
splice(@variables, 0, 3);


open(H, ">event-${event}-multiarg.h")
  or die "Cannot open event-${event}-multiarg.h: $!\n";
open(CPP, ">event-${event}-multiarg.cpp")
  or die "Cannot open event-${event}-multiarg.cpp: $!\n";

my $called_as = "$0 $ARGV[0] $ARGV[1] $ARGV[2]";
foreach my $x (@variables) {
  $called_as .= " \"$x\"";
}

print H << "EOF";
// This file was generated like this:
// $called_as
// Don not modify this file manually!
EOF

print CPP << "EOF";
// This file was generated like this:
// $called_as
// Do not modify this file manually!
EOF


my $i;

for ($i = 2; $i <= $noof_fns; $i++) {
  print H "void doEvent\u${event}(";
  print CPP "void\nLogger::doEvent\u${event}(";
  my $n;
  for ($n = 1; $n <= $i; $n++) {
    print H "," unless $n == 1;
    print H "\n\t";
    print CPP "," unless $n == 1;
    print CPP "\n\t";
    my $first = 1;
    foreach my $var (@variables) {
      print H ", " unless $first;
      print H "${var}${n}";
      print CPP ", " unless $first;
      print CPP "${var}${n}";
      $first = 0;
    }
  }
  print H ");\n\n";
  print CPP ")\n{\n";

  print CPP "\tdoLog(event, \"\", 0, \"${event}/${event_version}\"";
  for ($n = 1; $n <= $i; $n++) {
    foreach my $var (@variables) {
      my $type;
      my $quot = "";
      if ($var =~ m=double =) {
	$type = "%lf";
      } elsif ($var =~ m=int =) {
	$type = "%d";
      } elsif ($var =~ m=const char ?\*=) {
	$type = "%s";
	$quot = "\\\"";
      } else {
	die "Don't know printf format for variable $var\n";
      }
      my $name = $var;
      $name =~ s=.*[ *&]==;
      print CPP "\n\t\t\" ${name}=${quot}${type}${quot}\"";
    }
  }
  print CPP ",";
  for ($n = 1; $n <= $i; $n++) {
    my $first = 1;
    print CPP "," unless $n == 1;
    print CPP "\n\t\t";
    foreach my $var (@variables) {
      print CPP ", " unless $first;
      $first = 0;
      my $name = $var;
      $name =~ s=.*[ *&]==;
      print CPP "${name}${n}";
    }
  }

  print CPP ");\n";

  print CPP "}\n\n";
}
