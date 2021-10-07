# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package Yahoo::Vespa::Mocks::ClusterControllerMock;

use strict;
use warnings;
use URI::Escape;
use Yahoo::Vespa::ConsoleOutput;
use Yahoo::Vespa::Mocks::HttpClientMock;
use Yahoo::Vespa::Utils;

BEGIN {
    use base 'Exporter';
    our @EXPORT = qw(
    );
}

our $forceInternalServerError = 0;

# Register a handler in the Http Client mock
registerHttpClientHandler(\&handleCCRequest);

our $clusterListJson = <<EOS;
{
    "cluster" : {
        "books" : {
            "link" : "/cluster/v2/books"
        },
        "music" : {
            "link" : "/cluster/v2/music"
        }
    }
}
EOS
our $musicClusterJson = <<EOS;
{
  "state" : {
    "generated" : {
      "state" : "up",
      "reason" : ""
    }
  },
  "service" : {
    "distributor" : {
      "node" : {
        "0" : {
          "attributes" : { "hierarchical-group" : "top" },
          "state" : {
            "generated" : { "state" : "down", "reason" : "Setting it down" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "down", "reason" : "Setting it down" }
          }
        },
        "1" : {
          "attributes" : { "hierarchical-group" : "top" },
          "state" : {
            "generated" : { "state" : "up", "reason" : "Setting it up" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "up", "reason" : ""
            }
          }
        }
      }
    },
    "storage" : {
      "node" : {
        "0" : {
          "attributes" : { "hierarchical-group" : "top" },
          "state" : {
            "generated" : { "state" : "retired", "reason" : "Stop using" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "retired", "reason" : "Stop using" }
          },
          "partition" : {
            "0" : {
              "metrics" : {
                "bucket-count" : 5,
                "unique-document-count" : 10,
                "unique-document-total-size" : 1000
              }
            }
          }
        },
        "1" : {
          "attributes" : { "hierarchical-group" : "top" },
          "state" : {
            "generated" : { "state" : "up", "reason" : "Setting it up" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "up", "reason" : ""
            }
          },
          "partition" : {
            "0" : {
              "metrics" : {
                "bucket-count" : 50,
                "unique-document-count" : 100,
                "unique-document-total-size" : 10000
              }
            }
          }
        }
      }
    }
  }
}
EOS
our $booksClusterJson = <<EOS;
{
  "state" : {
    "generated" : {
      "state" : "up",
      "reason" : ""
    }
  },
  "service" : {
    "distributor" : {
      "node" : {
        "0" : {
          "attributes" : { "hierarchical-group" : "top.g1" },
          "state" : {
            "generated" : { "state" : "down", "reason" : "Setting it down" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "down", "reason" : "Setting it down" }
          }
        },
        "1" : {
          "attributes" : { "hierarchical-group" : "top.g2" },
          "state" : {
            "generated" : { "state" : "up", "reason" : "Setting it up" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "up", "reason" : ""
            }
          }
        }
      }
    },
    "storage" : {
      "node" : {
        "0" : {
          "attributes" : { "hierarchical-group" : "top.g1" },
          "state" : {
            "generated" : { "state" : "down", "reason" : "Not seen" },
            "unit" : { "state" : "down", "reason" : "Not in slobrok" },
            "user" : { "state" : "down", "reason" : "default" }
          }
        },
        "1" : {
          "attributes" : { "hierarchical-group" : "top.g2" },
          "state" : {
            "generated" : { "state" : "up", "reason" : "Setting it up" },
            "unit" : { "state" : "up", "reason" : "Now reporting state U" },
            "user" : { "state" : "up", "reason" : ""
            }
          }
        }
      }
    }
  }
}
EOS

return &init();

sub init {
    #print "Verifying that cluster list json is parsable.\n";
    my $json = Json::parse($clusterListJson);
    #print "Verifying that music json is parsable\n";
    $json = Json::parse($musicClusterJson);
    #print "Verifying that books json is parsable\n";
    $json = Json::parse($booksClusterJson);
    #print "All seems parsable.\n";
    return 1;
}

sub setClusterDown {
    $musicClusterJson =~ s/"up"/"down"/;
    $musicClusterJson =~ s/""/"Not enough nodes up"/;
    #print "Cluster state: $musicClusterJson\n";
    #print "Verifying that music json is parsable\n";
    my $json = Json::parse($musicClusterJson);
}

sub handleCCRequest { # (Type, Host, Port, Path, ParameterMap, Content, Headers)
    my ($type, $host, $port, $path, $params, $content, $headers) = @_;
    my %paramHash;
    if (defined $params) {
        %paramHash = @$params;
    }
    if ($forceInternalServerError) {
        printDebug "Forcing internal server error response\n";
        return (
            'code' => 500,
            'status' => 'Internal Server Error (forced)'
        );
    }
    if ($path eq "/cluster/v2/") {
        printDebug "Handling cluster list request\n";
        return (
            'code' => 200,
            'status' => 'OK',
            'content' => $clusterListJson
        );
    }
    if ($path eq "/cluster/v2/music/"
        && (exists $paramHash{'recursive'}
            && $paramHash{'recursive'} eq 'true'))
    {
        printDebug "Handling cluster music state request\n";
        return (
            'code' => 200,
            'status' => 'OK',
            'content' => $musicClusterJson
        );
    }
    if ($path eq "/cluster/v2/books/"
        && (exists $paramHash{'recursive'}
            && $paramHash{'recursive'} eq 'true'))
    {
        printDebug "Handling cluster books state request\n";
        return (
            'code' => 200,
            'status' => 'OK',
            'content' => $booksClusterJson
        );
    }
    if ($path =~ /^\/cluster\/v2\/(books|music)\/(storage|distributor)\/(\d+)$/)
    {
        my ($cluster, $service, $index) = ($1, $2, $3);
        my $json = Json::parse($content);
        my $state = $json->{'state'}->{'user'}->{'state'};
        my $description = $json->{'state'}->{'user'}->{'reason'};
        if (!defined $description && $state eq 'up') {
            $description = "";
        }
        if ($state !~ /^(?:up|down|maintenance|retired)$/) {
            return (
                'code' => 500,
                'status' => "Unknown state '$state' specified"
            );
        }
        if (!defined $state || !defined $description) {
            return (
                'code' => 500,
                'status' => "Invalid form data or failed parsing: '$content'"
            );
        }
        printDebug "Handling set user state request $cluster/$service/$index";
        return (
            'code' => 200,
            'status' => "Set user state for $cluster/$service/$index to "
                      . "'$state' with reason '$description'"
        );
    }
    printDebug "Request to '$path' not matched. Params:\n";
    foreach my $key (keys %paramHash) {
        printDebug "  $key => '$paramHash{$key}'\n";
    }
    return;
}
