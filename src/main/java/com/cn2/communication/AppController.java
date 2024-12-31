package com.cn2.communication;

import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.*;
import java.io.*;
import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

@RestController
@RequestMapping("/api")
public class AppController {

	// UDP variables
	static DatagramSocket messageSocket; // For receiving/sending messages
	static DatagramSocket voiceSocket; // For receiving/sending call requests

	// Local Ports for message and voice communication
	static final int LOCAL_PORT_MESSAGE = 12345; // Local port for receiving messages
	static final int LOCAL_PORT_VOICE = 12346; // Local port for receiving voice data

	// Remote Ports for message and voice communication
	static final int REMOTE_PORT_MESSAGE = 12345; // Remote port for sending messages
	static final int REMOTE_PORT_VOICE = 12346; // Remote port for sending voice

	static final String REMOTE_IP = "127.0.0.1"; // Replace with the remote peer's IP address

    // For calling
    boolean callActive = false;
    TargetDataLine targetLine;
    SourceDataLine sourceLine;

    // For keeping track of messages
    static List<String> messages = new ArrayList<>();

    // TCP Sockets for messaging and voice
    static ServerSocket tcpMessageServerSocket; // Server-side TCP socket for messaging
    static Socket tcpMessageSocket; // Client-side TCP socket for messaging
    static ServerSocket tcpVoiceServerSocket; // Server-side TCP socket for voice
    static Socket tcpVoiceSocket; // Client-side TCP socket for voice

    static boolean usingUDP = true; // Default to UDP protocol
    static boolean transition = false; // Flag to indicate a protocol switch

    // For encryption
    static final String KEY = "123456789ABCDEFG"; // 16-byte key

    static {
		try {
			// Initialize the message socket to listen for incoming messages
			messageSocket = new DatagramSocket(LOCAL_PORT_MESSAGE);

			// Initialize the voice socket to listen for incoming voice data
			voiceSocket = new DatagramSocket(LOCAL_PORT_VOICE);

			System.out.println("Sockets initialized for message and voice communication.");
		} catch (SocketException e) {
			System.err.println("Error initializing UDP sockets: " + e.getMessage());
			e.printStackTrace();
		}

        // Start a thread to listen for incoming messages
        new Thread(() -> {
            while (true) {
                try {
                    if (usingUDP) {
                        // UDP Logic
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        messageSocket.receive(packet); // Waits for an incoming message on the message socket
                        String receivedMessage = decryptMessage(new String(packet.getData(), 0, packet.getLength()));
                        synchronized (messages) {
                            messages.add("Remote: " + receivedMessage); // Store the message
                        }
                    } else {
                        // TCP Logic
                        BufferedReader reader = new BufferedReader(new InputStreamReader(tcpMessageSocket.getInputStream()));
                        String receivedMessage = reader.readLine(); // Read incoming message line by line
                        if (receivedMessage != null) {
                            receivedMessage = decryptMessage(receivedMessage);
                            synchronized (messages) {
                                messages.add("Remote: " + receivedMessage); // Store the message
                            }
                        }
                    }
                } catch (Exception e) {
                    if (transition) {
                        continue;
                    }
                    System.out.println("Error receiving message: " + e.getMessage() + transition);
                }
            }
        }).start();
    }

