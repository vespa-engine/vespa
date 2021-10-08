# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Output handler
#
# Intentions:
#   - Make it easy for unit tests to redirect output.
#   - Allow programmers to add all sorts of debug information into tools usable
#     for debugging, while hiding it by default for real users.
#   - Allow generic functionality that can be reused by all. For instance color 
#     coding of very important information.
#
# Ideas for improvement:
#   - Could possibly detect terminal width and do proper line breaking of long
#     lines
#
# A note about colors:
#   - This module will detect if terminal supports colors. If not, it will not
#     print any. (Color support can be turned off by giving --nocolors argument
#     through argument parser, by setting a TERM value that does not support
#     colors or programmatically call setUseAnsiColors(0).
#   - Currently only red and grey are used in addition to default. These colors
#     should work well for both light and dark backgrounds.
# 

package Yahoo::Vespa::ConsoleOutput;

use strict;
use warnings;
use Yahoo::Vespa::Utils;

BEGIN { # - Define exports for modul
    use base 'Exporter';
    our @EXPORT = qw(
        printResult printError printWarning printInfo printDebug printSpam
        enableAutomaticLineBreaks
        COLOR_RESET COLOR_WARN COLOR_ERR COLOR_ANON
    );
    our @EXPORT_OK = qw(
        getTerminalWidth getVerbosity usingAnsiColors ansiColorsSupported
        setVerbosity
    );
}

my %TYPES = (
    'result'  => 0, # Output from a tool. Expected when app runs successfully.
    'error'   => 1, # Error found, typically aborting the script with a failure.
    'warning' => 2, # An issue that may or may not cause the program to fail.
    'info'    => 3, # Useful information to get from the script.
    'debug'   => 4, # Debug information useful to debug script or to see
                    # internals of what is happening.
    'spam'    => 5, # Spammy information used when large amounts of details is
                    # wanted. Typically to debug some failure.
);
my $VERBOSITY; # Current verbosity level
my $ANSI_COLORS_SUPPORTED; # True if terminal supports colors
my $ANSI_COLORS; # True if we want to use colors (and support it)
my %ATTRIBUTE_PREFIX; # Ansi escape prefixes for verbosity levels
my %ATTRIBUTE_POSTFIX; # Ansi escape postfixes for verbosity levels
my %OUTPUT_STREAM; # Where to write different verbosity levels (stdout|stderr)
my $TERMINAL_WIDTH; # With of terminal in columns
my $COLUMN_POSITION; # Current index of cursor in terminal
my $ENABLE_AUTO_LINE_BREAKS;

use constant COLOR_RESET => "\e[0m";
use constant COLOR_ERR => "\e[91m";
use constant COLOR_WARN => "\e[93m";
use constant COLOR_ANON => "\e[90m";

&initialize(*STDOUT, *STDERR);

return 1;

########################## Default exported functions ########################

sub printResult { # (Output...)
    printAtLevel('result', @_);
}
sub printError { # (Output...)
    printAtLevel('error', @_);
}
sub printWarning { # (Output...)
    printAtLevel('warning', @_);
}
sub printInfo { # (Output...)
    printAtLevel('info', @_);
}
sub printDebug { # (Output...)
    printAtLevel('debug', @_);
}
sub printSpam { # (Output...)
    printAtLevel('spam', @_);
}
sub enableAutomaticLineBreaks { # (Bool) -> (OldValue)
    my $oldval = $ENABLE_AUTO_LINE_BREAKS;
    $ENABLE_AUTO_LINE_BREAKS = ($_[0] ? 1 : 0);
    return $oldval;
}

######################## Optionally exported functions #######################

sub getTerminalWidth { # () -> ColumnCount
        # May be undefined if someone prints before initialized
    return (defined $TERMINAL_WIDTH ? $TERMINAL_WIDTH : 80);
}
sub getVerbosity { # () -> VerbosityLevel
    return $VERBOSITY;
}
sub usingAnsiColors { # () -> Bool
    return $ANSI_COLORS;
}
sub ansiColorsSupported { # () -> Bool
    return $ANSI_COLORS_SUPPORTED;
}
sub setVerbosity { # (VerbosityLevel)
    $VERBOSITY = $_[0];
}

