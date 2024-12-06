package com.cn2.communication;

import java.io.*;
import java.net.*;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import java.awt.*;
import java.awt.event.*;
import java.lang.Thread;

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

	// UDP variables
	static DatagramSocket messageSocket; // For receiving/sending messages
	static DatagramSocket voiceSocket; // For receiving/sending call requests

	// Local Ports for message and voice communication
	static final int LOCAL_PORT_MESSAGE = 12345; // Local port for receiving messages
	static final int LOCAL_PORT_VOICE = 12346; // Local port for receiving voice data

	// Remote Ports for message and voice communication
	static final int REMOTE_PORT_MESSAGE = 12347; // Remote port for sending messages
	static final int REMOTE_PORT_VOICE = 12348; // Remote port for sending voice

	static final String REMOTE_IP = "127.0.0.1"; // Replace with the remote peer's IP address

	// Threads for receiving and sending messages
	static Thread receiveThread;

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

		/*
		 * 2. Adding the components to the GUI
		 */
		add(scrollPane);
		add(inputTextField);
		add(sendButton);
		add(callButton);

		/*
		 * 3. Linking the buttons to the ActionListener
		 */
		sendButton.addActionListener(this);
		callButton.addActionListener(this);

		/*
		 * 4. UDP Initialization (Message and Voice sockets)
		 */
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
			byte[] buffer = new byte[1024]; // Allocates a buffer to store incoming data
			while (true) {
				try {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					messageSocket.receive(packet); // Waits for an incoming message on the message socket
					String receivedMessage = new String(packet.getData(), 0, packet.getLength()); // Extracts the
																									// received data and
																									// convert it to a
																									// string
					textArea.append("Peer: " + receivedMessage + newline);
				} catch (IOException e) {
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

			// The "Send" button was clicked
			String message = inputTextField.getText();

			if (!message.isEmpty()) {
				// Create a DatagramPacket with the message data and send it to the remote
				// peer's message port

				byte[] messageData = message.getBytes(); // Converts the message into a byte array

				DatagramPacket messagePacket = new DatagramPacket(messageData, messageData.length,
						new InetSocketAddress(REMOTE_IP, REMOTE_PORT_MESSAGE));

				try {
					messageSocket.send(messagePacket); // Send messagePacket via the messageSocket
					textArea.append("You: " + message + newline); // Display message in GUI
					inputTextField.setText(""); // Clear the inputTextField
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

		} else if (e.getSource() == callButton) {

			// The "Call" button was clicked

			// TODO: Your code goes here...

		}

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
