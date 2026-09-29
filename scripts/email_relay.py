import http.server, json, smtplib, ssl
from email.mime.text import MIMEText

GMAIL_USER = 'jonlevi.jlv@gmail.com'
GMAIL_PASS = 'ffaeorqwvupclnrc'

class EmailRelayHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(length)
            data = json.loads(body.decode('utf-8'))
            
            to = data.get('to', GMAIL_USER)
            subject = data.get('subject', 'PayPink Banking: Transaction Alert')
            text = data.get('body', '')
            
            msg = MIMEText(text)
            msg['Subject'] = subject
            msg['From'] = GMAIL_USER
            msg['To'] = to
            
            ctx = ssl._create_unverified_context()
            with smtplib.SMTP_SSL('smtp.gmail.com', 465, context=ctx) as server:
                server.login(GMAIL_USER, GMAIL_PASS)
                server.send_message(msg)
                
            print(f"[EMAIL_RELAY] Successfully sent email to {to}: {subject}")
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status":"DELIVERED"}')
        except Exception as ex:
            print(f"[EMAIL_RELAY_ERROR] {ex}")
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(ex)}).encode('utf-8'))

    def log_message(self, format, *args):
        pass

if __name__ == '__main__':
    server = http.server.HTTPServer(('0.0.0.0', 8095), EmailRelayHandler)
    print("PayPink Email Relay listening on 0.0.0.0:8095...")
    server.serve_forever()
