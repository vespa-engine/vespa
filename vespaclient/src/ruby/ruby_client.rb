#!/usr/bin/env ruby
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

require 'socket'
require 'uri'
require 'optparse'
require 'rexml/document'

# Client for putting or getting vespa documents. The standard library
# routines for http requests are not used because they don't as per
# ruby 1.8.5 support streaming file transfer.
#
# gunnarga@yahoo-inc.com
# september 2007
#
class RubyClient
  include REXML

  CHUNKSIZE = 8192

  def initialize(uri)
    uri_components = URI.split(uri)
    @hostname = uri_components[2]
    @port = uri_components[3]
    @path = uri_components[5]
    @query = uri_components[7]
    @socket = TCPSocket.new(@hostname, @port)
  end

  def http_post(feedfile)
    puts "* Parsing file #{feedfile}..."
    fsize = File.stat(feedfile).size
    header  = "POST #{@path}?#{@query} HTTP/1.1\r\n"
    header += "Host: #{@hostname}:#{@port}\r\n"
    header += "User-Agent: Ruby/#{RUBY_VERSION}\r\n"
    header += "Content-Length: #{fsize}\r\n"
    header += "Content-Type: application/xml\r\n"
    header += "\r\n"

    begin
      @socket.print(header)
      File.open(feedfile) do |file|
        while buf = file.read(CHUNKSIZE)
          @socket.print(buf)
        end
      end
    rescue Exception => e
      puts "Exception caught: #{e}"
    end

    print_response
  end

  def http_get
    header  = "GET #{@path}?#{@query} HTTP/1.1\r\n"
    header += "Host: #{@hostname}:#{@port}\r\n"
    header += "User-Agent: Ruby/#{RUBY_VERSION}\r\n"
    header += "\r\n"

    begin
      @socket.print(header)
    rescue Exception => e
      puts "Exception caught: #{e}"
    end

    print_response
  end

  def print_response
    xmldata = ""
    begin
      firstline = @socket.gets
      if not firstline =~ /HTTP\/1.1 200 OK/
        puts "HTTP gateway returned error message: #{firstline}"
      end
      while line = @socket.gets
        if line =~ /Content-Length: (\d+)/
          content_length = $1.to_i
        end
        if line == "\r\n"
          break
        end
      end

      xmldata = @socket.read(content_length)
    rescue Exception => e
      puts "Exception caught: #{e}"
    end

    begin
      xmldoc = Document.new(xmldata)
      xmldoc.elements.each("/result/error") {|e| puts "Critical error: #{e.text}"}
      xmldoc.elements.each("//messages/message") {|e| puts e.to_s}
      successes = XPath.first(xmldoc, "//successes")
      if (successes)
        print "\nSuccessful operations:"
        if (ok_puts = successes.attribute("put"))
          puts " #{ok_puts} puts"
        end
        if (ok_updates = successes.attribute("update"))
          puts " #{ok_updates} updates"
        end
        if (ok_removes = successes.attribute("remove"))
          puts " #{ok_removes} removes"
        end
      end
      xmldoc.elements.each("/result/document") {|e| puts e.to_s}
    rescue Exception => e
      puts "Exception caught: #{e}"
    end
  end

end

if ARGV.length < 1
  puts "Usage: #{$0} <url> [feedfile]\n";
  puts "\turl\t\tHttpGateway URL, e.g. http://myhost:myport/document/?abortondocumenterror=false";
  puts "\tfeedfile\tXML file to feed";
  exit 1
end

url = ARGV[0]
filename = ARGV[1]

feeder = RubyClient.new(url)
if filename and File.exists?(filename)
  feeder.http_post(filename)
else
  feeder.http_get
end
