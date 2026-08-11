import {
  DeleteOutlined,
  EditOutlined,
  GiftOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Avatar,
  Button,
  Drawer,
  Form,
  Image,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useRef, useState } from 'react';
import { listUsers } from '../api/admin.api';
import {
  deleteGachaItem,
  grantGachaItem,
  listGachaItemOwners,
  listGachaItems,
  updateGachaItem,
} from '../api/gacha.api';
import { uploadImage } from '../api/upload.api';
import type { GachaItem, ItemRarity, ItemType } from '../types';

/** Màu phẩm chất — khớp mục 0.2 của GACHA_PLAN (app dùng đúng bộ này). */
const RARITY_META: Record<ItemRarity, { color: string; label: string }> = {
  R: { color: '#4FC3F7', label: 'R — Hiếm' },
  SR: { color: '#B388FF', label: 'SR — Rất hiếm' },
  SSR: { color: '#FFA726', label: 'SSR — Cực hiếm' },
};

const TYPE_META: Record<ItemType, { label: string; color: string; hint: string }> = {
  FRAME: {
    label: 'Khung ảnh',
    color: 'gold',
    hint: 'Tự đồng bộ từ trang Khung ảnh: điều kiện mở khóa "Quay gacha" là vào kho.',
  },
  EFFECT: {
    label: 'Hiệu ứng chạm',
    color: 'purple',
    hint: 'Asset nằm trong app — chỉ sửa được thông tin, không thêm mới.',
  },
  SKIN: {
    label: 'Giao diện',
    color: 'orange',
    hint: 'Asset nằm trong app — chỉ sửa được thông tin, không thêm mới.',
  },
};

interface ItemForm {
  itemName: string;
  rarity: ItemRarity;
  isActive: boolean;
  sortOrder?: number;
}

/**
 * Trang quản lý kho vật phẩm gacha.
 *
 * KHÔNG có nút thêm mới (bỏ 2026-08-11): khung ảnh được server TỰ ĐỘNG thêm vào
 * kho khi tạo/sửa khung với điều kiện mở khóa "Quay gacha" (và tự rút khỏi kho
 * khi đổi sang điều kiện khác) — quản lý ở trang Khung ảnh. Skin và hiệu ứng
 * chạm có asset nằm trong APK, khớp qua `refId`. Ở đây chỉ sửa tên / phẩm chất
 * / ảnh đại diện / bật-tắt.
 */
