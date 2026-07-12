"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = __importDefault(require("express"));
const http_1 = __importDefault(require("http"));
const socket_io_1 = require("socket.io");
const cors_1 = __importDefault(require("cors"));
const dotenv_1 = __importDefault(require("dotenv"));
// Cấu hình đọc file .env
dotenv_1.default.config();
const app = (0, express_1.default)();
const PORT = process.env.PORT || 5000;
// Cấu hình Middlewares cơ bản
app.use((0, cors_1.default)());
app.use(express_1.default.json());
app.use(express_1.default.urlencoded({ extended: true }));
// Tạo HTTP Server lồng vào Express
const server = http_1.default.createServer(app);
// Khởi tạo Socket.io Server (Cần thiết cho tính năng chat/reaction realtime)
const io = new socket_io_1.Server(server, {
    cors: {
        origin: "*", // Trong thực tế nên giới hạn domain của Frontend
        methods: ["GET", "POST"]
    }
});
// Test Endpoint HTTP
app.get('/ping', (req, res) => {
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
