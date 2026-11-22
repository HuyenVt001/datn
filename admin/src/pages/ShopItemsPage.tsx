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
  FRAME: { label: 'Frame', color: 'gold' },
  SKIN: { label: 'Skin', color: 'orange' },
  EFFECT: { label: 'Touch effect', color: 'purple' },
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

const formatAstrite = (n: number) => `${n.toLocaleString('en-US')} ⭐`;

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
      message.success(editing ? 'Item updated.' : 'Item listed.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ item, isActive }: { item: ShopItem; isActive: boolean }) =>
      updateShopItem(item.itemId, { isActive }),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Now on sale.' : 'Hidden from the Shop.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (itemId: string) => deleteShopItem(itemId),
    onSuccess: () => {
      message.success('Item removed from the Shop.');
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
      message.success('Item gifted to the user.');
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
      title: 'Image',
      dataIndex: 'imageUrl',
      width: 90,
      render: (url: string | undefined, item) =>
        url ? (
          <Image src={url} width={48} height={48} style={{ objectFit: 'contain' }} />
        ) : item.itemType === 'FRAME' ? (
          <Tooltip title="This frame has no image — click Edit to upload one.">
            <Tag color="red">Missing image</Tag>
          </Tooltip>
        ) : (
          <Tooltip title="No custom image — the app draws the preview from APK assets.">
            <Tag>Drawn by app</Tag>
          </Tooltip>
        ),
    },
    { title: 'Item name', dataIndex: 'itemName' },
    {
      title: 'Type',
      dataIndex: 'itemType',
      width: 140,
      render: (type: ShopItemType) => (
        <Tag color={TYPE_META[type]?.color}>{TYPE_META[type]?.label ?? type}</Tag>
      ),
    },
    {
      title: 'Price',
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
    { title: 'Order', dataIndex: 'sortOrder', width: 80 },
    {
      title: 'On sale',
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
      title: 'Actions',
      key: 'actions',
      width: 230,
      render: (_, item) => (
        <Space>
          <Tooltip title="Gift this item to a user">
            <Button size="small" icon={<GiftOutlined />} onClick={() => openGrant(item)} />
          </Tooltip>
          <Tooltip title="Who owns this?">
            <Button size="small" icon={<TeamOutlined />} onClick={() => setOwnersTarget(item)} />
          </Tooltip>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(item)}>
            Edit
          </Button>
          <Popconfirm
            title="Remove item from the Shop?"
            description={
              item.itemType === 'FRAME'
                ? 'Buyers keep it. Note: editing the frame on the Frames page will relist it automatically.'
                : 'Buyers keep it. To hide it temporarily, turn off the switch instead of removing it.'
            }
            okText="Remove"
            cancelText="Cancel"
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
          Shop items
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          List item
        </Button>
      </Space>
      <Typography.Paragraph type="secondary" style={{ maxWidth: 820 }}>
        Users buy with Astrite on the app&apos;s Shop screen. <b>Frames are listed automatically</b> when
        their unlock condition is &quot;Sold in Shop&quot; on the Frames page (default price 320 ⭐ — edit
        the price here). Skins and touch effects ship with the app and are listed by id. Astrite top-up
        packages are managed on the <b>Top-up packages</b> page.
      </Typography.Paragraph>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load items."
          description={(error as Error).message}
        />
      )}
      <Space style={{ marginBottom: 16 }} wrap>
        <Segmented
          value={typeFilter}
          onChange={(v) => setTypeFilter(v as ShopItemType | 'ALL')}
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'FRAME', label: 'Frame' },
            { value: 'SKIN', label: 'Skin' },
            { value: 'EFFECT', label: 'Touch effect' },
          ]}
        />
      </Space>
      <Table<ShopItem>
        rowKey="itemId"
        columns={columns}
        dataSource={shown}
        loading={isLoading}
        pagination={{ pageSize: 20, showTotal: (total) => `${total} items total` }}
      />

      <Modal
        title={editing ? `Edit: ${editing.itemName}` : 'List a new item'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void form.submit()}
        confirmLoading={saveMutation.isPending}
        okText={editing ? 'Save' : 'List'}
        cancelText="Cancel"
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
                  Type and refId can&apos;t be changed — they point to the real item; changing them would
                  turn what players <b>already bought</b> into something else.
                </>
              }
            />
          ) : (
            <>
              <Form.Item name="itemType" label="Item type" rules={[{ required: true }]}>
                <Segmented
                  options={[
                    { value: 'SKIN', label: 'Skin' },
                    { value: 'EFFECT', label: 'Touch effect' },
                    { value: 'FRAME', label: 'Frame' },
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
                  label="Frame"
                  rules={[{ required: true, message: 'Choose a frame.' }]}
                  extra={
                    frameOptions.length === 0
                      ? 'Every "Sold in Shop" frame is already listed. To sell another frame, change its unlock condition on the Frames page.'
                      : 'Only shows frames with the "Sold in Shop" condition that are not listed yet.'
                  }
                >
                  <Select
                    placeholder="Choose a frame..."
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
                  label={formType === 'SKIN' ? 'Skin id in the app' : 'Effect id in the app'}
                  rules={[{ required: true, message: 'Choose or enter an id.' }]}
                  extra="The id must exist in the APK. New ids need an app update before they can be listed."
                >
                  <Select
                    showSearch
                    placeholder="Choose an id..."
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
            label="Display name in the Shop"
            rules={[{ required: true, message: 'Enter an item name.' }]}
            extra="Shown as-is in the app (e.g. Snow, Flower)."
          >
            <Input maxLength={100} />
          </Form.Item>

          <Form.Item
            name="price"
            label="Price (Astrite)"
            rules={[{ required: true, message: 'Enter a price.' }]}
            extra={`Defaults: frame ${DEFAULT_PRICE.FRAME} · effect ${DEFAULT_PRICE.EFFECT} · skin ${DEFAULT_PRICE.SKIN}.`}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} addonAfter="⭐" />
          </Form.Item>

          <Form.Item
            label="Thumbnail"
            extra={
              formType === 'FRAME'
                ? 'Defaults to the frame image.'
                : 'Optional — if empty, the app draws the preview from APK assets.'
            }
          >
            <ImageUploadField value={imageUrl} onChange={setImageUrl} maxPreviewHeight={140} />
          </Form.Item>

          <Form.Item name="sortOrder" label="Display order (lower first)">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="On sale"
            valuePropName="checked"
            extra="Off = hidden from the Shop; buyers keep it."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={grantTarget ? `Gift: ${grantTarget.itemName}` : 'Gift item'}
        open={grantTarget !== null}
        onCancel={() => setGrantTarget(null)}
        onOk={() =>
          grantTarget && grantUid && grantMutation.mutate({ itemId: grantTarget.itemId, uid: grantUid })
        }
        okText="Gift"
        cancelText="Cancel"
        okButtonProps={{ disabled: !grantUid }}
        confirmLoading={grantMutation.isPending}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          Unlocks directly on the account (demo / compensation). Gifting to someone who already owns
          it changes nothing. No Astrite is debited and no purchase history is written.
        </Typography.Paragraph>
        <Select
          showSearch
          style={{ width: '100%' }}
          placeholder="Search by email or name..."
          value={grantUid}
          onChange={setGrantUid}
          onSearch={onUserSearch}
          filterOption={false}
          loading={searchingUsers}
          options={(userOptions?.items ?? []).map((u) => ({
            value: u.uid,
            label: `${u.fullName || '(no name)'} — ${u.email ?? u.uid}`,
          }))}
        />
      </Modal>

      <Drawer
        title={ownersTarget ? `Who owns: ${ownersTarget.itemName}` : ''}
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
            locale={{ emptyText: 'Nobody owns this item yet.' }}
            renderItem={(owner) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<Avatar src={owner.avatar}>{owner.fullName?.[0] ?? '?'}</Avatar>}
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
