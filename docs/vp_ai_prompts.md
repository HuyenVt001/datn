# Prompt cho Visual Paradigm — AI Diagram Generation

Sinh tự động từ `docs/diagrams/seq_*.puml`. Cách dùng: Tools → AI Diagram Generation → loại **Sequence Diagram** → dán nguyên khối prompt của biểu đồ cần vẽ → Generate. Sau khi sinh, đối chiếu lại với bảng message (AI có thể vẫn đổi chữ hoặc thêm bước — sửa tay phần lệch).

## Quản lý khung ảnh

### 1. Xem danh sách khung ảnh

File nguồn: `seq_quan_ly_khung_anh_1_xem_danh_sach.puml`

```text
Create a UML sequence diagram titled "Xem danh sách khung ảnh".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":FramesRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "mở trang Frames" [call (solid arrow)]
2. :FramesPage -> :frames.api : "listFrames()" [call (solid arrow)]
3. :frames.api -> :FramesController : "listAsAdmin()" [call (solid arrow)]
4. :FramesController -> :FramesService : "listAll()" [call (solid arrow)]
5. :FramesService -> :FramesRepository : "list()" [call (solid arrow)]
6. :FramesRepository -> :FramesService : "Frame[]" [reply (dashed arrow)]
7. :FramesService -> :FramesController : "Frame[]" [reply (dashed arrow)]
8. :FramesController -> :frames.api : "Frame[]" [reply (dashed arrow)]
9. :frames.api -> :FramesPage : "Frame[]" [reply (dashed arrow)]
10. :FramesPage -> Quản trị viên : "hiển thị danh sách khung ảnh" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 2. Thêm khung ảnh

File nguồn: `seq_quan_ly_khung_anh_2_them_khung.puml`

```text
Create a UML sequence diagram titled "Thêm khung ảnh".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":FramesRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "nhấn Add frame" [call (solid arrow)]
2. :FramesPage -> Quản trị viên : "hiển thị popup Add frame" [reply (dashed arrow)]
3. Quản trị viên -> :FramesPage : "nhập tên, chọn điều kiện mở khóa, tải ảnh PNG, nhấn Add" [call (solid arrow)]
4. :FramesPage -> :frames.api : "createFrame(body)" [call (solid arrow)]
5. :frames.api -> :FramesController : "create(dto)" [call (solid arrow)]
6. :FramesController -> :FramesService : "create(dto)" [call (solid arrow)]
7. :FramesService -> :FramesRepository : "create(frame)" [call (solid arrow)]
8. :FramesRepository -> :FramesService : "Frame" [reply (dashed arrow)]
9. :FramesService -> :FramesController : "Frame" [reply (dashed arrow)]
10. :FramesController -> :frames.api : "Frame" [reply (dashed arrow)]
11. :frames.api -> :FramesPage : "Frame" [reply (dashed arrow)]
12. :FramesPage -> Quản trị viên : "hiển thị khung mới trong danh sách" [reply (dashed arrow)]
13. :FramesPage -> Quản trị viên : "báo lỗi trên form" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Dữ liệu hợp lệ] contains messages 4 to 12; operand [Thiếu tên hoặc ảnh] contains message 13.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 3. Sửa khung ảnh

File nguồn: `seq_quan_ly_khung_anh_3_sua_khung.puml`