export function GachaItemsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [typeFilter, setTypeFilter] = useState<ItemType | 'ALL'>('ALL');

  const { data: items, isLoading, error } = useQuery({
    queryKey: ['gacha-items'],
    queryFn: listGachaItems,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['gacha-items'] });

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<GachaItem | null>(null); // vật phẩm đang sửa (giữ để modal đóng mượt)
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const [form] = Form.useForm<ItemForm>();

  const shown = useMemo(
    () => (items ?? []).filter((i) => typeFilter === 'ALL' || i.itemType === typeFilter),
    [items, typeFilter],
  );

  const saveMutation = useMutation({
    mutationFn: (values: ItemForm) =>
      updateGachaItem(editing!.itemId, {
        itemName: values.itemName,
        rarity: values.rarity,
        imageUrl,
        isActive: values.isActive,
        sortOrder: values.sortOrder,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật vật phẩm.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ item, isActive }: { item: GachaItem; isActive: boolean }) =>
      updateGachaItem(item.itemId, { isActive }),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Đã bật — vật phẩm vào kho quay.' : 'Đã tắt — không quay ra nữa.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (itemId: string) => deleteGachaItem(itemId),
    onSuccess: () => {
      message.success('Đã xoá vật phẩm khỏi kho quay.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Kho thuong: tang vat pham cho user ====
  const [grantTarget, setGrantTarget] = useState<GachaItem | null>(null);
  const [grantUid, setGrantUid] = useState<string | undefined>(undefined);
  const [userSearch, setUserSearch] = useState('');
  // Debounce go phim o search user — moi keystroke la 1 request /admin/users neu khong debounce
  const searchTimer = useRef<number | undefined>(undefined);
  const onUserSearch = (value: string) => {
    window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => setUserSearch(value), 300);
  };
  const openGrant = (item: GachaItem) => {
    setUserSearch(''); // khong de dinh ket qua loc cua lan tang truoc
    setGrantUid(undefined);
    setGrantTarget(item);
  };

  const { data: userOptions, isFetching: searchingUsers } = useQuery({
    queryKey: ['admin-users-search', userSearch],
    queryFn: () => listUsers({ page: 1, limit: 20, search: userSearch || undefined }),
    enabled: grantTarget !== null,
  });

  const grantMutation = useMutation({
    mutationFn: ({ itemId, uid }: { itemId: string; uid: string }) => grantGachaItem(itemId, uid),
    onSuccess: () => {
      message.success('Đã tặng vật phẩm cho người dùng.');
      setGrantTarget(null);
      setGrantUid(undefined);
      void queryClient.invalidateQueries({ queryKey: ['gacha-item-owners'] });
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Drawer danh sách user sở hữu vật phẩm ====
  const [ownersTarget, setOwnersTarget] = useState<GachaItem | null>(null);

  const {
    data: ownersData,
    isFetching: loadingOwners,
    error: ownersError,
  } = useQuery({
    queryKey: ['gacha-item-owners', ownersTarget?.itemId],
    queryFn: () => listGachaItemOwners(ownersTarget!.itemId),
    enabled: ownersTarget !== null,
  });

  const openEdit = (item: GachaItem) => {
    setEditing(item);
    setImageUrl(item.imageUrl);
    form.setFieldsValue({
      itemName: item.itemName,
      rarity: item.rarity,
      isActive: item.isActive,
      sortOrder: item.sortOrder,
    });
    setModalOpen(true);
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      setImageUrl((await uploadImage(file)).url);
      message.success('Đã tải ảnh lên.');
    } catch (err) {
      message.error((err as Error).message);
    } finally {
      setUploading(false);
    }
    return false; // chặn upload mặc định của AntD — ta tự gọi API
  };

  const columns: ColumnsType<GachaItem> = [
    {
      title: 'Ảnh',
      dataIndex: 'imageUrl',
      width: 80,
      render: (url?: string) =>
        url ? (
          <Image src={url} width={48} height={48} style={{ objectFit: 'contain' }} />
        ) : (
          <Tooltip title="Chưa có ảnh — thẻ kết quả quay sẽ trống. Bấm Sửa để tải ảnh lên.">
            <Tag color="red">Thiếu ảnh</Tag>
          </Tooltip>
        ),
    },
    { title: 'Tên vật phẩm', dataIndex: 'itemName' },
    {
      title: 'Loại',
      dataIndex: 'itemType',
      width: 140,
      render: (type: ItemType) => <Tag color={TYPE_META[type].color}>{TYPE_META[type].label}</Tag>,
    },
    {
      title: 'Phẩm chất',
      dataIndex: 'rarity',
      width: 130,
      render: (rarity: ItemRarity) => (
        <Tag color={RARITY_META[rarity].color} style={{ color: '#000', fontWeight: 600 }}>
          {rarity}
        </Tag>
      ),
    },
    {
      title: 'refId',
      dataIndex: 'refId',
      width: 130,
      render: (refId: string) => <Typography.Text code>{refId}</Typography.Text>,
    },
    { title: 'Thứ tự', dataIndex: 'sortOrder', width: 90 },
    {
      title: 'Trong kho quay',
      dataIndex: 'isActive',
      width: 130,
      render: (isActive: boolean, item) => (
        <Switch
          checked={isActive}
          loading={toggleMutation.isPending && toggleMutation.variables?.item.itemId === item.itemId}
          onChange={(checked) => toggleMutation.mutate({ item, isActive: checked })}
        />
      ),
    },
    {
      title: 'Hành động',
      key: 'actions',
      width: 230,
      render: (_, item) => (
        <Space>
          <Tooltip title="Tặng vật phẩm này cho một người dùng">
            <Button size="small" icon={<GiftOutlined />} onClick={() => openGrant(item)} />
          </Tooltip>
          <Tooltip title="Ai đang sở hữu?">
            <Button size="small" icon={<TeamOutlined />} onClick={() => setOwnersTarget(item)} />
          </Tooltip>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(item)}>
            Sửa
          </Button>
          <Popconfirm
            title="Xoá vật phẩm khỏi kho quay?"
            description="Người đã sở hữu vẫn giữ. Muốn tạm ẩn thì tắt công tắc thay vì xoá."
            okText="Xoá"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(item.itemId)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Kho vật phẩm gacha
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 760 }}>
        Vật phẩm đang bật mới nằm trong kho quay. <b>Khung ảnh được tự động đồng bộ</b> từ trang
        Khung ảnh: khung có điều kiện mở khóa &quot;Quay gacha&quot; tự vào kho, đổi sang điều kiện
        khác thì tự rút khỏi kho. Giao diện và hiệu ứng chạm có sẵn trong app. Ở đây chỉ sửa tên /
        phẩm chất / ảnh đại diện / bật-tắt.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được kho vật phẩm."
          description={(error as Error).message}
        />
      )}
      <Space style={{ marginBottom: 16 }} wrap>
        <Segmented
          value={typeFilter}
          onChange={(v) => setTypeFilter(v as ItemType | 'ALL')}
          options={[
            { value: 'ALL', label: 'Tất cả' },
            { value: 'FRAME', label: 'Khung ảnh' },
            { value: 'SKIN', label: 'Giao diện' },
            { value: 'EFFECT', label: 'Hiệu ứng chạm' },
          ]}
        />
      </Space>
      <Table<GachaItem>
        rowKey="itemId"
        columns={columns}
        dataSource={shown}
        loading={isLoading}
        pagination={{ pageSize: 20, showTotal: (total) => `Tổng ${total} vật phẩm` }}
      />

      <Modal
        title={editing ? `Sửa: ${editing.itemName}` : 'Sửa vật phẩm'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Hủy"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          {editing && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`${TYPE_META[editing.itemType].label} · refId ${editing.refId}`}
              description={
                <>
                  Không đổi được loại và refId — hai giá trị này trỏ tới vật phẩm thật, đổi đi thì
                  thứ người chơi <b>đã sở hữu</b> bỗng thành thứ khác.
                </>
              }
            />
          )}

          <Form.Item
            name="itemName"
            label="Tên vật phẩm"
            rules={[{ required: true, message: 'Nhập tên vật phẩm.' }]}
          >
            <Input placeholder="VD: Khung Giáng Sinh" maxLength={100} />
          </Form.Item>

          <Form.Item
            name="rarity"
            label="Phẩm chất"
            rules={[{ required: true, message: 'Chọn phẩm chất.' }]}
            extra="Khung ảnh theo thiết kế là bậc R; hiệu ứng SR; giao diện SSR."
          >
            <Select
              options={(Object.keys(RARITY_META) as ItemRarity[]).map((r) => ({
                value: r,
                label: RARITY_META[r].label,
              }))}
            />
          </Form.Item>

          <Form.Item label="Ảnh đại diện" extra="Hiện trên thẻ kết quả quay trong app.">
            <Space direction="vertical">
              {imageUrl && <Image src={imageUrl} width={96} />}
              <Upload beforeUpload={(file) => handleUpload(file)} showUploadList={false}>
                <Button icon={<UploadOutlined />} loading={uploading}>
                  {imageUrl ? 'Đổi ảnh' : 'Tải ảnh lên'}
                </Button>
              </Upload>
            </Space>
          </Form.Item>

          <Form.Item name="sortOrder" label="Thứ tự hiển thị">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="Nằm trong kho quay"
            valuePropName="checked"
            extra="Tắt = không quay ra nữa; người đã sở hữu vẫn giữ."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={grantTarget ? `Tặng: ${grantTarget.itemName}` : 'Tặng vật phẩm'}
        open={grantTarget !== null}
        onCancel={() => setGrantTarget(null)}
        onOk={() =>
          grantTarget && grantUid && grantMutation.mutate({ itemId: grantTarget.itemId, uid: grantUid })
        }
        okText="Tặng"
        cancelText="Hủy"
        okButtonProps={{ disabled: !grantUid }}
        confirmLoading={grantMutation.isPending}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          Mở khoá thẳng vào tài khoản (demo / đền bù). Người đã sở hữu rồi thì tặng lại không đổi
          gì. Không cộng Astrite.
        </Typography.Paragraph>
        <Select
          showSearch
          style={{ width: '100%' }}
          placeholder="Tìm theo email hoặc tên..."
          value={grantUid}
          onChange={setGrantUid}
          onSearch={onUserSearch}
          filterOption={false}
          loading={searchingUsers}
          options={(userOptions?.items ?? []).map((u) => ({
            value: u.uid,
            label: `${u.fullName || '(chưa đặt tên)'} — ${u.email ?? u.uid}`,
          }))}
        />
      </Modal>

      <Drawer
        title={ownersTarget ? `Ai đang sở hữu: ${ownersTarget.itemName}` : ''}
        open={ownersTarget !== null}
        onClose={() => setOwnersTarget(null)}
        width={400}
      >
        {ownersError ? (
          <Alert type="error" showIcon message={(ownersError as Error).message} />
        ) : (
          <List
            loading={loadingOwners}
            dataSource={ownersData?.owners ?? []}
            locale={{ emptyText: 'Chưa có ai sở hữu vật phẩm này.' }}
            renderItem={(owner) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<Avatar src={owner.avatar}>{owner.fullName?.[0] ?? '?'}</Avatar>}
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
