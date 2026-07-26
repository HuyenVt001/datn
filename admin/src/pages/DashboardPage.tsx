import {
  CameraOutlined,
  FireOutlined,
  HeartOutlined,
  MessageOutlined,
  TeamOutlined,
  TrophyOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Row, Statistic, Typography } from 'antd';
import { useState, type ReactNode } from 'react';
import { getDailyStats, getStats } from '../api/admin.api';

interface StatCard {
  title: string;
  value?: number;
  icon: ReactNode;
}

/**
 * Mau bieu do da validate bang dataviz validator (light + dark deu PASS):
 * nau cam #A85A1E (bai dang — gan brand #8C6239), xanh duong #0C7BB3 (user moi).
 */
const CHART_COLORS = { moments: '#A85A1E', newUsers: '#0C7BB3' };

interface ChartPoint {
  date: string;
  value: number;
}

/** "2026-07-26" -> "26/07". */
function shortDate(date: string): string {
  const [, m, d] = date.split('-');
  return `${d}/${m}`;
}

/**
 * Bieu do cot 1 series (SVG thuan, khong them thu vien):
 * cot mong bo goc 4px o DINH (neo baseline), grid ngang mo, tooltip hover,
 * nhan truc tiep CHI o cot cao nhat (selective label).
 */
function DailyBarChart({
  data,
  color,
  unit,
}: {
  data: ChartPoint[];
  color: string;
  unit: string;
}) {
  const [hover, setHover] = useState<number | null>(null);

  const W = 460;
  const H = 200;
  const PAD = { top: 18, right: 8, bottom: 26, left: 34 };
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;

  const rawMax = Math.max(...data.map((d) => d.value), 1);
  // Tran truc "dep": boi so cua 1/2/5/10... >= max
  const niceMax = (() => {
    const pow = 10 ** Math.floor(Math.log10(rawMax));
    for (const m of [1, 2, 5, 10]) {
      if (m * pow >= rawMax) {
        return m * pow;
      }
    }
    return 10 * pow;
  })();

  const band = plotW / data.length;
  const barW = Math.min(30, band * 0.55);
  const y = (v: number) => PAD.top + plotH - (v / niceMax) * plotH;
  const maxIdx = data.reduce((best, d, i) => (d.value > data[best].value ? i : best), 0);
  // Chi ve gridline co nhan NGUYEN (du lieu la so dem — nhan 2.5 gay kho hieu)
  const gridValues = [niceMax / 2, niceMax].filter((v) => Number.isInteger(v));

  return (
    <div style={{ position: 'relative' }}>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        style={{ width: '100%', height: 'auto', display: 'block' }}
        role="img"
        aria-label={data.map((d) => `${shortDate(d.date)}: ${d.value} ${unit}`).join(', ')}
      >
        {/* Grid ngang mo + nhan gia tri truc y */}
        {gridValues.map((v) => (
          <g key={v}>
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={y(v)}
              y2={y(v)}
              stroke="rgba(0,0,0,0.08)"
              strokeWidth={1}
            />
            <text x={PAD.left - 6} y={y(v) + 4} textAnchor="end" fontSize={11} fill="#8c8c8c">
              {v}
            </text>
          </g>
        ))}
        {/* Truc hoanh (baseline) */}
        <line
          x1={PAD.left}
          x2={W - PAD.right}
          y1={y(0)}
          y2={y(0)}
          stroke="rgba(0,0,0,0.2)"
          strokeWidth={1}
        />

        {data.map((d, i) => {
          const cx = PAD.left + band * i + band / 2;
          const barH = Math.max((d.value / niceMax) * plotH, 0);
          const top = y(d.value);
          const r = Math.min(4, barH); // bo goc DINH cot, khong bo o baseline
          const path =
            barH === 0
              ? ''
              : `M ${cx - barW / 2} ${y(0)}
                 L ${cx - barW / 2} ${top + r}
                 Q ${cx - barW / 2} ${top} ${cx - barW / 2 + r} ${top}
                 L ${cx + barW / 2 - r} ${top}
                 Q ${cx + barW / 2} ${top} ${cx + barW / 2} ${top + r}
                 L ${cx + barW / 2} ${y(0)} Z`;
          return (
            <g key={d.date}>
              {barH > 0 && <path d={path} fill={color} opacity={hover === i ? 1 : 0.85} />}
              {/* Nhan truc tiep: CHI cot cao nhat (khi co du lieu) */}
              {i === maxIdx && d.value > 0 && (
                <text
                  x={cx}
                  y={top - 5}
                  textAnchor="middle"
                  fontSize={11}
                  fontWeight={600}
                  fill="rgba(0,0,0,0.65)"
                >
                  {d.value}
                </text>
              )}
              <text x={cx} y={H - 8} textAnchor="middle" fontSize={11} fill="#8c8c8c">
                {shortDate(d.date)}
              </text>
              {/* Vung hover to hon cot (hit target) */}
              <rect
                x={PAD.left + band * i}
                y={PAD.top}
                width={band}
                height={plotH}
                fill="transparent"
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
              />
            </g>
          );
        })}
      </svg>
      {hover !== null && (
        <div
          style={{
            position: 'absolute',
            left: `${((PAD.left + band * hover + band / 2) / W) * 100}%`,
            top: 0,
            transform: 'translate(-50%, -4px)',
            background: 'rgba(0,0,0,0.78)',
            color: '#fff',
            borderRadius: 6,
            padding: '4px 10px',
            fontSize: 12,
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
          }}
        >
          {shortDate(data[hover].date)} · {data[hover].value} {unit}
        </div>
      )}
    </div>
  );
}

