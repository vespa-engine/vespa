<?php

# Client for putting or getting vespa documents.
#
# gunnarga@yahoo-inc.com
# september 2007
#

if ($argc < 2) {
  echo "Usage: $argv[0] <url> [feedfile]\n";
  echo "\turl\t\tHttpGateway URL, e.g. http://myhost:myport/document/?abortondocumenterror=false\n";
  echo "\tfeedfile\tXML file to feed\n";
  exit(1);
}

$url = $argv[1];
$filename = $argv[2];

# split uri into subcomponents
$parsed_url = parse_url($url);
$hostname = $parsed_url['host'];
$port = $parsed_url['port'];
$path = $parsed_url['path'];
$query = $parsed_url['query'];

$socket = stream_socket_client("{$hostname}:{$port}", $errno, $errstr, 30);

if (file_exists($filename)) {
  echo "* Parsing file {$filename}...\n";
  $feedfile = fopen($filename, "r");
  if ($feedfile) {
    $fsize = filesize($filename);
    $version = phpversion();
    $header  = "POST {$path}?{$query} HTTP/1.1\r\n";
    $header .= "Host: {$hostname}:{$port}\r\n";
    $header .= "User-Agent: PHP/$version\r\n";
    $header .= "Content-Length: $fsize\r\n";
    $header .= "Content-Type: application/xml\r\n";
    $header .= "\r\n";

    fwrite($socket, $header);
    while (!feof($feedfile)) {
      $buf = fgets($feedfile);
      fwrite($socket, $buf);
    }
    fclose($feedfile);
  }

} else {
  $version = phpversion();
  $header  = "GET {$path}?{$query} HTTP/1.1\r\n";
  $header .= "Host: {$hostname}:{$port}\r\n";
  $header .= "User-Agent: PHP/$version\r\n";
  $header .= "\r\n";

  fwrite($socket, $header);
}

# check HTTP response
$firstline = fgets($socket);
if (!preg_match("/HTTP\/1.1 200 OK/", $firstline)) {
  echo "HTTP gateway returned error message: $firstline";
}

# read rest of the HTTP headers
while (!feof($socket)) {
  $line = fgets($socket);
  if (preg_match("/Content-Length: (\d+)/", $line, $matches)) {
    $content_length = $matches[1];
  }
  if ($line == "\r\n") {
    break;
  }
}

# collect xml data
$xmldata = stream_get_contents($socket, $content_length);

# parse xml data
$xml = new SimpleXMLElement($xmldata);
foreach ($xml->error as $error) {
  echo "Critical error: $error\n";
}
foreach($xml->xpath("//messages/message") as $message) {
  echo $message->asXML();
  echo "\n";
}

if (isset($xml->report->successes)) {
  echo "\nSuccessful operations:";
  $ok_puts = $xml->report->successes["put"];
  $ok_updates = $xml->report->successes["update"];
  $ok_removes = $xml->report->successes["remove"];

  if (isset($ok_puts)) {
    echo " {$ok_puts} puts";
  }
  if (isset($ok_updates)) {
    echo " {$ok_updates} updates";
  }
  if (isset($ok_removes)) {
    echo " {$ok_removes} removes";
  }
  echo "\n";
}

foreach($xml->xpath("/result/document") as $document) {
  echo $document->asXML();
  echo "\n";
}

?>
