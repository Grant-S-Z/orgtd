#!/usr/bin/env python3
"""最小 WebDAV 桩：只实现 GET / PUT，带 ETag 与 If-Match，用于端到端验证。"""
import hashlib
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/orgtd-webdav"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8089


def etag_of(data: bytes) -> str:
    return '"' + hashlib.md5(data).hexdigest() + '"'


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _path(self) -> str:
        return os.path.join(ROOT, self.path.lstrip("/"))

    def do_GET(self):
        path = self._path()
        if not os.path.isfile(path):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        data = open(path, "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("ETag", etag_of(data))
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_PUT(self):
        path = self._path()
        length = int(self.headers.get("Content-Length", 0))
        data = self.rfile.read(length)

        if os.path.isfile(path):
            current = etag_of(open(path, "rb").read())
            if_match = self.headers.get("If-Match")
            if if_match and if_match != current:
                self.send_response(412)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return

        os.makedirs(os.path.dirname(path) or ROOT, exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)

        self.send_response(204)
        self.send_header("ETag", etag_of(data))
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.command, self.path))


if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
