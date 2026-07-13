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
import { Card, Col, Row, Statistic, Typography } from 'antd';
import type { ReactNode } from 'react';
import { getStats } from '../api/admin.api';

interface StatCard {
  title: string;
  value?: number;
  icon: ReactNode;
}

/** Trang tổng quan: các thẻ thống kê từ GET /admin/stats. */
export function DashboardPage() {
  const { data, isLoading } = useQuery({ queryKey: ['admin-stats'], queryFn: getStats });

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
      <Row gutter={[16, 16]}>
        {cards.map((card) => (
          <Col key={card.title} xs={24} sm={12} lg={8}>
            <Card loading={isLoading}>
              <Statistic title={card.title} value={card.value ?? 0} prefix={card.icon} />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
