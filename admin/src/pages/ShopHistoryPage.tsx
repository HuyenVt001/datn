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
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { listShopPurchases } from '../api/shop.api';
import type { AdminPurchase, ShopItemType } from '../types';

const TYPE_META: Record<ShopItemType, { label: string; color: string }> = {
  FRAME: { label: 'Khung ảnh', color: 'gold' },
  SKIN: { label: 'Giao diện', color: 'orange' },
  EFFECT: { label: 'Hiệu ứng chạm', color: 'purple' },
};

/** Lịch sử mua vật phẩm toàn hệ thống (2026-11-15 — thay Lịch sử quay). */
export function ShopHistoryPage() {
  const [uid, setUid] = useState('');
  const [itemType, setItemType] = useState<ShopItemType | undefined>(undefined);
  const [date, setDate] = useState<Dayjs | null>(null);

  const filter = useMemo(
    () => ({ uid: uid || undefined, itemType, date: date ? date.format('YYYY-MM-DD') : undefined }),
    [uid, itemType, date],
  );

  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['shop-purchases', filter],
    queryFn: () => listShopPurchases(filter),
    placeholderData: keepPreviousData,
  });

  const rows = useMemo(() => data ?? [], [data]);

  /** Tổng hợp trên đúng tập đang lọc — khớp bảng bên dưới. */
  const summary = useMemo(
    () => ({
      count: rows.length,
      spent: rows.reduce((sum, r) => sum + r.price, 0),
      buyers: new Set(rows.map((r) => r.uid)).size,
      byType: rows.reduce<Record<string, number>>((acc, r) => {
        acc[r.itemType] = (acc[r.itemType] ?? 0) + 1;
        return acc;
      }, {}),
    }),
    [rows],
  );

  const columns: ColumnsType<AdminPurchase> = [
    {
      title: 'Thời điểm',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => new Date(value).toLocaleString('vi-VN'),
    },
    {
      title: 'Người mua',
      dataIndex: 'fullName',
      width: 220,
      render: (fullName: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{fullName}</span>
          <Typography.Text type="secondary" copyable style={{ fontSize: 12 }}>
            {row.uid}
          </Typography.Text>
        </Space>
      ),
    },
    { title: 'Vật phẩm', dataIndex: 'itemName' },
    {
      title: 'Loại',
      dataIndex: 'itemType',
      width: 140,
      render: (type: ShopItemType) => (
        <Tag color={TYPE_META[type]?.color}>{TYPE_META[type]?.label ?? type}</Tag>
      ),
    },
    {
      title: 'Giá đã trả',
      dataIndex: 'price',
      width: 120,
      render: (v: number) => `${v.toLocaleString('vi-VN')} ⭐`,
    },
    {
      title: 'Số dư sau',
      dataIndex: 'balanceAfter',
      width: 120,
      render: (v: number) => v.toLocaleString('vi-VN'),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Lịch sử mua
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 760 }}>
        Mỗi dòng là một lần mua vật phẩm bằng Astrite. Tên và giá là <b>bản chụp lúc mua</b> — sửa
        hay gỡ mặt hàng sau đó không làm đổi lịch sử. Vật phẩm admin tặng không nằm ở đây (xem trang
        Nhật ký).
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được lịch sử mua."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Lọc theo uid người mua..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => setUid(value.trim())}
        />
        <Select
          placeholder="Mọi loại"
          allowClear
          style={{ width: 180 }}
          value={itemType}
          onChange={setItemType}
          options={(Object.keys(TYPE_META) as ShopItemType[]).map((t) => ({
            value: t,
            label: TYPE_META[t].label,
          }))}
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
            <Statistic title="Lượt mua" value={summary.count} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite đã tiêu" value={summary.spent} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Người mua" value={summary.buyers} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Space wrap>
              {(Object.keys(TYPE_META) as ShopItemType[]).map((t) => (
                <Tag key={t} color={TYPE_META[t].color}>
                  {TYPE_META[t].label}: {summary.byType[t] ?? 0}
                </Tag>
              ))}
            </Space>
          </Card>
        </Col>
      </Row>

      <Table<AdminPurchase>
        rowKey="purchaseId"
        columns={columns}
        dataSource={rows}
        loading={isFetching}
        pagination={{ pageSize: 20, showTotal: (total) => `Tổng ${total} lượt mua` }}
      />
    </div>
  );
}
