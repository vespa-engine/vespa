# Logstash Ouput Plugin for Vespa

Plugin for [Logstash](https://github.com/elastic/logstash) to write to [Vespa](https://vespa.ai). Apache 2.0 license.

## Table of Contents
- [Quick start](#quick-start)
- [Usage](#usage)
  - [Mode 1: Generating an application package](#mode-1-generating-an-application-package)
  - [Mode 2: Sending data to Vespa](#mode-2-sending-data-to-vespa)
- [Development](#development)
  - [Integration tests](#integration-tests)
  - [Publishing the gem](#publishing-the-gem)

## Quick start

[Download and unpack/install Logstash](https://www.elastic.co/downloads/logstash), then install the plugin:
```
bin/logstash-plugin install logstash-output-vespa_feed
```

Write a Logstash config file (a couple of examples are below), then start Logstash, pointing it to the config file:
```
bin/logstash -f logstash.conf
```

## Usage
There are two modes of operation:
1. **Generating an application package**: this is useful if you're just getting started with Vespa and you don't have e.g. a schema yet.
2. **Sending data to Vespa**: once you have an application package deployed, you can send data to Vespa.

### Mode 1: generating an application package

If you're just getting started with Vespa, you can use the `detect_schema` option to generate an application package that works with the data you're sending.
That application package can be deployed to [Vespa Cloud](https://vespa.ai/free-trial/) or a local Vespa instance.

To process the data, the `input` and `filter` sections are the same as when you send data to Vespa (see the [config example below](#mode-2-sending-data-to-vespa)). The output section is different. Here's an example for Vespa Cloud:

```
output {
  vespa_feed {
    # enable detect schema mode
    detect_schema => true
    # to get copy-paste-able Vespa CLI commands
    deploy_package => true

    # Vespa Cloud application details
    vespa_cloud_tenant => "my_tenant"
    vespa_cloud_application => "my_application"

    ### optional settings
    # where to save the generated application package
    application_package_dir => "/OS_TMPDIR/vespa_app"
    # How long to wait (in empty batches) before showing the CLI commands for deploying the application
    # This is useful if Logstash doesn't exit quickly and waits (e.g. when tailing a file)
    # Otherwise, the plugin will show the CLI commands before Logstash exits
    idle_batches => 10
    # whether to generate mTLS certificates (defaults to true for Vespa Cloud, because you'll need them)
    generate_mtls_certificates => true
    # common name for the mTLS certificates
    certificate_common_name => "cloud.vespa.logstash"
    # validity days for the mTLS certificates
    certificate_validity_days => 30
    # where should the client certificate and key be saved
    client_cert => "/OS_TMPDIR/vespa_app/security/clients.pem"
    client_key => "/OS_TMPDIR/vespa_app/data-plane-private-key.pem"
  }
}
```

For self-hosted Vespa, options are slightly different:
```
output {
  vespa_feed {
    # enable detect schema mode
    detect_schema => true
    # whether to actually deploy the application package
    deploy_package => true

    # config server endpoint (derived from vespa_url, which defaults to http://localhost:8080)
    # used for deploying the application package, if deploy_package=true
    config_server => "http://localhost:19071"

    ### same optional settings as for Vespa Cloud
    ### exception: generate_mtls_certificates defaults to false
  }
}
```

In the end, you should have an application package in `application_package_dir`, which defaults to your OS's temp directory + `vespa_app`. We encourage you to check it out and change it as needed. If Logstash didn't already deploy it, you can do so with the [Vespa CLI](https://docs.vespa.ai/en/vespa-cli.html):
```
cd /path/to/application_package
### show deployment logs for up to 15 minutes
vespa deploy --wait 900
```

### Mode 2: sending data to Vespa

Some more Logstash config examples can be found [in this blog post](https://blog.vespa.ai/logstash-vespa-tutorials/), but here's one with all the relevant output options:

```
# read stuff
input {
  # if you want to just send stuff to a "message" field from the terminal
  #stdin {}

  file {
    # let's assume we have some data in a CSV file here
    path => "/path/to/data.csv"
    # read the file from the beginning
    start_position => "beginning"
    # on Logstash restart, forget where we left off and start over again
    sincedb_path => "/dev/null"
  }
}

# parse and transform data here
filter {
  csv {
    # how does the CSV file look like?
    separator => ","
    quote_char => '"'

    # if the first line is the header, we'll skip it
    skip_header => true

    # columns of the CSV file. Make sure you have these fields in the Vespa schema
    columns => ["id", "description", ...]
  }

  # remove fields we don't need
  # NOTE: the fields below are added by Logstash by default. You probably *need* this block
  # otherwise Vespa will reject documents complaining that e.g. @timestamp is an unknown field
  mutate {
    remove_field => ["@timestamp", "@version", "event", "host", "log", "message"]
  }
}

# publish to Vespa
output {
  # for debugging. You can have multiple outputs (just as you can have multiple inputs/filters)
  #stdout {}

  vespa_feed { # including defaults here
  
    # Vespa endpoint
    vespa_url => "http://localhost:8080"
    
    # for HTTPS URLS (e.g. Vespa Cloud), you may want to provide a certificate and key for mTLS authentication
    # the defaults are relative to application_package_dir (see the detect_schema section above)
    client_cert => "/OS_TMPDIR/vespa_app/security/clients.pem"
    # make sure the key isn't password-protected
    # if it is, you can create a new key without a password like this:
    # openssl rsa -in myapp_key_with_pass.pem -out myapp_key.pem
    client_key => "/OS_TMPDIR/vespa_app/data-plane-private-key.pem"

    # for Vespa Cloud, you can use an auth token instead of mTLS certificates
    auth_token => "vespa_cloud_TOKEN_GOES_HERE"
    
    # namespace could be static or in the %{field} format, picking from a field in the document
    namespace => "defaults_to_the_document_type_value"
    # similarly, doc type could be static or in the %{field} format
    document_type => "doctype"
    
    # operation can be "put", "update", "remove" or dynamic (in the %{field} format)
    operation => "put"
    
    # add the create=true parameter to the feed request (for update and put operations)
    create => false

    # take the document ID from this field in each row
    # if the field doesn't exist, we generate a UUID
    id_field => "id"

    # remove fields from the document after using them for writing
    remove_id => false          # if set to true, remove the ID field after using it
    remove_namespace => false   # would remove the namespace field (if dynamic)
    remove_document_type => false # same for document type
    remove_operation => false   # and operation

    # how many HTTP/2 connections to keep open
    max_connections => 1
    # number of streams per connection
    max_streams => 128
    # request timeout (seconds) for each write operation
    operation_timeout => 180
    # after this time (seconds), the circuit breaker will be half-open:
    # it will ping the endpoint to see if it's back,
    # then resume sending requests when it's back
    grace_period => 10
    
    # how many times to retry on transient failures
    max_retries => 10

    # if we we exceed the number of retries or if there are intransient errors,
    # like field not in the schema, invalid operation, we can send the document to a dead letter queue

    # you'd need to set this to true, default is false. NOTE: this overrides whatever is in logstash.yml
    enable_dlq => false

    # the path to the dead letter queue. NOTE: the last part of the path is the pipeline ID,
    # if you want to use the dead letter queue input plugin
    dlq_path => "data/dead_letter_queue"

    # max dead letter queue size (bytes)
    max_queue_size => 1073741824
    # max segment size (i.e. file from the dead letter queue - also in bytes)
    max_segment_size => 10485760
    # flush interval (how often to commit the DLQ to disk, in milliseconds)
    flush_interval => 5000
  }
}
```

## Development
If you're developing the plugin, you'll want to do something like:
```
# build the gem
./gradlew gem
# run tests
./gradlew test
# install it as a Logstash plugin
/opt/logstash/bin/logstash-plugin install /path/to/logstash-output-vespa/logstash-output-vespa_feed-1.0.0.gem
# profit
/opt/logstash/bin/logstash
```
Some more good info about Logstash Java plugins can be found [here](https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html).

It looks like the JVM options from [here](https://github.com/logstash-plugins/.ci/blob/main/dockerjdk17.env)
are useful to make JRuby's `bundle install` work.

### Integration tests
To run integration tests, you'll need to have a Vespa instance running with an app deployed that supports an "id" field. And Logstash installed.

Check out the `integration-test` directory for more information.

```
cd integration-test
./run_tests.sh
```

### Publishing the gem

Note to self: for some reason, `bundle exec rake publish_gem` fails, but `gem push logstash-output-vespa_feed-$VERSION.gem`
does the trick.
