import { ReloadOutlined } from '@ant-design/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Image,
  Input,
  Progress,
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
import { listAiVerifications } from '../api/admin.api';
import type { AiVerification, AiVerificationOutcome } from '../types';

const PAGE_SIZE = 15;

/** Nhãn + màu kết quả — khớp `AiQuestResult.result` phía server. */
const OUTCOME_META: Record<AiVerificationOutcome, { label: string; color: string; hint: string }> = {
  MATCHED: { label: 'Matched', color: 'green', hint: 'Photo contains the object → AI quest completed, +30 Astrite' },
  NOT_MATCHED: { label: 'Not matched', color: 'orange', hint: 'Post still published, quest not ticked' },
  SKIPPED: { label: 'Skipped', color: 'default', hint: 'AI service error/timeout — posting unaffected' },
};

const OUTCOME_OPTIONS = (Object.keys(OUTCOME_META) as AiVerificationOutcome[]).map((o) => ({
  value: o,
  label: OUTCOME_META[o].label,
}));

/** Tên tiếng Việt cho 12 lớp model (9 lớp ra đề + 3 lớp chỉ có trong `scores`). */
const CLASS_VI: Record<string, string> = {
  cup: 'cup',
  bottle: 'bottle',
  book: 'book',
  chair: 'chair',
  'potted plant': 'potted plant',
  laptop: 'laptop',
  keyboard: 'keyboard',
  backpack: 'backpack',
  clock: 'clock',
  umbrella: 'umbrella',
  bicycle: 'bicycle',
  motorcycle: 'motorcycle',
};

function classLabel(cls: string) {
  return CLASS_VI[cls] ? `${CLASS_VI[cls]} (${cls})` : cls;
}

/** Điểm của lớp mục tiêu so với ngưỡng — thanh tiến trình có vạch ngưỡng để nhìn nhanh biên độ. */
function ScoreCell({ row }: { row: AiVerification }) {
  if (row.score === undefined) {
    return <Typography.Text type="secondary">—</Typography.Text>;
  }
  const pct = Math.round(row.score * 100);
  const thr = row.threshold !== undefined ? Math.round(row.threshold * 100) : undefined;
  return (
    <Tooltip title={thr !== undefined ? `Score ${pct}% · threshold ${thr}%` : `Score ${pct}%`}>
      <div style={{ minWidth: 120 }}>
        <Progress
          percent={pct}
          size="small"
          status={row.outcome === 'MATCHED' ? 'success' : 'normal'}
          format={() => `${pct}%`}
        />
        {thr !== undefined && (
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            threshold {thr}%
          </Typography.Text>
        )}
      </div>
    </Tooltip>
  );
}

/** Top 3 điểm cao nhất trong 12 lớp — thấy model "nghĩ" ảnh là gì khi trượt. */
function TopScores({ scores, target }: { scores?: Record<string, number>; target: string }) {
  if (!scores) {
    return <Typography.Text type="secondary">—</Typography.Text>;
  }
  const top = Object.entries(scores)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3);
  return (
    <Space direction="vertical" size={0}>
      {top.map(([cls, s]) => (
        <Typography.Text key={cls} strong={cls === target} style={{ fontSize: 12 }}>
          {classLabel(cls)}: {Math.round(s * 100)}%
        </Typography.Text>
      ))}
    </Space>
  );
}

/**
 * Log AI xác minh ảnh quest — mỗi dòng = 1 lần server hỏi AI service "ảnh này có chứa X không"
 * (kể cả trượt / SKIPPED). Dùng để: (1) đo accuracy thực tế trên production cho báo cáo,
 * (2) soi lớp yếu → calibrate ngưỡng / bỏ khỏi vòng quay (QUEST_AI_PLAN mục 2.3, 5.2, 15.3).
 */