/** Trang tổng quan: thẻ thống kê + biểu đồ 7 ngày (GET /admin/stats + /admin/stats/daily). */
export function DashboardPage() {
  const { data, isLoading, error: statsError } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: getStats,
  });
  const {
    data: daily,
    isLoading: loadingDaily,
    error: dailyError,
  } = useQuery({
    queryKey: ['admin-stats-daily'],
    queryFn: () => getDailyStats(7),
  });
  const loadError = (statsError ?? dailyError) as Error | null;

  const cards: StatCard[] = [
    { title: 'Người dùng', value: data?.users, icon: <TeamOutlined /> },
    { title: 'Bài đăng', value: data?.moments, icon: <CameraOutlined /> },
    { title: 'Bài đăng hôm nay', value: data?.momentsToday, icon: <FireOutlined /> },
    { title: 'Tin nhắn', value: data?.messages, icon: <MessageOutlined /> },
    { title: 'Cặp bạn bè', value: data?.friendships, icon: <HeartOutlined /> },
    { title: 'Nhóm chat', value: data?.chatGroups, icon: <UsergroupAddOutlined /> },
    {
      title: 'Lượt hoàn thành quest hôm nay',
      value: data?.questCompletionsToday,
      icon: <TrophyOutlined />,
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Tổng quan
      </Typography.Title>
      {loadError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được số liệu thống kê."
          description={loadError.message}
        />
      )}
      <Row gutter={[16, 16]}>
        {cards.map((card) => (
          <Col key={card.title} xs={24} sm={12} lg={8}>
            <Card loading={isLoading}>
              <Statistic title={card.title} value={card.value ?? 0} prefix={card.icon} />
            </Card>
          </Col>
        ))}
      </Row>
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="Bài đăng 7 ngày qua" loading={loadingDaily}>
            {daily && (
              <DailyBarChart
                data={daily.map((d) => ({ date: d.date, value: d.moments }))}
                color={CHART_COLORS.moments}
                unit="bài đăng"
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Người dùng mới 7 ngày qua" loading={loadingDaily}>
            {daily && (
              <DailyBarChart
                data={daily.map((d) => ({ date: d.date, value: d.newUsers }))}
                color={CHART_COLORS.newUsers}
                unit="người dùng mới"
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
