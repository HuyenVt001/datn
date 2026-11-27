import {
  DeleteOutlined,
  EditOutlined,
  GiftOutlined,
  PictureOutlined,
  PlusOutlined,
  TeamOutlined,
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
import { ImageUploadField } from '../components/ImageUploadField';
import { withSelectedUser } from '../utils/selectedOption';
import type { Frame, UnlockType } from '../types';

interface FrameForm {
  frameName: string;
  unlockType: UnlockType;
  unlockValue?: number;
}

const MILESTONE_OPTIONS = [3, 7, 14, 30].map((days) => ({
  value: days,
  label: `${days}-day streak milestone`,
}));

/** Nhãn + màu + mô tả từng điều kiện mở khóa (khớp UNLOCK_TYPES server). */
const UNLOCK_META: Record<
  UnlockType,
  { label: string; color: string; tag: (n?: number | null) => string; hint: string }
> = {
  SHOP: {
    label: 'Sold in Shop',
    color: 'gold',
    tag: () => '🛍️ Shop',
    hint: 'AUTOMATICALLY listed in the Shop (default price 320 Astrite — edit the price on the Items page). Users unlock it by buying with Astrite. Switching to another condition removes it from the Shop.',
  },
  STREAK_MILESTONE: {
    label: 'Personal streak milestone',
    color: 'volcano',
    tag: (n) => `🔥 ${n}-day streak`,
    hint: 'Unlocks when the user reaches a 3/7/14/30-day personal streak.',
  },
  POST_COUNT: {
    label: 'Post count milestone',
    color: 'geekblue',
    tag: (n) => `📸 ${n} posts`,
    hint: 'Unlocks when the user\'s total post count reaches N.',
  },
  FRIEND_COUNT: {
    label: 'Friend count milestone',
    color: 'green',
    tag: (n) => `👥 ${n} friends`,
    hint: 'Unlocks (for both sides) when the user\'s friend count reaches N (max 20).',
  },
  COOP_FIRST: {
    label: 'First co-op capture',
    color: 'purple',
    tag: () => '🤝 First co-op capture',
    hint: 'Unlocks for BOTH people when they complete their first co-op capture.',
  },
  DEFAULT: {
    label: 'Unlocked for everyone',
    color: 'cyan',
    tag: () => '✨ Unlocked',
    hint: 'Every user has it from the start, no condition needed.',
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
      hint: 'Unknown condition type — check this frame\'s data in Firestore.',
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
  // Server de imageUrl optional -> phai chan o day, khong thi khung SHOP khong anh len ban ngay
  const [imageMissing, setImageMissing] = useState(false);
  const [form] = Form.useForm<FrameForm>();
  const unlockType = Form.useWatch('unlockType', form) ?? 'SHOP';

  const openCreate = () => {
    setEditing(null);
    setImageUrl(undefined);
    setImageMissing(false);
    form.resetFields();
    form.setFieldsValue({ unlockType: 'SHOP' });
    setModalOpen(true);
  };

  const openEdit = (frame: Frame) => {
    setEditing(frame);
    setImageUrl(frame.imageUrl);
    setImageMissing(false);
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
      message.success(editing ? 'Frame updated.' : 'Frame added.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (frameId: string) => deleteFrame(frameId),
    onSuccess: (_result, frameId) => {
      message.success('Frame deleted.');
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
  // Nhan cua user da chon — giu lai khi ket qua search moi khong con chua user do
  const [grantLabel, setGrantLabel] = useState<string | undefined>(undefined);
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
      message.success('Frame granted to user.');
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
          Manage frames
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add frame
        </Button>
      </div>

      {framesError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load frames."
          description={(framesError as Error).message}
        />
      )}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : !frames || frames.length === 0 ? (
        !framesError && <Empty description="No frames yet — click 'Add frame' to create one." />
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
                  <EditOutlined key="edit" title="Edit" onClick={() => openEdit(frame)} />,
                  <GiftOutlined key="grant" title="Grant to user" onClick={() => openGrant(frame)} />,
                  <TeamOutlined
                    key="owners"
                    title="Who owns this?"
                    onClick={() => setOwnersTarget(frame)}
                  />,
                  <Popconfirm
                    key="delete"
                    title="Delete this frame?"
                    description="The frame will disappear from the app catalog."
                    okText="Delete"
                    cancelText="Cancel"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => deleteMutation.mutate(frame.frameId)}
                  >
                    <DeleteOutlined title="Delete" />
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
        title={editing ? 'Edit frame' : 'Add frame'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editing ? 'Save' : 'Add'}
        cancelText="Cancel"
        confirmLoading={saveMutation.isPending}
      >
        <Form<FrameForm>
          form={form}
          layout="vertical"
          onFinish={(values) => {
            if (!imageUrl) {
              setImageMissing(true);
              return;
            }
            saveMutation.mutate(values);
          }}
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
            label="Frame name"
            rules={[{ required: true, message: 'Enter a frame name.' }]}
          >
            <Input placeholder="e.g. 7-day streak fire frame" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="unlockType"
            label="Unlock condition"
            rules={[{ required: true, message: 'Choose an unlock condition.' }]}
            extra={metaOf(unlockType).hint}
          >
            <Select options={UNLOCK_OPTIONS} />
          </Form.Item>
          {unlockType === 'STREAK_MILESTONE' && (
            <Form.Item
              name="unlockValue"
              label="Streak milestone"
              rules={[{ required: true, message: 'Choose a streak milestone.' }]}
            >
              <Select placeholder="Choose 3 / 7 / 14 / 30 days" options={MILESTONE_OPTIONS} />
            </Form.Item>
          )}
          {unlockType === 'POST_COUNT' && (
            <Form.Item
              name="unlockValue"
              label="Required post count (N)"
              rules={[
                { required: true, message: 'Enter the post count N.' },
                { type: 'number', min: 1, max: 100000, message: 'N must be between 1 and 100000.' },
              ]}
            >
              <InputNumber min={1} max={100000} style={{ width: '100%' }} placeholder="e.g. 10" />
            </Form.Item>
          )}
          {unlockType === 'FRIEND_COUNT' && (
            <Form.Item
              name="unlockValue"
              label="Required friend count (N, max 20)"
              rules={[
                { required: true, message: 'Enter the friend count N.' },
                { type: 'number', min: 1, max: 20, message: 'N must be between 1 and 20.' },
              ]}
            >
              <InputNumber min={1} max={20} style={{ width: '100%' }} placeholder="e.g. 5" />
            </Form.Item>
          )}
          <Form.Item
            label="Frame image (transparent PNG)"
            required
            validateStatus={imageMissing ? 'error' : undefined}
            help={imageMissing ? 'Please upload a frame image.' : undefined}
          >
            <ImageUploadField
              value={imageUrl}
              onChange={(url) => {
                setImageUrl(url);
                if (url) {
                  setImageMissing(false);
                }
              }}
              buttonText="Upload frame image"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal cấp khung cho user */}
      <Modal
        title={grantTarget ? `Grant frame "${grantTarget.frameName}" to a user` : ''}
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
        okText="Grant frame"
        cancelText="Cancel"
        okButtonProps={{ disabled: !grantUid }}
        confirmLoading={grantMutation.isPending}
      >
        <Select
          showSearch
          style={{ width: '100%' }}
          placeholder="Search users by email/name..."
          value={grantUid}
          onChange={(value: string, option) => {
            setGrantUid(value);
            setGrantLabel((option as { label?: string } | undefined)?.label);
          }}
          onSearch={onUserSearch}
          filterOption={false}
          loading={searchingUsers}
          options={withSelectedUser(
            (userOptions?.items ?? []).map((u) => ({
              value: u.uid,
              label: `${u.fullName || '(no name)'} — ${u.email ?? u.uid}`,
            })),
            grantUid,
            grantLabel,
          )}
          notFoundContent={searchingUsers ? <Spin size="small" /> : 'No users found'}
        />
      </Modal>

      {/* Drawer danh sách user sở hữu khung */}
      <Drawer
        title={
          ownersTarget
            ? `Owners of "${ownersTarget.frameName}"${
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
            message="This frame is unlocked for EVERY user."
            description="The list below only includes people unlocked/granted manually (before the frame became unlocked for everyone)."
          />
        )}
        {ownersError && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="Couldn't load owners."
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
            locale={{ emptyText: 'No users own this frame yet.' }}
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
                  title={owner.fullName || '(no name)'}
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
