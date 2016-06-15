#!/usr/bin/perl
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This is the config pre-processor.
# It handles import statements, and does syntax checking etc.
# The idea is that it will be called directly from the script
# that does the code generation.
#
# Errors and warnings are printed in "next-error" compatible ways
# for emacs etc.
#
# Indented like this:
#  (cperl-set-style "Whitesmith")
#  (setq cperl-continued-brace-offset -4)

require 5.006_001;
use strict;
use warnings;
use Digest::MD5;

use Math::BigInt;
use Math::BigFloat;

die "Usage: $0 <def-file>" unless $#ARGV == 0;

my $defname = $ARGV[0];

my $md5 = Digest::MD5->new;

my @c_keywords =
  ("asm", "auto", "bool", "break", "case", "catch",
   "char", "class", "const", "const_cast", "continue", "default",
   "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit",
   "export", "extern", "false", "float", "for", "friend", "goto", "if",
   "inline", "int", "long", "mutable", "namespace", "new", "operator",
   "private", "protected", "public", "register", "reinterpret_cast",
   "return", "short", "signed", "sizeof", "static", "static_cast",
   "struct", "switch", "template", "this", "throw", "true", "try",
   "typedef", "typeid", "typename", "union", "unsigned",
   "using", "virtual", "void", "volatile", "wchar_t", "while", "and", "bitor",
   "not", "or", "xor", "and_eq", "compl", "not_eq", "or_eq", "xor_eq",
   "bitand");


my @java_keywords =
  ("abstract", "boolean", "break", "byte", "case",
   "catch", "char", "class","continue", "default", "do", "double",
   "else", "extends","false", "final", "finally", "float", "for",
   "if","implements", "import", "instanceof", "int", "interface",
   "long","native", "new", "null", "package", "private",
   "protected","public", "return", "short", "static",
   "strictfp","super","switch", "synchronized", "this",
   "throw","throws","transient", "true", "try", "void",
   "volatile","while", "byvalue", "cast", "const", "future",
   "generic","goto", "inner", "operator", "outer", "rest", "var");

my %reserved_words;

foreach my $word (@c_keywords) {
  $reserved_words{$word} = "C";
}

foreach my $word (@java_keywords) {
  my $x = $reserved_words{$word};
  if (defined($x)) {
    $x = "$x, Java";
  } else {
    $x = "Java";
  }
  $reserved_words{$word} = $x;
}

my $MIN_INT = -0x80000000;
my $MAX_INT =  0x7fffffff;
my $MIN_DOUBLE = -1e308;
my $MAX_DOUBLE =  1e308;


