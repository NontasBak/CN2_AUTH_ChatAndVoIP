package com.cn2.communication;

import java.io.*;
import java.net.*;

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
	final static String newline="\n";		
	static JButton callButton;				
	
    static DatagramSocket chatSendSocket;
    static DatagramSocket chatReceiveSocket;
    static DatagramSocket voipSendSocket;
    static DatagramSocket voipReceiveSocket;
    static int CHAT_PORT = 9988;
    static int VOIP_PORT = 9977;
    static String IP_ADDRESS = "127.0.0.1";
    boolean callActive = false;
	
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
		textArea = new JTextArea(10,40);			
		textArea.setLineWrap(true);				
		textArea.setEditable(false);			
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		//Setting up the buttons
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

		
	}
	
	/**
	 * The main method of the application. It continuously listens for
	 * new messages.
	 */
	public static void main(String[] args){
	
		/*
		 * 1. Create the app's window
		 */
		App app = new App("CN2 - AUTH");  // TODO: You can add the title that will displayed on the Window of the App here																		  
		app.setSize(500,250);				  
		app.setVisible(true);				  

		/*
		 * 2. 
		 */
        try {
            chatSendSocket = new DatagramSocket();
            chatReceiveSocket = new DatagramSocket(CHAT_PORT);
            voipSendSocket = new DatagramSocket();
            voipReceiveSocket = new DatagramSocket(VOIP_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        new Thread(() -> {
            do {        
                try {
                    byte[] buffer = new byte[1024];
                    
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    chatReceiveSocket.receive(packet); // Wait for a packet to arrive
            
                    String message = new String(packet.getData(), 0, packet.getLength()); // Extract package
            
                    textArea.append("Remote: " + message + newline);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (true);
        }).start();
	}
	
	/**
	 * The method that corresponds to the Action Listener. Whenever an action is performed
	 * (i.e., one of the buttons is clicked) this method is executed. 
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		/*
		 * Check which button was clicked.
		 */
		if (e.getSource() == sendButton){
            try {
                String message = inputTextField.getText();
                if (!message.isEmpty()) {
                    byte[] data = message.getBytes();
                    InetAddress remoteAddress = InetAddress.getByName(IP_ADDRESS);
                    DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, CHAT_PORT);
            
                    chatSendSocket.send(packet);
            
                    textArea.append("Local: " + message + newline);
            
                    inputTextField.setText("");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
		} else if(e.getSource() == callButton){
            if (callButton.getText().equals("End Call")) {
                callActive = false;
                callButton.setText("Call");
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

                        // Play the received audio data
                        sourceLine.write(packet.getData(), 0, packet.getLength());
                    }
                } catch (LineUnavailableException | IOException ex) {
                    ex.printStackTrace();
                }
            }).start();
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
