# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Argument parser.
#
# Intentions:
#   - Make it very easy for programs to get info from command line.
#   - Allow shared libraries to register own options, such that a program can
#     delegate command line options to libraries used. (For instance, verbosity
#     arguments will be automatically delegated to console output module without
#     program needing to care much.
#   - Create a unified looking syntax page for all command line tools.
#   - Be able to reuse input validation. For instance that an integer don't
#     have a decimal point and that a hostname can be resolved.
# 

package Yahoo::Vespa::ArgParser;

use strict;
use warnings;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Utils;

BEGIN { # - Define exports and dependency aliases for module.
    use base 'Exporter';
    our @EXPORT = qw(
        addArgParserValidator
        setProgramBinaryName setProgramDescription
        setArgument setOptionHeader
        setFlagOption setHostOption setPortOption setStringOption
        setIntegerOption setFloatOption setUpCountingOption setDownCountingOption
        handleCommandLineArguments
        OPTION_SECRET OPTION_INVERTEDFLAG OPTION_REQUIRED
    );
        # Alias so we can avoid writing the entire package name
    *ConsoleOutput:: = *Yahoo::Vespa::ConsoleOutput::
}

my @ARGUMENTS;
my $DESCRIPTION;
my $BINARY_NAME;
my @ARG_SPEC_ARRAY;
my %OPTION_SPEC;
my @OPTION_SPEC_ARRAY;
my $SYNTAX_PAGE;
my $SHOW_HIDDEN;
my @VALIDATORS;
use constant OPTION_SECRET => 1;
use constant OPTION_INVERTEDFLAG => 2;
use constant OPTION_ADDFIRST => 4;
use constant OPTION_REQUIRED => 8;

# These variables are properties needed by ConsoleOutput module. ArgParser
# handles that modules argument settings as it cannot possibly depend upon
# ArgParser itself.
my $VERBOSITY; # Default verbosity before parsing arguments
my $ANSI_COLORS; # Whether to use ansi colors or not.

&initialize();

return 1;

########################## Default exported functions ########################

sub handleCommandLineArguments { # () Parses and sets all values
    my ($args, $validate_args_sub) = @_;

    &registerInternalParameters();
    if (!&parseCommandLineArguments($args)) {
        &writeSyntaxPage();
        exitApplication(1);
    }
    if (defined $validate_args_sub && !&$validate_args_sub()) {
        &writeSyntaxPage();
        exitApplication(1);
    }
    if ($SYNTAX_PAGE) {
        &writeSyntaxPage();
        exitApplication(0);
    }
}

sub addArgParserValidator { # (Validator) Add callback to verify parsing
    # Using such callbacks you can verify more than is supported natively by
    # argument parser, such that you can fail argument parsing at same step as
    # internally supported checks are handled.
    scalar @_ == 1 or confess "Invalid number of arguments given.";
    push @VALIDATORS, $_[0];
}
sub setProgramBinaryName { # (Name)  Defaults to name used on command line
    scalar @_ == 1 or confess "Invalid number of arguments given.";
    ($BINARY_NAME) = @_;
}
sub setProgramDescription { # (Description)
    scalar @_ == 1 or confess "Invalid number of arguments given.";
    ($DESCRIPTION) = @_;
}

sub setOptionHeader { # (Description)
    my ($desc) = @_;
    push @OPTION_SPEC_ARRAY, $desc; 
}