sub do_file {
  my ($file, $prefix, $strip) = @_;

  local *FH;
  open FH, "< $file" or die "Cannot open $file: $!\n";

  local *COPY;
  my $dir = $ENV{"VESPA_CONFIG_DEF_DIR"};
  my $copy;
  my $file_version;
  if (defined($dir)) {
    $copy = $file;
    $copy =~ s=.*/==;
    $copy = "$dir/$copy";
    open COPY, ">$copy.new" or die "Cannot open file $copy.new: $!\n";
  }

  # Read line by line.
  #  1. Strip away comments and trailing blanks
  #  2. Report any errors
  #  3. Handle import statements, disallow multi-level imports
  #  4. Print everyting to stdout

  my $linenr = 0;
  my $written_lines = 0;
  my $quoted_strip = quotemeta($strip);
  my $seen_version = 0;

  while (<FH>) {
    print COPY $_ if $copy;
    ++$linenr;
    my $line = $_;
    chomp $line;
    
    # Don't process comments or add them to md5 checksum, but print them
    # such that codegen can include comments
    if ($line =~ /^\s*#/) {
	print "$line\n";
        next;
    }
    
    # Strip away comments that are not at start of line
    $line = &strip_trailing_comment($line, $linenr) 
      if ($line =~ m=[\\\#]=);
    
    if ($line eq "::error::") {
      return -1;
    }

    # Skip lines that are only whitespace
    next if $line =~ m=^\s*$=;
    
    # Get rid of trailing whitespace
    $line =~ s=\s+$==;
    
    if (!$seen_version) {
      if ($line =~ m!^version=([a-zA-Z0-9][-a-zA-Z0-9_/?]*)!) {
        $file_version = $1;
        $seen_version = 1;
        if ($prefix) {
          print "$prefix imported $file";
          print ":$strip" if $strip;
          print " ";
        }
        print "$line\n";
        next;
      } else {
        print STDERR "$file:$linenr: error: Definition file does not "
          . "start with a valid version= identifier!\n";
        return -1;
      }
    }

    if ($strip) {
      next unless $line =~ m=^${quoted_strip}[. \t]=;
    }

    if (&check_syntax($line, $linenr, $file) == -1) {
      return -1;
    }

    # Handle import statements
    my ($name, $type, $remains, $junk) = split(/\s+/, $line, 4);
    if ($type eq "import") {
      if ($strip || $prefix) {
        my $col = index($line, $type, length("$name "));
        print STDERR "$file:$linenr:$col: error: Multi-level "
          . "imports are disallowed.\n";
        return -1;
      }
      if ($junk) {
        my $col = index($line, $junk, length("$name $type $remains"))
          + 1;
        print STDERR "$file:$linenr:$col: error: Junk after import "
          . "target \"$remains\": \"$junk\"\n";
        return -1;
      }
      my ($impfile, $var) = split(/:/, $remains, 2);
      $var = "" unless $var;    # Make it defined.

      # Make sure only arrays can include arrays:
      if ($name =~ m=\[\]$= && (!$var || $var !~ m=\[\]$=)) {
        print STDERR "$file:$linenr: error: Array cannot import "
          . "non-array in: $line\n";
        return -1;
      } elsif ($name !~ m=\[\]$= && ($var && $var =~ m=\[\]$=)) {
        print STDERR "$file:$linenr: error: Non-array cannot import "
          . "array in: $line\n";
        return -1;
      }

      local *X;
      unless (open(X, "< $impfile")) {
        my $col = index($line, $remains, length("$name $type")) + 1;
        print STDERR "$file:$linenr:$col: error: Cannot open "
          . "\"$impfile\": $!\n";
        return -1;
      }
      close X;
      my $imported_lines = &do_file("$impfile", "$name", "$var");
      if ($imported_lines == -1) {
        my $col = index($line, $remains, length("$name $type")) + 1;
        print STDERR "$file:$linenr:$col: error: Imported from here "
          . "as: $line\n";
        return -1;
      } elsif ($imported_lines == 0) {
        my $col = index($line, $remains, length("$name $type")) + 1;
        print STDERR "$file:$linenr:$col: error: Import target "
          . "\"$var\" not found in \"$impfile\"\n";
        return -1;
      }
      $written_lines += $imported_lines;
    } else {
      ++$written_lines;
      if ($strip) {
        $line =~ s=^${quoted_strip}=${prefix}=
      } elsif ($prefix) {
        $line = $prefix . "." . $line;
      }

      if (&check_name_sanity($line, $linenr, $file) == -1
          || &check_enum_sanity($line, $linenr, $file) == -1) {
        return -1;
      }

      $line = &normalize_line($line, $linenr);
      if ($line eq "::error::") {
        return -1;
      }
      print $line . "\n";
    }
    # Add this line to the md5 checksum
    $md5->add("$line\n") unless $prefix;
  }

  print "md5=" . $md5->hexdigest . "\n" unless $prefix;
  close FH;
  if ($copy) {
    close COPY;
    # We have made a copy. It needs a new name..
    my $new_name = $copy;
    $new_name =~ s=\.def==;
    $new_name .= ".${file_version}.def";
    if (-f $new_name) {
      system "cmp $copy.new $new_name 2>/dev/null" and die "$file:1: error: Definition file $file differs from ${new_name}!\n";
      unlink("$copy.new");
    } else {
      rename("$copy.new", "$new_name") or die "Rename $copy.new -> $new_name failed: $!\n";
    }
  }
  return $written_lines;
}

sub normalize_enum {
  my($x, $linenr, $colnr) = @_;
  my $len = length($x);
  my $char = '';
  my $output = '{ ';
  my $index;
  my %enum = ();
  my $current_variable = '';
  for ($index = $colnr + 1; $index < $len; ++$index) {
    $char = substr($x, $index, 1);
    if ($char eq '}') {
      if (length($current_variable) < 2) {
        print STDERR "$defname:$linenr:$index: error: ".
          " variable must be at least two characters: $x\n" ;
        return ('', 0);
      } elsif ($enum{$current_variable}) {
        print STDERR "$defname:$linenr:$index: error: ".
          " enum variable declared twice: $x\n" ;
        return ('', 0);
      } elsif (!%enum && !$current_variable) {
        print STDERR "$defname:$linenr:$index: error: ".
          " enum cannot be empty: $x\n" ;
        return ('', 0);
      }
      return ($output.$current_variable." } ", $index);
    } elsif ($char eq ',') {
      if (length($current_variable) < 2) {
        print STDERR "$defname:$linenr:$index: error: ".
          " variable must be at least two characters: $x\n" ;
        return ('', 0);
      } elsif ($enum{$current_variable}) {
        print STDERR "$defname:$linenr:$index: error: ".
          " enum variable declared twice: $x\n" ;
        return ('', 0);
      }
      $enum{$current_variable} = 1;
      $output .= "$current_variable, ";
      $current_variable = '';
    } elsif ($char =~ m=[A-Z]=) {
      $current_variable .= $char;
    } elsif ($char =~ m=[0-9_]= && $current_variable) {
      $current_variable .= $char;
    } elsif ($char =~ m=\s=) {
      if ($current_variable && !($x =~ /^.{$index}\s*[,\}]/)) {
        print STDERR "$defname:$linenr:$index: error: ".
          "expected ',' or '}': $x\n" ;
        return ("", 0);
      } else {  
        # skip whitespace
      }
    } else {
      print STDERR "<$char> <$current_variable>\n";

      print STDERR "$defname:$linenr:$index: error: ".
        "Enum must match [A-Z][A-Z0-9_]+: $x\n";
    }
  }
  return ($output, $index);
}

{ package Range;

  $Range::DOUBLE_RANGE = 
    new Range("a double range=[$MIN_DOUBLE,$MAX_DOUBLE] ",0,14);
  $Range::INT_RANGE = new Range("a int range=[$MIN_INT,$MAX_INT] ",0,11);


  sub in_range {
    my($self, $value) = @_;

    if ($value =~ s/KB$//) {
      $value *= 1024;
    } elsif ($value =~ s/MB$//) {
      $value *= (1024 * 1024);
    } elsif ($value =~ s/GB$//) {
      $value *= (1024*1024*1024);
    } elsif ($value =~ s/k$//) {
      $value *= 1000;
    } elsif ($value =~ s/M$//) {
      $value *= 1_000_000;
    } elsif ($value =~ s/G$//) {
      $value *= 1_000_000_000;
    } elsif ($value =~ m=^0[xX]=) {
      $value = hex($value);
    }

    if ($self->{start_bracket} eq '(' ) {
      return 0 if $value <= $self->{min};
    } elsif ($self->{start_bracket} eq '[' ) {
      return 0 if $value < $self->{min};
    } else {
      print STDERR "Illegal start_bracket '$self->{start_bracket}'\n";
      return undef;
    }
    if ($self->{end_bracket} eq ')' ) {
      return 0 if $value >= $self->{max};
    } elsif ($self->{end_bracket} eq ']' ) {
      return 0 if $value > $self->{max};
    } else {
      print STDERR "Illegal end_bracket '$self->{start_bracket}'\n";
      return undef;
    }
    return 1;
  }


  sub new {
    my($class, $x, $linenr, $colnr) = @_;
    my $len = length($x);
    my $self = {};
    bless($self, $class);
    $self->{min_value} = '';
    my $index;
    for ($index = $colnr + 1; $index < $len; ++$index) {
      my $char = substr($x, $index, 1);
      if (($char eq '(' || $char eq '[') && !$self->{start_bracket}) {
        $self->{start_bracket} = $char;
      } elsif (($char eq ')' || $char eq ']') && !$self->{end_bracket}) {
        $self->{end_bracket} = $char;
        last;
      } elsif ($char =~ m=\s=) {
        #ignore whitespace
      } elsif ($char eq ',' && !defined($self->{max_value})) {
        $self->{max_value} = '';
      } elsif ($char =~ m=[\d\.\+eE-]= ) {
        (defined($self->{max_value}) 
         ? $self->{max_value} : $self->{min_value}) .= $char;
      } else {
        print STDERR "$defname:$linenr:$index: error: ".
          " syntax error: $x\n" ;
        return undef;
      }
    }
    if ($self->{min_value} eq '' && $self->{max_value} eq '') {
      print STDERR "$defname:$linenr:$colnr: error: ".
        " range cannot be unbounded in both ends: $x\n" ;
      return undef;
    }
    unless ($self->{start_bracket} && $self->{end_bracket}) {
      print STDERR "$defname:$linenr:$colnr: error: ".
        " missing bracket: $x\n" ;
      return undef;
    }


    my @arr = split(/\s+/, $x, 3);
    if ($arr[1] eq 'int') {
      $self->{min} = Math::BigInt->new
        ($self->{min_value} eq '' ? $MIN_INT : $self->{min_value});
      unless (defined($self->{min}) && $self->{min} ne 'NaN') {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " parse error $self->{min_value}: $x\n" ;
        return undef;
      }
      my $min_val = 
        $self->{min} + ($self->{start_bracket} eq '('? 1 : 0);

      $self->{max} = Math::BigInt->new
        ($self->{max_value} eq '' ? $MAX_INT : $self->{max_value});
      unless (defined($self->{max}) && $self->{max} ne 'NaN') {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " parse error $self->{max_value}: $x\n" ;
        return undef;
      }
      my $max_val = 
        $self->{max} - ($self->{end_bracket} eq ')'? 1 : 0);

      if ($min_val < $MIN_INT ) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " start of interval less than MIN_INT: $x\n" ;
        return undef;
      }
      if ($max_val > $MAX_INT) {
        print STDERR "$self->{max} - 1 > $MAX_INT\n";
        print STDERR "$defname:$linenr:$colnr: error: ".
          " end of interval greater than MAX_INT: $x\n" ;
        return undef;
      }
      if ($max_val < $min_val) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " illegal range: $x\n" ;
        return undef;
      }
      $self->{string} = 
        "$self->{start_bracket}$self->{min},$self->{max}$self->{end_bracket}";
      $self->{string} =~ s/\+//g;
      $self->{index} = $index;
      return $self;
    } elsif ($arr[1] eq 'double') {
      $self->{min} = Math::BigFloat->new
        ($self->{min_value} eq '' ? $MIN_DOUBLE : $self->{min_value});
      unless (defined($self->{min}) && $self->{min} ne 'NaN') {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " parse error $self->{min_value}: $x\n" ;
        return undef;
      }
      $self->{max} = Math::BigFloat->new
        ($self->{max_value} eq '' ? $MAX_DOUBLE : $self->{max_value});
      unless (defined($self->{max}) && $self->{max} ne 'NaN') {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " parse error $self->{max_value}: $x\n" ;
        return undef;
      }
      if ($self->{min} < $MIN_DOUBLE) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " start of interval less than MIN_DOUBLE: $x\n" ;
        return undef;
      }
      if ($self->{max} > $MAX_DOUBLE) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " start of interval greater than MAX_DOUBLE: $x\n" ;
        return undef;
      }
      if ($self->{max} < $self->{min}) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " illegal range: $x\n" ;
        return undef;
      }
      if (($self->{start_bracket} eq '(' || $self->{end_bracket} eq ')')
          && ($self->{min_value} + $self->{min_value} 
              >= $self->{min_value} + $self->{max_value})
          && ($self->{max_value} + $self->{max_value}
              <= $self->{min_value} + $self->{max_value})) {
        print STDERR "$defname:$linenr:$colnr: error: ".
          " illegal range: $x\n" ;
        return undef;
      }
      $self->{string} = $self->{start_bracket}.$self->{min}->fnorm.
        ','.$self->{max}->fnorm.$self->{end_bracket};
      $self->{string} =~ s/\+//g;
      $self->{index} = $index;
      return $self;
    } else {
      print STDERR "$defname:$linenr:$colnr: error: ".
        " range-option works only for type 'int' and 'double': $x\n" ;
      return undef;
    }
    print STDERR "$defname:$linenr:$colnr: error: ".
      " script error: $x\n" ;
    return undef;
    
  }

}




