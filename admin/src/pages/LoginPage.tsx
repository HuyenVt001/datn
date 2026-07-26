import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { App as AntApp, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface LoginForm {
  email: string;
  password: string;
}

/** Doi loi Firebase (code tieng Anh) sang thong bao tieng Viet than thien. */
function friendlyError(err: unknown): string {
  const code = (err as { code?: string })?.code;
  switch (code) {
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      return 'Email hoặc mật khẩu không đúng.';
    case 'auth/invalid-email':
      return 'Email không hợp lệ.';
    case 'auth/too-many-requests':
      return 'Thử sai quá nhiều lần, vui lòng thử lại sau.';
    case 'auth/network-request-failed':
      return 'Lỗi mạng — kiểm tra kết nối tới Firebase.';
    default:
      // Loi tu server (adminLogin) da la tieng Viet
      return err instanceof Error ? err.message : 'Đăng nhập thất bại.';
  }
}

export function LoginPage() {
  const { token, login } = useAuth();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);

  // Da dang nhap roi thi khoi vao lai trang login
  if (token) {
    return <Navigate to="/" replace />;
  }

  const onFinish = async (values: LoginForm) => {
    setLoading(true);
    try {
      await login(values.email, values.password);
      navigate('/', { replace: true });
    } catch (err) {
      message.error(friendlyError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f0ea',
      }}
    >
      <Card style={{ width: 380 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Typography.Title level={3} style={{ marginBottom: 4 }}>
            📸 Snapget Admin
          </Typography.Title>
          <Typography.Text type="secondary">Đăng nhập bằng tài khoản quản trị</Typography.Text>
        </div>
        <Form<LoginForm> layout="vertical" onFinish={(values) => void onFinish(values)}>
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Nhập email quản trị.' },
              { type: 'email', message: 'Email không hợp lệ.' },
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="admin@example.com" autoFocus />
          </Form.Item>
          <Form.Item
            name="password"
            label="Mật khẩu"
            rules={[{ required: true, message: 'Nhập mật khẩu.' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="••••••••" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            Đăng nhập
          </Button>
        </Form>
      </Card>
    </div>
  );
}
