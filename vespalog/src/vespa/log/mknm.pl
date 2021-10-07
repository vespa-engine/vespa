#!/usr/bin/perl
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

while (<>) {
  if ( s/.*\benum\s+LogLevel\s*\{// ) {
    chomp;
    $t = $_;
    while (<>) {
      if ( s/\}.*// ) {
        $t .= $_;
        $t =~ s/,/ /g;
        @t = split(" ", $t);
        if ( $t[$#t] ne "NUM_LOGLEVELS" ) {
          die "expected NUM_LOGLEVELS got '$t[$#t]'\n";
        }
	pop @t;
	makecpp();
      }
      $t .= $_;
    }
  }
}
die "did not find enum\n";

sub makecpp
{
        print "#include <string.h>\n";
        print '#include <vespa/log/log.h>';
        print "\n\n" . "namespace ns_log {" . "\n\n";

        print "enum Logger::LogLevel\n";
	print "Logger::parseLevel(const char *lname)\n{\n";
	foreach $l ( @t ) {
		print "    if (strcmp(lname, \"$l\") == 0) return $l;\n";
	}
	print "    // bad level name signaled by NUM_LOGLEVELS\n";
	print "    return NUM_LOGLEVELS;\n";
	print "}\n\n";

        print "const char *Logger::logLevelNames[] = {" . "\n    ";
	foreach $l ( @t ) { $l = "\"$l\""; }
	push @t, "0 // converting NUM_LOGLEVELS gives null pointer\n";
        print join(",\n    ", @t);
        print "};\n\n} // namespace\n";
	exit(0);
}
