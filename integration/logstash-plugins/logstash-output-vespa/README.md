# Logstash Ouput Plugin for Vespa

Plugin for [Logstash](https://github.com/elastic/logstash) to write to [Vespa](https://vespa.ai). Apache 2.0 license.

## Installation

Download and unpack/install Logstash, then:
```
bin/logstash-plugin install logstash-output-vespa_feed
```

## Development
If you're developing the plugin, you'll want to do something like:
```
# build the gem
./gradlew gem
# install it as a Logstash plugin
/opt/logstash/bin/logstash-plugin install /path/to/logstash-output-vespa/logstash-output-vespa_feed-0.4.0.gem
# profit
/opt/logstash/bin/logstash
```
Some more good info about Logstash Java plugins can be found [here](https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html).

It looks like the JVM options from [here](https://github.com/logstash-plugins/.ci/blob/main/dockerjdk17.env)
are useful to make JRuby's `bundle install` work.

Note to self: for some reason, `bundle exec rake publish_gem` fails, but `gem push logstash-output-vespa_feed-$VERSION.gem`
does the trick.

## Usage

Logstash config example:

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

  # remove fields that we don't need. Here you can do a lot more processing
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
    client_cert => "/home/radu/vespa_apps/myapp/security/clients.pem"
    # make sure the key isn't password-protected
    # if it is, you can create a new key without a password like this:
    # openssl rsa -in myapp_key_with_pass.pem -out myapp_key.pem
    client_key => "/home/radu/vespa_apps/myapp_key.pem"
    
    # namespace could be static or in the %{field} format, picking from a field in the document
    namespace => "no_default_provide_yours"
    # similarly, doc type could be static or in the %{field} format
    document_type => "no_default_provide_yours_from_schema"
    
    # operation can be "put", "update", "remove" or dynamic (in the %{field} format)
    operation => "put"
    
    # add the create=true parameter to the feed request (for update and put operations)
    create => false

    # take the document ID from this field in each row
    # if the field doesn't exist, we generate a UUID
    id_field => "id"

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
  }
}
```

Then you can start Logstash while pointing to the config file like:
```
bin/logstash -f logstash.conf
```
