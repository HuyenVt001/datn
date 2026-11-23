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
      message.success(result.disabled ? 'Account disabled.' : 'Account enabled.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const grantMutation = useMutation({
    mutationFn: (uid: string) => grantAdmin(uid),
    onSuccess: () => {
      message.success('Admin granted. The user must sign in to the admin panel again.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const revokeMutation = useMutation({
    mutationFn: (uid: string) => revokeAdmin(uid),
    onSuccess: () => {
      message.success('Admin revoked — effective immediately.');
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
          {user.uid === myUid && <Tag color="blue">You</Tag>}
        </Space>
      ),
    },
    {
      title: 'Display name',
      dataIndex: 'fullName',
      render: (name: string) => name || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Role',
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
        <Typography.Text strong={astrite > 0}>⭐ {astrite.toLocaleString('en-US')}</Typography.Text>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => (value ? new Date(value).toLocaleString('en-US') : '—'),
    },
    {
      title: 'Last sign-in',
      dataIndex: 'lastSignInAt',
      width: 165,
      render: (value?: string) =>
        value ? (
          new Date(value).toLocaleString('en-US')
        ) : (
          <Typography.Text type="secondary">Never signed in</Typography.Text>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'disabled',
      width: 110,
      render: (disabled: boolean) =>
        disabled ? <Tag color="red">Disabled</Tag> : <Tag color="green">Active</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 280,
      render: (_, user) => {
        // Chinh minh: khong khoa / khong thu quyen duoc (server cung chan)
        if (user.uid === myUid) {
          return <Typography.Text type="secondary">Your account</Typography.Text>;
        }
        return (
          <Space>
            <Popconfirm
              title={user.disabled ? 'Enable this account?' : 'Disable this account?'}
              description={
                user.disabled
                  ? 'The user will be able to sign in again.'
                  : 'The user will no longer be able to sign in (current sessions are revoked too).'
              }
              okText={user.disabled ? 'Enable' : 'Disable'}
              cancelText="Cancel"
              okButtonProps={{ danger: !user.disabled }}
              onConfirm={() => disableMutation.mutate({ uid: user.uid, disabled: !user.disabled })}
            >
              <Button
                size="small"
                danger={!user.disabled}
                icon={user.disabled ? <UnlockOutlined /> : <LockOutlined />}
                loading={disableMutation.isPending && disableMutation.variables?.uid === user.uid}
              >
                {user.disabled ? 'Enable' : 'Disable'}
              </Button>
            </Popconfirm>
            {user.admin ? (
              <Popconfirm
                title="Revoke this user's admin rights?"
                description="They lose access to the admin panel IMMEDIATELY."
                okText="Revoke"
                cancelText="Cancel"
                okButtonProps={{ danger: true }}
                onConfirm={() => revokeMutation.mutate(user.uid)}
              >
                <Button
                  size="small"
                  danger
                  icon={<UserDeleteOutlined />}
                  loading={revokeMutation.isPending && revokeMutation.variables === user.uid}
                >
                  Revoke
                </Button>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="Grant admin rights to this user?"
                description="They will be able to sign in to this admin panel (takes effect after signing in again)."
                okText="Grant"
                cancelText="Cancel"
                onConfirm={() => grantMutation.mutate(user.uid)}
              >
                <Button
                  size="small"
                  icon={<CrownOutlined />}
                  loading={grantMutation.isPending && grantMutation.variables === user.uid}
                >
                  Grant admin
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
        Manage users
      </Typography.Title>
      
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load users."
          description={(error as Error).message}
        />
      )}
      <Input.Search
        placeholder="Search by email or name..."
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
          showTotal: (total) => `${total} users total`
,
        }}
      />
    </div>
  );
}
