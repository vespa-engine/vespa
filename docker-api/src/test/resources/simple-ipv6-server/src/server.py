# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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


class DualHTTPServer(HTTPServer):
    def __init__(self, address, handler):
        self.address_family = socket.AF_INET6 if (':' in address[0]) else socket.AF_INET
        HTTPServer.__init__(self, address, handler)


def main(ipv6):
    server = DualHTTPServer(('::' if ipv6 else '', 80), MyHandler)
    server.serve_forever()


if __name__ == '__main__':
    main(False)