sub setFlagOption { # (ids[], Result&, Description, Flags)
    scalar @_ >= 3 or confess "Invalid number of arguments given.";
    my ($ids, $result, $description, $flags) = @_;
    if (!defined $flags) { $flags = 0; }
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 0,
        'initializer' => sub {
            $$result = (($flags & OPTION_INVERTEDFLAG) == 0 ? 0 : 1);
            return 1;
        },
        'result_evaluator' => sub {
            $$result = (($flags & OPTION_INVERTEDFLAG) == 0 ? 1 : 0);
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setHostOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 1,
        'result_evaluator' => sub {
            my ($id, $args) = @_;
            scalar @$args == 1 or confess "Should have one arg here.";
            my $host = $$args[0]; 
            if (!&validHost($host)) {
                printError "Invalid host '$host' given to option '$id'. "
                         . "Not a valid host\n";
                return 0;
            }
            printSpam "Set value of '$id' to $host.\n";
            $$result = $host;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setPortOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 1,
        'result_evaluator' => sub {
            my ($id, $args) = @_;
            scalar @$args == 1 or confess "Should have one arg here.";
            my $val = $$args[0]; 
            if ($val !~ /^\d+$/ || $val < 0 || $val >= 65536)  {
                printError "Invalid value '$val' given to port option '$id'."
                         . " Must be an unsigned 16 bit integer.\n";
                return 0;
            }
            printSpam "Set value of '$id' to $val.\n";
            $$result = $val;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setIntegerOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 1,
        'result_evaluator' => sub {
            my ($id, $args) = @_;
            scalar @$args == 1 or confess "Should have one arg here.";
            my $val = $$args[0]; 
            if ($val !~ /^(?:[-\+])?\d+$/)  {
                printError "Invalid value '$val' given to integer option "
                         . "'$id'.\n";
                return 0;
            }
            printSpam "Set value of '$id' to $val.\n";
            $$result = $val;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setFloatOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 1,
        'result_evaluator' => sub {
            my ($id, $args) = @_;
            scalar @$args == 1 or confess "Should have one arg here.";
            my $val = $$args[0]; 
            if ($val !~ /^(?:[-\+])?\d+(?:\.\d+)?$/)  {
                printError "Invalid value '$val' given to float option "
                         . "'$id'.\n";
                return 0;
            }
            printSpam "Set value of '$id' to $val.\n";
            $$result = $val;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setStringOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 1,
        'result_evaluator' => sub {
            my ($id, $args) = @_;
            scalar @$args == 1 or confess "Should have one arg here.";
            my $val = $$args[0]; 
            printSpam "Set value of '$id' to $val.\n";
            $$result = $val;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setUpCountingOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my $org = $$result;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 0,
        'initializer' => sub {
            $$result = $org;
            return 1;
        },
        'result_evaluator' => sub {
            if (!defined $$result) {
                $$result = 0;
            }
            ++$$result;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}
sub setDownCountingOption { # (ids[], Result&, Description, Flags)
    my ($ids, $result, $description, $flags) = @_;
    my $org = $$result;
    my %optionspec = (
        'result' => $result,
        'flags' => $flags,
        'ids' => $ids,
        'description' => $description,
        'arg_count' => 0,
        'initializer' => sub {
            $$result = $org;
            return 1;
        },
        'result_evaluator' => sub {
            if (!defined $$result) {
                $$result = 0;
            }
            --$$result;
            return 1;
        }
    );
    setGenericOption($ids, \%optionspec);
}

sub setArgument { # (Result&, Name, Description)
    my ($result, $name, $description, $flags) = @_;
    if (!defined $flags) { $flags = 0; }
    if (scalar @ARG_SPEC_ARRAY > 0 && ($flags & OPTION_REQUIRED) != 0) {
        my $last = $ARG_SPEC_ARRAY[scalar @ARG_SPEC_ARRAY - 1];
        if (($$last{'flags'} & OPTION_REQUIRED) == 0) {
            confess "Cannot add required argument after optional argument";
        }
    }
    my %argspec = (
        'result' => $result,
        'flags' => $flags,
        'name' => $name,
        'description' => $description,
        'result_evaluator' => sub {
            my ($arg) = @_;
            $$result = $arg;
            return 1;
        }
    );
    push @ARG_SPEC_ARRAY, \%argspec;
}

######################## Externally usable functions #######################

sub registerInternalParameters { # ()
    # Register console output parameters too, as the output module can't depend
    # on this tool.
    setFlagOption(
            ['show-hidden'],
            \$SHOW_HIDDEN,
            'Also show hidden undocumented debug options.',
            OPTION_ADDFIRST);
    setDownCountingOption(
            ['s'],
            \$VERBOSITY,
            'Create less verbose output.',
            OPTION_ADDFIRST);
    setUpCountingOption(
            ['v'],
            \$VERBOSITY,
            'Create more verbose output.',
            OPTION_ADDFIRST);
    setFlagOption(
            ['h', 'help'],
            \$SYNTAX_PAGE,
            'Show this help page.',
            OPTION_ADDFIRST);

    # If color use is supported and turned on by default, give option to not use
    if ($ANSI_COLORS) {
        setOptionHeader('');
        setFlagOption(
                ['nocolors'],
                \$ANSI_COLORS,
                'Do not use ansi colors in print.',
                OPTION_SECRET | OPTION_INVERTEDFLAG);
    }
}
sub setShowHidden { # (Bool)
    $SHOW_HIDDEN = ($_[0] ? 1 : 0);
}

############## Utility functions - Not intended for external use #############

sub initialize { # ()
    $VERBOSITY = 3;
    $ANSI_COLORS = Yahoo::Vespa::ConsoleOutput::ansiColorsSupported();
    $DESCRIPTION = undef;
    $BINARY_NAME = $0;
    if ($BINARY_NAME =~ /\/([^\/]+)$/) {
        $BINARY_NAME = $1;
    }
    %OPTION_SPEC = ();
    @OPTION_SPEC_ARRAY = ();
    @ARG_SPEC_ARRAY = ();
    @VALIDATORS = ();
    $SYNTAX_PAGE = undef;
    $SHOW_HIDDEN = undef;
    @ARGUMENTS = undef;
}
sub parseCommandLineArguments { # (ArgumentListRef)
    printDebug "Parsing command line arguments\n";
    @ARGUMENTS = @{ $_[0] };
    foreach my $spec (@OPTION_SPEC_ARRAY) {
        if (ref($spec) && exists $$spec{'initializer'}) {
            my $initsub = $$spec{'initializer'};
            &$initsub();
        }
    }
    my %eaten_args;
    if (!&parseOptions(\%eaten_args)) {
        printDebug "Option parsing failed\n";
        return 0;
    }
    if (!&parseArguments(\%eaten_args)) {
        printDebug "Argument parsing failed\n";
        return 0;
    }
    ConsoleOutput::setVerbosity($VERBOSITY);
    ConsoleOutput::setUseAnsiColors($ANSI_COLORS);
    return 1;
}
sub writeSyntaxPage { # ()
    if (defined $DESCRIPTION) {
        printResult $DESCRIPTION . "\n\n";
    }
    printResult "Usage: " . $BINARY_NAME;
    if (scalar keys %OPTION_SPEC > 0) {
        printResult " [Options]";
    }
    foreach my $arg (@ARG_SPEC_ARRAY) {
        if (($$arg{'flags'} & OPTION_REQUIRED) != 0) {
            printResult " <" . $$arg{'name'} . ">";
        } else {
            printResult " [" . $$arg{'name'} . "]";
        }
    }
    printResult "\n";

    if (scalar @ARG_SPEC_ARRAY > 0) {
        &writeArgumentSyntax();
    }
    if (scalar keys %OPTION_SPEC > 0) {
        &writeOptionSyntax();
    }
}
sub setGenericOption { # (ids[], Optionspec)
    my ($ids, $spec) = @_;
    if (!defined $$spec{'flags'}) {
        $$spec{'flags'} = 0;
    }
    foreach my $id (@$ids) {
        if (length $id == 1 && $id =~ /[0-9]/) {
            confess "A short option can not be a digit. Reserved so we can parse "
                . "-4 as a negative number argument rather than an option 4";
        }
    }
    foreach my $id (@$ids) {
        $OPTION_SPEC{$id} = $spec;
    }
    if (($$spec{'flags'} & OPTION_ADDFIRST) == 0) {
        push @OPTION_SPEC_ARRAY, $spec;
    } else {
        unshift @OPTION_SPEC_ARRAY, $spec;
    }
}
sub parseArguments { # (EatenArgs)
    my ($eaten_args) = @_;
    my $stopIndex = 10000000;
    my $argIndex = 0;
    printSpam "Parsing arguments\n";
    for (my $i=0; $i<scalar @ARGUMENTS; ++$i) {
        printSpam "Processing arg '$ARGUMENTS[$i]'.\n";
        if ($i <= $stopIndex && $ARGUMENTS[$i] eq '--') {
            printSpam "Found --. Further dash prefixed args will be args\n";
            $stopIndex = $i;
        } elsif ($i <= $stopIndex && $ARGUMENTS[$i] =~ /^-/) {
            printSpam "Option declaration. Ignoring\n";
        } elsif (exists $$eaten_args{$i}) {
            printSpam "Already eaten argument. Ignoring\n";
        } elsif ($argIndex < scalar @ARG_SPEC_ARRAY) {
            my $spec = $ARG_SPEC_ARRAY[$argIndex];
            my $name = $$spec{'name'};
            if (!&{$$spec{'result_evaluator'}}($ARGUMENTS[$i])) {
                printDebug "Failed evaluate result of arg $name. Aborting\n";
                return 0;
            }
            printSpam "Successful parsing of argument '$name'.\n";
            $$eaten_args{$i} = 1;
            ++$argIndex;
        } else {
            printError "Unhandled argument '$ARGUMENTS[$i]'.\n";
            return 0;
        }
    }
    if ($SYNTAX_PAGE) { # Ignore required arg check if syntax page is to be shown
        return 1;
    }
    for (my $i=$argIndex; $i<scalar @ARG_SPEC_ARRAY; ++$i) {
        my $spec = $ARG_SPEC_ARRAY[$i];
        if (($$spec{'flags'} & OPTION_REQUIRED) != 0) {
            my $name = $$spec{'name'};
            printError "Argument $name is required but not specified.\n";
            return 0;
        }
    }
    return 1;
}
sub getOptionArguments { # (Count, MinIndex, EatenArgs)
    my ($count, $minIndex, $eaten_args) = @_;
    my $stopIndex = 10000000;
    my @result;
    if ($count == 0) { return \@result; }
    for (my $i=0; $i<scalar @ARGUMENTS; ++$i) {
        printSpam "Processing arg '$ARGUMENTS[$i]'.\n";
        if ($i <= $stopIndex && $ARGUMENTS[$i] eq '--') {
            printSpam "Found --. Further dash prefixed args will be args\n";
            $stopIndex = $i;
        } elsif ($i <= $stopIndex && $ARGUMENTS[$i] =~ /^-[^0-9]/) {
            printSpam "Option declaration. Ignoring\n";
        } elsif (exists $$eaten_args{$i}) {
            printSpam "Already eaten argument. Ignoring\n";
        } elsif ($i < $minIndex) {
            printSpam "Not eaten, but too low index to be option arg.\n";
        } else {
            printSpam "Using argument\n";
            push @result, $ARGUMENTS[$i];
            $$eaten_args{$i} = 1;
            if (scalar @result == $count) {
                return \@result;
            }
        }
    }
    printSpam "Too few option arguments found. Returning undef\n";
    return;
}
sub parseOption { # (Id, EatenArgs, Index)
    my ($id, $eaten_args, $index) = @_;
    if (!exists $OPTION_SPEC{$id}) {
        printError "Unknown option '$id'.\n";
        return 0;
    }
    my $spec = $OPTION_SPEC{$id};
    my $args = getOptionArguments($$spec{'arg_count'}, $index, $eaten_args);
    if (!defined $args) {
        printError "Too few arguments for option '$id'.\n";
        return 0;
    }
    printSpam, "Found " . (scalar @$args) . " args\n";
    if (!&{$$spec{'result_evaluator'}}($id, $args)) {
        printDebug "Failed evaluate result of option '$id'. Aborting\n";
        return 0;
    }
    printSpam "Successful parsing of option '$id'.\n";
    return 1;
}
sub parseOptions { # (EatenArgs)
    my ($eaten_args) = @_;
    for (my $i=0; $i<scalar @ARGUMENTS; ++$i) {
        if ($ARGUMENTS[$i] =~ /^--(.+)$/) {
            my $id = $1;
            printSpam "Parsing long option '$id'.\n";
            if (!&parseOption($id, $eaten_args, $i)) {
                return 0;
            }
        } elsif ($ARGUMENTS[$i] =~ /^-([^0-9].*)$/) {
            my $shortids = $1;
            while ($shortids =~ /^(.)(.*)$/) {
                my ($id, $rest) = ($1, $2);
                printSpam "Parsing short option '$id'.\n";
                if (!&parseOption($id, $eaten_args, $i)) {
                    return 0;
                }
                $shortids = $rest;
            }
        }
    }
    printSpam "Successful parsing of all options.\n";
    return 1;
}
sub writeArgumentSyntax { # ()
    printResult "\nArguments:\n";
    my $max_name_length = &getMaxNameLength();
    if ($max_name_length > 30) { $max_name_length = 30; }
    foreach my $spec (@ARG_SPEC_ARRAY) {
        &writeArgumentName($$spec{'name'}, $max_name_length);
        &writeOptionDescription($spec, $max_name_length + 3);
    }
}
sub getMaxNameLength { # ()
    my $max = 0;
    foreach my $spec (@ARG_SPEC_ARRAY) {
        my $len = 1 + length $$spec{'name'};
        if ($len > $max) { $max = $len; }
    }
    return $max;
}
sub writeArgumentName { # (Name, MaxNameLength)
    my ($name, $maxnamelen) = @_;
    printResult " $name";
    my $totalLength = 1 + length $name;
    if ($totalLength <= $maxnamelen) {
        for (my $i=$totalLength; $i<$maxnamelen; ++$i) {
            printResult ' ';
        }
    } else {
        printResult "\n";
        for (my $i=0; $i<$maxnamelen; ++$i) {
            printResult ' ';
        }
    }
    printResult " : ";
}
sub writeOptionSyntax { # ()
    printResult "\nOptions:\n";
    my $max_id_length = &getMaxIdLength();
    if ($max_id_length > 30) { $max_id_length = 30; }
    my $cachedHeader;
    foreach my $spec (@OPTION_SPEC_ARRAY) {
        if (ref($spec) eq 'HASH') {
            my $flags = $$spec{'flags'};
            if ($SHOW_HIDDEN || ($flags & OPTION_SECRET) == 0) {
                if (defined $cachedHeader) {
                    printResult "\n";
                    if ($cachedHeader ne '') {
                        &writeOptionHeader($cachedHeader);
                    }
                    $cachedHeader = undef;
                }
                &writeOptionId($spec, $max_id_length);
                &writeOptionDescription($spec, $max_id_length + 3);
            }
        } else {
            $cachedHeader = $spec;
        }
    }
}
sub getMaxIdLength { # ()
    my $max = 0;
    foreach my $spec (@OPTION_SPEC_ARRAY) {
        if (!ref($spec)) { next; } # Ignore option headers
        my $size = 0;
        foreach my $id (@{ $$spec{'ids'} }) {
            my $len = length $id;
            if ($len == 1) {
                $size += 3;
            } else {
                $size += 3 + $len;
            }
        }
        if ($size > $max) { $max = $size; }
    }
    return $max;
}
sub writeOptionId { # (Spec, MaxNameLength)
    my ($spec, $maxidlen) = @_;
    my $totalLength = 0;
    foreach my $id (@{ $$spec{'ids'} }) {
        my $len = length $id;
        if ($len == 1) {
            printResult " -" . $id;
            $totalLength += 3;
        } else {
            printResult " --" . $id;
            $totalLength += 3 + $len;
        }
    }
    if ($totalLength <= $maxidlen) {
        for (my $i=$totalLength; $i<$maxidlen; ++$i) {
            printResult ' ';
        }
    } else {
        printResult "\n";
        for (my $i=0; $i<$maxidlen; ++$i) {
            printResult ' ';
        }
    }
    printResult " : ";
}
sub writeOptionDescription { # (Spec, MaxNameLength)
    my ($spec, $maxidlen) = @_;
    my $width = ConsoleOutput::getTerminalWidth() - $maxidlen;
    my $desc = $$spec{'description'};
    my $min = int ($width / 2);
    while (length $desc > $width) {
        if ($desc =~ /^(.{$min,$width}) (.*)$/s) {
            my ($first, $rest) = ($1, $2);
            printResult $first . "\n";
            for (my $i=0; $i<$maxidlen; ++$i) {
                printResult ' ';
            }
            $desc = $rest;
        } else {
            last;
        }
    }
    printResult $desc . "\n";
}
sub writeOptionHeader { # (Description)
    my ($desc) = @_;
    my $width = ConsoleOutput::getTerminalWidth();
    my $min = 2 * $width / 3;
    while (length $desc > $width) {
        if ($desc =~ /^(.{$min,$width}) (.*)$/s) {
            my ($first, $rest) = ($1, $2);
            printResult $first . "\n";
            $desc = $rest;
        } else {
            last;
        }
    }
    printResult $desc . "\n";
}
sub validHost { # (Hostname)
    my ($host) = @_;
    if ($host !~ /^[a-zA-Z][-_a-zA-Z0-9\.]*$/) {
        return 0;
    }
    if (system("host $host >/dev/null 2>/dev/null") != 0) {
        return 0;
    }
    return 1;
}