```text
Create a UML sequence diagram titled "Sửa khung ảnh".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":FramesRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "nhấn icon chỉnh sửa" [call (solid arrow)]
2. :FramesPage -> Quản trị viên : "hiển thị popup Edit frame" [reply (dashed arrow)]
3. Quản trị viên -> :FramesPage : "đổi tên, điều kiện hoặc ảnh, nhấn Save" [call (solid arrow)]
4. :FramesPage -> :frames.api : "updateFrame(frameId, body)" [call (solid arrow)]
5. :frames.api -> :FramesController : "update(frameId, dto)" [call (solid arrow)]
6. :FramesController -> :FramesService : "update(frameId, dto)" [call (solid arrow)]
7. :FramesService -> :FramesRepository : "update(frameId, patch)" [call (solid arrow)]
8. :FramesRepository -> :FramesService : "Success" [reply (dashed arrow)]
9. :FramesService -> :FramesController : "Frame" [reply (dashed arrow)]
10. :FramesController -> :frames.api : "Frame" [reply (dashed arrow)]
11. :frames.api -> :FramesPage : "Frame" [reply (dashed arrow)]
12. :FramesPage -> Quản trị viên : "cập nhật khung trong danh sách" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 4. Xóa khung ảnh

File nguồn: `seq_quan_ly_khung_anh_4_xoa_khung.puml`

```text
Create a UML sequence diagram titled "Xóa khung ảnh".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":FramesRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "nhấn icon thùng rác" [call (solid arrow)]
2. :FramesPage -> Quản trị viên : "hiển thị hộp xác nhận Delete this frame?" [reply (dashed arrow)]
3. Quản trị viên -> :FramesPage : "nhấn Delete" [call (solid arrow)]
4. :FramesPage -> :frames.api : "deleteFrame(frameId)" [call (solid arrow)]
5. :frames.api -> :FramesController : "delete(frameId)" [call (solid arrow)]
6. :FramesController -> :FramesService : "delete(frameId)" [call (solid arrow)]
7. :FramesService -> :FramesRepository : "delete(frameId)" [call (solid arrow)]
8. :FramesRepository -> :FramesService : "Success" [reply (dashed arrow)]
9. :FramesService -> :FramesController : "Success" [reply (dashed arrow)]
10. :FramesController -> :frames.api : "Success" [reply (dashed arrow)]
11. :frames.api -> :FramesPage : "Success" [reply (dashed arrow)]
12. :FramesPage -> Quản trị viên : "xóa khung khỏi danh sách" [reply (dashed arrow)]
13. Quản trị viên -> :FramesPage : "nhấn Cancel" [call (solid arrow)]
14. :FramesPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Nhấn Delete] contains messages 3 to 12; operand [Nhấn Cancel] contains messages 13 to 14.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 5. Xem người sở hữu khung

File nguồn: `seq_quan_ly_khung_anh_5_xem_nguoi_so_huu.puml`

```text
Create a UML sequence diagram titled "Xem người sở hữu khung".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":UsersRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "nhấn icon Who owns this?" [call (solid arrow)]
2. :FramesPage -> :frames.api : "listFrameOwners(frameId)" [call (solid arrow)]
3. :frames.api -> :FramesController : "listOwners(frameId)" [call (solid arrow)]
4. :FramesController -> :FramesService : "listOwners(frameId)" [call (solid arrow)]
5. :FramesService -> :UsersRepository : "listByUnlockedFrame(frameId)" [call (solid arrow)]
6. :UsersRepository -> :FramesService : "User[]" [reply (dashed arrow)]
7. :FramesService -> :FramesController : "{frame, owners}" [reply (dashed arrow)]
8. :FramesController -> :frames.api : "{frame, owners}" [reply (dashed arrow)]
9. :frames.api -> :FramesPage : "{frame, owners}" [reply (dashed arrow)]
10. :FramesPage -> Quản trị viên : "hiển thị danh sách người sở hữu" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 6. Tặng khung

File nguồn: `seq_quan_ly_khung_anh_6_tang_khung.puml`

```text
Create a UML sequence diagram titled "Tặng khung".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":UsersRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "nhấn icon quà tặng" [call (solid arrow)]
2. :FramesPage -> Quản trị viên : "hiển thị popup Grant frame" [reply (dashed arrow)]
3. Quản trị viên -> :FramesPage : "tìm và chọn người dùng, nhấn Grant frame" [call (solid arrow)]
4. :FramesPage -> :frames.api : "grantFrame(frameId, uid)" [call (solid arrow)]
5. :frames.api -> :FramesController : "grant(frameId, uid)" [call (solid arrow)]
6. :FramesController -> :FramesService : "unlockForUser(uid, frameId)" [call (solid arrow)]
7. :FramesService -> :UsersRepository : "unlockFrame(uid, frameId)" [call (solid arrow)]
8. :UsersRepository -> :FramesService : "Success" [reply (dashed arrow)]
9. :FramesService -> :FramesController : "Success" [reply (dashed arrow)]
10. :FramesController -> :frames.api : "Success" [reply (dashed arrow)]
11. :frames.api -> :FramesPage : "Success" [reply (dashed arrow)]
12. :FramesPage -> Quản trị viên : "thông báo đã tặng khung" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

