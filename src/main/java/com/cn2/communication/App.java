package com.cn2.communication;

import java.io.*;
import java.net.*;
import java.util.Base64;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import java.awt.*;
import java.awt.event.*;
import java.lang.Thread;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class App extends Frame implements WindowListener, ActionListener {

	/*
	 * Definition of the app's fields
	 */
	static TextField inputTextField;
	static JTextArea textArea;
	static JFrame frame;
	static JButton sendButton;
	static JTextField meesageTextField;
	public static Color gray;
	final static String newline = "\n";
	static JButton callButton;
    
	static JButton switchProtocolButton; // Button to switch protocols
	static boolean usingUDP = true; // Default to UDP protocol
    static boolean transition = false; // Flag to indicate a protocol switch

	// UDP variables
	static DatagramSocket messageSocket; // For receiving/sending messages
	static DatagramSocket voiceSocket; // For receiving/sending call requests

	// TCP Sockets for messaging and voice
	static ServerSocket tcpMessageServerSocket; // Server-side TCP socket for messaging
	static Socket tcpMessageSocket; // Client-side TCP socket for messaging
	static ServerSocket tcpVoiceServerSocket; // Server-side TCP socket for voice
	static Socket tcpVoiceSocket; // Client-side TCP socket for voice

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

    // For encryption
	static String KEY = "123456789ABCDEFG"; // 16-byte key

	/**
	 * Construct the app's frame and initialize important parameters
	 */
	public App(String title) {

		/*
		 * 1. Defining the components of the GUI
		 */

		// Setting up the characteristics of the frame
		super(title);
		gray = new Color(254, 254, 254);
		setBackground(gray);
		setLayout(new FlowLayout());
		addWindowListener(this);

		// Setting up the TextField and the TextArea
		inputTextField = new TextField();
		inputTextField.setColumns(20);

		// Setting up the TextArea.
		textArea = new JTextArea(10, 40);
		textArea.setLineWrap(true);
		textArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		// Setting up the buttons
		sendButton = new JButton("Send");
		callButton = new JButton("Call");
		switchProtocolButton = new JButton("Switch to TCP");

		/*
		 * 2. Adding the components to the GUI
		 */
		add(scrollPane);
		add(inputTextField);
		add(sendButton);
		add(callButton);
		add(switchProtocolButton);

		/*
		 * 3. Linking the buttons to the ActionListener
		 */
		sendButton.addActionListener(this);
		callButton.addActionListener(this);
		switchProtocolButton.addActionListener(this);

		/*
		 * 4. UDP Initialization (UDP set us default protocol)
		 */

		initUDPSockets();

	}

	/**
	 * Initializes UDP sockets for message and voice communication.
	 */
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

	/**
	 * Deinitializes UDP sockets (closes them).
	 */
	private void deinitUDPSockets() {
		if (messageSocket != null && !messageSocket.isClosed()) {
			messageSocket.close();
		}
		if (voiceSocket != null && !voiceSocket.isClosed()) {
			voiceSocket.close();
		}
		System.out.println("UDP sockets deinitialized.");
	}

	/**
	 * Initializes TCP sockets for message and voice communication.
	 */
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

	/**
	 * Deinitializes TCP sockets (closes them).
	 */
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

	/**
	 * The main method of the application. It continuously listens for
	 * new messages.
	 */
	public static void main(String[] args) {

		/*
		 * 1. Create the app's window
		 */
		App app = new App("CN2 - AUTH - Team K"); // Create the app's window

		app.setSize(500, 250);
		app.setVisible(true);

		/*
		 * 2. Continuously listen for incoming messages
		 */
		new Thread(() -> {
			while (true) {
				try {
					if (usingUDP) {
						// Handle UDP messages
						byte[] buffer = new byte[1024];
						DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
						messageSocket.receive(packet); // Wait for incoming message on the UDP socket
						String receivedMessage = decryptMessage(new String(packet.getData(), 0, packet.getLength()));
						textArea.append("Peer: " + receivedMessage + newline);
					} else {
						// Handle TCP messages
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(tcpMessageSocket.getInputStream()));
						String receivedMessage = reader.readLine(); // Read incoming message line by line
						if (receivedMessage != null) {
							receivedMessage = decryptMessage(receivedMessage);
							textArea.append("Peer: " + receivedMessage + newline);
						}
					}
				} catch (Exception e) {
                    if (transition) {
                        continue;
                    }
					System.err.println("Error receiving message: " + e.getMessage());
				}
			}
		}).start();
	}

	/**
	 * The method that corresponds to the Action Listener. Whenever an action is
	 * performed
	 * (i.e., one of the buttons is clicked) this method is executed.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {

		/*
		 * Check which button was clicked.
		 */
		if (e.getSource() == sendButton) {
			// Send message logic (UDP or TCP, based on protocol)
			String message = inputTextField.getText();

			if (!message.isEmpty()) {
				try {
					String encryptedMessage = encryptMessage(message); // Encrypt the message
					if (usingUDP) {
						// Send message via UDP
						DatagramPacket messagePacket = new DatagramPacket(encryptedMessage.getBytes(), encryptedMessage.getBytes().length,
								new InetSocketAddress(REMOTE_IP, REMOTE_PORT_MESSAGE));
						messageSocket.send(messagePacket);
					} else {
						// Send message via TCP
						OutputStream os = tcpMessageSocket.getOutputStream();
						os.write(encryptedMessage.getBytes());
						os.flush();
					}

					textArea.append("You: " + message + newline); // Display message in GUI
					inputTextField.setText(""); // Clear the inputTextField
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		} else if (e.getSource() == callButton) {

			if (callActive) {
				terminateCall();
				return;
			}
			callActive = true;
			callButton.setText("End Call");
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
							packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(REMOTE_IP),
									REMOTE_PORT_VOICE);
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
					ex.printStackTrace();
				}
			}).start();
		} else if (e.getSource() == switchProtocolButton) {

			// Switch between UDP and TCP
            transition = true;
            if(callActive)
                terminateCall();

			if (usingUDP) {

				// Switch to TCP

				System.out.println("Switching to TCP protocol...");
				deinitUDPSockets(); // Close UDP sockets
				initTCPSockets(); // Initialize TCP sockets
				usingUDP = false;
				switchProtocolButton.setText("Switch to UDP");
				textArea.append("Switched to TCP protocol." + newline);
			} else {

				// Switch to UDP

				System.out.println("Switching to UDP protocol...");
				deinitTCPSockets(); // Close TCP sockets
				initUDPSockets(); // Initialize UDP sockets
				usingUDP = true;
				switchProtocolButton.setText("Switch to TCP");
				textArea.append("Switched to UDP protocol." + newline);
			}
            transition = false;
		}

	}

    private void terminateCall() {
        callActive = false;
        callButton.setText("Call");

        if (targetLine != null && targetLine.isOpen()) {
            targetLine.stop();
            targetLine.close();
        }
        if (sourceLine != null && sourceLine.isOpen()) {
            sourceLine.stop();
            sourceLine.close();
        }
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

	/**
	 * These methods have to do with the GUI. You can use them if you wish to define
	 * what the program should do in specific scenarios (e.g., when closing the
	 * window).
	 */
	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
        transition = true;
		deinitUDPSockets();
		deinitTCPSockets();

		dispose();
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
	}
}
