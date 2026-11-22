import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { listLogs } from '../api/admin.api';
import type { AdminAction, AdminLog } from '../types';

const PAGE_SIZE = 15;

/** Nhan + mau hien thi cho tung hanh dong. */
const ACTION_META: Record<AdminAction, { label: string; color: string }> = {
  USER_DISABLE: { label: 'Disable account', color: 'red' },
  USER_ENABLE: { label: 'Enable account', color: 'green' },
  GRANT_ADMIN: { label: 'Grant admin', color: 'gold' },
  REVOKE_ADMIN: { label: 'Revoke admin', color: 'volcano' },
  FRAME_CREATE: { label: 'Add frame', color: 'blue' },
  FRAME_UPDATE: { label: 'Edit frame', color: 'geekblue' },
  FRAME_DELETE: { label: 'Delete frame', color: 'magenta' },
  FRAME_GRANT: { label: 'Grant frame', color: 'cyan' },
  MOMENT_DELETE: { label: 'Delete post', color: 'purple' },
  SHOP_ITEM_CREATE: { label: 'List item', color: 'blue' },
  SHOP_ITEM_UPDATE: { label: 'Edit Shop item', color: 'geekblue' },
  SHOP_ITEM_DELETE: { label: 'Remove Shop item', color: 'magenta' },
  SHOP_ITEM_GRANT: { label: 'Gift item', color: 'cyan' },
  // Log cũ trước 2026-11-15 (gacha đã bỏ)
  GACHA_ITEM_CREATE: { label: 'Add gacha item', color: 'blue' },
  GACHA_ITEM_UPDATE: { label: 'Edit gacha item', color: 'geekblue' },
  GACHA_ITEM_DELETE: { label: 'Delete gacha item', color: 'magenta' },
  GACHA_ITEM_GRANT: { label: 'Gift item', color: 'cyan' },
  // Gói nạp đụng tiền thật — 3 hành động này là thứ cần soát lại đầu tiên khi
  // có tranh chấp "sao tôi trả từng này mà nhận được từng kia".
  TOPUP_PACKAGE_CREATE: { label: 'Add top-up package', color: 'green' },
  TOPUP_PACKAGE_UPDATE: { label: 'Edit top-up package', color: 'gold' },
  TOPUP_PACKAGE_DELETE: { label: 'Delete top-up package', color: 'volcano' },
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
      title: 'Time',
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => (value ? new Date(value).toLocaleString('en-US') : '—'),
    },
    {
      title: 'Admin',
      dataIndex: 'actorEmail',
      render: (email: string | undefined, log) => email ?? log.actorUid,
    },
    {
      title: 'Action',
      dataIndex: 'action',
      width: 170,
      render: (action: AdminAction) => (
        <Tag color={actionMeta(action).color}>{actionMeta(action).label}</Tag>
      ),
    },
    {
      title: 'Target',
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
        Admin audit log
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        Every admin action (disabling accounts, granting/revoking rights, managing frames, deleting
        posts) is logged automatically for auditing.
      </Typography.Paragraph>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load the audit log."
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
          showTotal: (total) => `${total} log entries total`,
        }}
      />
    </div>
  );
}