## Quản lý cửa hàng

### 1. Xem danh sách vật phẩm

File nguồn: `seq_quan_ly_cua_hang_1_xem_danh_sach.puml`

```text
Create a UML sequence diagram titled "Xem danh sách vật phẩm".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "mở menu Shop, chọn Items" [call (solid arrow)]
2. :ShopItemsPage -> :shop.api : "listShopItems()" [call (solid arrow)]
3. :shop.api -> :ShopController : "listItemsAsAdmin()" [call (solid arrow)]
4. :ShopController -> :ShopService : "listAllItems()" [call (solid arrow)]
5. :ShopService -> :ShopRepository : "listAllItems()" [call (solid arrow)]
6. :ShopRepository -> :ShopService : "ShopItem[]" [reply (dashed arrow)]
7. :ShopService -> :ShopController : "ShopItem[]" [reply (dashed arrow)]
8. :ShopController -> :shop.api : "ShopItem[]" [reply (dashed arrow)]
9. :shop.api -> :ShopItemsPage : "ShopItem[]" [reply (dashed arrow)]
10. :ShopItemsPage -> Quản trị viên : "hiển thị danh sách vật phẩm" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 2. Thêm vật phẩm vào cửa hàng

File nguồn: `seq_quan_ly_cua_hang_2_them_vat_pham.puml`

```text
Create a UML sequence diagram titled "Thêm vật phẩm vào cửa hàng".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "nhấn List item" [call (solid arrow)]
2. :ShopItemsPage -> Quản trị viên : "hiển thị popup List a new item" [reply (dashed arrow)]
3. Quản trị viên -> :ShopItemsPage : "chọn loại và id, nhập tên, giá, ảnh, nhấn List" [call (solid arrow)]
4. :ShopItemsPage -> :shop.api : "createShopItem(body)" [call (solid arrow)]
5. :shop.api -> :ShopController : "createItem(dto)" [call (solid arrow)]
6. :ShopController -> :ShopService : "createItem(dto)" [call (solid arrow)]
7. :ShopService -> :ShopRepository : "findItemByRef(itemType, refId)" [call (solid arrow)]
8. :ShopRepository -> :ShopService : "ShopItem | null" [reply (dashed arrow)]
9. :ShopService -> :ShopRepository : "createItem(item)" [call (solid arrow)]
10. :ShopRepository -> :ShopService : "ShopItem" [reply (dashed arrow)]
11. :ShopService -> :ShopController : "ShopItem" [reply (dashed arrow)]
12. :ShopController -> :shop.api : "ShopItem" [reply (dashed arrow)]
13. :shop.api -> :ShopItemsPage : "ShopItem" [reply (dashed arrow)]
14. :ShopItemsPage -> Quản trị viên : "hiển thị vật phẩm trong danh sách" [reply (dashed arrow)]
15. :ShopService -> :ShopController : "ConflictException" [reply (dashed arrow)]
16. :ShopController -> :shop.api : "409 Conflict" [reply (dashed arrow)]
17. :shop.api -> :ShopItemsPage : "lỗi" [reply (dashed arrow)]
18. :ShopItemsPage -> Quản trị viên : "báo vật phẩm đã được niêm yết" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Vật phẩm chưa niêm yết] contains messages 9 to 14; operand [Vật phẩm đã niêm yết] contains messages 15 to 18.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 3. Sửa vật phẩm

