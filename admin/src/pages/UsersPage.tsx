import {
  CrownOutlined,
  LockOutlined,
  UnlockOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntApp, Button, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { grantAdmin, listUsers, revokeAdmin, setUserDisabled } from '../api/admin.api';
import { useAuth } from '../auth/AuthContext';
import type { AdminUser } from '../types';

const PAGE_SIZE = 10;

/**
 * Trang quản lý người dùng: tìm kiếm, phân trang, khóa/mở khóa, cấp/THU quyền admin.
 * Không thao tác được lên chính mình (server cũng chặn) — đảm bảo luôn còn >= 1 admin.
 */
export function UsersPage() {
  const { message } = AntApp.useApp();
  const { uid: myUid } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');

  const { data, isFetching, error } = useQuery({
    queryKey: ['admin-users', page, search],
    queryFn: () => listUsers({ page, limit: PAGE_SIZE, search: search || undefined }),
    placeholderData: keepPreviousData,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] });

  const disableMutation = useMutation({
    mutationFn: ({ uid, disabled }: { uid: string; disabled: boolean }) =>
      setUserDisabled(uid, disabled),
    onSuccess: (result) => {
      message.success(result.disabled ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const grantMutation = useMutation({
    mutationFn: (uid: string) => grantAdmin(uid),
    onSuccess: () => {
      message.success('Đã cấp quyền admin. Người dùng cần đăng nhập lại trang quản trị.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const revokeMutation = useMutation({
    mutationFn: (uid: string) => revokeAdmin(uid),
    onSuccess: () => {
      message.success('Đã thu hồi quyền admin — hiệu lực ngay lập tức.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const columns: ColumnsType<AdminUser> = [
    {
      title: 'Email',
      dataIndex: 'email',
      render: (email: string | undefined, user) => (
        <Space size={4}>
          {email || <Typography.Text type="secondary">—</Typography.Text>}
          {user.uid === myUid && <Tag color="blue">Bạn</Tag>}
        </Space>
      ),
    },
    {
      title: 'Tên hiển thị',
      dataIndex: 'fullName',
      render: (name: string) => name || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Vai trò',
      dataIndex: 'admin',
      width: 110,
      render: (admin: boolean) =>
        admin ? (
          <Tag color="gold" icon={<CrownOutlined />}>
            Admin
          </Tag>
        ) : (
          <Tag>User</Tag>
        ),
    },
    {
      title: 'Astrite',
      dataIndex: 'astrite',
      width: 110,
      align: 'right',
      sorter: (a, b) => a.astrite - b.astrite,
      render: (astrite: number) => (
        <Typography.Text strong={astrite > 0}>⭐ {astrite.toLocaleString('vi-VN')}</Typography.Text>
      ),
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => (value ? new Date(value).toLocaleString('vi-VN') : '—'),
    },
    {
      title: 'Đăng nhập cuối',
      dataIndex: 'lastSignInAt',
      width: 165,
      render: (value?: string) =>
        value ? (
          new Date(value).toLocaleString('vi-VN')
        ) : (
          <Typography.Text type="secondary">Chưa đăng nhập</Typography.Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'disabled',
      width: 110,
      render: (disabled: boolean) =>
        disabled ? <Tag color="red">Đã khóa</Tag> : <Tag color="green">Hoạt động</Tag>,
    },
    {
      title: 'Hành động',
      key: 'actions',
      width: 280,
      render: (_, user) => {
        // Chinh minh: khong khoa / khong thu quyen duoc (server cung chan)
        if (user.uid === myUid) {
          return <Typography.Text type="secondary">Tài khoản của bạn</Typography.Text>;
        }
        return (
          <Space>
            <Popconfirm
              title={user.disabled ? 'Mở khóa tài khoản này?' : 'Khóa tài khoản này?'}
              description={
                user.disabled
                  ? 'Người dùng sẽ đăng nhập lại được.'
                  : 'Người dùng sẽ không thể đăng nhập nữa (phiên hiện tại cũng bị thu hồi).'
              }
              okText={user.disabled ? 'Mở khóa' : 'Khóa'}
              cancelText="Hủy"
              okButtonProps={{ danger: !user.disabled }}
              onConfirm={() => disableMutation.mutate({ uid: user.uid, disabled: !user.disabled })}
            >
              <Button
                size="small"
                danger={!user.disabled}
                icon={user.disabled ? <UnlockOutlined /> : <LockOutlined />}
                loading={disableMutation.isPending && disableMutation.variables?.uid === user.uid}
              >
                {user.disabled ? 'Mở khóa' : 'Khóa'}
              </Button>
            </Popconfirm>
            {user.admin ? (
              <Popconfirm
                title="Thu hồi quyền admin của người dùng này?"
                description="Họ mất quyền truy cập trang quản trị NGAY lập tức."
                okText="Thu hồi"
                cancelText="Hủy"
                okButtonProps={{ danger: true }}
                onConfirm={() => revokeMutation.mutate(user.uid)}
              >
                <Button
                  size="small"
                  danger
                  icon={<UserDeleteOutlined />}
                  loading={revokeMutation.isPending && revokeMutation.variables === user.uid}
                >
                  Thu quyền
                </Button>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="Cấp quyền admin cho người dùng này?"
                description="Họ sẽ đăng nhập được trang quản trị này (đăng nhập lại để có hiệu lực)."
                okText="Cấp quyền"
                cancelText="Hủy"
                onConfirm={() => grantMutation.mutate(user.uid)}
              >
                <Button
                  size="small"
                  icon={<CrownOutlined />}
                  loading={grantMutation.isPending && grantMutation.variables === user.uid}
                >
                  Cấp admin
                </Button>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Quản lý người dùng
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        Chỉ tài khoản có vai trò <Tag color="gold">Admin</Tag> đăng nhập được trang quản trị này.
        Người dùng thường phải được một admin cấp quyền mới truy cập được.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được danh sách người dùng."
          description={(error as Error).message}
        />
      )}
      <Input.Search
        placeholder="Tìm theo email hoặc tên..."
        allowClear
        style={{ maxWidth: 360, marginBottom: 16 }}
        onSearch={(value) => {
          setSearch(value.trim());
          setPage(1);
        }}
      />
      <Table<AdminUser>
        rowKey="uid"
        columns={columns}
        dataSource={data?.items}
        loading={isFetching}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: data?.total ?? 0,
          onChange: setPage,
          showSizeChanger: false,
          showTotal: (total) => `Tổng ${total} người dùng`,
        }}
      />
    </div>
  );
}