################## Functions for unit tests to mock internals ################

sub setTerminalWidth { # (ColumnCount)
    $TERMINAL_WIDTH = $_[0];
}
sub setUseAnsiColors { # (Bool)
    if ($ANSI_COLORS_SUPPORTED && $_[0]) {
        $ANSI_COLORS = 1;
    } else {
        $ANSI_COLORS = 0;
    }
}

############## Utility functions - Not intended for external use #############

sub initialize { # ()
    my ($stdout, $stderr, $use_colors_by_default) = @_;
    if (!defined $VERBOSITY) {
        $VERBOSITY = &getDefaultVerbosity();
    }
    $COLUMN_POSITION = 0;
    $ENABLE_AUTO_LINE_BREAKS = 1;
    %ATTRIBUTE_PREFIX = map { $_ => '' } keys %TYPES;
    %ATTRIBUTE_POSTFIX = map { $_ => '' } keys %TYPES;
    &setAttribute('error', COLOR_ERR, COLOR_RESET);
    &setAttribute('warning', COLOR_WARN, COLOR_RESET);
    &setAttribute('debug', COLOR_ANON, COLOR_RESET);
    &setAttribute('spam', COLOR_ANON, COLOR_RESET);
    %OUTPUT_STREAM = map { $_ => $stdout } keys %TYPES;
    $OUTPUT_STREAM{'error'} = $stderr;
    $OUTPUT_STREAM{'warning'} = $stderr;
    if (defined $use_colors_by_default) {
        $ANSI_COLORS_SUPPORTED = $use_colors_by_default;
        $ANSI_COLORS = $ANSI_COLORS_SUPPORTED;
    } else {
        &detectTerminalColorSupport();
    }
    if (!defined $TERMINAL_WIDTH) {
        $TERMINAL_WIDTH = &detectTerminalWidth();
    }
}
sub setAttribute { # (type, prefox, postfix)
    my ($type, $prefix, $postfix) = @_;
    $ATTRIBUTE_PREFIX{$type} = $prefix;
    $ATTRIBUTE_POSTFIX{$type} = $postfix;
}
sub stripAnsiEscapes { # (Line) -> (StrippedLine)
    $_[0] =~ s/\e\[[^m]*m//g;
    return $_[0];
}
sub getDefaultVerbosity { # () -> VerbosityLevel
    # We can not print at correct verbosity levels before argument parsing has
    # completed. We try some simple arg parsing here assuming default options
    # used to set verbosity, such that we likely guess correctly, allowing
    # correct verbosity from the start.
    my $default = 3;
    foreach my $arg (@ARGV) {
        if ($arg eq '--') { return $default; }
        if ($arg =~ /^-([^-]+)/) {
            my $optstring = $1;
            while ($optstring =~ /^(.)(.*)$/) {
                my $char = $1;
                $optstring = $2;
                if ($char eq 'v') {
                    ++$default;
                }
                if ($char eq 's') {
                    if ($default > 0) {
                        --$default;
                    }
                }
            }
        }
    }
    return $default;
}
sub detectTerminalWidth { #() -> ColumnCount
    my $cols = &checkConsoleFeature('cols');
    if (!defined $cols) {
        printDebug "Assuming terminal width of 80.\n";
        return 80;
    }
    if ($cols =~ /^\d+$/ && $cols > 10 && $cols < 500) {
        printDebug "Detected terminal width of $cols.\n";
        return $cols;
    } else {
        printDebug "Unexpected terminal width of '$cols' given. "
                 . "Assuming size of 80.\n";
        return 80;
    }
}
sub detectTerminalColorSupport { # () -> Bool
    my $colorcount = &checkConsoleFeature('colors');
    if (!defined $colorcount) {
        $ANSI_COLORS_SUPPORTED = 0;
        printDebug "Assuming no color support.\n";
        return 0;
    }
    if ($colorcount =~ /^\d+$/ && $colorcount >= 8) {
        $ANSI_COLORS_SUPPORTED = 1;
        if (!defined $ANSI_COLORS) {
            $ANSI_COLORS = $ANSI_COLORS_SUPPORTED;
        }
        printDebug "Color support detected.\n";
        return 1;
    }
}
sub checkConsoleFeature { # (Feature) -> Bool
    my ($feature) = @_;
        # Unit tests must mock. Can't depend on TERM being set.
    assertNotUnitTest();
    if (!exists $ENV{'TERM'}) {
        printDebug "Terminal not set. Unknown.\n";
        return;
    }
    if (-f '/usr/bin/tput') {
        my ($fh, $result);
        if (open ($fh, "tput $feature 2>/dev/null |")) {
            $result = <$fh>;
            close $fh;
        } else {
            printDebug "Failed to open tput pipe.\n";
            return;
        }
        if ($? != 0) {
            printDebug "Failed tput call to detect feature $feature $!\n";
            return;
        }
        chomp $result;
        #printSpam "Console feature $feature: '$result'\n";
        return $result;
    } else {
        printDebug "No tput binary. Dont know how to detect feature.\n";
        return;
    }
}
sub printAtLevel { # (Level, Output...)
    # Prints an array of data that may contain newlines
    my $level = shift @_;
    exists $TYPES{$level} or confess "Unknown print level '$level'.";
    if ($TYPES{$level} > $VERBOSITY) {
        return;
    }
    my $buffer = '';
    my $width = &getTerminalWidth();
    foreach my $printable (@_) {
        my @lines = split(/\n/, $printable, -1);
        my $current = 0;
        for (my $i=0; $i < scalar @lines; ++$i) {
            if ($i != 0) {
                $buffer .= "\n";
                $COLUMN_POSITION = 0;
            }
            my $last = ($i + 1 == scalar @lines);
            printLineAtLevel($level, $lines[$i], \$buffer, $last);
        }
    }
    my $stream = $OUTPUT_STREAM{$level};
    print $stream $buffer;
}
sub printLineAtLevel { # (Level, Line, Buffer, Last)
    # Prints a single line, which might still have to be broken into multiple
    # lines
    my ($level, $data, $buffer, $last) = @_;
    if (!$ANSI_COLORS) {
        $data = &stripAnsiEscapes($data);
    }
    my $width = &getTerminalWidth();
    while (1) {
        my $remaining = $width - $COLUMN_POSITION;
        if (&prefixLineWithLevel($level)) {
            $remaining -= (2 + length $level);
        }
        if ($ENABLE_AUTO_LINE_BREAKS && $remaining < length $data) {
            my $min = int (2 * $width / 3) - $COLUMN_POSITION;
            if ($min < 1) { $min = 1; }
            if ($data =~ /^(.{$min,$remaining}) (.*?)$/s) {
                my ($first, $rest) = ($1, $2);
                &printLinePartAtLevel($level, $first, $buffer);
                $$buffer .= "\n";
                $data = $rest;
                $COLUMN_POSITION = 0;
            } else {
                last;
            }
        } else {
            last;
        }
    }
    if (!$last || length $data > 0) {
        &printLinePartAtLevel($level, $data, $buffer);
    }
}
sub printLinePartAtLevel { # ($Level, Line, Buffer)
    # Print a single line that should fit on one line
    my ($level, $data, $buffer) = @_;
    if ($ANSI_COLORS) {
        $$buffer .= $ATTRIBUTE_PREFIX{$level};
    }
    if (&prefixLineWithLevel($level)) {
        $$buffer .= $level . ": ";
        $COLUMN_POSITION = (length $level) + 2;
    }
    $$buffer .= $data;
    $COLUMN_POSITION += length $data;
    if ($ANSI_COLORS) {
        $$buffer .= $ATTRIBUTE_POSTFIX{$level};
    }
}
sub prefixLineWithLevel { # (Level) -> Bool
    my ($level) = @_;
    return ($TYPES{$level} > 2 && $VERBOSITY >= 4 && $COLUMN_POSITION == 0);
}

