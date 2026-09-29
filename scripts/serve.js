const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3001;
const FRONTEND_DIR = path.resolve(__dirname, '..', 'frontend');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.ttf': 'font/ttf'
};

const server = http.createServer((req, res) => {
  let reqUrl = req.url.split('?')[0];

  // Map /bank/ to /bank/index.html
  if (reqUrl === '/bank' || reqUrl === '/bank/') {
    reqUrl = '/bank/index.html';
  } else if (reqUrl === '/') {
    reqUrl = '/index.html';
  }

  let filePath = path.join(FRONTEND_DIR, reqUrl);

  // Security: prevent path traversal
  if (!filePath.startsWith(FRONTEND_DIR)) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('403 Forbidden');
    return;
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      // Fallback for SPA routing
      if (reqUrl.startsWith('/bank')) {
        filePath = path.join(FRONTEND_DIR, 'bank', 'index.html');
      } else {
        filePath = path.join(FRONTEND_DIR, 'index.html');
      }
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (readErr, content) => {
      if (readErr) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end('500 Internal Server Error');
        return;
      }

      res.writeHead(200, {
        'Content-Type': contentType,
        'Access-Control-Allow-Origin': '*'
      });
      res.end(content);
    });
  });
});

server.listen(PORT, () => {
  console.log(`PayPink UI Server running at http://localhost:${PORT}/`);
  console.log(`- Personal Banking: http://localhost:${PORT}/bank/`);
  console.log(`- Admin / Simulation Workspace: http://localhost:${PORT}/`);
});
