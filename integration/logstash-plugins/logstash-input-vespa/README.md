# Logstash Input Plugin for Vespa

Plugin for [Logstash](https://github.com/elastic/logstash) to read from [Vespa](https://vespa.ai). Apache 2.0 license.

## Installation

Download and unpack/install Logstash, then:
```
bin/logstash-plugin install logstash-input-vespa
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