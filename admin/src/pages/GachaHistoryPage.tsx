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
import { listGachaRolls } from '../api/gacha.api';
import type { AdminRoll, RollResultEntry, RollTier } from '../types';

/** Màu bậc — khớp mục 0.2 của GACHA_PLAN (N xám · R xanh · SR tím · SSR cam). */
const TIER_COLOR: Record<RollTier, string> = {
  N: '#9E9E9E',
  R: '#4FC3F7',
  SR: '#B388FF',
  SSR: '#FFA726',
};

const TIER_OPTIONS = (Object.keys(TIER_COLOR) as RollTier[]).map((t) => ({
  value: t,
  label: t === 'N' ? 'N — chỉ ra Astrite' : t,
}));

/** 1 kết quả lẻ trong lượt quay: bậc N hiện số Astrite, còn lại hiện tên vật phẩm. */
function ResultTag({ entry }: { entry: RollResultEntry }) {
  const label =
    entry.tier === 'N' ? `+${entry.astriteAmount ?? 0} ⭐` : (entry.itemName ?? entry.refId ?? '?');
  const tag = (
    <Tag color={TIER_COLOR[entry.tier]} style={{ color: '#000', fontWeight: 600, marginBottom: 4 }}>
      {entry.tier} · {label}
      {entry.isDuplicate && ` (trùng +${entry.refundAstrite})`}
    </Tag>
  );
  return entry.isDuplicate ? (
    <Tooltip title={`Đã sở hữu từ trước — hoàn ${entry.refundAstrite} Astrite`}>{tag}</Tooltip>
  ) : (
    tag
  );
}

/**
 * Lịch sử quay toàn hệ thống. 1 dòng = 1 lượt **bấm nút** (quay x10 vẫn là 1 dòng,
 * 10 kết quả trong cột Kết quả) — khớp cách server lưu `gachaRolls`.
 */
export function GachaHistoryPage() {
  const [uid, setUid] = useState('');
  const [tier, setTier] = useState<RollTier | undefined>(undefined);
  const [date, setDate] = useState<Dayjs | null>(null);

  const filter = useMemo(
    () => ({ uid: uid || undefined, tier, date: date ? date.format('YYYY-MM-DD') : undefined }),
    [uid, tier, date],
  );

  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['gacha-rolls', filter],
    queryFn: () => listGachaRolls(filter),
    placeholderData: keepPreviousData,
  });

  const rolls = useMemo(() => data ?? [], [data]);

  /** Tổng hợp trên đúng tập đang lọc — đọc số liệu khớp bảng bên dưới. */
  const summary = useMemo(() => {
    const results = rolls.flatMap((r) => r.results);
    const byTier = results.reduce<Record<string, number>>((acc, r) => {
      acc[r.tier] = (acc[r.tier] ?? 0) + 1;
      return acc;
    }, {});
    return {
      rolls: rolls.length,
      draws: results.length,
      spent: rolls.reduce((sum, r) => sum + r.cost, 0),
      refunded: rolls.reduce((sum, r) => sum + r.refundTotal, 0),
      byTier,
    };
  }, [rolls]);

  const columns: ColumnsType<AdminRoll> = [
    {
      title: 'Thời điểm',
      dataIndex: 'createdAt',
      width: 165,
      render: (value: string) => new Date(value).toLocaleString('vi-VN'),
    },
    {
      title: 'Người quay',
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
      title: 'Loại',
      dataIndex: 'rollType',
      width: 90,
      render: (type: string) => <Tag>{type === 'TEN' ? 'x10' : 'x1'}</Tag>,
    },
    {
      title: 'Kết quả',
      dataIndex: 'results',
      render: (results: RollResultEntry[]) => (
        <div>
          {results.map((entry, i) => (
            <ResultTag key={i} entry={entry} />
          ))}
        </div>
      ),
    },
    { title: 'Trừ', dataIndex: 'cost', width: 90 },
    {
      title: 'Hoàn',
      dataIndex: 'refundTotal',
      width: 90,
      render: (value: number) => (value > 0 ? `+${value}` : '—'),
    },
    { title: 'Số dư sau', dataIndex: 'balanceAfter', width: 110 },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Lịch sử quay gacha
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 760 }}>
        Mỗi dòng là <b>một lượt bấm nút</b> — quay x10 vẫn là một dòng với 10 kết quả. Số liệu tổng
        hợp bên dưới tính trên đúng tập đang lọc.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được lịch sử quay."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Lọc theo uid người quay..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => setUid(value.trim())}
        />
        <Select
          placeholder="Mọi bậc"
          allowClear
          style={{ width: 190 }}
          value={tier}
          onChange={setTier}
          options={TIER_OPTIONS}
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
            <Statistic title="Lượt quay" value={summary.rolls} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Số lần rút" value={summary.draws} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite đã tiêu" value={summary.spent} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Astrite hoàn lại" value={summary.refunded} />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }} wrap>
        {(Object.keys(TIER_COLOR) as RollTier[]).map((t) => (
          <Tag key={t} color={TIER_COLOR[t]} style={{ color: '#000', fontWeight: 600 }}>
            {t}: {summary.byTier[t] ?? 0}
          </Tag>
        ))}
      </Space>

      <Table<AdminRoll>
        rowKey="rollId"
        columns={columns}
        dataSource={rolls}
        loading={isFetching}
        pagination={{ pageSize: 20, showTotal: (total) => `Tổng ${total} lượt quay` }}
      />
    </div>
  );
}
