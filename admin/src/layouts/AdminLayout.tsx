import {
  CameraOutlined,
  DashboardOutlined,
  DollarOutlined,
  GiftOutlined,
  HistoryOutlined,
  LogoutOutlined,
  PictureOutlined,
  RobotOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Space, Typography, theme } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const { Header, Sider, Content } = Layout;

const MENU_ITEMS = [
  { key: '/', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/users', icon: <TeamOutlined />, label: 'Người dùng' },
  { key: '/moments', icon: <CameraOutlined />, label: 'Bài đăng' },
  { key: '/frames', icon: <PictureOutlined />, label: 'Khung ảnh' },
  { key: '/gacha', icon: <GiftOutlined />, label: 'Kho vật phẩm' },
  { key: '/gacha-history', icon: <ThunderboltOutlined />, label: 'Lịch sử quay' },
  { key: '/topup', icon: <WalletOutlined />, label: 'Gói nạp' },
  { key: '/topup-history', icon: <DollarOutlined />, label: 'Lịch sử nạp' },
  { key: '/ai-verifications', icon: <RobotOutlined />, label: 'AI quest' },
  { key: '/logs', icon: <HistoryOutlined />, label: 'Nhật ký' },
];

/** Khung chung: sidebar menu + header (email + đăng xuất) + nội dung trang. */
export function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { email, logout } = useAuth();
  const { token: themeToken } = theme.useToken();

  // Menu active theo segment dau tien cua path
  const selectedKey = location.pathname === '/' ? '/' : `/${location.pathname.split('/')[1]}`;

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsible>
        <div
          style={{
            height: 48,
            margin: 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: 16,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
          }}
        >
          📸 Snapget Admin
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={(e) => navigate(e.key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: themeToken.colorBgContainer,
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            paddingInline: 24,
          }}
        >
          <Space>
            <Typography.Text type="secondary">{email}</Typography.Text>
            <Button icon={<LogoutOutlined />} onClick={() => void handleLogout()}>
              Đăng xuất
            </Button>
          </Space>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
