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

    private static DatagramSocket chatSendSocket;
    private static DatagramSocket chatReceiveSocket;
    private static DatagramSocket voipSendSocket;
    private static DatagramSocket voipReceiveSocket;
    private static final int CHAT_PORT = 9988;
    private static final int VOIP_PORT = 9977;
    private static final String IP_ADDRESS = "127.0.0.1";
    private boolean callActive = false;
    private static List<String> messages = new ArrayList<>();

    static {
        try {
            chatSendSocket = new DatagramSocket();
            chatReceiveSocket = new DatagramSocket(CHAT_PORT);
            voipSendSocket = new DatagramSocket();
            voipReceiveSocket = new DatagramSocket(VOIP_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // Start a thread to listen for incoming messages
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                while (true) {
                    chatReceiveSocket.receive(packet); // Wait for a packet to arrive
                    String message = new String(packet.getData(), 0, packet.getLength()); // Extract message
                    synchronized (messages) {
                        messages.add("Remote: " + message); // Store the message
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
                byte[] data = message.getBytes();
                InetAddress remoteAddress = InetAddress.getByName(IP_ADDRESS);
                DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, CHAT_PORT);
                chatSendSocket.send(packet);

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
        callActive = true;

        new Thread(() -> {
            try {
                // Audio format
                AudioFormat format = new AudioFormat(8000, 8, 1, true, true);

                // For mic audio
                DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetLine.open(format);
                targetLine.start();

                // For speaker audio
                DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine sourceLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                sourceLine.open(format);
                sourceLine.start();

                byte[] buffer = new byte[1024];
                DatagramPacket packet;

                while (callActive) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length); // Capture audio data from the mic

                    // Send mic data
                    packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(IP_ADDRESS), VOIP_PORT);
                    voipSendSocket.send(packet);

                    // Receive audio data
                    packet = new DatagramPacket(buffer, buffer.length);
                    voipReceiveSocket.receive(packet);

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
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonUserId);
            int userId = jsonNode.get("userId").asInt();

            callActive = false;
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