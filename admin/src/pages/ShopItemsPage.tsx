import { DeleteOutlined, EditOutlined, GiftOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useRef, useState } from 'react';
import { listUsers } from '../api/admin.api';
import { listFrames } from '../api/frames.api';
import {
  createShopItem,
  deleteShopItem,
  grantShopItem,
  listShopItemOwners,
  listShopItems,
  updateShopItem,
} from '../api/shop.api';
import { ImageUploadField } from '../components/ImageUploadField';
import type { ShopItem, ShopItemType } from '../types';

/** Giá mặc định theo loại — khớp `SHOP_DEFAULT_PRICE_*` của server. */
const DEFAULT_PRICE: Record<ShopItemType, number> = { FRAME: 320, SKIN: 4800, EFFECT: 1600 };

const TYPE_META: Record<ShopItemType, { label: string; color: string }> = {
  FRAME: { label: 'Khung ảnh', color: 'gold' },
  SKIN: { label: 'Giao diện', color: 'orange' },
  EFFECT: { label: 'Hiệu ứng chạm', color: 'purple' },
};

/**
 * Id skin / hiệu ứng ĐANG CÓ trong APK (SkinRegistry / TouchEffectRegistry).
 * Chỉ để gợi ý khi niêm yết — app thêm id mới thì cập nhật danh sách này.
 */
const APK_IDS: Record<'SKIN' | 'EFFECT', { id: number; name: string }[]> = {
  SKIN: [
    { id: 1, name: 'Snow' },
    { id: 2, name: 'Forest' },
  ],
  EFFECT: [
    { id: 1, name: 'Flower' },
    { id: 2, name: 'Snowflake' },
    { id: 3, name: 'Leaf' },
    { id: 4, name: 'Magic' },
  ],
};

interface ItemForm {
  itemType: ShopItemType;
  refId: string;
  itemName: string;
  price: number;
  isActive: boolean;
  sortOrder?: number;
}

const formatAstrite = (n: number) => `${n.toLocaleString('vi-VN')} ⭐`;

/**
 * Trang quản lý mặt hàng Shop (2026-11-15 — thay Kho vật phẩm gacha).
 *
 * - Khung ảnh có điều kiện "Bán tại Cửa hàng" được server TỰ niêm yết (giá 320)
 *   khi tạo/sửa ở trang Khung ảnh; ở đây sửa giá / tên / ảnh / bật-tắt.
 * - Giao diện & hiệu ứng chạm: asset nằm trong APK — niêm yết theo id có sẵn.
 * - Gói nạp Astrite quản lý ở trang "Gói nạp" (cùng nhóm Cửa hàng).
 */