File nguồn: `seq_quan_ly_cua_hang_3_sua_vat_pham.puml`

```text
Create a UML sequence diagram titled "Sửa vật phẩm".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "nhấn Edit" [call (solid arrow)]
2. :ShopItemsPage -> Quản trị viên : "hiển thị popup Edit (khóa loại và id)" [reply (dashed arrow)]
3. Quản trị viên -> :ShopItemsPage : "đổi tên, giá, ảnh hoặc thứ tự, nhấn Save" [call (solid arrow)]
4. :ShopItemsPage -> :shop.api : "updateShopItem(itemId, body)" [call (solid arrow)]
5. :shop.api -> :ShopController : "updateItem(itemId, dto)" [call (solid arrow)]
6. :ShopController -> :ShopService : "updateItem(itemId, dto)" [call (solid arrow)]
7. :ShopService -> :ShopRepository : "updateItem(itemId, patch)" [call (solid arrow)]
8. :ShopRepository -> :ShopService : "Success" [reply (dashed arrow)]
9. :ShopService -> :ShopController : "ShopItem" [reply (dashed arrow)]
10. :ShopController -> :shop.api : "ShopItem" [reply (dashed arrow)]
11. :shop.api -> :ShopItemsPage : "ShopItem" [reply (dashed arrow)]
12. :ShopItemsPage -> Quản trị viên : "cập nhật vật phẩm trong danh sách" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 4. Ẩn/hiện vật phẩm

File nguồn: `seq_quan_ly_cua_hang_4_an_hien_vat_pham.puml`

```text
Create a UML sequence diagram titled "Ẩn/hiện vật phẩm".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "gạt công tắc mở bán" [call (solid arrow)]
2. :ShopItemsPage -> :shop.api : "updateShopItem(itemId, {isActive})" [call (solid arrow)]
3. :shop.api -> :ShopController : "updateItem(itemId, dto)" [call (solid arrow)]
4. :ShopController -> :ShopService : "updateItem(itemId, dto)" [call (solid arrow)]
5. :ShopService -> :ShopRepository : "updateItem(itemId, patch)" [call (solid arrow)]
6. :ShopRepository -> :ShopService : "Success" [reply (dashed arrow)]
7. :ShopService -> :ShopController : "ShopItem" [reply (dashed arrow)]
8. :ShopController -> :shop.api : "ShopItem" [reply (dashed arrow)]
9. :shop.api -> :ShopItemsPage : "ShopItem" [reply (dashed arrow)]
10. :ShopItemsPage -> Quản trị viên : "cập nhật trạng thái hiển thị trong Shop" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 5. Gỡ vật phẩm

File nguồn: `seq_quan_ly_cua_hang_5_go_vat_pham.puml`

