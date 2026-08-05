import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { listLogs } from '../api/admin.api';
import type { AdminAction, AdminLog } from '../types';

const PAGE_SIZE = 15;

/** Nhan + mau hien thi cho tung hanh dong. */
const ACTION_META: Record<AdminAction, { label: string; color: string }> = {
  USER_DISABLE: { label: 'Khóa tài khoản', color: 'red' },
  USER_ENABLE: { label: 'Mở khóa tài khoản', color: 'green' },
  GRANT_ADMIN: { label: 'Cấp quyền admin', color: 'gold' },
  REVOKE_ADMIN: { label: 'Thu quyền admin', color: 'volcano' },
  FRAME_CREATE: { label: 'Thêm khung', color: 'blue' },
  FRAME_UPDATE: { label: 'Sửa khung', color: 'geekblue' },
  FRAME_DELETE: { label: 'Xóa khung', color: 'magenta' },
  FRAME_GRANT: { label: 'Cấp khung', color: 'cyan' },
  MOMENT_DELETE: { label: 'Xóa bài đăng', color: 'purple' },
  GACHA_ITEM_CREATE: { label: 'Thêm vật phẩm gacha', color: 'blue' },
  GACHA_ITEM_UPDATE: { label: 'Sửa vật phẩm gacha', color: 'geekblue' },
  GACHA_ITEM_DELETE: { label: 'Xóa vật phẩm gacha', color: 'magenta' },
  // Gói nạp đụng tiền thật — 3 hành động này là thứ cần soát lại đầu tiên khi
  // có tranh chấp "sao tôi trả từng này mà nhận được từng kia".
  TOPUP_PACKAGE_CREATE: { label: 'Thêm gói nạp', color: 'green' },
  TOPUP_PACKAGE_UPDATE: { label: 'Sửa gói nạp', color: 'gold' },
  TOPUP_PACKAGE_DELETE: { label: 'Xóa gói nạp', color: 'volcano' },
};

function actionMeta(action: AdminAction) {
  return ACTION_META[action] ?? { label: String(action), color: 'default' };
}

/** Trang nhật ký admin: ai làm gì, lên đối tượng nào, lúc nào (audit log). */
export function LogsPage() {
  const [page, setPage] = useState(1);

  const { data, isFetching, error } = useQuery({
    queryKey: ['admin-logs', page],
    queryFn: () => listLogs({ page, limit: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnsType<AdminLog> = [
    {
      title: 'Thời gian',
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => (value ? new Date(value).toLocaleString('vi-VN') : '—'),
    },
    {
      title: 'Admin thao tác',
      dataIndex: 'actorEmail',
      render: (email: string | undefined, log) => email ?? log.actorUid,
    },
    {
      title: 'Hành động',
      dataIndex: 'action',
      width: 170,
      render: (action: AdminAction) => (
        <Tag color={actionMeta(action).color}>{actionMeta(action).label}</Tag>
      ),
    },
    {
      title: 'Đối tượng',
      key: 'target',
      render: (_, log) =>
        log.targetLabel || log.targetId ? (
          <span>
            {log.targetLabel ?? ''}
            {log.targetId && (
              <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                {log.targetId}
              </Typography.Text>
            )}
          </span>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Nhật ký admin
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        Mọi hành động quản trị (khóa tài khoản, cấp/thu quyền, quản lý khung, xóa bài) đều được ghi
        lại tự động để đối soát.
      </Typography.Paragraph>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được nhật ký."
          description={(error as Error).message}
        />
      )}

      <Table<AdminLog>
        rowKey="logId"
        columns={columns}
        dataSource={data?.items}
        loading={isFetching}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: data?.total ?? 0,
          onChange: setPage,
          showSizeChanger: false,
          showTotal: (total) => `Tổng ${total} dòng nhật ký`,
        }}
      />
    </div>
  );
}
