const http = require('http');

function queryDS(queryObj) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(queryObj);
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
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(data) }));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function run() {
  const now = Date.now();
  const range = { from: String(now - 24 * 3600 * 1000), to: String(now) };

  console.log('--- Fetching transaction-service logs ---');
  const res = await queryDS({
    from: range.from,
    to: range.to,
    queries: [{
      refId: 'A',
      datasource: { type: 'loki', uid: 'loki' },
      expr: '{job="transaction-service"}',
      maxLines: 20
    }]
  });

  const frames = res.body.results?.A?.frames?.[0];
  console.log('Fields:', frames?.schema?.fields?.map(f => f.name));
  const rawLines = frames?.data?.values?.[2] || frames?.data?.values?.[0] || [];
  console.log(`Total log lines found: ${rawLines.length}`);
  rawLines.slice(0, 10).forEach((line, idx) => console.log(`[${idx}]`, line));
}

run().catch(console.error);