sub strip_trailing_comment {
  my ($x, $linenr) = @_;

  my $index = 0;
  my $len = length($x);
  my $in_quotes = 0;

  # ### Support both " and ' quotes maybe?

  for ($index = 0; $index < $len; ++$index) {
    if (substr($x, $index, 1) eq "\\") {
      ++$index;
      next;
    }
    if (substr($x, $index, 1) eq "\"") {
      $in_quotes ^= 1;
    }
    if ($in_quotes == 0 && substr($x, $index, 1) eq "#") {
      if (!(substr($x, $index - 1, 1) =~ m=\s=)) {
        my $col = $index + 1;
        print STDERR "$defname:$linenr:$col: warning: No whitespace "
          . "before comment in line: $x\n";
      }
      print substr($x, $index). "\n";
      $x = substr($x, 0, $index);
      last;
    }
  }
  if ($index > $len) {
    print STDERR "$defname:$linenr:$len: error: syntax error, line "
      . "ends with \\: \"$x\"\n";
    return "::error::";
  }
    
  return $x;
}

sub normalize_line {
  my ($x, $linenr) = @_;

  my $index = 0;
  my $len = length($x);
  my $in_quotes = 0;
  my $char = '';
  my $output = '';
  my %hash = ();

  my @arr = split(/\s+/, $x, 3);
  $hash{type} = $arr[1];

  for ($index = 0; $index < length($x); ++$index) {
    $char = substr($x, $index, 1);
    if ($char eq "\\") {
      $output .= substr($x, $index, 2);
      ++$index;
      next;
    }
    if ($char eq "\"") {
      $in_quotes ^= 1;
      $output .= $char;
      next;
    }
    my $ends_with_whitespace = ($output =~ m= $=);

    if ($in_quotes == 0) {
      if ($char =~ m=\s=) {
        #delete multiple spaces
        if (!$ends_with_whitespace) { # && ($output =~ !m=\=$=)) {
          $output .= ' ';
        }
      } elsif ($char eq '{') {
        my($enum, $i) = &normalize_enum($x, $linenr, $index);
        return "::error::" unless $i;
        $index = $i;
        $output .= ($ends_with_whitespace) ? $enum : " $enum ";
      } elsif ($char eq ',') {
        chop $output if ($ends_with_whitespace);
        $output .= ',';
      } elsif ($char eq '=') {
        chop $output if ($ends_with_whitespace);
        $output .= '=';
        if ($output =~ /range=$/) {
          $hash{range} = 
            new Range($x, $linenr, $index);
          return "::error::" unless $hash{range};
          $index = $hash{range}->{index};
          $output .= $hash{range}->{string}." ";
        }
        if ($output =~ /default=$/ 
            && ($hash{type} eq 'int' || $hash{type} eq 'double')) {
          $x =~ /^.{$index}=\s*(\S+)/;
          $hash{default} = $1;
          if ($hash{type} eq 'int' && 
              !$Range::INT_RANGE->in_range($hash{default})) {
            print STDERR "$defname:$linenr:$index: error: ".
              "Default not in range: $x\n";
            return "::error::";
          }
          if ($hash{type} eq 'double' && 
              !$Range::DOUBLE_RANGE->in_range($hash{default})) {
            print STDERR "$defname:$linenr:$index: error: ".
              "Default not in range: $x\n";
            return "::error::";
          }
        }
        if (defined($hash{default}) && $hash{range}) {
          unless ($hash{range}->in_range($hash{default})) {
            print STDERR "$defname:$linenr:$index: error: ".
              "Default not in range: $x\n";
            return "::error::";
          }
        }
      } else {
        $output .= $char;
      }
    } else {
      $output .= $char;
    }
  }
  if ($index > $len) {
    print STDERR "$defname:$linenr:$len: error: syntax error, line "
      . "ends with \\: \"$x\"\n";
    return "::error::";
  }
  chop $output if $output =~ m/ $/;
  return $output;
}