```text
Create a UML sequence diagram titled "Gỡ vật phẩm".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "nhấn icon thùng rác" [call (solid arrow)]
2. :ShopItemsPage -> Quản trị viên : "hiển thị hộp xác nhận Remove item from the Shop?" [reply (dashed arrow)]
3. Quản trị viên -> :ShopItemsPage : "nhấn Remove" [call (solid arrow)]
4. :ShopItemsPage -> :shop.api : "deleteShopItem(itemId)" [call (solid arrow)]
5. :shop.api -> :ShopController : "deleteItem(itemId)" [call (solid arrow)]
6. :ShopController -> :ShopService : "deleteItem(itemId)" [call (solid arrow)]
7. :ShopService -> :ShopRepository : "deleteItem(itemId)" [call (solid arrow)]
8. :ShopRepository -> :ShopService : "Success" [reply (dashed arrow)]
9. :ShopService -> :ShopController : "ShopItem" [reply (dashed arrow)]
10. :ShopController -> :shop.api : "ShopItem" [reply (dashed arrow)]
11. :shop.api -> :ShopItemsPage : "ShopItem" [reply (dashed arrow)]
12. :ShopItemsPage -> Quản trị viên : "xóa vật phẩm khỏi danh sách" [reply (dashed arrow)]
13. Quản trị viên -> :ShopItemsPage : "nhấn Cancel" [call (solid arrow)]
14. :ShopItemsPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Nhấn Remove] contains messages 3 to 12; operand [Nhấn Cancel] contains messages 13 to 14.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 6. Tặng vật phẩm

File nguồn: `seq_quan_ly_cua_hang_6_tang_vat_pham.puml`

```text
Create a UML sequence diagram titled "Tặng vật phẩm".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":UsersRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "nhấn icon quà tặng" [call (solid arrow)]
2. :ShopItemsPage -> Quản trị viên : "hiển thị popup Gift" [reply (dashed arrow)]
3. Quản trị viên -> :ShopItemsPage : "tìm và chọn người dùng, nhấn Gift" [call (solid arrow)]
4. :ShopItemsPage -> :shop.api : "grantShopItem(itemId, uid)" [call (solid arrow)]
5. :shop.api -> :ShopController : "grantItem(itemId, uid)" [call (solid arrow)]
6. :ShopController -> :ShopService : "grantItem(itemId, uid)" [call (solid arrow)]
7. :ShopService -> :UsersRepository : "findByUid(uid)" [call (solid arrow)]
8. :UsersRepository -> :ShopService : "User" [reply (dashed arrow)]
9. :ShopService -> :UsersRepository : "unlockCollectible(uid, field, value)" [call (solid arrow)]
10. :UsersRepository -> :ShopService : "Success" [reply (dashed arrow)]
11. :ShopService -> :ShopController : "ShopItem" [reply (dashed arrow)]
12. :ShopController -> :shop.api : "ShopItem" [reply (dashed arrow)]
13. :shop.api -> :ShopItemsPage : "ShopItem" [reply (dashed arrow)]
14. :ShopItemsPage -> Quản trị viên : "thông báo đã tặng vật phẩm" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 7. Xem lịch sử mua hàng

File nguồn: `seq_quan_ly_cua_hang_7_xem_lich_su_mua.puml`

```text
Create a UML sequence diagram titled "Xem lịch sử mua hàng".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopHistoryPage"
3. Control ":shop.api"
4. Control ":ShopController"
5. Control ":ShopService"
6. Entity ":ShopRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopHistoryPage : "chọn menu Purchase history, chọn bộ lọc" [call (solid arrow)]
2. :ShopHistoryPage -> :shop.api : "listShopPurchases(filter)" [call (solid arrow)]
3. :shop.api -> :ShopController : "listPurchasesAsAdmin(query)" [call (solid arrow)]
4. :ShopController -> :ShopService : "listAllPurchases(filter)" [call (solid arrow)]
5. :ShopService -> :ShopRepository : "listAllPurchases(1000)" [call (solid arrow)]
6. :ShopRepository -> :ShopService : "ShopPurchase[]" [reply (dashed arrow)]
7. :ShopService -> :ShopService : "lọc theo người mua, loại, ngày; tính thống kê" [call (solid arrow), self message]
8. :ShopService -> :ShopController : "{rows, summary}" [reply (dashed arrow)]
9. :ShopController -> :shop.api : "{rows, summary}" [reply (dashed arrow)]
10. :shop.api -> :ShopHistoryPage : "{rows, summary}" [reply (dashed arrow)]
11. :ShopHistoryPage -> Quản trị viên : "hiển thị lịch sử mua và thống kê" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

## Quản lý gói nạp

### 1. Xem danh sách gói nạp

File nguồn: `seq_quan_ly_goi_nap_1_xem_danh_sach.puml`

```text
Create a UML sequence diagram titled "Xem danh sách gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "mở menu Shop, chọn Top-up packages" [call (solid arrow)]
2. :TopupPackagesPage -> :topup.api : "listTopupPackages()" [call (solid arrow)]
3. :topup.api -> :TopupController : "listPackagesAsAdmin()" [call (solid arrow)]
4. :TopupController -> :TopupService : "listAllPackages()" [call (solid arrow)]
5. :TopupService -> :TopupRepository : "listAllPackages()" [call (solid arrow)]
6. :TopupRepository -> :TopupService : "TopupPackage[]" [reply (dashed arrow)]
7. :TopupService -> :TopupController : "TopupPackage[]" [reply (dashed arrow)]
8. :TopupController -> :topup.api : "TopupPackage[]" [reply (dashed arrow)]
9. :topup.api -> :TopupPackagesPage : "TopupPackage[]" [reply (dashed arrow)]
10. :TopupPackagesPage -> Quản trị viên : "hiển thị danh sách gói nạp" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 2. Thêm gói nạp

