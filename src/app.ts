import express, { Application, Request, Response } from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import dotenv from 'dotenv';

dotenv.config();

const app: Application = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

const server = http.createServer(app);

// Khởi tạo Socket.io Server 
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// Test Endpoint HTTP
app.get('/ping', (req: Request, res: Response) => {
    res.status(200).json({ message: 'Pong! Server is running smoothly.' });
});

// Quản lý kết nối Realtime (Socket.io)
io.on('connection', (socket) => {
    console.log(`⚡ Người dùng kết nối thành công: ${socket.id}`);

    // Ví dụ: Lắng nghe sự kiện thả emoji từ client
    socket.on('send_emoji', (data) => {
        console.log('Emoji nhận được:', data);
        // Phát lại emoji này cho những người khác trong phòng
        socket.broadcast.emit('receive_emoji', data);
    });

    socket.on('disconnect', () => {
        console.log(`❌ Người dùng đã ngắt kết nối: ${socket.id}`);
    });
});

// Chạy server bằng biến `server` thay vì `app` để kích hoạt cả Socket
server.listen(PORT, () => {
    console.log(`Server đang chạy tại địa chỉ: http://localhost:${PORT}`);
});