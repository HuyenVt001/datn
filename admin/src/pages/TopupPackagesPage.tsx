import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import {
  createTopupPackage,
  deleteTopupPackage,
  listTopupPackages,
  updateTopupPackage,
} from '../api/topup.api';
import type { TopupPackage } from '../types';

const vnd = (n: number) => `${n.toLocaleString('en-US')} VND`;

interface PackageForm {
  name: string;
  astrite: number;
  priceVnd: number;
  isActive: boolean;
  isTest: boolean;
  sortOrder?: number;
}

/**
 * Trang quản lý gói nạp Astrite.
 *
 * ⚠️ Đây là **tiền thật**: sửa giá hoặc số Astrite ở đây làm đổi ngay số tiền
 * người dùng phải trả trong app. Đơn đã tạo không bị hồi tố — mỗi đơn chụp lại
 * giá và số Astrite của chính nó lúc tạo — nhưng đơn mới thì áp ngay lập tức.
 */
export function TopupPackagesPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const {
    data: packages,
    isLoading,
    error,
  } = useQuery({ queryKey: ['topup-packages'], queryFn: listTopupPackages });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['topup-packages'] });

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TopupPackage | null>(null); // null = đang thêm mới
  const [form] = Form.useForm<PackageForm>();

  const saveMutation = useMutation({
    mutationFn: (values: PackageForm) =>
      editing ? updateTopupPackage(editing.packageId, values) : createTopupPackage(values),
    onSuccess: () => {
      message.success(editing ? 'Package updated.' : 'Package added.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ pkg, isActive }: { pkg: TopupPackage; isActive: boolean }) =>
      updateTopupPackage(pkg.packageId, { isActive }),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Enabled — package shown in the app.' : 'Disabled — package hidden from the app.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (packageId: string) => deleteTopupPackage(packageId),
    onSuccess: () => {
      message.success('Package deleted.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ isActive: true, isTest: false, sortOrder: 0 });
    setModalOpen(true);
  };

  const openEdit = (pkg: TopupPackage) => {
    setEditing(pkg);
    form.setFieldsValue({
      name: pkg.name,
      astrite: pkg.astrite,
      priceVnd: pkg.priceVnd,
      isActive: pkg.isActive,
      isTest: pkg.isTest,
      sortOrder: pkg.sortOrder,
    });
    setModalOpen(true);
  };

  const columns: ColumnsType<TopupPackage> = [
    {
      title: 'Package name',
      dataIndex: 'name',
      render: (name: string, pkg) => (
        <Space>
          <Typography.Text strong>{name}</Typography.Text>
          {pkg.isTest && (
            <Tooltip title="Test package. Still real money — just cheap.">
              <Tag color="volcano">TEST</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: 'Astrite',
      dataIndex: 'astrite',
      width: 140,
      align: 'right',
      render: (n: number) => n.toLocaleString('en-US'),
    },
    {
      title: 'Price',
      dataIndex: 'priceVnd',
      width: 130,
      align: 'right',
      render: (n: number) => <Typography.Text strong>{vnd(n)}</Typography.Text>,
    },
    {
      // Cột này để so sánh các gói với nhau: gói to phải đáng tiền hơn gói nhỏ,
      // nếu không thì không ai mua gói to.
      title: 'Astrite / 1,000 VND',
      key: 'rate',
      width: 150,
      align: 'right',
      render: (_, pkg) =>
        Math.round((pkg.astrite / pkg.priceVnd) * 1000).toLocaleString('en-US'),
    },
    { title: 'Order', dataIndex: 'sortOrder', width: 90 },
    {
      title: 'Shown in app',
      dataIndex: 'isActive',
      width: 140,
      render: (isActive: boolean, pkg) => (
        <Switch
          checked={isActive}
          loading={
            toggleMutation.isPending && toggleMutation.variables?.pkg.packageId === pkg.packageId
          }
          onChange={(checked) => toggleMutation.mutate({ pkg, isActive: checked })}
        />
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 170,
      render: (_, pkg) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(pkg)}>
            Edit
          </Button>
          <Popconfirm
            title="Delete package?"
            description="Old order history is kept. To hide it from the app temporarily, turn off the switch instead of deleting."
            okText="Delete"
            cancelText="Cancel"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(pkg.packageId)}
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
        Astrite top-up packages
      </Typography.Title>
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Couldn't load top-up packages."
          description={(error as Error).message}
        />
      )}
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add package
        </Button>
      </Space>
      <Table<TopupPackage>
        rowKey="packageId"
        columns={columns}
        dataSource={packages ?? []}
        loading={isLoading}
        pagination={false}
      />

      <Modal
        title={editing ? `Edit: ${editing.name}` : 'Add package'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void form.submit()}
        confirmLoading={saveMutation.isPending}
        okText={editing ? 'Save' : 'Add'}
        cancelText="Cancel"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="Package name"
            rules={[{ required: true, message: 'Enter a package name.' }]}
            extra="Shown in the app's top-up popup. e.g. 600 Astrite"
          >
            <Input placeholder="VD: 600 Astrite" maxLength={60} />
          </Form.Item>

          <Form.Item
            name="astrite"
            label="Astrite received"
            rules={[{ required: true, message: 'Enter the Astrite amount.' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="priceVnd"
            label="Price (VND)"
            rules={[{ required: true, message: 'Enter a price.' }]}
            extra="Minimum 1,000 VND — below this, PayOS refuses to create a payment link."
          >
            <InputNumber min={1000} step={1000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="sortOrder" label="Display order">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="Shown in app"
            valuePropName="checked"
            extra="Off = hidden from the top-up popup; existing orders are unaffected."
          >
            <Switch />
          </Form.Item>

          <Form.Item
            name="isTest"
            label="Mark as test package"
            valuePropName="checked"
            extra="Just a label to spot it on this page — it still charges real money like any other package."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
