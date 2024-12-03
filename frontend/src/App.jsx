import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './App.css';

function App() {
    const [messages, setMessages] = useState([]);
    const [message, setMessage] = useState('');

    useEffect(() => {
        const fetchMessages = () => {
            axios.get('http://localhost:8080/api/messages')
                .then(response => {
                    // console.log(response.data);
                    if (Array.isArray(response.data)) {
                        setMessages(response.data);
                    } else {
                        console.error('Expected an array but got:', response.data);
                    }
                })
                .catch(error => console.error(error));
        };

        // Fetch messages initially
        fetchMessages();

        // Fetch messages every few milliseconds
        const intervalId = setInterval(fetchMessages, 200);

        return () => clearInterval(intervalId);
    }, []);

    const sendMessage = () => {
        if (!message) {
            return;
        }
        axios.post('http://localhost:8080/api/send', { message })
            .then(() => {
                setMessages([...messages, "Local: " + message]);
                setMessage('');
            })
            .catch(error => console.error(error));
    };

    const startCall = () => {
        axios.post('http://localhost:8080/api/call')
            .then(() => console.log('Call started'))
            .catch(error => console.error(error));
    };

    const endCall = () => {
        axios.post('http://localhost:8080/api/endCall')
            .then(() => console.log('Call ended'))
            .catch(error => console.error(error));
    };

    return (
        <div>
            <h1>Messages</h1>
            <ul>
                {messages.map((msg, index) => (
                    <li key={index}>{msg}</li>
                ))}
            </ul>
            <input
                type="text"
                value={message}
                onChange={e => setMessage(e.target.value)}
            />
            <button onClick={sendMessage}>Send</button>
            <button onClick={startCall}>Start Call</button>
            <button onClick={endCall}>End Call</button>
        </div>
    );
}

export default App;