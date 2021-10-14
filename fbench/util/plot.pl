#!/usr/bin/perl -s
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# TODO
# 	- parameter for input and output file name
#	- more graphs

sub usage {
  die qq{usage: plot.pl [-h] [-x] <plotno> <format>
Plot the contents of 'result.txt' to 'graph.<format>'.
	-h	This help
	-x	Output to X11 window
	plotno:	1: Response Time Percentiles by NumCli
		2: Rate by NumCli
		3: Response Time Percentiles by Rate
	format:	png (default), ps
};
}

$plotno = shift || die usage;
$term = shift || "png";

if ($h) {
  usage;
}

# setup the output
if ($x) {
  # X11 output
  open(PLOTSCRIPT, "| gnuplot -persist");
  print PLOTSCRIPT "set term X11\n";

} else {
  open(PLOTSCRIPT, "| gnuplot");
  if ("$term" eq "ps") {
    print PLOTSCRIPT "set term postscript\n";
    print PLOTSCRIPT "set output \"graph.ps\"\n";
  }
  else {
    print PLOTSCRIPT "set term png transparent small medium enhanced\n";
    print PLOTSCRIPT "set output \"graph.png\"\n";
  }
}
select(PLOTSCRIPT);



# choose the graph
if ($plotno == 1) {
  # Cli Percentile
  print qq{
set data style lines
set title "Response Time Percentiles by NumCli"
set xlabel "Number of clients"
set ylabel "Response time (msec)"
set key left top
plot 'result.txt' using 1:10 title "max", 'result.txt' using 1:17 title "99 %", 'result.txt' using 1:16 title "95 %", 'result.txt' using 1:15 title "90 %", 'result.txt' using 1:14 title "75 %", 'result.txt' using 1:13 title "50 %", 'result.txt' using 1:12 title "25 %", 'result.txt' using 1:9 title "min"
  };

} elsif ($plotno == 2) {
  # Cli Rate
  print qq{
set data style lines
set title "Rate by NumCli"
set xlabel "Number of clients"
set ylabel "Rate (queries/sec)"
set nokey
plot 'result.txt' using 1:18
  };
} elsif ($plotno == 3) {
  # Rate Percentile
  print qq{
set data style lines
set title "Response Time Percentiles by Rate"
set xlabel "Rate (queries/sec)"
set ylabel "Response time (msec)"
set key left top
plot 'result.txt' using 18:17 title "99 %", 'result.txt' using 18:16 title "95 %", 'result.txt' using 18:15 title "90 %", 'result.txt' using 18:14 title "75 %", 'result.txt' using 18:13 title "50 %", 'result.txt' using 18:12 title "25 %"
  };
}

close(PLOTSCRIPT);
