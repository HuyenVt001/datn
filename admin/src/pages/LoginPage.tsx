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
      return 'Incorrect email or password.';
    case 'auth/invalid-email':
      return 'Invalid email.';
    case 'auth/too-many-requests':
      return 'Too many failed attempts, please try again later.';
    case 'auth/network-request-failed':
      return 'Network error — check the connection to Firebase.';
    default:
      // Loi tu server (adminLogin) da la tieng Viet
      return err instanceof Error ? err.message : 'Sign-in failed.';
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
          <Typography.Text type="secondary">Sign in with an admin account</Typography.Text>
        </div>
        <Form<LoginForm> layout="vertical" onFinish={(values) => void onFinish(values)}>
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Enter the admin email.' },
              { type: 'email', message: 'Invalid email.' },
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="admin@example.com" autoFocus />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[{ required: true, message: 'Enter the password.' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="••••••••" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            Sign in
          </Button>
        </Form>
      </Card>
    </div>
  );
}
