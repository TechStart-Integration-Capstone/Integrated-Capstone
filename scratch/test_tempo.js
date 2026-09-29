const http = require('http');

const body = JSON.stringify({
  queries: [{
    datasource: { type: 'tempo', uid: 'tempo' },
    queryType: 'traceId',
    query: 'c03a5a964cbbbf9c2a630e1b89e58b',
    refId: 'A'
  }],
  from: 'now-15m',
  to: 'now'
});

const req = http.request('http://localhost:3000/api/ds/query', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Basic ' + Buffer.from('admin:admin').toString('base64'),
    'Content-Length': Buffer.byteLength(body)
  }
}, res => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    console.log('STATUS:', res.statusCode);
    console.log('RESPONSE:', data.substring(0, 300));
  });
});

req.on('error', console.error);
req.write(body);
req.end();
