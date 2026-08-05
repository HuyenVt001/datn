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

const vnd = (n: number) => `${n.toLocaleString('vi-VN')}đ`;

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
      message.success(editing ? 'Đã cập nhật gói nạp.' : 'Đã thêm gói nạp.');
      setModalOpen(false);
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ pkg, isActive }: { pkg: TopupPackage; isActive: boolean }) =>
      updateTopupPackage(pkg.packageId, { isActive }),
    onSuccess: (_, { isActive }) => {
      message.success(isActive ? 'Đã bật — gói hiện trong app.' : 'Đã tắt — gói ẩn khỏi app.');
      void invalidate();
    },
    onError: (err: Error) => message.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (packageId: string) => deleteTopupPackage(packageId),
    onSuccess: () => {
      message.success('Đã xoá gói nạp.');
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
      title: 'Tên gói',
      dataIndex: 'name',
      render: (name: string, pkg) => (
        <Space>
          <Typography.Text strong>{name}</Typography.Text>
          {pkg.isTest && (
            <Tooltip title="Gói dùng để kiểm thử. Vẫn là tiền thật — chỉ là giá rẻ.">
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
      render: (n: number) => n.toLocaleString('vi-VN'),
    },
    {
      title: 'Giá',
      dataIndex: 'priceVnd',
      width: 130,
      align: 'right',
      render: (n: number) => <Typography.Text strong>{vnd(n)}</Typography.Text>,
    },
    {
      // Cột này để so sánh các gói với nhau: gói to phải đáng tiền hơn gói nhỏ,
      // nếu không thì không ai mua gói to.
      title: 'Astrite / 1.000đ',
      key: 'rate',
      width: 150,
      align: 'right',
      render: (_, pkg) =>
        Math.round((pkg.astrite / pkg.priceVnd) * 1000).toLocaleString('vi-VN'),
    },
    { title: 'Thứ tự', dataIndex: 'sortOrder', width: 90 },
    {
      title: 'Hiện trong app',
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
      title: 'Hành động',
      key: 'actions',
      width: 170,
      render: (_, pkg) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(pkg)}>
            Sửa
          </Button>
          <Popconfirm
            title="Xoá gói nạp?"
            description="Lịch sử đơn cũ vẫn giữ. Muốn tạm ẩn khỏi app thì tắt công tắc thay vì xoá."
            okText="Xoá"
            cancelText="Hủy"
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
        Gói nạp Astrite
      </Typography.Title>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16, maxWidth: 860 }}
        message="Đây là tiền thật (PayOS production)"
        description={
          <>
            Sửa giá hoặc số Astrite ở đây <b>đổi ngay số tiền người dùng phải trả</b> cho các đơn tạo
            từ lúc này. Đơn đã tạo giữ nguyên giá của chính nó nên không bị hồi tố. Mọi thao tác ở
            trang này đều được ghi vào Nhật ký.
          </>
        }
      />
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được danh sách gói nạp."
          description={(error as Error).message}
        />
      )}
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Thêm gói nạp
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
        title={editing ? `Sửa: ${editing.name}` : 'Thêm gói nạp'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void form.submit()}
        confirmLoading={saveMutation.isPending}
        okText={editing ? 'Lưu' : 'Thêm'}
        cancelText="Hủy"
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
            label="Tên gói"
            rules={[{ required: true, message: 'Nhập tên gói.' }]}
            extra="Hiện trong popup nạp của app. VD: 600 Astrite"
          >
            <Input placeholder="VD: 600 Astrite" maxLength={60} />
          </Form.Item>

          <Form.Item
            name="astrite"
            label="Số Astrite nhận được"
            rules={[{ required: true, message: 'Nhập số Astrite.' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="priceVnd"
            label="Giá (VND)"
            rules={[{ required: true, message: 'Nhập giá.' }]}
            extra="Tối thiểu 1.000đ — dưới mức này PayOS từ chối tạo link thanh toán."
          >
            <InputNumber min={1000} step={1000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="sortOrder" label="Thứ tự hiển thị">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="Hiện trong app"
            valuePropName="checked"
            extra="Tắt = ẩn khỏi popup nạp; đơn đã tạo không bị ảnh hưởng."
          >
            <Switch />
          </Form.Item>

          <Form.Item
            name="isTest"
            label="Đánh dấu là gói kiểm thử"
            valuePropName="checked"
            extra="Chỉ là nhãn cho dễ nhận ra ở trang này — vẫn thu tiền thật như mọi gói khác."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
