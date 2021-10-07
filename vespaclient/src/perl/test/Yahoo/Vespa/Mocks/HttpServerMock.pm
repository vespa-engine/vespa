# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# A mock of an HTTP server, such that HTTP client library can be tested.
#
# Known limitations:
#   - Does line by line reading of TCP data, so the content part of the HTML
#     request has to end in a newline, otherwise, the server will block waiting
#     for more data.
#
# Default connection handler:
#   - If no special case, server returns request 200 OK, with the complete
#     client request as text/plain utf8 content.
#   - If request matches contenttype=\S+ (Typically due to setting a URI
#     parameter), the response will contain the content of the request with the
#     given content type set.
#   - If request matches code=\d+ (Typically due to setting a URI parameter),
#     the response will use that return code.
#   - If request matches status=\S+ (Typically due to setting a URI parameter),
#     the response will use that status line
#

package Yahoo::Vespa::Mocks::HttpServerMock;

use strict;
use warnings;
use IO::Socket::IP;
use URI::Escape;

BEGIN { # - Set up exports for module
    use base 'Exporter';
    our @EXPORT = qw(
        setupTestHttpServer
    );
}

my $HTTP_TEST_SERVER;
my $HTTP_TEST_SERVER_PORT;
my $HTTP_TEST_SERVER_PID;
my $CONNECTION_HANDLER = \&defaultConnectionHandler;

END { # - Kill forked HTTP handler process on exit
    if (defined $HTTP_TEST_SERVER_PID) {
        kill(9, $HTTP_TEST_SERVER_PID);
    }
}

return 1;

####################### Default exported functions ############################

sub setupTestHttpServer { # () -> HttpServerPort
    my $portfile = "/tmp/vespaclient.$$.perl.httptestserverport";
    unlink($portfile);
    my $pid = fork();
    if ($pid == 0) {
        $HTTP_TEST_SERVER = IO::Socket::IP->new(
            'Proto' => 'tcp',
            'LocalPort' => 0,
            'Listen' => SOMAXCONN,
            'ReuseAddr' => 1,
        );
        # print "Started server listening to port " . $HTTP_TEST_SERVER->sockport()
        #    . "\n";
        my $fh;
        open ($fh, ">$portfile") or die "Failed to write port used to file.";
        print $fh "<" . $HTTP_TEST_SERVER->sockport() . ">";
        close $fh;
        defined $HTTP_TEST_SERVER or die "Failed to set up test HTTP server";
        while (1) {
            &$CONNECTION_HANDLER();
        }
        exit(0);
    } else {
        $HTTP_TEST_SERVER_PID = $pid;
        while (1) {
            if (-e $portfile) {
                my $port = `cat $portfile`;
                chomp $port;
                if (defined $port && $port =~ /\<(\d+)\>/) {
                    #print "Client using port $1\n";
                    $HTTP_TEST_SERVER_PORT = $1;
                    last;
                }
            }
            sleep(0.01);
        }
    }
    unlink($portfile);
    return $HTTP_TEST_SERVER_PORT;
}

####################### Internal utility functions ############################

sub defaultConnectionHandler {
    my $client = $HTTP_TEST_SERVER->accept();
    defined $client or die "No connection to accept?";
    my $request;
    my $line;
    my $content_length = 0;
    my $content_type;
    while ($line = <$client>) {
        if ($line =~ /^(.*?)\s$/) {
            $line = $1;
        }
        if ($line =~ /Content-Length:\s(\d+)/) {
            $content_length = $1;
        }
        if ($line =~ /contenttype=(\S+)/) {
            $content_type = uri_unescape($1);
        }
        #print "Got line '$line'\n";
        if ($line eq '') {
            last;
        }
        $request .= $line . "\n";
    }
    if ($content_length > 0) {
        $request .= "\n";
        if (defined $content_type) {
            $request = "";
        }
        my $read = 0;
        while ($line = <$client>) {
            $read += length $line;
            if ($line =~ /^(.*?)\s$/) {
                $line = $1;
            }
            $request .= $line;
            if ($read >= $content_length) {
                last;
            }
        }
    }
    # print "Got request '$request'.\n";
    $request =~ s/\n/\r\n/g;
    my $code = 200;
    my $status = "OK";
    if ($request =~ /code=(\d+)/) {
        $code = $1;
    }
    if ($request =~ /status=([A-Za-z0-9]+)/) {
        $status = $1;
    }
    my $response = "HTTP/1.1 $code $status\n";
    if (defined $content_type) {
        $response .= "Content-Type: $content_type\n";
    } else {
        $response .= "Content-Type: text/plain; charset=utf-8\n";
    }
    $response .= "Content-Length: " . (length $request) . "\n"
               . "\n";
    $response =~ s/\n/\r\n/g;
    $response .= $request;
    print $client $response;
    close $client;
}
