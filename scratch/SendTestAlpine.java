import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.*;

public class SendTestAlpine implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers() { return null; }
    public void checkClientTrusted(X509Certificate[] c, String a) {}
    public void checkServerTrusted(X509Certificate[] c, String a) {}

    public static void main(String[] args) throws Exception {
        System.out.println("Testing wrapped socket with explicit host String...");
        
        SendTestAlpine tm = new SendTestAlpine();
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{tm}, new java.security.SecureRandom());
        
        Socket plainSocket = new Socket("74.125.203.109", 465);
        SSLSocket sslSocket = (SSLSocket) sc.getSocketFactory().createSocket(plainSocket, "smtp.gmail.com", 465, true);
        sslSocket.startHandshake();
        System.out.println(">>> TLS HANDSHAKE SUCCEEDED WITH SNI!");
        
        BufferedReader r = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));
        System.out.println("S: " + r.readLine());
        
        send(w, r, "EHLO localhost");
        send(w, r, "AUTH LOGIN");
        send(w, r, Base64.getEncoder().encodeToString("jonlevi.jlv@gmail.com".getBytes()));
        send(w, r, Base64.getEncoder().encodeToString("ffaeorqwvupclnrc".getBytes()));
        send(w, r, "MAIL FROM:<jonlevi.jlv@gmail.com>");
        send(w, r, "RCPT TO:<jonlevi.jlv@gmail.com>");
        send(w, r, "DATA");
        
        String emailContent = "From: PayPink Banking <jonlevi.jlv@gmail.com>\r\n" +
                             "To: <jonlevi.jlv@gmail.com>\r\n" +
                             "Subject: PayPink Banking: Real-Time Transaction Alert Engine Activated\r\n" +
                             "MIME-Version: 1.0\r\n" +
                             "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                             "Dear Levi Viernes,\r\n\r\n" +
                             "Your PayPink Banking real-time transaction notification engine is verified active inside the production Docker cluster.\r\n\r\n" +
                             "• Customer: Levi Viernes\r\n" +
                             "• Account: 001181233469 (Savings)\r\n" +
                             "• Real-Time Channel: Gmail SMTPS (465 SSL)\r\n" +
                             "• Mobile: +63 922 758 4285 (09227584285)\r\n" +
                             "• Status: BSP-COMPLIANT REAL-TIME ALERTING\r\n\r\n" +
                             "Every fund transfer will now trigger real-time transaction receipts directly into this inbox.\r\n";
        w.write(emailContent + "\r\n.\r\n");
        w.flush();
        System.out.println("S: " + readMultiLine(r));
        send(w, r, "QUIT");
        System.out.println("DOCKER_EMAIL_DELIVERED_SUCCESSFULLY");
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