my %used_enum;
sub check_enum_sanity {
  my ($line, $linenr, $file) = @_;

  my ($name, $type, $rest) = split(/\s+/, $line, 3);
  return 0 unless ($type eq "enum");

  $name =~ /(.*)\./;
  my $prefix = $1;
  $prefix = "" unless defined $prefix; # Make top level prefix
  $used_enum{"$prefix"} = $used_enum{"$prefix"} || {};
  $rest = "" unless defined $rest;
  $rest =~ /\{\s*(.*?)\}/;
  my @values = split(/[,\s]+/, $1);
  foreach my $value (@values) {
    if ($used_enum{"$prefix"}->{$value}) {
      print STDERR 
        "$file:$linenr: error: Name \"$value\" is already defined\n";
      my $prevdef = $used_enum{"$prefix"}->{$value};
      print STDERR "$prevdef: error: At this point\n";
      return -1;
    } else {
      $used_enum{"$prefix"}->{$value} = "$file:$linenr";
    }
  }
  return 0;
}


my %used_name;
my %used_component;
my %banned_prefixes;
my $cns_prev_name;
sub check_name_sanity {
  my ($line, $linenr, $file) = @_;
  my ($name, $junk) = split(/\s+/, $line, 2);

  my $plain_name = $name;
  $plain_name =~ s=\[\]$==;

  # See if the name is already used.
  if ($used_name{"$plain_name"}) {
    print STDERR 
      "$file:$linenr: error: Name \"$name\" is already defined\n";
    my $prevdef = $used_name{$name};
    print STDERR "$prevdef: error: At this point\n";
    return -1;
  } else {
    $used_name{$name} = "$file:$linenr";
  }

  # Test for bans
  my $banned = "${name}.";
  do {
    my $err = $banned_prefixes{$banned};
    if (defined($err)) {
      print STDERR "$file:$linenr: error: The prefix \"$banned\" is illegal here\n";
      print STDERR "$err\n";
      return -1;
    }
  } while (($banned =~ s=[.][^.]+[.]$=.=));

  # Add any new bans generated by this line
  $banned_prefixes{"${name}."} = "$file:$linenr: error: \"${name}\" cannot "
    . "be both a struct and a non-struct!";
  if ($cns_prev_name) {
    my $prev = $cns_prev_name;
    my $oldprev = $prev;
    while (($prev =~ s=[.][^.]+[.]?$=.=)) {
      if (substr($name, 0, length($prev)) eq $prev) {
        $banned_prefixes{"$oldprev"} = "$file:" . ($linenr - 1)
          . ": error: Last possible line is after this";
        last;
      }
      $oldprev = $prev;
    }
  }
  $cns_prev_name = $name;

  # See if any of the components previously have a different "arrayness"
  my $part_name = $name;
  while (($part_name =~ s=[.][^.]+$==)) {
    my $clashing_name = $part_name;
    if ($part_name =~ m=\[\]$=) {
      $clashing_name =~ s=\[\]$==;
    } else {
      $clashing_name .= "[]";
    }
    my $clashline = $used_component{"$clashing_name"};
    if (defined $clashline) {
      print STDERR "$file:$linenr: error: \"$clashing_name\" cannot be both array and non-array\n";
      print STDERR "$clashline: error: Previously defined here\n";
      return -1;
    } elsif (!$used_component{"$part_name"}) {
      $used_component{"$part_name"} = "$file:$linenr";
    }
  }
  return 0;
}