export function ShopItemsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [typeFilter, setTypeFilter] = useState<ShopItemType | 'ALL'>('ALL');

  const { data: items, isLoading, error } = useQuery({
    queryKey: ['shop-items'],
    queryFn: listShopItems,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['shop-items'] });

  const shown = useMemo(
    () => (items ?? []).filter((i) => typeFilter === 'ALL' || i.itemType === typeFilter),
    [items, typeFilter],
  );

  // ==== Modal thêm / sửa ====
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ShopItem | null>(null); // null = đang thêm mới
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [form] = Form.useForm<ItemForm>();
  const formType = Form.useWatch('itemType', form) ?? 'SKIN';

  // Khung SHOP chưa niêm yết — chỉ tải khi mở modal thêm mới
  const { data: frames } = useQuery({
    queryKey: ['frames'],
    queryFn: listFrames,
    enabled: modalOpen && !editing,
  });
  const listedRefs = useMemo(
    () => new Set((items ?? []).map((i) => `${i.itemType}:${i.refId}`)),
    [items],
  );
  const frameOptions = (frames ?? [])
    .filter((f) => f.unlockType === 'SHOP' && !listedRefs.has(`FRAME:${f.frameId}`))
    .map((f) => ({ value: f.frameId, label: f.frameName, imageUrl: f.imageUrl }));
  const apkOptions =
    formType === 'FRAME'
      ? []
      : APK_IDS[formType].map((x) => ({
          value: String(x.id),
          label: `#${x.id} — ${x.name}`,
          disabled: listedRefs.has(`${formType}:${x.id}`),
          name: x.name,
        }));

  const openCreate = () => {
    setEditing(null);
    setImageUrl(undefined);
    form.resetFields();
    form.setFieldsValue({ itemType: 'SKIN', price: DEFAULT_PRICE.SKIN, isActive: true });
    setModalOpen(true);
  };

  const openEdit = (item: ShopItem) => {
    setEditing(item);
    setImageUrl(item.imageUrl);
    form.setFieldsValue({
      itemType: item.itemType,
      refId: item.refId,
      itemName: item.itemName,
      price: item.price,
      isActive: item.isActive,
      sortOrder: item.sortOrder,
    });
    setModalOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: (values: ItemForm) =>
      editing
        ? updateShopItem(editing.itemId, {
            itemName: values.itemName,
            price: values.price,
            imageUrl,
            isActive: values.isActive,
            sortOrder: values.sortOrder,
          })
        : createShopItem({
            itemName: values.itemName,
            itemType: values.itemType,
            refId: values.refId,
            price: values.price,
            imageUrl,
            isActive: values.isActive,
            sortOrder: values.sortOrder,
          }),
    onSuccess: () => {
      message.success(editing ? 'Đã cập nhật vật phẩm.' : 'Đã niêm yết vật phẩm.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ item, isActive }: { item: ShopItem; isActive: boolean }) =>
      updateShopItem(item.itemId, { isActive }),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Đã mở bán.' : 'Đã ẩn khỏi Shop.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (itemId: string) => deleteShopItem(itemId),
    onSuccess: () => {
      message.success('Đã gỡ vật phẩm khỏi Cửa hàng.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Tặng vật phẩm cho user ====
  const [grantTarget, setGrantTarget] = useState<ShopItem | null>(null);
  const [grantUid, setGrantUid] = useState<string | undefined>(undefined);
  const [userSearch, setUserSearch] = useState('');
  // Debounce gõ phím ở ô tìm user — mỗi phím là 1 request /admin/users nếu không debounce
  const searchTimer = useRef<number | undefined>(undefined);
  const onUserSearch = (value: string) => {
    window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => setUserSearch(value), 300);
  };
  const openGrant = (item: ShopItem) => {
    setUserSearch('');
    setGrantUid(undefined);
    setGrantTarget(item);
  };

  const { data: userOptions, isFetching: searchingUsers } = useQuery({
    queryKey: ['admin-users-search', userSearch],
    queryFn: () => listUsers({ page: 1, limit: 20, search: userSearch || undefined }),
    enabled: grantTarget !== null,
  });

  const grantMutation = useMutation({
    mutationFn: ({ itemId, uid }: { itemId: string; uid: string }) => grantShopItem(itemId, uid),
    onSuccess: () => {
      message.success('Đã tặng vật phẩm cho người dùng.');
      setGrantTarget(null);
      setGrantUid(undefined);
      void queryClient.invalidateQueries({ queryKey: ['shop-item-owners'] });
    },
    onError: (err: Error) => message.error(err.message),
  });

  // ==== Drawer ai đang sở hữu ====
  const [ownersTarget, setOwnersTarget] = useState<ShopItem | null>(null);
  const {
    data: ownersData,
    isFetching: loadingOwners,
    error: ownersError,
  } = useQuery({
    queryKey: ['shop-item-owners', ownersTarget?.itemId],
    queryFn: () => listShopItemOwners(ownersTarget!.itemId),
    enabled: ownersTarget !== null,
  });

  const columns: ColumnsType<ShopItem> = [
    {
      title: 'Ảnh',
      dataIndex: 'imageUrl',
      width: 90,
      render: (url: string | undefined, item) =>
        url ? (
          <Image src={url} width={48} height={48} style={{ objectFit: 'contain' }} />
        ) : item.itemType === 'FRAME' ? (
          <Tooltip title="Khung chưa có ảnh — bấm Sửa để tải ảnh lên.">
            <Tag color="red">Thiếu ảnh</Tag>
          </Tooltip>
        ) : (
          <Tooltip title="Không có ảnh riêng — app tự vẽ xem trước từ asset trong APK.">
            <Tag>Vẽ từ app</Tag>
          </Tooltip>
        ),
    },
    { title: 'Tên vật phẩm', dataIndex: 'itemName' },
    {
      title: 'Loại',
      dataIndex: 'itemType',
      width: 140,
      render: (type: ShopItemType) => (
        <Tag color={TYPE_META[type]?.color}>{TYPE_META[type]?.label ?? type}</Tag>
      ),
    },
    {
      title: 'Giá',
      dataIndex: 'price',
      width: 120,
      sorter: (a, b) => a.price - b.price,
      render: (price: number) => <b>{formatAstrite(price)}</b>,
    },
    {
      title: 'refId',
      dataIndex: 'refId',
      width: 130,
      render: (refId: string) => <Typography.Text code>{refId}</Typography.Text>,
    },
    { title: 'Thứ tự', dataIndex: 'sortOrder', width: 80 },
    {
      title: 'Mở bán',
      dataIndex: 'isActive',
      width: 100,
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
            title="Gỡ vật phẩm khỏi Cửa hàng?"
            description={
              item.itemType === 'FRAME'
                ? 'Người đã mua vẫn giữ. Lưu ý: sửa khung ở trang Khung ảnh sẽ tự niêm yết lại khung này.'
                : 'Người đã mua vẫn giữ. Muốn tạm ẩn thì tắt công tắc thay vì gỡ.'
            }
            okText="Gỡ"
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
      <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          Vật phẩm Cửa hàng
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Niêm yết vật phẩm
        </Button>
      </Space>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 820 }}>
        Người dùng mua bằng Astrite trong màn Shop của app. <b>Khung ảnh tự động lên kệ</b> khi có
        điều kiện mở khóa &quot;Bán tại Cửa hàng&quot; ở trang Khung ảnh (giá mặc định 320 ⭐ — sửa
        giá tại đây). Giao diện và hiệu ứng chạm có sẵn trong app, niêm yết theo id. Gói nạp Astrite
        quản lý ở trang <b>Gói nạp</b>.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được danh sách vật phẩm."
          description={(error as Error).message}
        />
      )}
      <Space style={{ marginBottom: 16 }} wrap>
        <Segmented
          value={typeFilter}
          onChange={(v) => setTypeFilter(v as ShopItemType | 'ALL')}
          options={[
            { value: 'ALL', label: 'Tất cả' },
            { value: 'FRAME', label: 'Khung ảnh' },
            { value: 'SKIN', label: 'Giao diện' },
            { value: 'EFFECT', label: 'Hiệu ứng chạm' },
          ]}
        />
      </Space>
      <Table<ShopItem>
        rowKey="itemId"
        columns={columns}
        dataSource={shown}
        loading={isLoading}
        pagination={{ pageSize: 20, showTotal: (total) => `Tổng ${total} vật phẩm` }}
      />

      <Modal
        title={editing ? `Sửa: ${editing.itemName}` : 'Niêm yết vật phẩm mới'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void form.submit()}
        confirmLoading={saveMutation.isPending}
        okText={editing ? 'Lưu' : 'Niêm yết'}
        cancelText="Hủy"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          {editing ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`${TYPE_META[editing.itemType]?.label ?? editing.itemType} · refId ${editing.refId}`}
              description={
                <>
                  Không đổi được loại và refId — hai giá trị này trỏ tới vật phẩm thật, đổi đi thì
                  thứ người chơi <b>đã mua</b> bỗng thành thứ khác.
                </>
              }
            />
          ) : (
            <>
              <Form.Item name="itemType" label="Loại vật phẩm" rules={[{ required: true }]}>
                <Segmented
                  options={[
                    { value: 'SKIN', label: 'Giao diện' },
                    { value: 'EFFECT', label: 'Hiệu ứng chạm' },
                    { value: 'FRAME', label: 'Khung ảnh' },
                  ]}
                  onChange={(v) => {
                    const type = v as ShopItemType;
                    form.setFieldsValue({ refId: undefined, price: DEFAULT_PRICE[type] });
                  }}
                />
              </Form.Item>
              {formType === 'FRAME' ? (
                <Form.Item
                  name="refId"
                  label="Khung ảnh"
                  rules={[{ required: true, message: 'Chọn khung ảnh.' }]}
                  extra={
                    frameOptions.length === 0
                      ? 'Mọi khung "Bán tại Cửa hàng" đều đã lên kệ. Muốn bán khung khác: đổi điều kiện mở khóa của nó ở trang Khung ảnh.'
                      : 'Chỉ hiện khung có điều kiện "Bán tại Cửa hàng" và chưa niêm yết.'
                  }
                >
                  <Select
                    placeholder="Chọn khung..."
                    options={frameOptions}
                    onChange={(id: string) => {
                      const f = frameOptions.find((o) => o.value === id);
                      if (f) {
                        form.setFieldsValue({ itemName: f.label });
                        setImageUrl(f.imageUrl);
                      }
                    }}
                  />
                </Form.Item>
              ) : (
                <Form.Item
                  name="refId"
                  label={formType === 'SKIN' ? 'Mã giao diện trong app' : 'Mã hiệu ứng trong app'}
                  rules={[{ required: true, message: 'Chọn hoặc nhập mã.' }]}
                  extra="Mã phải có sẵn trong APK. Mã mới cần cập nhật app trước rồi mới niêm yết."
                >
                  <Select
                    showSearch
                    placeholder="Chọn mã..."
                    options={apkOptions}
                    onChange={(id: string) => {
                      const o = apkOptions.find((x) => x.value === id);
                      if (o && !form.getFieldValue('itemName')) {
                        form.setFieldsValue({ itemName: o.name });
                      }
                    }}
                  />
                </Form.Item>
              )}
            </>
          )}

          <Form.Item
            name="itemName"
            label="Tên hiển thị trong Shop"
            rules={[{ required: true, message: 'Nhập tên vật phẩm.' }]}
            extra="App hiển thị tiếng Anh — nên đặt tên tiếng Anh (VD: Snow, Flower)."
          >
            <Input maxLength={100} />
          </Form.Item>

          <Form.Item
            name="price"
            label="Giá (Astrite)"
            rules={[{ required: true, message: 'Nhập giá.' }]}
            extra={`Mặc định: khung ${DEFAULT_PRICE.FRAME} · hiệu ứng ${DEFAULT_PRICE.EFFECT} · giao diện ${DEFAULT_PRICE.SKIN}.`}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} addonAfter="⭐" />
          </Form.Item>

          <Form.Item
            label="Ảnh đại diện"
            extra={
              formType === 'FRAME'
                ? 'Mặc định lấy ảnh của khung.'
                : 'Tuỳ chọn — để trống thì app tự vẽ xem trước từ asset trong APK.'
            }
          >
            <ImageUploadField value={imageUrl} onChange={setImageUrl} maxPreviewHeight={140} />
          </Form.Item>

          <Form.Item name="sortOrder" label="Thứ tự hiển thị (nhỏ lên trước)">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="Mở bán"
            valuePropName="checked"
            extra="Tắt = ẩn khỏi Shop; người đã mua vẫn giữ."
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
          gì. Không trừ Astrite, không ghi lịch sử mua.
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
