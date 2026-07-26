import {
  DeleteOutlined,
  EditOutlined,
  GiftOutlined,
  PictureOutlined,
  PlusOutlined,
  TeamOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Avatar,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Row,
  Select,
  Spin,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { useRef, useState } from 'react';
import { listUsers } from '../api/admin.api';
import {
  createFrame,
  deleteFrame,
  grantFrame,
  listFrameOwners,
  listFrames,
  updateFrame,
} from '../api/frames.api';
import { uploadImage } from '../api/upload.api';
import type { Frame, UnlockType } from '../types';

interface FrameForm {
  frameName: string;
  unlockType: UnlockType;
  unlockValue?: number;
}

const MILESTONE_OPTIONS = [3, 7, 14, 30].map((days) => ({
  value: days,
  label: `Mốc streak ${days} ngày`,
}));

/** Nhãn + màu + mô tả từng điều kiện mở khóa (khớp UNLOCK_TYPES server). */
const UNLOCK_META: Record<
  UnlockType,
  { label: string; color: string; tag: (n?: number | null) => string; hint: string }
> = {
  QUEST_RANDOM: {
    label: 'Thưởng quest ngẫu nhiên',
    color: 'gold',
    tag: () => '🎁 Thưởng quest',
    hint: 'Vào pool thưởng ngẫu nhiên khi user hoàn thành 2/2 quest/ngày.',
  },
  STREAK_MILESTONE: {
    label: 'Mốc streak cá nhân',
    color: 'volcano',
    tag: (n) => `🔥 Mốc streak ${n} ngày`,
    hint: 'Tự mở khi user đạt mốc streak cá nhân 3/7/14/30 ngày.',
  },
  POST_COUNT: {
    label: 'Mốc số bài đăng',
    color: 'geekblue',
    tag: (n) => `📸 Đăng đủ ${n} bài`,
    hint: 'Tự mở khi tổng số bài đăng của user đạt ngưỡng N.',
  },
  FRIEND_COUNT: {
    label: 'Mốc số bạn bè',
    color: 'green',
    tag: (n) => `👥 Đủ ${n} bạn bè`,
    hint: 'Tự mở (cho cả 2 phía) khi số bạn bè của user đạt ngưỡng N (tối đa 20).',
  },
  COOP_FIRST: {
    label: 'Chụp chung lần đầu',
    color: 'purple',
    tag: () => '🤝 Chụp chung lần đầu',
    hint: 'Tự mở cho CẢ 2 người khi hoàn thành chụp chung (co-op capture) đầu tiên.',
  },
  DEFAULT: {
    label: 'Mở sẵn cho mọi người',
    color: 'cyan',
    tag: () => '✨ Mở sẵn',
    hint: 'Mọi user đều có ngay từ đầu, không cần điều kiện.',
  },
};

const UNLOCK_OPTIONS = (Object.keys(UNLOCK_META) as UnlockType[]).map((type) => ({
  value: type,
  label: UNLOCK_META[type].label,
}));

/**
 * Meta an toan: doc Firestore bi sua tay ra gia tri ngoai 6 loai se khong lam
 * TypeError -> trang trang; hien tag thô de admin biet du lieu la.
 */
function metaOf(type: UnlockType) {
  return (
    UNLOCK_META[type] ?? {
      label: String(type),
      color: 'default',
      tag: () => String(type),
      hint: 'Loai dieu kien khong xac dinh — kiem tra du lieu khung nay tren Firestore.',
    }
  );
}

/** Loai can nhap nguong N. */
const NEEDS_VALUE: UnlockType[] = ['STREAK_MILESTONE', 'POST_COUNT', 'FRIEND_COUNT'];

/** Trang quản lý khung ảnh: CRUD + điều kiện mở khóa + cấp khung + xem user sở hữu. */
export function FramesPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const {
    data: frames,
    isLoading,
    error: framesError,
  } = useQuery({ queryKey: ['frames'], queryFn: listFrames });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['frames'] });

  // ==== Modal thêm/sửa khung ====
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Frame | null>(null); // null = đang thêm mới
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const [form] = Form.useForm<FrameForm>();
  const unlockType = Form.useWatch('unlockType', form) ?? 'QUEST_RANDOM';

  const openCreate = () => {
    setEditing(null);
    setImageUrl(undefined);
    form.resetFields();
    form.setFieldsValue({ unlockType: 'QUEST_RANDOM' });
    setModalOpen(true);
  };

  const openEdit = (frame: Frame) => {
    setEditing(frame);
    setImageUrl(frame.imageUrl);
    form.setFieldsValue({
      frameName: frame.frameName,
      unlockType: frame.unlockType,
      unlockValue: frame.unlockValue ?? undefined,
    });
    setModalOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: async (values: FrameForm) => {
      const body = {
        frameName: values.frameName,
        imageUrl,
        unlockType: values.unlockType,
        unlockValue: NEEDS_VALUE.includes(values.unlockType) ? values.unlockValue : undefined,
      };
      return editing ? updateFrame(editing.frameId, body) : createFrame(body);
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
    onSuccess: (_result, frameId) => {
      message.success('Đã xóa khung ảnh.');
      void invalidate();
      // Drawer owners cua khung vua xoa (neu dang mo) phai dong + bo cache
      void queryClient.invalidateQueries({ queryKey: ['frame-owners'] });
      setOwnersTarget((current) => (current?.frameId === frameId ? null : current));
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Modal cấp khung cho user ====
  const [grantTarget, setGrantTarget] = useState<Frame | null>(null);
  const [grantUid, setGrantUid] = useState<string | undefined>(undefined);
  const [userSearch, setUserSearch] = useState('');
  // Debounce go phim o search user — moi keystroke la 1 request /admin/users neu khong debounce
  const searchTimer = useRef<number | undefined>(undefined);
  const onUserSearch = (value: string) => {
    window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => setUserSearch(value), 300);
  };
  const openGrant = (frame: Frame) => {
    setUserSearch(''); // khong de dinh ket qua loc cua lan cap truoc
    setGrantUid(undefined);
    setGrantTarget(frame);
  };

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
      void queryClient.invalidateQueries({ queryKey: ['frame-owners'] });
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Drawer danh sách user sở hữu khung ====
  const [ownersTarget, setOwnersTarget] = useState<Frame | null>(null);

  const {
    data: ownersData,
    isFetching: loadingOwners,
    error: ownersError,
  } = useQuery({
    queryKey: ['frame-owners', ownersTarget?.frameId],
    queryFn: () => listFrameOwners(ownersTarget!.frameId),
    enabled: ownersTarget !== null,
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

      {framesError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được danh sách khung ảnh."
          description={(framesError as Error).message}
        />
      )}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : !frames || frames.length === 0 ? (
        !framesError && <Empty description="Chưa có khung ảnh nào — bấm 'Thêm khung' để tạo." />
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
                  <GiftOutlined key="grant" title="Cấp cho user" onClick={() => openGrant(frame)} />,
                  <TeamOutlined
                    key="owners"
                    title="Ai đang sở hữu?"
                    onClick={() => setOwnersTarget(frame)}
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
                    <Tag color={metaOf(frame.unlockType).color}>
                      {metaOf(frame.unlockType).tag(frame.unlockValue)}
                    </Tag>
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
          // Doi loai dieu kien -> XOA nguong cu (fix 2026-07-26: khong reset thi
          // nguong cua loai cu dinh sang loai moi — vd streak 7 thanh "7 bai dang"
          // luu lang le, hoac 50 bai thanh FRIEND_COUNT 50 bi server 400)
          onValuesChange={(changed) => {
            if ('unlockType' in changed) {
              form.setFieldsValue({ unlockValue: undefined });
            }
          }}
        >
          <Form.Item
            name="frameName"
            label="Tên khung"
            rules={[{ required: true, message: 'Nhập tên khung.' }]}
          >
            <Input placeholder="Ví dụ: Khung lửa streak 7 ngày" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="unlockType"
            label="Điều kiện mở khóa"
            rules={[{ required: true, message: 'Chọn điều kiện mở khóa.' }]}
            extra={metaOf(unlockType).hint}
          >
            <Select options={UNLOCK_OPTIONS} />
          </Form.Item>
          {unlockType === 'STREAK_MILESTONE' && (
            <Form.Item
              name="unlockValue"
              label="Mốc streak"
              rules={[{ required: true, message: 'Chọn mốc streak.' }]}
            >
              <Select placeholder="Chọn mốc 3 / 7 / 14 / 30 ngày" options={MILESTONE_OPTIONS} />
            </Form.Item>
          )}
          {unlockType === 'POST_COUNT' && (
            <Form.Item
              name="unlockValue"
              label="Số bài đăng cần đạt (N)"
              rules={[
                { required: true, message: 'Nhập số bài đăng N.' },
                { type: 'number', min: 1, max: 100000, message: 'N phải từ 1 đến 100000.' },
              ]}
            >
              <InputNumber min={1} max={100000} style={{ width: '100%' }} placeholder="Ví dụ: 10" />
            </Form.Item>
          )}
          {unlockType === 'FRIEND_COUNT' && (
            <Form.Item
              name="unlockValue"
              label="Số bạn bè cần đạt (N, tối đa 20)"
              rules={[
                { required: true, message: 'Nhập số bạn bè N.' },
                { type: 'number', min: 1, max: 20, message: 'N phải từ 1 đến 20.' },
              ]}
            >
              <InputNumber min={1} max={20} style={{ width: '100%' }} placeholder="Ví dụ: 5" />
            </Form.Item>
          )}
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
          onSearch={onUserSearch}
          filterOption={false}
          loading={searchingUsers}
          options={(userOptions?.items ?? []).map((u) => ({
            value: u.uid,
            label: `${u.fullName || '(chưa đặt tên)'} — ${u.email ?? u.uid}`,
          }))}
          notFoundContent={searchingUsers ? <Spin size="small" /> : 'Không tìm thấy người dùng'}
        />
      </Modal>

      {/* Drawer danh sách user sở hữu khung */}
      <Drawer
        title={
          ownersTarget
            ? `Người sở hữu "${ownersTarget.frameName}"${
                ownersData ? ` (${ownersData.owners.length})` : ''
              }`
            : ''
        }
        open={ownersTarget !== null}
        onClose={() => setOwnersTarget(null)}
        width={420}
      >
        {ownersTarget?.unlockType === 'DEFAULT' && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Khung mở sẵn cho MỌI user."
            description="Danh sách dưới chỉ gồm những người được mở/cấp thủ công (trước khi khung chuyển sang mở sẵn)."
          />
        )}
        {ownersError && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="Không tải được danh sách sở hữu."
            description={(ownersError as Error).message}
          />
        )}
        {loadingOwners ? (
          <div style={{ textAlign: 'center', padding: 32 }}>
            <Spin />
          </div>
        ) : (
          <List
            dataSource={ownersData?.owners ?? []}
            locale={{ emptyText: 'Chưa có người dùng nào sở hữu khung này.' }}
            renderItem={(owner) => (
              <List.Item>
                <List.Item.Meta
                  avatar={
                    owner.avatar ? (
                      <Avatar src={owner.avatar} />
                    ) : (
                      <Avatar icon={<UserOutlined />} />
                    )
                  }
                  title={owner.fullName || '(chưa đặt tên)'}
                  description={owner.email ?? owner.uid}
                />
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </div>
  );
}