# These are all the allowed types/commands
my %types = ( "int" => \&check_int,
              "double" => \&check_double,
              "string" => \&check_string,
              "reference" => \&check_reference,
              "enum" => \&check_enum,
              "bool" => \&check_bool,
              "properties" => \&check_properties,
              "import" => \&check_import );

sub check_syntax {
  my ($line, $linenr, $file) = @_;

  my $col = 0;
  my $llen = length($line);

  # Step 1. Sanity check the name.
  my $atstart = 1;
  my $array_ok = 1;

  for ($col = 0; $col < $llen; ++$col) {
    my $c = substr($line, $col, 1);
    if ($atstart) {
      if ($c !~ m=[a-zA-Z]=) {
        print STDERR "$file:$linenr:$col: error: Non-alphabetic start "
          . "of variable name in $line\n";
        return -1;
      }
      $atstart = 0;
    } else {
      if ($c =~ m=[a-zA-Z0-9_]=) {
        0;                      # Do nothing
      } elsif ($c eq ".") {
        $atstart = 1;
        $array_ok = 1;
      } elsif ($c eq "[") {
        if (!$array_ok) {
          ++$col;
          print STDERR "$file:$linenr:$col: error: Arrays cannot be "
            . "multidimensional in $line\n";
          return -1;
        } 
        ++$col;
        $array_ok = 0;
        $c = substr($line, $col, 1);
        if ($c ne "]") {
          ++$col;
          print STDERR "$file:$linenr:$col: error: Expected ] to "
            . "terminate array definition in $line\n";
          return -1;
        }
      } elsif ($c =~ m=\s=) {
        last;
      } else {
        ++$col;
        print STDERR "$file:$linenr:$col: error: Syntax error, "
          . "unexpected character in $line\n";
        return -1;
      }
    }
  }

  my $name = substr($line, 0, $col);
  $name =~ s=.*[.]==;
  $name =~ s=[[]]$==;

  my $clash = $reserved_words{$name};
  if ($clash) {
    $col -= (3 + length($name));
    $col = index($line, $name, $col) + 1;
    print STDERR "$file:$linenr:$col: error: $name is a reserved word in: "
      . "${clash}\n";
    return -1;
  }

  while (substr($line, $col, 1) =~ m=\s=) {
    ++$col;
  }

  # At this point the name is sane. Next, check the type.
  my ($type) = split(/\s/, substr($line, $col));

  unless (defined $types{$type}) {
    ++$col;
    print STDERR "$file:$linenr:$col: error: Unknown type/command "
      . "\"$type\"\n";
    return -1;
  }
  $col += length($type);
  while (substr($line, $col, 1) =~ m=\s=) {
    ++$col;
  }
  return $types{$type}($col, $line, $linenr, $file);
}

