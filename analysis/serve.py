#!/usr/bin/env python3
"""Serve pages/ with caching turned off.

`python3 -m http.server` lets the browser keep old copies of pages, scripts and
JSON, so after an update.py run a page can show its old self, or an old page
can be handed new data it no longer understands and draw nothing.

    python3 serve.py          # http://localhost:8000/
    python3 serve.py 8001
"""

import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PAGES = Path(__file__).resolve().parent / 'pages'


class NoCacheHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, must-revalidate')
        super().end_headers()


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    server = ThreadingHTTPServer(('', port), partial(NoCacheHandler, directory=str(PAGES)))
    print(f'Serving {PAGES} at http://localhost:{port}/  (ctrl-c to stop)')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
