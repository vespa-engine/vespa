# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package Log;

require 5.006_001;
use strict;
use warnings;
use Sys::Hostname;


#initialize
my $VESPA_LOG_TARGET = $ENV{VESPA_LOG_TARGET} || "fd:2";
my $VESPA_LOG_LEVELS = $ENV{VESPA_LOG_LEVELS} || "all -debug -spam";

my $SERVICE = $ENV{VESPA_SERVICE_NAME} || "-";
my $HOST = hostname;

my %LEVEL = ( error =>   0x01,
              warning => 0x02,
              info =>    0x04,
              config =>  0x08,
              debug =>   0x10,
              spam =>    0x20,
              all =>     0x3f);

my %instance = (); 

my $VESPA_LOG_FILTER;
foreach (split(/\s+/, $VESPA_LOG_LEVELS)) {
    /(-?)(\S+)/ || die "Log level parse error: $_";
    my ($inv, $value) = ($1, $LEVEL{$2}); 
    die "Unknown level: $2" unless $value;
    if ($inv) {
        $VESPA_LOG_FILTER &= ~$value;
    } else {
        $VESPA_LOG_FILTER |= $value;
    }
}

if ($VESPA_LOG_TARGET =~ /fd:(\d+)/) {
    open(TARGET, ">&=$1") || die $!;
} elsif ($VESPA_LOG_TARGET =~ /file:(.+)/) {
    open(TARGET, ">>$1") || die $!;
} else {
    die "Illegal target $VESPA_LOG_TARGET";
}
select(TARGET); $| = 1;

sub new {
    my ($self, $name)  = @_;
    my $type = ref($self) || $self;
    $instance{$name} = bless { component => $name } unless $instance{$name};
    return $instance{$name};
}

sub open_target {
    return unless $VESPA_LOG_TARGET =~ /file:(.+)/;
    close(TARGET);
    open(TARGET, ">>$1") || die $!;
    select(TARGET); $| = 1;
}

sub escape_message {
    $_ = shift;
    s/\n/\\n/g;
    s/\r/\\r/g;
    s/\t/\\t/g;
    s/([\x80-\xFF])/sprintf("\\x%x",ord($1))/eg;
    return $_;
}

sub log {
    my ($this, $level, $msg) = @_;
    my $component = ref($this) ? $this->{component} : "-";
    die "Unknown logging level: '$level'" unless $LEVEL{$level};
    return unless $VESPA_LOG_FILTER & $LEVEL{$level};

    open_target;
    $msg = escape_message($msg);

    # format: time host pid service component level message
    print TARGET (time()."\t$HOST\t$$\t$SERVICE\t$component\t$level\t$msg\n");
}

sub error   { &log(shift, "error",   @_); }
sub warning { &log(shift, "warning", @_); }
sub info    { &log(shift, "info",    @_); }
sub config  { &log(shift, "config",  @_); }
sub debug   { &log(shift, "debug",   @_); }
sub spam    { &log(shift, "spam",    @_); }


$SIG{__DIE__}  = sub { Log->log("error",   @_); };
$SIG{__WARN__} = sub { Log->log("warning", @_); };
