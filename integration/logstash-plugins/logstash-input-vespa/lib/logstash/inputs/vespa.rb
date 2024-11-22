# encoding: utf-8
require "logstash/inputs/base"
require "logstash/namespace"
require "net/http"
require "uri"
require "json"
require "openssl"

# This is the logstash vespa input plugin. It is used to read from Vespa
# via Visit : https://docs.vespa.ai/en/reference/document-v1-api-reference.html#visit
# Each document becomes an event.

class LogStash::Inputs::Vespa < LogStash::Inputs::Base
  config_name "vespa"

  # We should get JSON from Vespa, so let's use the JSON codec.
  default :codec, "json"

  # The URL to use to connect to Vespa.
  config :vespa_url, :validate => :uri, :default => "http://localhost:8080"

  # The cluster parameter to use in the request.
  config :cluster, :validate => :string, :required => true

  # Path to the client certificate file for mTLS.
  config :client_cert, :validate => :path

  # Path to the client key file for mTLS.
  config :client_key, :validate => :path

  # desired page size for the visit request, i.e. the wantedDocumentCount parameter
  config :page_size, :validate => :number, :default => 100

  # backend concurrency for the visit request, i.e. the concurrency parameter
  config :backend_concurrency, :validate => :number, :default => 1

  # selection. A query in Vespa selector language
  config :selection, :validate => :string

  # timeout for each HTTP request
  config :timeout, :validate => :number, :default => 180

  # lower timestamp limit for the visit request, i.e. the fromTimestamp parameter
  # microseconds since epoch
  config :from_timestamp, :validate => :number

  # upper timestamp limit for the visit request, i.e. the toTimestamp parameter
  config :to_timestamp, :validate => :number

  public
  def register
    if @client_cert != nil
      @cert = OpenSSL::X509::Certificate.new(File.read(@client_cert))
    end
    if @client_key != nil
      @key = OpenSSL::PKey::RSA.new(File.read(@client_key))
    end

    if @client_cert.nil? ^ @client_key.nil?
      raise LogStash::ConfigurationError, "Both client_cert and client_key must be set, you can't have just one"
    end
    
    @uri_params = {
      :cluster => @cluster,
      :wantedDocumentCount => @page_size,
      :concurrency => @backend_concurrency,
      :timeout => @timeout
    }

    if @selection != nil
      @uri_params[:selection] = @selection
    end

    if @from_timestamp != nil
      @uri_params[:fromTimestamp] = @from_timestamp
    end

    if @to_timestamp != nil
      @uri_params[:toTimestamp] = @to_timestamp
    end
  end # def register

  def run(queue)
    uri = URI.parse("#{@vespa_url}/document/v1/")
    uri.query = URI.encode_www_form(@uri_params)
    continuation = nil

    loop do
      response = fetch_documents_from_vespa(uri)
      # response should look like:
      # {
      #   "pathId":"/document/v1/","documents":[
      #     {"id":"id:namespace:doctype::docid","fields":{"field1":"value1","field2":7.0}}
      #   ],
      #   "documentCount":1,"continuation":"continuation_string"
      # }

      if response.is_a?(Net::HTTPSuccess)
        response_parsed = parse_response(response)
        break unless response_parsed

        document_count = response_parsed["documentCount"]
        # record the continuation token for the next request (if it exists)
        continuation = response_parsed["continuation"]
        documents = response_parsed["documents"]

        process_documents(documents, queue)

        # Exit the loop if there are no more documents to process
        if continuation != nil
          uri.query = URI.encode_www_form(@uri_params.merge({:continuation => continuation}))
        else
          @logger.info("No continuation ID => no more documents to fetch from Vespa")
          break
        end

        if @stopping
          @logger.info("Stopping Vespa input")
          break
        end

      else
        @logger.error("Failed to fetch documents from Vespa", :request => uri.to_s,
                      :response_code => response.code, :response_message => response.message)
        break # TODO retry? Only on certain codes?
      end # if response.is_a?(Net::HTTPSuccess)

    end # loop do
  end # def run

  def fetch_documents_from_vespa(uri)
    http = Net::HTTP.new(uri.host, uri.port)
    if uri.scheme == "https"
      http.use_ssl = true
      http.cert = @cert
      http.key = @key
      http.verify_mode = OpenSSL::SSL::VERIFY_PEER
    end

    request = Net::HTTP::Get.new(uri.request_uri)
    http.request(request)
  rescue => e
      @logger.error("Failed to make HTTP request to Vespa", :error => e.message)
      nil
  end # def fetch_documents_from_vespa

  def parse_response(response)
    JSON.parse(response.body)
  rescue JSON::ParserError => e
    @logger.error("Failed to parse JSON response", :error => e.message)
    nil
  end # def parse_response

  def process_documents(documents, queue)
    documents.each do |document|
      event = LogStash::Event.new(document)
      decorate(event)
      queue << event
    end
  end # def process_documents

  def stop
    @stopping = true
  end
end # class LogStash::Inputs::Vespa
