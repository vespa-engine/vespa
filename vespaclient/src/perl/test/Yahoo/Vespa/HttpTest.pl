# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Tests of the Http wrapper library..
#
# NOTE: Test server set up does not support content not ending in newline.
# 

use strict;
use Test::More;
use Yahoo::Vespa::Mocks::HttpServerMock;

BEGIN {
    use_ok( 'Yahoo::Vespa::Http' );
    *Http:: = *Yahoo::Vespa::Http::
}
require_ok( 'Yahoo::Vespa::Http' );

my $httpTestServerPort = setupTestHttpServer();
ok(defined $httpTestServerPort, "Test server set up");

&testSimpleGet();
&testAdvancedGet();
&testFailingGet();
&testSimplePost();
&testJsonReturnInPost();

done_testing();

exit(0);

sub filterRequest {
    my ($request) = @_;
    $request =~ s/\r//g;
    $request =~ s/(Content-Length:\s*)\d+/$1##/g;
    $request =~ s/(Host: localhost:)\d+/$1##/g;
    $request =~ s/(?:Connection|TE|Client-[^:]+):[^\n]*\n//g;

    return $request;
}

sub testSimpleGet {
    my %r = Http::get('localhost', $httpTestServerPort, '/foo');
    is( $r{'code'}, 200, "Get request code" );
    is( $r{'status'}, 'OK', "Get request status" );

    my $expected = <<EOS;
HTTP/1.1 200 OK
Content-Length: ##
Content-Type: text/plain; charset=utf-8

GET /foo HTTP/1.1
Host: localhost:##
User-Agent: Vespa-perl-script
EOS
    is( &filterRequest($r{'all'}), $expected, 'Get result' );
}

sub testAdvancedGet {
    my @headers = ("X-Foo" => 'Bar');
    my @uri_param = ("uricrap" => 'special=?&%value',
                     "other" => 'hmm');
    my %r = Http::request('GET', 'localhost', $httpTestServerPort, '/foo',
                          \@uri_param, undef, \@headers);
    is( $r{'code'}, 200, "Get request code" );
    is( $r{'status'}, 'OK', "Get request status" );

    my $expected = <<EOS;
HTTP/1.1 200 OK
Content-Length: ##
Content-Type: text/plain; charset=utf-8

GET /foo?uricrap=special%3D%3F%26%25value&other=hmm HTTP/1.1
Host: localhost:##
User-Agent: Vespa-perl-script
X-Foo: Bar
EOS
    is( &filterRequest($r{'all'}), $expected, 'Get result' );
}

sub testFailingGet {
    my @uri_param = ("code" => '501',
                     "status" => 'Works');
    my %r = Http::request('GET', 'localhost', $httpTestServerPort, '/foo',
                          \@uri_param);
    is( $r{'code'}, 501, "Get request code" );
    is( $r{'status'}, 'Works', "Get request status" );

    my $expected = <<EOS;
HTTP/1.1 501 Works
Content-Length: ##
Content-Type: text/plain; charset=utf-8

GET /foo?code=501&status=Works HTTP/1.1
Host: localhost:##
User-Agent: Vespa-perl-script
EOS
    is( &filterRequest($r{'all'}), $expected, 'Get result' );
}

sub testSimplePost {
    my @uri_param = ("uricrap" => 'Rrr' );
    my %r = Http::request('POST', 'localhost', $httpTestServerPort, '/foo',
                          \@uri_param, "Some content\n");
    is( $r{'code'}, 200, "Get request code" );
    is( $r{'status'}, 'OK', "Get request status" );

    my $expected = <<EOS;
HTTP/1.1 200 OK
Content-Length: ##
Content-Type: text/plain; charset=utf-8

POST /foo?uricrap=Rrr HTTP/1.1
Host: localhost:##
User-Agent: Vespa-perl-script
Content-Length: ##
Content-Type: application/x-www-form-urlencoded

Some content
EOS
    is( &filterRequest($r{'all'}), $expected, 'Get result' );
}

sub testJsonReturnInPost
{
    my @uri_param = ("contenttype" => 'application/json' );
    my $json = "{ \"key\" : \"value\" }\n";
    my %r = Http::request('POST', 'localhost', $httpTestServerPort, '/foo',
                          \@uri_param, $json);
    is( $r{'code'}, 200, "Get request code" );
    is( $r{'status'}, 'OK', "Get request status" );

    my $expected = <<EOS;
HTTP/1.1 200 OK
Content-Length: ##
Content-Type: application/json

{ "key" : "value" }
EOS
    is( &filterRequest($r{'all'}), $expected, 'Get json result' );
}
