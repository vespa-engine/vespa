#!/usr/bin/perl -w
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This is a tool to parse the output given by vespamalloc when dumping all
# memory usage with stacktraces.
#
# This tool will try to group similar stack traces together, identify them,
# and show a report of how much memory you use for various tasks in the given
# dump.
#
# Suggested usage:
#   - Manually rotate your vespa.log file.
#   - Send signal to vespamalloc to make it dump stack traces.
#   - When dump is done, cat all the log files generated in correct order into
#     one file.
#   - Send this file to STDIN of this script.
#
# By doing it like this, it's easy to rerun this script and to update it to
# work with anything encountered in your report that this script doesn't
# currently handle.
#
# It is for instance likely, that you need to add some more regexes to match
# unknown stack traces into appropriate groups.

use strict;

# If this variable is set, unrecognized stack traces will be concatenated to
# one unrecognized chunk.
my $combineUnrecognizedStacks = 1;
# If this variable is set, all stack traces are shown, no matter if they are
# recognized or not.
my $showRecognizedStacks = 0;
# If this variable is set, memory usage is grouped on the first word in the
# group name. Thus, by using a proper name convention, one can distinguish
# how much memory are used on various parts of the system
my $sortOnCategories = 1;

# These patterns are used to recognized what memory is used for based on the
# stacktrace of the allocation. The keys are regular expressions that can match
# stacktraces. The value is the group name this allocation will be marked as if
# the trace matches the regex. If matching multiple entries, the first entry
# found will be the match.

# Note that as I made this tool to analyze VDS memory usage, the stuff added is
# just stuff I found using memory in that current run. We should probably add
# more patterns as needed.
my %patterns = (
        
    'FRT_MemoryTub::BigAlloc.*mbus::MessageBus::send.*' => 'RPC send messagebus message - big alloc',
    'FRT_MemoryTub::BigAlloc.*storage::rpc::Destination::sendRequest', => 'RPC send storage API command - big alloc',
    'FRT_RPCRequestPool::AllocRPCRequest.*storage::rpc::Destination::sendRequest' => 'RPC send storage API command',

    'FNET_DataBuffer::Shrink.*FNET_Connection::HandleWriteEvent' => 'RPC shrinked write buffer',
    'FNET_DataBuffer::Shrink.*FNET_Connection::HandleReadEvent' => 'RPC shrinked read buffer',
    
    'FRT_MemoryTub::BigAlloc.*FRT_RPCRequestPacket::Decode.*FNET_Connection::HandleReadEvent' => 'RPC read event - decode - big alloc',
    'FRT_RPCRequestPool::AllocRPCRequest.*FNET_Connection::HandlePacket.*FNET_Connection::HandleReadEvent' => 'RPC read event - handle packet',
    'FNET_DataBuffer::Pack.*FNET_Connection::HandleReadEvent' => 'RPC packed read buffer',
    'FNET_DataBuffer::Pack.*FNET_Connection::HandleWriteEvent' => 'RPC packed write buffer',
    'FNET_PacketQueue_NoLock::ExpandBuf.*FNET_Connection::HandleWriteEvent()' => 'RPC expand write buffer',
    'FNET_ChannelPool::AllocChannelCluster.*storage::rpc::Destination::sendRequest' => 'RPC storage API alloc channel cluster',
    
    'storage::SlotFileBuffer::getAlignedBuffer.*SlotFileImpl::close' => 'Persistence layer - move buffer to cache',
    'storage::SlotFileBuffer::getAlignedBuffer.*SlotFileBuffer::getBuffer' => 'Persistence layer - Get aligned buffer',
    'storage::SlotFileBuffer::getInputBuffer' => 'Persistence layer - Input buffer',
    'storage::SlotFileBuffer::getIndexBuffer' => 'Persistence layer - Index buffer',
    'storage::SlotFileBuffer::getOutputBuffer' => 'Persistence layer - Output buffer',

    'storage::api::\S*::makeReply.*storage::MessageDispatcher::handleCommand' => 'Messages - Replies stored in message dispatcher',
    'document::SerializableArray::onDeserialize.*storage::CommunicationManager::onEvent\(std::auto_ptr<storage::rpc::Event>\)'
        => 'Messages - Documents from storage API messages - serializable arrays',
    'document::SerializableDocumentSharedPointer.*storage::rpc::Handle::onEvent\(std::auto_ptr<storage::rpc::Event>\)'
        => 'Messages - Documents from storage API messages - original byte buffers',
    'storage::api::StorageMessageAddress::create.*storage::rpc::Handle::onEvent\(std::auto_ptr<storage::rpc::Event>\)'
        => 'Messages - Storage API message addresses',
    'document::IdString::createIdString.*storage::api::StorageMessage::onDeserialize'
        => 'Messages - Document identifiers',
    'storage::api::ApplyBucketDiffCommand::Entry::Entry.*storage::FileStorThread::run'
        => 'Messages - Merge apply entries',
    'storage::api::ApplyBucketDiffCommand::Entry::Entry.*storage::api::StorageMessage::onDeserialize'
        => 'Messages - Merge apply entries',
        => 'Messages - Queued multi operation commands - cloned for local use in message dispatcher',

    'storage::BufHolder::reserve.*storage::FileStorThread::onGetIterCommand' => 'Visiting - Downsized docblocks',
    'storage::BufHolder::resize.*storage::FileStorThread::onGetIterCommand' => 'Visiting - Upsized docblocks',

    'JudyLIns.*storage::StorBucketDatabase::(get|insert)' => 'Bucket database - Judy',
    'JudyLDel.*WrappedEntry::remove' => 'Bucket database - Judy',
    'std::vector<.*storage::LockableMap.*::WrappedEntry::remove()' => 'Bucket database - Lockable map lock list',

    'document::Printable::toString'
        => 'Other - Temporary data created during toString operations',

    'metrics::MetricsManager::getSnapshotForConsumer' => 'Metrics - Snapshot created',
    'storage::BucketManagerMetrics::BucketManagerMetrics\(\)' => 'Metrics - Stored metrics',
    'storage::FileStorThreadMetrics::FileStorThreadMetrics\(std::basic_string' => 'Metrics - Stored metrics',
    'metrics::MetricsSet::addAll' => 'Metrics - Add all',
    'storage::StatusMetricConsumer::run\(\)' => 'Metrics',

    'slobrok::api::MirrorAPI::PerformTask\(\)' => 'Slobrok - Mirror API perform task'

);

