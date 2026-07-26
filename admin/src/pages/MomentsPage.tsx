import { DeleteOutlined, PlayCircleOutlined, TeamOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Col,
  Empty,
  Pagination,
  Popconfirm,
  Row,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import { deleteMoment, listMoments } from '../api/admin.api';

const PAGE_SIZE = 12;

/** URL video Cloudinary (.mp4...) khong render duoc bang <img>. */
function isVideo(moment: { contentType: string; mediaUrl: string }): boolean {
  return moment.contentType === 'VIDEO' || /\.(mp4|webm|mov)(\?|$)/i.test(moment.mediaUrl);
}

/** Trang kiểm duyệt bài đăng: lưới bài mới nhất của mọi user + xóa bài vi phạm. */
export function MomentsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);

  const { data, isFetching, error } = useQuery({
    queryKey: ['admin-moments', page],
    queryFn: () => listMoments({ page, limit: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const deleteMutation = useMutation({
    mutationFn: (momentId: string) => deleteMoment(momentId),
    onSuccess: () => {
      message.success('Đã xóa bài đăng vi phạm.');
      void queryClient.invalidateQueries({ queryKey: ['admin-moments'] });
      void queryClient.invalidateQueries({ queryKey: ['admin-stats'] });
    },
    onError: (err: Error) => message.error(err.message),
  });

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Kiểm duyệt bài đăng
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 720 }}>
        Toàn bộ bài đăng mới nhất của mọi người dùng. Xóa bài vi phạm sẽ xóa cả lượt xem/reaction
        và được ghi vào Nhật ký admin.
      </Typography.Paragraph>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được danh sách bài đăng."
          description={(error as Error).message}
        />
      )}

      {isFetching && !data ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : !data || data.items.length === 0 ? (
        !error && <Empty description="Chưa có bài đăng nào." />
      ) : (
        <>
          <Row gutter={[16, 16]}>
            {data.items.map((moment) => (
              <Col key={moment.momentId} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  cover={
                    isVideo(moment) ? (
                      <a href={moment.mediaUrl} target="_blank" rel="noreferrer">
                        <div
                          style={{
                            height: 200,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: '#141414',
                            color: '#fff',
                            fontSize: 42,
                          }}
                        >
                          <PlayCircleOutlined />
                        </div>
                      </a>
                    ) : (
                      <a href={moment.mediaUrl} target="_blank" rel="noreferrer">
                        <img
                          src={moment.mediaUrl}
                          alt={moment.caption ?? 'moment'}
                          style={{ height: 200, width: '100%', objectFit: 'cover' }}
                        />
                      </a>
                    )
                  }
                  actions={[
                    <Popconfirm
                      key="delete"
                      title="Xóa bài đăng này?"
                      description="Bài + lượt xem/reaction sẽ bị xóa vĩnh viễn (có ghi nhật ký)."
                      okText="Xóa"
                      cancelText="Hủy"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => deleteMutation.mutate(moment.momentId)}
                    >
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        loading={
                          deleteMutation.isPending && deleteMutation.variables === moment.momentId
                        }
                      >
                        Xóa bài
                      </Button>
                    </Popconfirm>,
                  ]}
                >
                  <Card.Meta
                    title={
                      <span>
                        {moment.authorName}
                        {moment.coopUserId && (
                          <Tag color="purple" style={{ marginLeft: 8 }}>
                            <TeamOutlined /> Chụp chung
                          </Tag>
                        )}
                      </span>
                    }
                    description={
                      <>
                        {moment.caption && (
                          <Typography.Paragraph
                            ellipsis={{ rows: 1 }}
                            style={{ marginBottom: 4 }}
                          >
                            “{moment.caption}”
                          </Typography.Paragraph>
                        )}
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {new Date(moment.postTime).toLocaleString('vi-VN')}
                        </Typography.Text>
                      </>
                    }
                  />
                </Card>
              </Col>
            ))}
          </Row>
          <Pagination
            style={{ marginTop: 16, textAlign: 'center' }}
            current={page}
            pageSize={PAGE_SIZE}
            total={data.total}
            onChange={setPage}
            showSizeChanger={false}
            showTotal={(total) => `Tổng ${total} bài đăng`}
          />
        </>
      )}
    </div>
  );
}
