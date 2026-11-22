import { ReloadOutlined } from '@ant-design/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { listTopupOrders } from '../api/topup.api';
import type { AdminTopupOrder, TopupOrderStatus } from '../types';

const vnd = (n: number) => `${n.toLocaleString('en-US')} VND`;

const STATUS_META: Record<TopupOrderStatus, { color: string; label: string; hint: string }> = {
  PENDING: {
    color: 'processing',
    label: 'Pending',
    hint: 'Payment link created, the user hasn\'t finished paying.',
  },
  PAID: {
    color: 'success',
    label: 'Paid',
    hint: 'PayOS confirmed and Astrite was added to the wallet.',
  },
  CANCELLED: {
    color: 'default',
    label: 'Cancelled',
    hint: 'The user cancelled, or creating the payment link failed.',
  },
  EXPIRED: {
    color: 'warning',
    label: 'Expired',
    hint: 'Not paid within 30 minutes. If the money still arrives later, the server still credits Astrite.',
  },
};

const STATUS_OPTIONS = (Object.keys(STATUS_META) as TopupOrderStatus[]).map((s) => ({
  value: s,
  label: STATUS_META[s].label,
}));

/**
 * Lịch sử nạp tiền toàn hệ thống + doanh thu.
 *
 * Doanh thu chỉ tính đơn **Đã trả**, và tính trên **toàn bộ tập đã lọc** chứ
 * không phải trên số dòng đang hiển thị — đặt limit nhỏ không làm doanh thu tụt.
 */
export function TopupHistoryPage() {
  const [uid, setUid] = useState('');
  const [status, setStatus] = useState<TopupOrderStatus | undefined>(undefined);
  const [date, setDate] = useState<Dayjs | null>(null);

  const filter = useMemo(
    () => ({ uid: uid || undefined, status, date: date ? date.format('YYYY-MM-DD') : undefined }),
    [uid, status, date],
  );

  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['topup-orders', filter],
    queryFn: () => listTopupOrders(filter),
    placeholderData: keepPreviousData,
  });

  const rows = data?.rows ?? [];
  const summary = data?.summary;

  const columns: ColumnsType<AdminTopupOrder> = [
    {
      title: 'Time',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => new Date(value).toLocaleString('en-US'),
    },
    {
      title: 'User',
      dataIndex: 'fullName',
      width: 200,
      render: (fullName: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{fullName}</span>
          <Typography.Text type="secondary" copyable style={{ fontSize: 12 }}>
            {row.uid}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Package',
      dataIndex: 'packageName',
      render: (name: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{name}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            +{row.astrite.toLocaleString('en-US')} Astrite
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'amountVnd',
      width: 120,
      align: 'right',
      render: (n: number) => <Typography.Text strong>{vnd(n)}</Typography.Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (s: TopupOrderStatus, row) => (
        <Space direction="vertical" size={0}>
          <Tooltip title={STATUS_META[s].hint}>
            <Tag color={STATUS_META[s].color}>{STATUS_META[s].label}</Tag>
          </Tooltip>
          {row.isSimulated && (
            <Tooltip title="Order created by /topup/simulate in dev — NO real money.">
              <Tag color="cyan">Simulated</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      // Mã đơn + mã giao dịch ngân hàng: hai thứ cần để đối chiếu với dashboard PayOS
      title: 'Order code / Transaction ref',
      key: 'codes',
      width: 210,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text copyable style={{ fontSize: 12 }}>
            {String(row.orderCode)}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.payosReference ?? '—'}
          </Typography.Text>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Astrite top-up history
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 800 }}>
        Revenue counts only <b>Paid</b> orders. Match the <b>Transaction ref</b> here against the PayOS
        dashboard when you need to reconcile a payment.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load top-up history."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Filter by user uid..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => setUid(value.trim())}
        />
        <Select
          placeholder="All statuses"
          allowClear
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={STATUS_OPTIONS}
        />
        <DatePicker
          placeholder="All dates"
          value={date}
          onChange={setDate}
          format="DD/MM/YYYY"
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
        />
        <Button icon={<ReloadOutlined />} onClick={() => void refetch()} loading={isFetching}>
          Reload
        </Button>
      </Space>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title="Revenue"
              value={summary?.paidRevenueVnd ?? 0}
              suffix="VND"
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Paid orders" value={summary?.paidCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Pending orders" value={summary?.pendingCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite issued" value={summary?.paidAstrite ?? 0} />
          </Card>
        </Col>
      </Row>

      {!!summary?.byDate.length && (
        <Card size="small" title="Revenue by day" style={{ marginBottom: 16 }}>
          <Space wrap>
            {summary.byDate.map((d) => (
              <Tag key={d.date} color="green">
                {dayjs(d.date).format('DD/MM')}: {vnd(d.revenueVnd)} ({d.count} orders)
              </Tag>
            ))}
          </Space>
        </Card>
      )}

      <Table<AdminTopupOrder>
        rowKey="orderCode"
        columns={columns}
        dataSource={rows}
        loading={isFetching}
        pagination={{ pageSize: 20, showTotal: (total) => `${total} orders total` }}
      />
    </div>
  );
}