File nguồn: `seq_quan_ly_goi_nap_2_them_goi.puml`

```text
Create a UML sequence diagram titled "Thêm gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "nhấn Add package" [call (solid arrow)]
2. :TopupPackagesPage -> Quản trị viên : "hiển thị popup Add package" [reply (dashed arrow)]
3. Quản trị viên -> :TopupPackagesPage : "nhập tên gói, số Astrite, giá VND, nhấn Add" [call (solid arrow)]
4. :TopupPackagesPage -> :topup.api : "createTopupPackage(body)" [call (solid arrow)]
5. :topup.api -> :TopupController : "createPackage(dto)" [call (solid arrow)]
6. :TopupController -> :TopupService : "createPackage(dto)" [call (solid arrow)]
7. :TopupService -> :TopupRepository : "createPackage(pkg)" [call (solid arrow)]
8. :TopupRepository -> :TopupService : "TopupPackage" [reply (dashed arrow)]
9. :TopupService -> :TopupController : "TopupPackage" [reply (dashed arrow)]
10. :TopupController -> :topup.api : "TopupPackage" [reply (dashed arrow)]
11. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply (dashed arrow)]
12. :TopupPackagesPage -> Quản trị viên : "hiển thị gói mới trong danh sách" [reply (dashed arrow)]
13. :TopupPackagesPage -> Quản trị viên : "báo lỗi trên form" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Dữ liệu hợp lệ] contains messages 4 to 12; operand [Dữ liệu không hợp lệ] contains message 13.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 3. Sửa gói nạp

File nguồn: `seq_quan_ly_goi_nap_3_sua_goi.puml`

```text
Create a UML sequence diagram titled "Sửa gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "nhấn Edit" [call (solid arrow)]
2. :TopupPackagesPage -> Quản trị viên : "hiển thị popup Edit" [reply (dashed arrow)]
3. Quản trị viên -> :TopupPackagesPage : "đổi tên, số Astrite, giá hoặc thứ tự, nhấn Save" [call (solid arrow)]
4. :TopupPackagesPage -> :topup.api : "updateTopupPackage(packageId, body)" [call (solid arrow)]
5. :topup.api -> :TopupController : "updatePackage(packageId, dto)" [call (solid arrow)]
6. :TopupController -> :TopupService : "updatePackage(packageId, dto)" [call (solid arrow)]
7. :TopupService -> :TopupRepository : "updatePackage(packageId, patch)" [call (solid arrow)]
8. :TopupRepository -> :TopupService : "Success" [reply (dashed arrow)]
9. :TopupService -> :TopupController : "TopupPackage" [reply (dashed arrow)]
10. :TopupController -> :topup.api : "TopupPackage" [reply (dashed arrow)]
11. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply (dashed arrow)]
12. :TopupPackagesPage -> Quản trị viên : "cập nhật gói trong danh sách" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 4. Ẩn/hiện gói nạp

File nguồn: `seq_quan_ly_goi_nap_4_an_hien_goi.puml`