sub reg_words_check {
  my ($col, $line, $linenr, $file, $reg) = @_;
  my $remainder = substr($line, $col);
  my @options = split(/\s+/, $remainder);

  foreach my $option (@options) {
    # Keep track of where we are for error reporting
    $col = index($line, $option, $col) + 1;
    unless ($option =~ m!${reg}!) {
      print STDERR "$file:$linenr:$col: error: Bad option \"$option\" no match for m!${reg}!\n";
      return -1;
    }
  }
  return 0;
}

sub check_int {
  my ($col, $line, $linenr, $file) = @_;
  my $num = "(-?\\d+(KB|MB|GB|k|M|G)?|0x[0-9a-fA-F]+)"; # All legal numbers
  my $optnum = "(${num})?";     # All legal optional numbers
  return &reg_words_check($col, $line, $linenr, $file,
                          "^("
                          . "default=${num}"
                          . "|range=[[(]${optnum},${optnum}"."[])]"
                          . "|restart"
                          . ")\$");
}

sub check_double {
  my ($col, $line, $linenr, $file) = @_;
  my $num = "-?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?"; # All legal doubles
  my $optnum = "(${num})?";     # Optional doubles
  return &reg_words_check($col, $line, $linenr, $file,
                          "^("
                          .  "default=${num}"
                          . "|range=[[(]${optnum},${optnum}"."[])]"
                          . "|restart"
                          . ")\$");
}

