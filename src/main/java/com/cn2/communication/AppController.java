package com.cn2.communication;

import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.*;
import java.io.*;
import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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
            byte[] buffer = new byte[1024];
            try {
                
                
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    messageSocket.receive(packet); // Waits for an incoming message on the message socket
                    String receivedMessage = new String(packet.getData(), 0, packet.getLength()); // Extracts the
																									// received data and
																									// convert it to a
																									// string
                    synchronized (messages) {
                        messages.add("Remote: " + receivedMessage); // Store the message
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
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
                byte[] messageData = message.getBytes(); // Converts the message into a byte array
                
                try {
                    DatagramPacket messagePacket = new DatagramPacket(messageData, messageData.length,
						new InetSocketAddress(REMOTE_IP, REMOTE_PORT_MESSAGE));
                    messageSocket.send(messagePacket); // Send messagePacket via the messageSocket
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                synchronized (messages) {
                    messages.add("Local: " + message);
                }
            }
        } catch (IOException e) {
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

                    // Send mic data
                    packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(REMOTE_IP), REMOTE_PORT_VOICE);
                    voiceSocket.send(packet);

                    // Receive audio data
                    packet = new DatagramPacket(buffer, buffer.length);
                    voiceSocket.receive(packet);

                    // boolean isTalking = detectTalking(buffer, bytesRead);

                    // Play the received audio data
                    sourceLine.write(packet.getData(), 0, packet.getLength());
                }
            } catch (LineUnavailableException | IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private boolean detectTalking(byte[] audioData, int length) {
        double rms = 0.0;
        for (int i = 0; i < length; i++) {
            rms += audioData[i] * audioData[i];
        }
        rms = Math.sqrt(rms / length);
        return rms > 0.01; // Adjust threshold as needed
    }

    @PostMapping("/endCall")
    public void endCall(@RequestBody String jsonUserId) {
        if (!callActive) {
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonUserId);
            int userId = jsonNode.get("userId").asInt();

            callActive = false;

            if (targetLine != null && targetLine.isOpen()) {
                targetLine.stop();
                targetLine.close();
            }
            if (sourceLine != null && sourceLine.isOpen()) {
                sourceLine.stop();
                sourceLine.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/isTalking")
    public boolean isTalking(@RequestParam String jsonUserId) {
        int userId = -1;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonUserId);
            userId = jsonNode.get("userId").asInt();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

}