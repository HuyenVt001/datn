import {
  DeleteOutlined,
  EditOutlined,
  GiftOutlined,
  PictureOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Spin,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { useState } from 'react';
import { listUsers } from '../api/admin.api';
import { createFrame, deleteFrame, grantFrame, listFrames, updateFrame } from '../api/frames.api';
import { uploadImage } from '../api/upload.api';
import type { Frame } from '../types';

interface FrameForm {
  frameName: string;
  /** Moc streak (3/7/14/30) — bo trong = khung thuong quest ngau nhien. */
  milestone?: number;
}

const MILESTONE_OPTIONS = [3, 7, 14, 30].map((days) => ({
  value: days,
  label: `Mốc streak ${days} ngày`,
}));

/** Trang quản lý khung ảnh: thêm / sửa / xóa khung + cấp khung cho user. */
export function FramesPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const { data: frames, isLoading } = useQuery({ queryKey: ['frames'], queryFn: listFrames });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['frames'] });

  // ==== Modal thêm/sửa khung ====
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Frame | null>(null); // null = đang thêm mới
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const [form] = Form.useForm<FrameForm>();

  const openCreate = () => {
    setEditing(null);
    setImageUrl(undefined);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (frame: Frame) => {
    setEditing(frame);
    setImageUrl(frame.imageUrl);
    form.setFieldsValue({ frameName: frame.frameName, milestone: frame.milestone });
    setModalOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: async (values: FrameForm) => {
      if (editing) {
        // milestone: Select allowClear trả undefined khi xóa — JSON bỏ field undefined
        // nên server sẽ GIỮ mốc cũ. Map undefined -> null để server hiểu là XÓA mốc.
        return updateFrame(editing.frameId, {
          frameName: values.frameName,
          imageUrl,
          milestone: values.milestone ?? null,
        });
      }
      return createFrame({ frameName: values.frameName, imageUrl, milestone: values.milestone });
    },
    onSuccess: () => {
      message.success(editing ? 'Đã cập nhật khung ảnh.' : 'Đã thêm khung ảnh.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (frameId: string) => deleteFrame(frameId),
    onSuccess: () => {
      message.success('Đã xóa khung ảnh.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Modal cấp khung cho user ====
  const [grantTarget, setGrantTarget] = useState<Frame | null>(null);
  const [grantUid, setGrantUid] = useState<string | undefined>(undefined);
  const [userSearch, setUserSearch] = useState('');

  const { data: userOptions, isFetching: searchingUsers } = useQuery({
    queryKey: ['admin-users-search', userSearch],
    queryFn: () => listUsers({ page: 1, limit: 20, search: userSearch || undefined }),
    enabled: grantTarget !== null,
  });

  const grantMutation = useMutation({
    mutationFn: ({ frameId, uid }: { frameId: string; uid: string }) => grantFrame(frameId, uid),
    onSuccess: () => {
      message.success('Đã cấp khung cho người dùng.');
      setGrantTarget(null);
      setGrantUid(undefined);
    },
    onError: (err: Error) => message.error(err.message),
  });

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          Quản lý khung ảnh
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Thêm khung
        </Button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : !frames || frames.length === 0 ? (
        <Empty description="Chưa có khung ảnh nào — bấm 'Thêm khung' để tạo." />
      ) : (
        <Row gutter={[16, 16]}>
          {frames.map((frame) => (
            <Col key={frame.frameId} xs={24} sm={12} md={8} lg={6}>
              <Card
                size="small"
                cover={
                  frame.imageUrl ? (
                    <div
                      style={{
                        height: 180,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        // nen caro de thay vung trong suot cua khung PNG
                        background:
                          'repeating-conic-gradient(#f0f0f0 0% 25%, #ffffff 0% 50%) 50% / 20px 20px',
                      }}
                    >
                      <img
                        src={frame.imageUrl}
                        alt={frame.frameName}
                        style={{ maxWidth: '100%', maxHeight: 180, objectFit: 'contain' }}
                      />
                    </div>
                  ) : (
                    <div
                      style={{
                        height: 180,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: '#fafafa',
                        color: '#bbb',
                        fontSize: 40,
                      }}
                    >
                      <PictureOutlined />
                    </div>
                  )
                }
                actions={[
                  <EditOutlined key="edit" title="Sửa" onClick={() => openEdit(frame)} />,
                  <GiftOutlined
                    key="grant"
                    title="Cấp cho user"
                    onClick={() => setGrantTarget(frame)}
                  />,
                  <Popconfirm
                    key="delete"
                    title="Xóa khung ảnh này?"
                    description="Khung sẽ biến mất khỏi catalog của app."
                    okText="Xóa"
                    cancelText="Hủy"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => deleteMutation.mutate(frame.frameId)}
                  >
                    <DeleteOutlined title="Xóa" />
                  </Popconfirm>,
                ]}
              >
                <Card.Meta
                  title={frame.frameName}
                  description={
                    frame.milestone ? (
                      <Tag color="volcano">🔥 Mốc streak {frame.milestone} ngày</Tag>
                    ) : (
                      <Tag color="gold">Thưởng quest</Tag>
                    )
                  }
                />
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* Modal thêm / sửa khung */}
      <Modal
        title={editing ? 'Sửa khung ảnh' : 'Thêm khung ảnh'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editing ? 'Lưu' : 'Thêm'}
        cancelText="Hủy"
        confirmLoading={saveMutation.isPending}
      >
        <Form<FrameForm>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="frameName"
            label="Tên khung"
            rules={[{ required: true, message: 'Nhập tên khung.' }]}
          >
            <Input placeholder="Ví dụ: Khung lửa streak 7 ngày" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="milestone"
            label="Mốc streak (tùy chọn)"
            tooltip="Gắn mốc = khung chỉ mở khi user đạt mốc streak đó. Bỏ trống = khung thưởng ngẫu nhiên khi hoàn thành 2/2 quest/ngày."
          >
            <Select allowClear placeholder="Khung thưởng quest (không gắn mốc)" options={MILESTONE_OPTIONS} />
          </Form.Item>
          <Form.Item label="Ảnh khung (PNG nền trong suốt)">
            <Upload
              accept="image/*"
              maxCount={1}
              showUploadList={false}
              customRequest={async ({ file, onSuccess, onError }) => {
                setUploading(true);
                try {
                  const result = await uploadImage(file as File);
                  setImageUrl(result.url);
                  onSuccess?.(result);
                } catch (err) {
                  message.error(err instanceof Error ? err.message : 'Upload thất bại.');
                  onError?.(err as Error);
                } finally {
                  setUploading(false);
                }
              }}
            >
              {imageUrl ? (
                <div style={{ cursor: 'pointer' }} title="Bấm để đổi ảnh khác">
                  <img
                    src={imageUrl}
                    alt="Ảnh khung"
                    style={{
                      maxWidth: '100%',
                      maxHeight: 200,
                      display: 'block',
                      background:
                        'repeating-conic-gradient(#f0f0f0 0% 25%, #ffffff 0% 50%) 50% / 20px 20px',
                    }}
                  />
                </div>
              ) : (
                <Button icon={<UploadOutlined />} loading={uploading}>
                  Tải ảnh khung lên
                </Button>
              )}
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal cấp khung cho user */}
      <Modal
        title={grantTarget ? `Cấp khung "${grantTarget.frameName}" cho người dùng` : ''}
        open={grantTarget !== null}
        onCancel={() => {
          setGrantTarget(null);
          setGrantUid(undefined);
        }}
        onOk={() => {
          if (grantTarget && grantUid) {
            grantMutation.mutate({ frameId: grantTarget.frameId, uid: grantUid });
          }
        }}
        okText="Cấp khung"
        cancelText="Hủy"
        okButtonProps={{ disabled: !grantUid }}
        confirmLoading={grantMutation.isPending}
      >
        <Select
          showSearch
          style={{ width: '100%' }}
          placeholder="Tìm người dùng theo email/tên..."
          value={grantUid}
          onChange={setGrantUid}
          onSearch={setUserSearch}
          filterOption={false}
          loading={searchingUsers}
          options={(userOptions?.items ?? []).map((u) => ({
            value: u.uid,
            label: `${u.fullName || '(chưa đặt tên)'} — ${u.email ?? u.uid}`,
          }))}
          notFoundContent={searchingUsers ? <Spin size="small" /> : 'Không tìm thấy người dùng'}
        />
      </Modal>
    </div>
  );
}
