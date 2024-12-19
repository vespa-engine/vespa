# encoding: utf-8
require "logstash/devutils/rspec/spec_helper"
require "logstash/inputs/vespa"
require "webmock/rspec"

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
end 