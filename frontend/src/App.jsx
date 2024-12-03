import React, { useState, useEffect, useRef } from "react";
import axios from "axios";
import "./App.css";
import { Phone, PhoneOff, Send } from "lucide-react";

function App() {
    const [messages, setMessages] = useState([]);
    const [message, setMessage] = useState("");
    const [callActive, setCallActive] = useState(false);
    const [lastMessageCount, setLastMessageCount] = useState(0);
    const [userId, setUserId] = useState(null);
    const messagesEndRef = useRef(0);

    useEffect(() => {
        const fetchMessages = () => {
            axios
                .get("http://localhost:8080/api/messages")
                .then((response) => {
                    // console.log(response.data);
                    if (Array.isArray(response.data)) {
                        setMessages(response.data);
                    } else {
                        console.error(
                            "Expected an array but got:",
                            response.data
                        );
                    }
                })
                .catch((error) => console.error(error));
        };

        // Fetch messages initially
        fetchMessages();

        // Fetch messages every few milliseconds
        const intervalId = setInterval(fetchMessages, 200);

        return () => clearInterval(intervalId);
    }, []);

    useEffect(() => {
        const sessionId = new Date().getTime().toString();
        axios
            .post("http://localhost:8080/api/assignUserId", sessionId)
            .then((response) => {
                setUserId(response.data);
            })
            .catch((error) => console.error(error));
    }, []);

    useEffect(() => {
        if (messages.length > lastMessageCount) {
            messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
            setLastMessageCount(messages.length);
        }
    }, [messages, lastMessageCount]);

    const sendMessage = () => {
        if (!message || userId === null) {
            return;
        }
        axios
            .post("http://localhost:8080/api/send", { message, userId })
            .then(() => {
                setMessages([...messages, "user" + userId + ": " + message]);
                setMessage("");
            })
            .catch((error) => console.error(error));
    };

    const startCall = () => {
        axios
            .post("http://localhost:8080/api/call")
            .then((response) => {
                setUserId(response.data);
                setCallActive(true);
                console.log("Call started with userId:", response.data);
            })
            .catch((error) => console.error(error));
    };

    const endCall = () => {
        console.log("Ending call for userId:", userId);
        axios
            .post("http://localhost:8080/api/endCall", { userId })
            .then(() => {
                setCallActive(false);
                setUserId(null);
                console.log("Call ended");
            })
            .catch((error) => console.error(error));
    };

    return (
        <div className="flex flex-col items-center pt-8 text-2xl gap-4 h-screen">
            <h1 className="text-3xl font-semibold">Chat App</h1>
            <div className="p-4 w-1/2 h-1/2 border border-gray-400">
                <div className="overflow-y-scroll w-full h-full">
                    {messages.map((msg, index) => (
                        <div key={index}>{msg}</div>
                    ))}
                    <div ref={messagesEndRef} />
                </div>
            </div>
            <div className="flex gap-4">
                <input
                    type="text"
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    className="border-gray-400 border-2 rounded-md p-1"
                />
                <button
                    className={`flex items-center gap-2 transition-all p-2 rounded-md ${
                        !message
                            ? "cursor-not-allowed opacity-20"
                            : "hover:bg-gray-200"
                    }`}
                    onClick={sendMessage}
                    disabled={!message}
                >
                    <h3>Send</h3>
                    <Send size={22} />
                </button>
            </div>
            <button className="flex items-center gap-2 hover:bg-gray-200 transition-colors p-2 rounded-md">
                {callActive ? <PhoneOff size={22} /> : <Phone size={22} />}
                <h3 onClick={callActive ? endCall : startCall}>
                    {callActive ? "End Call" : "Start Call"}
                </h3>
            </button>
        </div>
    );
}

export default App;