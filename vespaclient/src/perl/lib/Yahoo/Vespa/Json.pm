# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Minimal JSON wrapper.
#
# Intentions:
#   - If needed, be able to switch the implementation of the JSON parser
#     without components using this class seeing it.
#   - Make API as simple as possible to use.
#
# Currently uses JSON.pm from ypan/perl-JSON
#
# Example usage:
#
# my $json = <<EOS;
# {
#   'foo' : [
#     { 'key1' : 2 },
#     { 'key2' : 5 }
#   ]
# }
#
# my $result = Json::parse($json);
# my $firstkey = $result->{'foo'}->[0]->{'key1'}
# my @keys = @{ $result->{'foo'} };
#
# See JsonTest for more usage. Add tests there if unsure.
#

package Yahoo::Vespa::Json;

use strict;
use warnings;
    # Location of JSON.pm is not in default search path on tested Yahoo nodes.
use lib ($ENV{'VESPA_HOME'} . '/lib64/perl5/site_perl/5.14/');
use JSON;

return 1;

# Parses a string with json data returning an object tree
sub parse { # (RawString) -> ObjTree
    my ($raw) = @_;
    my $json = decode_json($raw);
    return $json;
}

# Encodes an object tree as returned from parse back to a raw string
sub encode { # (ObjTree) -> RawString
    my ($json) = @_;
    my $JSON = JSON->new->allow_nonref;
    my $encoded = $JSON->pretty->encode($json);
    return $encoded;
}
