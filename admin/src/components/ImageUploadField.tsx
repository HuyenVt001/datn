import { UploadOutlined } from '@ant-design/icons';
import { App as AntApp, Button, Upload } from 'antd';
import { useState } from 'react';
import { uploadImage } from '../api/upload.api';

interface ImageUploadFieldProps {
  value?: string;
  onChange: (url: string) => void;
  /** Chu tren nut khi chua co anh. */
  buttonText?: string;
  /** Chieu cao toi da cua anh xem truoc (px). */
  maxPreviewHeight?: number;
}

/**
 * O tai anh len Cloudinary qua `POST /upload/admin` (dung chung FramesPage +
 * ShopItemsPage). Co anh -> hien xem truoc tren nen ca-ro (thay ro PNG trong
 * suot), bam vao anh de doi; chua co -> nut tai len.
 */
export function ImageUploadField({
  value,
  onChange,
  buttonText = 'Tải ảnh lên',
  maxPreviewHeight = 200,
}: ImageUploadFieldProps) {
  const { message } = AntApp.useApp();
  const [uploading, setUploading] = useState(false);

  return (
    <Upload
      accept="image/*"
      maxCount={1}
      showUploadList={false}
      customRequest={async ({ file, onSuccess, onError }) => {
        setUploading(true);
        try {
          const result = await uploadImage(file as File);
          onChange(result.url);
          onSuccess?.(result);
        } catch (err) {
          message.error(err instanceof Error ? err.message : 'Upload thất bại.');
          onError?.(err as Error);
        } finally {
          setUploading(false);
        }
      }}
    >
      {value ? (
        <div style={{ cursor: 'pointer' }} title="Bấm để đổi ảnh khác">
          <img
            src={value}
            alt="Ảnh xem trước"
            style={{
              maxWidth: '100%',
              maxHeight: maxPreviewHeight,
              display: 'block',
              opacity: uploading ? 0.5 : 1,
              background: 'repeating-conic-gradient(#f0f0f0 0% 25%, #ffffff 0% 50%) 50% / 20px 20px',
            }}
          />
        </div>
      ) : (
        <Button icon={<UploadOutlined />} loading={uploading}>
          {buttonText}
        </Button>
      )}
    </Upload>
  );
}
