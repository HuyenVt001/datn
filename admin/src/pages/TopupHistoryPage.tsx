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

const vnd = (n: number) => `${n.toLocaleString('vi-VN')}đ`;

const STATUS_META: Record<TopupOrderStatus, { color: string; label: string; hint: string }> = {
  PENDING: {
    color: 'processing',
    label: 'Chờ trả',
    hint: 'Đã tạo link thanh toán, người dùng chưa trả xong.',
  },
  PAID: {
    color: 'success',
    label: 'Đã trả',
    hint: 'PayOS đã xác nhận và Astrite đã được cộng vào ví.',
  },
  CANCELLED: {
    color: 'default',
    label: 'Đã huỷ',
    hint: 'Người dùng bấm huỷ, hoặc tạo link thanh toán thất bại.',
  },
  EXPIRED: {
    color: 'warning',
    label: 'Quá hạn',
    hint: 'Quá 30 phút chưa trả. Nếu tiền vẫn về sau đó thì server vẫn cộng Astrite.',
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
      title: 'Thời điểm',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => new Date(value).toLocaleString('vi-VN'),
    },
    {
      title: 'Người nạp',
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
      title: 'Gói',
      dataIndex: 'packageName',
      render: (name: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{name}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            +{row.astrite.toLocaleString('vi-VN')} Astrite
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Số tiền',
      dataIndex: 'amountVnd',
      width: 120,
      align: 'right',
      render: (n: number) => <Typography.Text strong>{vnd(n)}</Typography.Text>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (s: TopupOrderStatus, row) => (
        <Space direction="vertical" size={0}>
          <Tooltip title={STATUS_META[s].hint}>
            <Tag color={STATUS_META[s].color}>{STATUS_META[s].label}</Tag>
          </Tooltip>
          {row.isSimulated && (
            <Tooltip title="Đơn do /topup/simulate tạo ở môi trường dev — KHÔNG có tiền thật.">
              <Tag color="cyan">Giả lập</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      // Mã đơn + mã giao dịch ngân hàng: hai thứ cần để đối chiếu với dashboard PayOS
      title: 'Mã đơn / Mã giao dịch',
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
        Lịch sử nạp Astrite
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 800 }}>
        Doanh thu chỉ tính đơn <b>Đã trả</b>. Đối chiếu <b>Mã giao dịch</b> ở đây với dashboard PayOS
        khi cần soát lại một khoản tiền.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được lịch sử nạp."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Lọc theo uid người nạp..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => setUid(value.trim())}
        />
        <Select
          placeholder="Mọi trạng thái"
          allowClear
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={STATUS_OPTIONS}
        />
        <DatePicker
          placeholder="Mọi ngày"
          value={date}
          onChange={setDate}
          format="DD/MM/YYYY"
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
        />
        <Button icon={<ReloadOutlined />} onClick={() => void refetch()} loading={isFetching}>
          Tải lại
        </Button>
      </Space>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title="Doanh thu"
              value={summary?.paidRevenueVnd ?? 0}
              suffix="đ"
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Đơn đã trả" value={summary?.paidCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Đơn chờ trả" value={summary?.pendingCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite đã phát" value={summary?.paidAstrite ?? 0} />
          </Card>
        </Col>
      </Row>

      {!!summary?.byDate.length && (
        <Card size="small" title="Doanh thu theo ngày" style={{ marginBottom: 16 }}>
          <Space wrap>
            {summary.byDate.map((d) => (
              <Tag key={d.date} color="green">
                {dayjs(d.date).format('DD/MM')}: {vnd(d.revenueVnd)} ({d.count} đơn)
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
        pagination={{ pageSize: 20, showTotal: (total) => `Tổng ${total} đơn` }}
      />
    </div>
  );
}
