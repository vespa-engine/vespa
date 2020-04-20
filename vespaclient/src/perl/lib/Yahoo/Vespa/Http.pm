# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Simple HTTP wrapper library
#
# Intentions:
#   - Make it very easy for programs to do HTTP requests towards Rest APIs.
#   - Allow unit tests to fake returned data
#   - Allow using another external dependency for HTTP without affecting apps
#
# An HTTP request returns a Response that is a hash containing:
#   code - The HTTP status code
#   status - The HTTP status string that comes with the code
#   content - The content of the reply
#   all - The entire response coming over the TCP connection
#         This is here for debugging and testing. If you need specifics like
#         HTTP headers, we should just add specific fields for them rather than
#         to parse all content.
#
# Examples:
#
# my @headers = (
#     "X-Foo" => 'Bar'
# );
# my @params = (
#     "verbose" => 1
# );
# 
# $response = Http::get('localhost', 80, '/status.html');
# $response = Http::get('localhost', 80, '/status.html', \@params, \@headers);
# $response = Http::request('POST', 'localhost', 80, '/test', \@params,
#                           "Some content", \@headers);
# 

package Yahoo::Vespa::Http;

use strict;
use warnings;

use Net::INET6Glue::INET_is_INET6;
use LWP::Simple ();
use URI ();
use URI::Escape qw( uri_escape );
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Utils;

my %LEGAL_TYPES;
my $BROWSER;
my $EXECUTE;

&initialize();

return 1;

######################## Externally usable functions #######################

sub get { # (Host, Port, Path, Params, Headers) -> Response
    my ($host, $port, $path, $params, $headers) = @_;
    return &request('GET', $host, $port, $path, $params, undef, $headers);
}
sub request { # (Type, Host, Port, Path, Params, Content, Headers) -> Response
    my ($type, $host, $port, $path, $params, $content, $headers) = @_;
    if (!exists $LEGAL_TYPES{$type}) {
        confess "Invalid HTTP type '$type' specified.";
    }
    if (defined $params && ref($params) ne "ARRAY") {
        confess 'HTTP request attempted without array ref for params';
    }
    if (defined $headers && ref($headers) ne "ARRAY") {
        confess 'HTTP request attempted without array ref for headers';
    }
    return &$EXECUTE(
            $type, $host, $port, $path, $params, $content, $headers);
}
sub encodeForm { # (KeyValueMap) -> RawString
    my $data;
    for (my $i=0; $i < scalar @_; $i += 2) {
        my ($key, $value) = ($_[$i], $_[$i+1]);
        if ($i != 0) {
            $data .= '&';
        }
        $data .= uri_escape($key);
        if (defined $value) {
            $data .= '=' . uri_escape($value);
        }
    }
    return $data;
}

################## Functions for unit tests to mock internals ################

sub setHttpExecutor { # (Function)
    $EXECUTE = $_[0]
}

############## Utility functions - Not intended for external use #############

sub initialize { # ()
    %LEGAL_TYPES = map { $_ => 1 } ( 'GET', 'POST', 'PUT', 'DELETE');
    $BROWSER = LWP::UserAgent->new;
    my $tls_enabled = $ENV{'VESPA_TLS_ENABLED'};
    if (defined $tls_enabled and $tls_enabled eq '1') {
        $BROWSER->ssl_opts( SSL_version => 'TLSv12');
        my $hostname_verification_disabled = $ENV{'VESPA_TLS_HOSTNAME_VALIDATION_DISABLED'};
        if (defined $hostname_verification_disabled and $hostname_verification_disabled eq '1') {
            $BROWSER->ssl_opts( verify_hostname => 0);
        }
        $BROWSER->ssl_opts( SSL_cipher_list => 'ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-CHACHA20-POLY1305:TLS13-AES-128-GCM-SHA256:TLS13-AES-256-GCM-SHA384:TLS13-CHACHA20-POLY1305-SHA256' );
    }
    if (defined $ENV{'VESPA_TLS_CA_CERT'}) {
        $BROWSER->ssl_opts( SSL_ca_file => $ENV{'VESPA_TLS_CA_CERT'} );
    }
    if (defined $ENV{'VESPA_TLS_CERT'}) {
        $BROWSER->ssl_opts( SSL_cert_file => $ENV{'VESPA_TLS_CERT'} );
    }
    if (defined $ENV{'VESPA_TLS_PRIVATE_KEY'}) {
        $BROWSER->ssl_opts( SSL_key_file => $ENV{'VESPA_TLS_PRIVATE_KEY'} );
    }
    $BROWSER->agent('Vespa-perl-script');
    $EXECUTE = \&execute;
}
sub execute { # (Type, Host, Port, Path, Params, Content, Headers) -> Response
    my ($type, $host, $port, $path, $params, $content, $headers) = @_;
    if (!defined $headers) { $headers = []; }
    if (!defined $params) { $params = []; }
    my $url = URI->new(&buildUri($host, $port, $path));
    if (defined $params) {
        $url->query_form(@$params);
    }
    printSpam "Performing HTTP request $type '$url'.\n";
    my $response;
    if ($type eq 'GET') {
        !defined $content or confess "$type requests cannot have content";
        $response = $BROWSER->get($url, @$headers);
    } elsif ($type eq 'POST') {
        if (defined $content) {
            $response = $BROWSER->post($url, $params, @$headers,
                                       'Content' => $content);
        } else {
            $response = $BROWSER->post($url, $params, @$headers);
        }
    } elsif ($type eq 'PUT') {
        if (defined $content) {
            $response = $BROWSER->put($url, $params, @$headers,
                                     'Content' => $content);
        } else {
            $response = $BROWSER->put($url, $params, @$headers);
        }
    } elsif ($type eq 'DELETE') {
        !defined $content or confess "$type requests cannot have content";
        $response = $BROWSER->put($url, $params, @$headers);
    } else {
        confess "Unknown type $type";
    }
    my $autoLineBreak = enableAutomaticLineBreaks(0);
    printSpam "Got HTTP result: '" . $response->as_string . "'\n";
    enableAutomaticLineBreaks($autoLineBreak);
    return (
        'code' => $response->code,
        'headers' => $response->headers(),
        'status' => $response->message,
        'content' => $response->content,
        'all' => $response->as_string
    );
}
sub buildUri { # (Host, Port, Path) -> UriString
    my ($host, $port, $path) = @_;
    my $tls_enabled = $ENV{'VESPA_TLS_ENABLED'};
    my $uri = (defined $tls_enabled and $tls_enabled eq '1') ? "https:" : "http:";
    if (defined $host) {
        $uri .= '//' . $host;
        if (defined $port) {
            $uri .= ':' . $port;
        }
    }
    if (defined $path) {
        $uri .= $path;
    }
    return $uri;
}