    @GetMapping("/messages")
    public List<String> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages); // Return the messages
        }
    }

    @PostMapping("/send")
    public void sendMessage(@RequestBody String jsonMessage) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonMessage);
            String message = jsonNode.get("message").asText();
            
            if (!message.isEmpty()) {
                String encryptedMessage = encryptMessage(message);
                
                try {
                    if (usingUDP) {
                        DatagramPacket messagePacket = new DatagramPacket(encryptedMessage.getBytes(), encryptedMessage.getBytes().length,
                            new InetSocketAddress(REMOTE_IP, REMOTE_PORT_MESSAGE));
                        messageSocket.send(messagePacket); // Send messagePacket via the messageSocket
                    } else {
                        OutputStream os = tcpMessageSocket.getOutputStream();
                        os.write(encryptedMessage.getBytes());
                        os.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                synchronized (messages) {
                    messages.add("Local: " + message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/call")
    public void startCall() {
        if(callActive) {
            return;
        }
        callActive = true;

        new Thread(() -> {
            try {
                // Audio format
                AudioFormat format = new AudioFormat(8000, 8, 1, true, true);

                // For mic audio
                DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
                targetLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetLine.open(format);
                targetLine.start();

                // For speaker audio
                DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
                sourceLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                sourceLine.open(format);
                sourceLine.start();

                byte[] buffer = new byte[1024];
                DatagramPacket packet;

                while (callActive) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length); // Capture audio data from the mic

                    if (usingUDP) {
                        // Send mic data
                        packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(REMOTE_IP), REMOTE_PORT_VOICE);
                        voiceSocket.send(packet);

                        // Receive audio data
                        packet = new DatagramPacket(buffer, buffer.length);
                        voiceSocket.receive(packet);
                    } else {
                        // Send mic data
                        OutputStream voiceOutStream = tcpVoiceSocket.getOutputStream();
                        voiceOutStream.write(buffer, 0, bytesRead);
                        voiceOutStream.flush();

                        // Receive audio data
                        InputStream voiceInStream = tcpVoiceSocket.getInputStream();
                        bytesRead = voiceInStream.read(buffer, 0, buffer.length);
                    }

                    // Play the received audio data
                    sourceLine.write(buffer, 0, bytesRead);
                }
            } catch (LineUnavailableException | IOException ex) {
                if(transition) {
                    return;
                }
                System.out.println("Error in call: " + ex.getMessage());
            }
        }).start();
    }

    @PostMapping("/endCall")
    public void endCall() {
        if (!callActive) {
            return;
        }
        callActive = false;

        if (targetLine != null && targetLine.isOpen()) {
            targetLine.stop();
            targetLine.close();
        }
        if (sourceLine != null && sourceLine.isOpen()) {
            sourceLine.stop();
            sourceLine.close();
        }
    }

    @PostMapping("/switchProtocol")
    public void switchProtocol() {
        transition = true;
        if (callActive)
            endCall();
        
        if (usingUDP) {
            // Switch to TCP
            deinitUDPSockets();
            initTCPSockets();
            usingUDP = false;
        } else {
            // Switch to UDP
            deinitTCPSockets();
            initUDPSockets();
            usingUDP = true;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread sleep interrupted: " + e.getMessage());
        }
        transition = false;
    }

    static String encryptMessage(String message) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes); // Encode the encrypted bytes to a string
    }

    static String decryptMessage(String encryptedMessage) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedMessage);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes); // Return the decrypted message
    }

    private void initUDPSockets() {
        try {
            // Initialize the message socket to listen for incoming messages
            messageSocket = new DatagramSocket(LOCAL_PORT_MESSAGE);

            // Initialize the voice socket to listen for incoming voice data
            voiceSocket = new DatagramSocket(LOCAL_PORT_VOICE);

            System.out.println("UDP sockets initialized for message and voice communication.");
        } catch (SocketException e) {
            System.err.println("Error initializing UDP sockets: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deinitUDPSockets() {
        if (messageSocket != null && !messageSocket.isClosed()) {
            messageSocket.close();
        }
        if (voiceSocket != null && !voiceSocket.isClosed()) {
            voiceSocket.close();
        }
        System.out.println("UDP sockets deinitialized.");
    }

    private void initTCPSockets() {
        try {
            // Initialize the server and client TCP sockets for message communication
            tcpMessageServerSocket = new ServerSocket(LOCAL_PORT_MESSAGE);
            tcpMessageSocket = new Socket(REMOTE_IP, REMOTE_PORT_MESSAGE);

            // Initialize the server and client TCP sockets for voice communication
            tcpVoiceServerSocket = new ServerSocket(LOCAL_PORT_VOICE);
            tcpVoiceSocket = new Socket(REMOTE_IP, REMOTE_PORT_VOICE);

            System.out.println("TCP sockets initialized for message and voice communication.");
        } catch (IOException e) {
            System.err.println("Error initializing TCP sockets: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deinitTCPSockets() {
        try {
            if (tcpMessageServerSocket != null && !tcpMessageServerSocket.isClosed()) {
                tcpMessageServerSocket.close();
            }
            if (tcpMessageSocket != null && !tcpMessageSocket.isClosed()) {
                tcpMessageSocket.close();
            }
            if (tcpVoiceServerSocket != null && !tcpVoiceServerSocket.isClosed()) {
                tcpVoiceServerSocket.close();
            }
            if (tcpVoiceSocket != null && !tcpVoiceSocket.isClosed()) {
                tcpVoiceSocket.close();
            }
            System.out.println("TCP sockets deinitialized.");
        } catch (IOException e) {
            System.err.println("Error deinitializing TCP sockets: " + e.getMessage());
            e.printStackTrace();
        }
    }

}