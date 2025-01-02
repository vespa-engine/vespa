# Logstash Input Plugin for Vespa

Plugin for [Logstash](https://github.com/elastic/logstash) to read from [Vespa](https://vespa.ai). Apache 2.0 license.

## Installation

Download and unpack/install Logstash, then:
```
bin/logstash-plugin install logstash-input-vespa
```

## Development

To run tests, you'll need to clone the Logstash branch you're developing the plugin for. See https://github.com/elastic/logstash

Then:
```
export LOGSTASH_PATH=/path/to/logstash/repository/clone
export LOGSTASH_SOURCE=1
bundle exec rspec
```

To run integration tests, you'll need to have a Vespa instance running with an app deployed that supports an "id" field. And Logstash installed.

Check out the `integration-test` directory for more information.

```
cd integration-test
./run_tests.sh
```

## Usage

Minimal Logstash config example:
```
input {
  vespa {
    vespa_url => "http://localhost:8080"
    cluster => "test_cluster"
  }
}

output {
  stdout {}
}
```

With all the options:
```
input {
  vespa {
    # Vespa endpoint
    vespa_url => "http://localhost:8080"
    
    # cluster name from services.xml
    cluster => "test_cluster"

    # mTLS certificate and key
    client_cert => "/Users/myuser/.vespa/mytenant.myapp.default/data-plane-public-cert.pem"
    client_key => "/Users/myuser/.vespa/mytenant.myapp.default/data-plane-private-key.pem"

    # page size
    page_size => 100

    # Backend concurrency
    backend_concurrency => 1

    # Selection statement
    selection => "doc AND id.namespace == 'open'"

    # HTTP request timeout
    timeout => 180

    # maximum retries for failed HTTP requests
    max_retries => 3

    # delay in seconds for the first retry attempt. We double this delay for each subsequent retry.
    retry_delay => 1

    # lower timestamp bound (microseconds since epoch)
    from_timestamp => 1600000000000000

    # upper timestamp bound (microseconds since epoch)
    to_timestamp => 1800000000000000
  }
}

output {
  stdout {}
}
```