# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import socket
from BaseHTTPServer import HTTPServer
from SimpleHTTPServer import SimpleHTTPRequestHandler


class MyHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/ip':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write('Your IP address is %s\n' % self.client_address[0])
            return

        elif self.path == '/ping':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write('pong\n')
            return

        else:
            self.send_response(404)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write('Could not find ' + self.path + '! Try /ping or /ip.\n')
            return


class HTTPServerV6(HTTPServer):
    address_family = socket.AF_INET6


def main():
    server = HTTPServerV6(('::', 80), MyHandler)
    server.serve_forever()


if __name__ == '__main__':
    main()
