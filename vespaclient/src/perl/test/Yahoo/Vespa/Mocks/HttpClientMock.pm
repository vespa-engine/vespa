# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Switched the backend implementation of the Vespa::Http library, such that
# requests are sent here rather than onto the network. Register handlers here
# to respond to requests.
#
# Handlers are called in sequence until one of them returns a defined result.
# If none do, return a generic failure.
#

package Yahoo::Vespa::Mocks::HttpClientMock;

use strict;
use warnings;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Http;

BEGIN { # - Define default exports for module
    use base 'Exporter';
    our @EXPORT = qw(
        registerHttpClientHandler
    );
}

my @HANDLERS;

&initialize();

return 1;

#################### Default exported functions #############################

sub registerHttpClientHandler { # (Handler)
    push @HANDLERS, $_[0];
}

##################### Internal utility functions ##########################

sub initialize { # ()
    Yahoo::Vespa::Http::setHttpExecutor(\&clientMock);
}
sub clientMock { # (HttpRequest to forward) -> Response
    foreach my $handler (@HANDLERS) {
        my %result = &$handler(@_);
        if (exists $result{'code'}) {
            return %result;
        }
    }
    return (
        'code' => 500,
        'status' => 'No client handler for given request',
        'content' => '',
        'all' => ''
    );
}
