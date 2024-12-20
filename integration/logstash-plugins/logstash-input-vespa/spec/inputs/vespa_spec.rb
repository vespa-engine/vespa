# encoding: utf-8
require "logstash/devutils/rspec/spec_helper"
require "logstash/inputs/vespa"
require "webmock/rspec"
require 'tempfile'
require 'openssl'

describe LogStash::Inputs::Vespa do
  let(:config) do
    {
      "cluster" => "test-cluster",
      "vespa_url" => "http://localhost:8080",
      "retry_delay" => 0.1,  # Small delay for faster tests
      "max_retries" => 3
    }
  end

  let(:plugin) { described_class.new(config) }
  let(:queue) { Queue.new }
  let(:base_uri) { "#{config['vespa_url']}/document/v1/" }
  let(:uri_params) { "cluster=test-cluster&wantedDocumentCount=100&concurrency=1&timeout=180" }

  before do
    plugin.register
    allow(plugin).to receive(:sleep)  # Mock sleep to speed up tests
  end

  describe "#run" do
    context "when server returns retriable errors" do
      it "retries on 503 Service Unavailable" do
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(
            { status: 503, body: "Service Unavailable" },
            { status: 503, body: "Service Unavailable" },
            { status: 200, body: '{"documents": [], "documentCount": 0}' }
          )

        plugin.run(queue)
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.times(3)
      end

      it "retries on 502 Bad Gateway" do
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(
            { status: 502, body: "Bad Gateway" },
            { status: 200, body: '{"documents": [], "documentCount": 0}' }
          )

        plugin.run(queue)
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.times(2)
      end

      it "stops after max_retries attempts" do
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(status: 503, body: "Service Unavailable").times(4)

        plugin.run(queue)
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.times(config["max_retries"])
      end
    end

    context "when server returns non-retriable errors" do
      it "does not retry on 404 Not Found" do
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(status: 404, body: "Not Found")

        plugin.run(queue)
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.times(1)
      end

      it "does not retry on 401 Unauthorized" do
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(status: 401, body: "Unauthorized")

        plugin.run(queue)
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.times(1)
      end
    end

    context "when server returns successful responses" do
      it "processes documents and follows continuation tokens" do

        # First response with continuation token
        first_response = {
          "pathId" => "/document/v1/",
          "documents" => [
            {"id" => "id:namespace:doctype::doc1", "fields" => {"field1" => "value1", "field2" => 7.0}},
            {"id" => "id:namespace:doctype::doc2", "fields" => {"field1" => "value2", "field2" => 8.0}}
          ],
          "documentCount" => 2,
          "continuation" => "AAAAAA"
        }

        # Second response without continuation (last page)
        last_response = {
          "pathId" => "/document/v1/",
          "documents" => [
            {"id" => "id:namespace:doctype::doc3", "fields" => {"field1" => "value3", "field2" => 9.0}}
          ],
          "documentCount" => 1
        }

        # Stub the requests
        stub_request(:get, "#{base_uri}?#{uri_params}")
          .to_return(status: 200, body: first_response.to_json)

        stub_request(:get, "#{base_uri}?#{uri_params}&continuation=AAAAAA")
          .to_return(status: 200, body: last_response.to_json)

        plugin.run(queue)

        expect(queue.size).to eq(3)  # Total of 3 documents
        expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.once
        expect(a_request(:get, "#{base_uri}?#{uri_params}&continuation=AAAAAA")).to have_been_made.once
      end
    end
  end

  describe "#register" do
    let(:temp_cert) do
      file = Tempfile.new(['cert', '.pem'])
      # Create a self-signed certificate for testing
      key = OpenSSL::PKey::RSA.new(2048)
      cert = OpenSSL::X509::Certificate.new
      cert.version = 2
      cert.serial = 1
      cert.subject = OpenSSL::X509::Name.parse("/CN=Test")
      cert.issuer = cert.subject
      cert.public_key = key.public_key
      cert.not_before = Time.now
      cert.not_after = Time.now + 3600
      
      # Sign the certificate
      cert.sign(key, OpenSSL::Digest::SHA256.new)
      
      file.write(cert.to_pem)
      file.close
      file
    end

    let(:temp_key) do
      file = Tempfile.new(['key', '.pem'])
      # Create a valid RSA key for testing
      key = OpenSSL::PKey::RSA.new(2048)
      file.write(key.to_pem)
      file.close
      file
    end

    after do
      temp_cert.unlink
      temp_key.unlink
    end

    it "raises error when only client_cert is provided" do
      invalid_config = config.merge({"client_cert" => temp_cert.path})
      plugin = described_class.new(invalid_config)
      
      expect { plugin.register }.to raise_error(LogStash::ConfigurationError, 
        "Both client_cert and client_key must be set, you can't have just one")
    end

    it "raises error when only client_key is provided" do
      invalid_config = config.merge({"client_key" => temp_key.path})
      plugin = described_class.new(invalid_config)
      
      expect { plugin.register }.to raise_error(LogStash::ConfigurationError,
        "Both client_cert and client_key must be set, you can't have just one")
    end

    it "correctly sets up URI parameters" do
      full_config = config.merge({
        "selection" => "true",
        "from_timestamp" => 1234567890,
        "to_timestamp" => 2234567890,
        "page_size" => 50,
        
        "backend_concurrency" => 2,
        "timeout" => 120
      })
      
      plugin = described_class.new(full_config)
      plugin.register
      
      # Access the private @uri_params using send
      uri_params = plugin.send(:instance_variable_get, :@uri_params)
      expect(uri_params[:selection]).to eq("true")
      expect(uri_params[:fromTimestamp]).to eq(1234567890)
      expect(uri_params[:toTimestamp]).to eq(2234567890)
      expect(uri_params[:wantedDocumentCount]).to eq(50)
      expect(uri_params[:concurrency]).to eq(2)
      expect(uri_params[:timeout]).to eq(120)
    end
  end

  describe "#parse_response" do
    it "handles malformed JSON responses" do
      response = double("response", :body => "invalid json{")
      result = plugin.parse_response(response)
      expect(result).to be_nil
    end

    it "successfully parses valid JSON responses" do
      valid_json = {
        "documents" => [{"id" => "doc1"}],
        "documentCount" => 1
      }.to_json
      response = double("response", :body => valid_json)
      
      result = plugin.parse_response(response)
      expect(result["documentCount"]).to eq(1)
      expect(result["documents"]).to be_an(Array)
    end
  end

  describe "#process_documents" do
    it "creates events with correct decoration" do
      documents = [
        {"id" => "doc1", "fields" => {"field1" => "value1"}},
        {"id" => "doc2", "fields" => {"field1" => "value2"}}
      ]
      
      # Test that decoration is applied
      expect(plugin).to receive(:decorate).twice
      
      plugin.process_documents(documents, queue)
      expect(queue.size).to eq(2)
      
      event1 = queue.pop
      expect(event1.get("id")).to eq("doc1")
      expect(event1.get("fields")["field1"]).to eq("value1")
      
      event2 = queue.pop
      expect(event2.get("id")).to eq("doc2")
      expect(event2.get("fields")["field1"]).to eq("value2")
    end
  end

  describe "#stop" do
    it "sets stopping flag" do
      plugin.stop
      expect(plugin.instance_variable_get(:@stopping)).to be true
    end

    it "interrupts running visit operation" do
      request_made = Queue.new  # Use a Queue for thread synchronization

      # Setup a response that would normally continue
      stub_request(:get, "#{base_uri}?#{uri_params}")
        .to_return(status: 200, body: {
          documents: [{"id" => "doc1"}],
          documentCount: 1,
          continuation: "token"
        }.to_json)
        .with { |req| request_made.push(true); true }  # Signal when request is made

      # Run in a separate thread
      thread = Thread.new { plugin.run(queue) }
      
      # Wait for the first request to be made
      request_made.pop
      
      # Now we know the first request has been made, stop the plugin
      plugin.stop
      thread.join
      
      # Should only make one request despite having a continuation token
      expect(a_request(:get, "#{base_uri}?#{uri_params}")).to have_been_made.once
    end
  end
end 