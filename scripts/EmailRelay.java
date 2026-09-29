import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.*;

public class EmailRelay {
    private static final String GMAIL_USER = "jonlevi.jlv@gmail.com";
    private static final String GMAIL_PASS = "ffaeorqwvupclnrc";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8099), 0);
        server.createContext("/send", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                String to = extractJsonField(body, "to", GMAIL_USER);
                String subject = extractJsonField(body, "subject", "PayPink Banking: Transaction Alert");
                String text = extractJsonField(body, "body", "");
                
                try {
                    sendEmail(to, subject, text);
                    System.out.println("[EMAIL_RELAY] Successfully sent email to " + to + " (" + subject + ")");
                    byte[] response = "{\"status\":\"DELIVERED\"}".getBytes("UTF-8");
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    os.write(response);
                } catch (Exception ex) {
                    System.err.println("[EMAIL_RELAY_ERROR] " + ex.getMessage());
                    byte[] response = ("{\"error\":\"" + ex.getMessage() + "\"}").getBytes("UTF-8");
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, response.length);
                    os.write(response);
                }
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("PayPink Java Email Relay Server started on port 8095...");
    }

    private static void sendEmail(String to, String subject, String body) throws Exception {
        TrustManager[] trustAll = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());
        
        try (SSLSocket socket = (SSLSocket) sc.getSocketFactory().createSocket("smtp.gmail.com", 465);
             BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            
            socket.startHandshake();
            r.readLine();
            
            smtpCmd(w, r, "EHLO localhost");
            smtpCmd(w, r, "AUTH LOGIN");
            smtpCmd(w, r, Base64.getEncoder().encodeToString(GMAIL_USER.getBytes()));
            smtpCmd(w, r, Base64.getEncoder().encodeToString(GMAIL_PASS.getBytes()));
            smtpCmd(w, r, "MAIL FROM:<" + GMAIL_USER + ">");
            smtpCmd(w, r, "RCPT TO:<" + to + ">");
            smtpCmd(w, r, "DATA");
            
            String content = "From: PayPink Banking <" + GMAIL_USER + ">\r\n" +
                             "To: <" + to + ">\r\n" +
                             "Subject: " + subject + "\r\n" +
                             "MIME-Version: 1.0\r\n" +
                             "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                             body + "\r\n.\r\n";
            w.write(content);
            w.flush();
            readResp(r);
            smtpCmd(w, r, "QUIT");
        }
    }

    private static void smtpCmd(BufferedWriter w, BufferedReader r, String cmd) throws Exception {
        w.write(cmd + "\r\n");
        w.flush();
        readResp(r);
    }

    private static String readResp(BufferedReader r) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
            if (line.length() == 3) break;
        }
        return sb.toString().trim();
    }

    private static String extractJsonField(String json, String field, String defVal) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).replace("\\n", "\n").replace("\\r", "\r");
        return defVal;
    }
}