export function AiVerificationsPage() {
  const [page, setPage] = useState(1);
  const [uid, setUid] = useState('');
  const [outcome, setOutcome] = useState<AiVerificationOutcome | undefined>(undefined);
  const [date, setDate] = useState<Dayjs | null>(null);

  const filter = useMemo(
    () => ({
      uid: uid || undefined,
      outcome,
      date: date ? date.format('YYYY-MM-DD') : undefined,
    }),
    [uid, outcome, date],
  );

  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['ai-verifications', page, filter],
    queryFn: () => listAiVerifications({ page, limit: PAGE_SIZE, ...filter }),
    placeholderData: keepPreviousData,
  });

  // Tổng hợp trên TRANG đang xem (server phân trang) — đủ để nhìn nhanh; số liệu chuẩn lấy từ notebook.
  const summary = useMemo(() => {
    const items = data?.items ?? [];
    const decided = items.filter((v) => v.outcome !== 'SKIPPED');
    const matched = items.filter((v) => v.outcome === 'MATCHED').length;
    const latencies = items.map((v) => v.latencyMs).filter((x): x is number => x !== undefined);
    return {
      total: data?.total ?? 0,
      matched,
      matchRate: decided.length ? Math.round((matched / decided.length) * 100) : undefined,
      skipped: items.filter((v) => v.outcome === 'SKIPPED').length,
      avgLatency: latencies.length
        ? Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length)
        : undefined,
    };
  }, [data]);

  const columns: ColumnsType<AiVerification> = [
    {
      title: 'Time',
      dataIndex: 'createdAt',
      width: 160,
      render: (value: string) => new Date(value).toLocaleString('en-US'),
    },
    {
      title: 'Photo',
      dataIndex: 'mediaUrl',
      width: 90,
      render: (url: string | undefined) =>
        url ? (
          <Image src={url} width={64} height={64} style={{ objectFit: 'cover', borderRadius: 6 }} />
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    {
      title: 'User',
      dataIndex: 'uid',
      width: 200,
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text copyable style={{ fontSize: 12 }}>
            {value}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            moment {row.momentId}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Required quest',
      dataIndex: 'targetClass',
      width: 150,
      render: (cls: string, row) => (
        <Space direction="vertical" size={0}>
          <Tag color="blue">{classLabel(cls)}</Tag>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.date}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Outcome',
      dataIndex: 'outcome',
      width: 120,
      render: (o: AiVerificationOutcome, row) => (
        <Tooltip title={row.error ?? OUTCOME_META[o].hint}>
          <Tag color={OUTCOME_META[o].color}>{OUTCOME_META[o].label}</Tag>
        </Tooltip>
      ),
    },
    {
      title: 'Score / threshold',
      key: 'score',
      width: 160,
      render: (_, row) => <ScoreCell row={row} />,
    },
    {
      title: 'Top 3 classes',
      key: 'top',
      render: (_, row) => <TopScores scores={row.scores} target={row.targetClass} />,
    },
    {
      title: 'Model · ms',
      key: 'model',
      width: 120,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Tag>{row.modelVersion ?? '—'}</Tag>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.latencyMs !== undefined ? `${row.latencyMs} ms` : '—'}
            {row.roundTripMs !== undefined && ` · rt ${row.roundTripMs}`}
          </Typography.Text>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        AI quest photo verification
      </Typography.Title>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load AI logs."
          description={(error as Error).message}
        />
      )}

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Filter by user uid..."
          allowClear
          style={{ width: 300 }}
          onSearch={(value) => {
            setUid(value.trim());
            setPage(1);
          }}
        />
        <Select
          placeholder="All outcomes"
          allowClear
          style={{ width: 170 }}
          value={outcome}
          onChange={(v) => {
            setOutcome(v);
            setPage(1);
          }}
          options={OUTCOME_OPTIONS}
        />
        <DatePicker
          placeholder="All dates"
          value={date}
          onChange={(d) => {
            setDate(d);
            setPage(1);
          }}
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
            <Statistic title="Total verifications (filtered)" value={summary.total} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title="Match rate (this page)"
              value={summary.matchRate ?? '—'}
              suffix={summary.matchRate !== undefined ? '%' : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic title="Skipped (this page)" value={summary.skipped} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title="Avg model latency (this page)"
              value={summary.avgLatency ?? '—'}
              suffix={summary.avgLatency !== undefined ? 'ms' : undefined}
            />
          </Card>
        </Col>
      </Row>

      <Table<AiVerification>
        rowKey="id"
        columns={columns}
        dataSource={data?.items}
        loading={isFetching}
        scroll={{ x: 1100 }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: data?.total ?? 0,
          onChange: setPage,
          showSizeChanger: false,
          showTotal: (total) => `${total} verifications total`,
        }}
      />
    </div>
  );
}