```text
Create a UML sequence diagram titled "Ẩn/hiện gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "gạt công tắc hiển thị" [call (solid arrow)]
2. :TopupPackagesPage -> :topup.api : "updateTopupPackage(packageId, {isActive})" [call (solid arrow)]
3. :topup.api -> :TopupController : "updatePackage(packageId, dto)" [call (solid arrow)]
4. :TopupController -> :TopupService : "updatePackage(packageId, dto)" [call (solid arrow)]
5. :TopupService -> :TopupRepository : "updatePackage(packageId, patch)" [call (solid arrow)]
6. :TopupRepository -> :TopupService : "Success" [reply (dashed arrow)]
7. :TopupService -> :TopupController : "TopupPackage" [reply (dashed arrow)]
8. :TopupController -> :topup.api : "TopupPackage" [reply (dashed arrow)]
9. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply (dashed arrow)]
10. :TopupPackagesPage -> Quản trị viên : "cập nhật trạng thái hiển thị gói trong app" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 5. Xóa gói nạp

File nguồn: `seq_quan_ly_goi_nap_5_xoa_goi.puml`

```text
Create a UML sequence diagram titled "Xóa gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "nhấn icon thùng rác" [call (solid arrow)]
2. :TopupPackagesPage -> Quản trị viên : "hiển thị hộp xác nhận Delete package?" [reply (dashed arrow)]
3. Quản trị viên -> :TopupPackagesPage : "nhấn Delete" [call (solid arrow)]
4. :TopupPackagesPage -> :topup.api : "deleteTopupPackage(packageId)" [call (solid arrow)]
5. :topup.api -> :TopupController : "deletePackage(packageId)" [call (solid arrow)]
6. :TopupController -> :TopupService : "deletePackage(packageId)" [call (solid arrow)]
7. :TopupService -> :TopupRepository : "deletePackage(packageId)" [call (solid arrow)]
8. :TopupRepository -> :TopupService : "Success" [reply (dashed arrow)]
9. :TopupService -> :TopupController : "TopupPackage" [reply (dashed arrow)]
10. :TopupController -> :topup.api : "TopupPackage" [reply (dashed arrow)]
11. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply (dashed arrow)]
12. :TopupPackagesPage -> Quản trị viên : "xóa gói khỏi danh sách" [reply (dashed arrow)]
13. Quản trị viên -> :TopupPackagesPage : "nhấn Cancel" [call (solid arrow)]
14. :TopupPackagesPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply (dashed arrow)]

Add ONE combined fragment "alt" covering all lifelines, with 2 operands: operand [Nhấn Delete] contains messages 3 to 12; operand [Nhấn Cancel] contains messages 13 to 14.

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

### 6. Xem lịch sử nạp và doanh thu

File nguồn: `seq_quan_ly_goi_nap_6_xem_lich_su_nap.puml`

```text
Create a UML sequence diagram titled "Xem lịch sử nạp và doanh thu".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupHistoryPage"
3. Control ":topup.api"
4. Control ":TopupController"
5. Control ":TopupService"
6. Entity ":TopupRepository"

Use EXACTLY these messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not add, remove, merge, reorder or translate messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupHistoryPage : "chọn menu Top-up history, chọn bộ lọc" [call (solid arrow)]
2. :TopupHistoryPage -> :topup.api : "listTopupOrders(filter)" [call (solid arrow)]
3. :topup.api -> :TopupController : "listOrdersAsAdmin(query)" [call (solid arrow)]
4. :TopupController -> :TopupService : "listAllOrders(filter)" [call (solid arrow)]
5. :TopupService -> :TopupRepository : "listAllOrders()" [call (solid arrow)]
6. :TopupRepository -> :TopupService : "TopupOrder[]" [reply (dashed arrow)]
7. :TopupService -> :TopupService : "lọc theo người dùng, trạng thái, ngày; tính doanh thu đơn PAID" [call (solid arrow), self message]
8. :TopupService -> :TopupController : "{rows, summary}" [reply (dashed arrow)]
9. :TopupController -> :topup.api : "{rows, summary}" [reply (dashed arrow)]
10. :topup.api -> :TopupHistoryPage : "{rows, summary}" [reply (dashed arrow)]
11. :TopupHistoryPage -> Quản trị viên : "hiển thị lịch sử nạp và doanh thu" [reply (dashed arrow)]

Show an activation bar on a lifeline from the call it receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```
