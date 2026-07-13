import { CrownOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntApp, Button, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { grantAdmin, listUsers, setUserDisabled } from '../api/admin.api';
import type { AdminUser } from '../types';

const PAGE_SIZE = 10;

/** Trang quản lý người dùng: tìm kiếm, phân trang, khóa/mở khóa, cấp quyền admin. */
export function UsersPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');

  const { data, isFetching } = useQuery({
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
      message.success('Đã cấp quyền admin. Người dùng cần đăng nhập lại để có hiệu lực.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const columns: ColumnsType<AdminUser> = [
    {
      title: 'Email',
      dataIndex: 'email',
      render: (email?: string) => email || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Tên hiển thị',
      dataIndex: 'fullName',
      render: (name: string) => name || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => (value ? new Date(value).toLocaleString('vi-VN') : '—'),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'disabled',
      width: 120,
      render: (disabled: boolean) =>
        disabled ? <Tag color="red">Đã khóa</Tag> : <Tag color="green">Hoạt động</Tag>,
    },
    {
      title: 'Hành động',
      key: 'actions',
      width: 260,
      render: (_, user) => (
        <Space>
          <Popconfirm
            title={user.disabled ? 'Mở khóa tài khoản này?' : 'Khóa tài khoản này?'}
            description={
              user.disabled
                ? 'Người dùng sẽ đăng nhập lại được.'
                : 'Người dùng sẽ không thể đăng nhập nữa.'
            }
            okText={user.disabled ? 'Mở khóa' : 'Khóa'}
            cancelText="Hủy"
            okButtonProps={{ danger: !user.disabled }}
            onConfirm={() =>
              disableMutation.mutate({ uid: user.uid, disabled: !user.disabled })
            }
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
          <Popconfirm
            title="Cấp quyền admin cho người dùng này?"
            description="Họ sẽ đăng nhập được trang quản trị sau khi đăng nhập lại."
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
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Quản lý người dùng
      </Typography.Title>
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
