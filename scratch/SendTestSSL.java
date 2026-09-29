import java.io.*;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.*;

public class SendTestSSL {
    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to smtp.gmail.com:465 (Direct SSL)...");
        
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };
        
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLSocketFactory ssf = sc.getSocketFactory();
        
        SSLSocket sslSocket = (SSLSocket) ssf.createSocket("smtp.gmail.com", 465);
        sslSocket.startHandshake();
        System.out.println("TLS Handshake completed successfully!");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));
        
        System.out.println("S: " + reader.readLine());
        send(writer, reader, "EHLO localhost");
        send(writer, reader, "AUTH LOGIN");
        send(writer, reader, Base64.getEncoder().encodeToString("jonlevi.jlv@gmail.com".getBytes()));
        send(writer, reader, Base64.getEncoder().encodeToString("ffaeorqwvupclnrc".getBytes()));
        send(writer, reader, "MAIL FROM:<jonlevi.jlv@gmail.com>");
        send(writer, reader, "RCPT TO:<jonlevi.jlv@gmail.com>");
        send(writer, reader, "DATA");
        
        String emailContent = "From: PayPink Banking <jonlevi.jlv@gmail.com>\r\n" +
                             "To: <jonlevi.jlv@gmail.com>\r\n" +
                             "Subject: PayPink Banking: Real-Time Transaction Alert Engine Activated\r\n" +
                             "MIME-Version: 1.0\r\n" +
                             "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                             "Dear Levi Viernes,\r\n\r\n" +
                             "This is an official verification test from your PayPink Banking real-time notification engine.\r\n\r\n" +
                             "• Customer: Levi Viernes\r\n" +
                             "• Username: lviernes\r\n" +
                             "• Email: jonlevi.jlv@gmail.com\r\n" +
                             "• Mobile Number: +63 922 758 4285 (09227584285)\r\n" +
                             "• Primary Account: 001181233469 (Savings Account)\r\n" +
                             "• Engine Status: LIVE & BSP-COMPLIANT\r\n\r\n" +
                             "Every subsequent transfer or transaction will now automatically notify your email and banking dashboard in real time.\r\n\r\n" +
                             "Best regards,\r\n" +
                             "PayPink Core Banking Engine\r\n";
        
        writer.write(emailContent + "\r\n.\r\n");
        writer.flush();
        System.out.println("S: " + readMultiLine(reader));
        
        send(writer, reader, "QUIT");
        System.out.println("SUCCESSFULLY_SENT_TEST_EMAIL");
    }
    
    private static void send(BufferedWriter w, BufferedReader r, String msg) throws Exception {
        w.write(msg + "\r\n");
        w.flush();
        String resp = readMultiLine(r);
        System.out.println("C: " + (msg.startsWith("AUTH") || msg.length() > 20 ? "[AUTH_DATA]" : msg));
        System.out.println("S: " + resp);
    }
    
    private static String readMultiLine(BufferedReader r) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
            if (line.length() == 3) break;
        }
        return sb.toString().trim();
    }
}
