# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Tests of the Json wrapper library..
# 

use Test::More;

use strict;

BEGIN {
    use_ok( 'Yahoo::Vespa::Json' );
    *Json:: = *Yahoo::Vespa::Json:: # Alias namespace
}
require_ok( 'Yahoo::Vespa::Json' );

&testSimpleJson();

done_testing();

exit(0);

sub testSimpleJson {
    my $json = <<EOS;
{
  "foo" : "bar",
  "map" : {
    "abc" : "def",
    "num" : 13.0
  },
  "array" : [
    { "val1" : 3 },
    { "val2" : 6 }
  ]
}
EOS
    my $parsed = Json::parse($json);
    is( $parsed->{'foo'}, 'bar', 'json test 1' );
    is( $parsed->{'map'}->{'abc'}, 'def', 'json test 2' );
    is( $parsed->{'map'}->{'num'}, 13.0, 'json test 3' );
    my $prettyPrint = <<EOS;
{
   "array" : [
      {
         "val1" : 3
      },
      {
         "val2" : 6
      }
   ],
   "map" : {
      "num" : 13,
      "abc" : "def"
   },
   "foo" : "bar"
}
EOS
    is( Json::encode($parsed), $prettyPrint, 'simple json test - encode' );
    my @keys = sort keys %{$parsed->{'map'}};
    is( scalar @keys, 2, 'simple json test - map keys' );
    is( $keys[0], 'abc', 'simple json test - map key 1' );
    is( $keys[1], 'num', 'simple json test - map key 2' );

    @keys = @{ $parsed->{'array'} };
    is( scalar @keys, 2, 'simple json test - list keys' );
    is( $keys[0]->{'val1'}, 3, 'simple json test - list key 1' );
    is( $keys[1]->{'val2'}, 6, 'simple json test - list key 2' );
}
