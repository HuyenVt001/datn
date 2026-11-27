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
  FRAME: { label: 'Frame', color: 'gold' },
  SKIN: { label: 'Skin', color: 'orange' },
  EFFECT: { label: 'Touch effect', color: 'purple' },
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

  const rows = data?.rows ?? [];
  /** Server tính trên TOÀN BỘ tập đã lọc — bảng bên dưới có thể chỉ là phần đầu (tối đa 200). */
  const summary = data?.summary ?? { count: 0, spent: 0, buyers: 0, byType: {} };

  const columns: ColumnsType<AdminPurchase> = [
    {
      title: 'Time',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => new Date(value).toLocaleString('en-US'),
    },
    {
      title: 'Buyer',
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
    { title: 'Item', dataIndex: 'itemName' },
    {
      title: 'Type',
      dataIndex: 'itemType',
      width: 140,
      render: (type: ShopItemType) => (
        <Tag color={TYPE_META[type]?.color}>{TYPE_META[type]?.label ?? type}</Tag>
      ),
    },
    {
      title: 'Price paid',
      dataIndex: 'price',
      width: 120,
      render: (v: number) => `${v.toLocaleString('en-US')} ⭐`,
    },
    {
      title: 'Balance after',
      dataIndex: 'balanceAfter',
      width: 120,
      render: (v: number) => v.toLocaleString('en-US'),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Purchase history
      </Typography.Title>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load purchase history."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Filter by buyer uid..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => setUid(value.trim())}
        />
        <Select
          placeholder="All types"
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
            <Statistic title="Purchases" value={summary.count} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite spent" value={summary.spent} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Buyers" value={summary.buyers} />
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
        pagination={{
          pageSize: 20,
          showTotal: (shown) =>
            shown < summary.count
              ? `Showing newest ${shown} of ${summary.count} purchases`
              : `${shown} purchases total`,
        }}
      />
    </div>
  );
}