my $signal;
my $starttime;
my $size;
my %vals;
my %counts;

my $mallocfault;
my $local = 0;
my $global = 0;
my $globwaste = 0;

# Go through all the input gotten on STDIN.
foreach (<>) {
    if (/^(\d+)\.\d+\s+\S+\s+\d+\s+\S+\s+\S+\s+warning\s+(.*)$/) {
        # We only care for warnings to logs printed by vespamalloc
        my ($time, $line) = ($1, $2);
        if ($line =~ /SignalHandler (\d+) caught/) {
            # If a dump is just starting, reset all the variables that keeps
            # state
            $signal = $1;
            $size = 0;
            %vals = ();
            %counts = ();
            $local = 0;
            $global = 0;
            $globwaste = 0;
            $mallocfault = undef;
            $starttime = $time;
            print "\nProcessing dump from " . localtime($starttime) . "\n";
        } elsif ($line =~ /^SignalHandler $signal done/) {
            # If we are at the end of the vespamalloc report, print a report of
            # what we have found.
            &printReport();            
            $starttime = undef;
        } elsif (!$starttime) {
            # Ignore output that is not within dump
        } elsif ($line =~ /^SC\s*\d+\(\s*(\d+)\)\s*GetAlloc\(\s*(\d+)\)\s*GetFree\(\s*\d+\)\s*ExChangeAlloc\(\s*(\d+)\)\s*ExChangeFree\(\s*(\d+)\)\s*ExactAlloc\(\s*(\d+)\)\s*Returned\(\s*(\d+)\)\s*Malloc\(\s*(\d+)\)\s*/) {
            # Track size groups allocated in hopes of figuring out how much data
            # waste there is in vespa malloc
            my ($size, $get, $exalloc, $exfree, $exact, $returned, $malloc) = ($1, $2, $3, $4, $5, $6, $7);
            #print "$line\n";
            #print "Size $size, GetAlloc $get, ExChangeAlloc $exalloc, ExChangeFree $exfree, ExactAlloc $exact, Returned $returned, Malloc $malloc\n";
            my $mult = 65536;
            if ($size > $mult) { $mult = $size; }
            my $alloced = $get + $exalloc + $exact;
            my $ret = $exfree + $returned;
            if ($size != 2097152) {
                #print "Adding ($alloced - $ret) * $mult = " . (($alloced - $ret) * $mult) . "\n";
                my $global = ($alloced - $ret) * $mult;
                my $waste = $malloc * 1024 * 1024 - ($alloced - $ret) * $mult;
                #print "Size $size - Global $global - Waste $waste\n";
                #$global += ($alloced - $ret) * $mult;
                if ($waste >= 0) {
                    $global += $global;
                    $globwaste += $waste;
                } else {
                    $mallocfault = 1;
                }
            } else {
                $mallocfault = 1;
            }
        } elsif ($line =~ /SC\s*\d+\(\s*(\d+)\)\s*Local\(\s*(\d+)\)/) {
            # Track size groups allocated in hopes of figuring out how much data
            # waste there is in vespa malloc
            
            #print "Local $1 * $2 = ".($1 * $2)."\n";
            $local += $1 * $2;
        } elsif ($line =~ /^SizeClass\s*(\d+)/) {
            #print "$line\n";
        } elsif ($line =~ /^(Usage)/) {
            print "Vespa Malloc $line\n";
        } elsif ($line =~ /(DataSegment|Free|Start)/) { 
        } elsif ($line =~ /^(\d+)\s+:\s+\{\s+\S+\s+\S+\s+(\S+[^\}]*)/) {
            # This should match any stacktrace reported. Add this stacktrace
            # to the report.
            my ($count, $value) = ($1, $2);
            # Unify stack trace
            $value =~ s/0x[0-9a-f]+//g;
            $value =~ s/\(\d+\)//g;
            $value =~ s/\(\)//g;
            # Remove multiple UNKNOWN lines in backtrace
            my @stack = split /\s+/, $value;
            $value = '';
            my $last = "";
            foreach (@stack) {
                my $current = &cppfilt($_);
                if ($last !~ /UNKNOWN/ || $current !~ /UNKNOWN/) { # Don't print multiple unknown entries after one another
                    $value .= "\n  " . &cppfilt($_);
                }
                $last = $current;
            }
            # Detect known stack traces
            
            #print "$count - $value\n";
            my $replaced = 0;
            foreach my $pat (keys %patterns) {
                #print "Does $value match $pat?\n";
                if ($value =~ /$pat/s) {
                    if ($showRecognizedStacks) {
                        $value = $patterns{$pat} . $value;
                    } else {
                        $value = $patterns{$pat};
                    }
                    $replaced = 1;
                    last;
                }
            }
            if (!$replaced) {
                if ($combineUnrecognizedStacks) {
                    $value = "Unrecognized memory allocations";
                } elsif ($sortOnCategories) {
                    $value = "Unrecognized memory allocations" . $value;
                }
            }
            if (exists $vals{$value}) {
                $vals{$value} += $count * $size; 
                $counts{$value} += $count;
            } else {
                $vals{$value} = $count * $size; 
                $counts{$value} = $count;
            }
        } elsif ($line =~ /^Allocated Blocks SC\s*\d+\(\s*(\d+)/) {
            $size = $1;
            #print "Size: $1\n";
        } else {
            #print "$line\n";
        }
    } else {
        #print "$_";
    }
}
if ($starttime) {
    print "Input stopped in incomplete trace.\n";
}

exit(0);

my %filtered;

sub cppfilt {
    my $val = $_[0];
    if ($val =~ /UNKNOWN/) {
        return "UNKNOWN";
    } elsif (exists $filtered{$val}) {
        return $filtered{$val};
    } else {
        my $result = `c++filt $val`;
        chomp $result;
        $filtered{$val} = $result;
        return $result;
    }
}

sub getByteString {
    my $val = $_[0];
    if ($val < 5000) {
        return sprintf("%d B", $val);
    } elsif ($val < 5000000) {
        return sprintf("%d kB", $val / 1024);
    } else {
        return sprintf("%d MB", $val / (1024 * 1024));
    }
}

sub printReport {
    print "Total vespa malloc unused thread local data: " . &getByteString($local) . "\n";
    #print "Total global data: $global\n";
    print "Total vespa malloc global waste data: " . &getByteString($globwaste). "\n";

    my $total = 0;
    foreach (keys %vals) {
        $total += $vals{$_};
    }
    if (defined $mallocfault) {
        print "Warning: Some sketchy numbers from vespa malloc, so global and thread local "
            . "data is probably inaccurate.\n";
    }

    print "\nSummary of allocated data:\n";
    print &getByteString($total) . " bytes tracked total\n";

    if ($sortOnCategories) {
        my %categories;
        foreach (keys %vals) {
            if (/^\s*(\S+)/) {
                my $category = $1;
                if (exists $categories{$category}) {
                    ${$categories{$category}}[0] += $vals{$_};
                    push @{$categories{$category}}, $_;
                } else {
                    $categories{$category} = [ $vals{$_}, $_ ];
                }
            }
        }
        foreach (sort { ${$categories{$b}}[0] <=> ${$categories{$a}}[0] } keys %categories) {
            my @values = @{ $categories{$_} };
            print &getByteString($values[0]) . " - $_\n";
            shift @values;
            
            foreach my $val (sort { $vals{$b} <=> $vals{$a} } @values) {
                my $stack = $val;
                $stack =~ s/\n/\n    /sg;
                print "    " . &getByteString($vals{$val}) . " - " . $counts{$val} . " allocations - $stack\n";
            }
        }
    } else {
        foreach (sort { $vals{$b} <=> $vals{$a} } keys %vals) {
            print &getByteString($vals{$_}) . " - " . $counts{$_} . " allocations - $_\n";
        }
    }
}