sub check_string {
  my ($col, $line, $linenr, $file) = @_;
  my $opts = substr($line, $col);

  # not entirely correct either for something like \\"
  my $def = "default=((\"(\\\"|[^\"])*\")|null)";

  my $res = "restart";
  my $reg = "^(${def}\\s+${res}|(${def})?|${res}|${res}\\s+${def})\$";

  unless ($opts =~ m!${reg}!) {
    print STDERR "$file:$linenr:$col: error: Bad options \"$opts\", no match for m!${reg}!\n";
    return -1;
  }
  return 0;
}

sub check_reference {
  my ($col, $line, $linenr, $file) = @_;
  my $opts = substr($line, $col);
  my $def = "default=((\"(\\\"|[^\"])*\")|null)";

  unless ($opts eq "" || $opts =~m!${def}!) {
    print STDERR "$file:$linenr:$col: error: reference can only "
      . "take the 'default' option\n";
    return -1;
  }
  return 0;
}


sub check_enum {
  my ($col, $line, $linenr, $file) = @_;
  my $ret = &reg_words_check($col, $line, $linenr, $file,
                             "^("
                             .  "[{},]"
                             . "|[A-Z][A-Z0-9_]+,?"
                             . "|default=[A-Z][A-Z0-9_]+"
                             . "|restart"
                             . ")\$");
  return -1 if $ret;
  $col = index($line, '}', $col) + 1; #move $col to end of enum --> }
  while (substr($line, $col, 1) =~ m=[\s\{]=) {
    ++$col;
  }
  return 0 if $col >= length($line);


  return &reg_words_check($col, $line, $linenr, $file,
                          "^("
                          .  "default=[A-Z][A-Z0-9_]+"
                          . "|restart"
                          . ")\$");
}

sub check_bool {
  my ($col, $line, $linenr, $file) = @_;
  return &reg_words_check($col, $line, $linenr, $file,
                          "^("
                          .  "default=(true|false)"
                          . "|restart"
                          . ")\$");
}

sub check_properties {
  my ($col, $line, $linenr, $file) = @_;
  return &reg_words_check($col, $line, $linenr, $file, "^restart\$");
}

sub check_import {
  my ($col, $line, $linenr, $file) = @_;
  my $word = "[a-zA-Z][_a-zA-Z0-9]*";
  my $fnam = "${word}(\\.${word})*";
  my $var = "${word}((\\[\\])?\.${word})*(\\[\\])?";
  return &reg_words_check($col, $line, $linenr, $file,
                          "^${fnam}\\.def:(${var})?\$");
  return 0;
}


my $lines = &do_file($defname, "", "");

if ($lines == -1) {
  die "There were irrecoverable errors in \"$defname\"!\n";
}
if ($lines == 0) {
  die "$defname:1: error: Resulting definition is empty!\n";
}

exit 0;
