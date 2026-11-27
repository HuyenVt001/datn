# Prompt gộp cho Visual Paradigm — mỗi UC một biểu đồ

Sinh tự động từ `docs/diagrams/seq_*.puml`. Phần đầu là luồng mở trang chung; mỗi chức năng là một nhánh của khối **alt** lớn, nhánh nào có rẽ nhánh thì có thêm alt lồng bên trong.

Cách dùng: Tools → AI Diagram Generation → **Sequence Diagram** → dán **một** khối prompt → Generate. Biểu đồ dài, AI dễ bỏ sót — đối chiếu lại theo số thứ tự message và sửa tay phần lệch.

## Quản lý khung ảnh (71 message)

```text
Create ONE UML sequence diagram titled "Quản lý khung ảnh".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":FramesPage"
3. Control ":frames.api"
4. Control ":FramesController"
5. Control ":FramesService"
6. Entity ":FramesRepository"
7. Entity ":UsersRepository"

Use EXACTLY these 71 messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not translate, add, remove, merge or reorder messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :FramesPage : "mở trang Frames" [call, solid]
2. :FramesPage -> :frames.api : "listFrames()" [call, solid]
3. :frames.api -> :FramesController : "listAsAdmin()" [call, solid]
4. :FramesController -> :FramesService : "listAll()" [call, solid]
5. :FramesService -> :FramesRepository : "list()" [call, solid]
6. :FramesRepository -> :FramesService : "Frame[]" [reply, dashed]
7. :FramesService -> :FramesController : "Frame[]" [reply, dashed]
8. :FramesController -> :frames.api : "Frame[]" [reply, dashed]
9. :frames.api -> :FramesPage : "Frame[]" [reply, dashed]
10. :FramesPage -> Quản trị viên : "hiển thị danh sách khung ảnh" [reply, dashed]
11. Quản trị viên -> :FramesPage : "nhấn Add frame" [call, solid]
12. :FramesPage -> Quản trị viên : "hiển thị popup Add frame" [reply, dashed]
13. Quản trị viên -> :FramesPage : "nhập tên, chọn điều kiện mở khóa, tải ảnh PNG, nhấn Add" [call, solid]
14. :FramesPage -> :frames.api : "createFrame(body)" [call, solid]
15. :frames.api -> :FramesController : "create(dto)" [call, solid]
16. :FramesController -> :FramesService : "create(dto)" [call, solid]
17. :FramesService -> :FramesRepository : "create(frame)" [call, solid]
18. :FramesRepository -> :FramesService : "Frame" [reply, dashed]
19. :FramesService -> :FramesController : "Frame" [reply, dashed]
20. :FramesController -> :frames.api : "Frame" [reply, dashed]
21. :frames.api -> :FramesPage : "Frame" [reply, dashed]
22. :FramesPage -> Quản trị viên : "hiển thị khung mới trong danh sách" [reply, dashed]
23. :FramesPage -> Quản trị viên : "báo lỗi trên form" [reply, dashed]
24. Quản trị viên -> :FramesPage : "nhấn icon chỉnh sửa" [call, solid]
25. :FramesPage -> Quản trị viên : "hiển thị popup Edit frame" [reply, dashed]
26. Quản trị viên -> :FramesPage : "đổi tên, điều kiện hoặc ảnh, nhấn Save" [call, solid]
27. :FramesPage -> :frames.api : "updateFrame(frameId, body)" [call, solid]
28. :frames.api -> :FramesController : "update(frameId, dto)" [call, solid]
29. :FramesController -> :FramesService : "update(frameId, dto)" [call, solid]
30. :FramesService -> :FramesRepository : "update(frameId, patch)" [call, solid]
31. :FramesRepository -> :FramesService : "Success" [reply, dashed]
32. :FramesService -> :FramesController : "Frame" [reply, dashed]
33. :FramesController -> :frames.api : "Frame" [reply, dashed]
34. :frames.api -> :FramesPage : "Frame" [reply, dashed]
35. :FramesPage -> Quản trị viên : "cập nhật khung trong danh sách" [reply, dashed]
36. Quản trị viên -> :FramesPage : "nhấn icon thùng rác" [call, solid]
37. :FramesPage -> Quản trị viên : "hiển thị hộp xác nhận Delete this frame?" [reply, dashed]
38. Quản trị viên -> :FramesPage : "nhấn Delete" [call, solid]
39. :FramesPage -> :frames.api : "deleteFrame(frameId)" [call, solid]
40. :frames.api -> :FramesController : "delete(frameId)" [call, solid]
41. :FramesController -> :FramesService : "delete(frameId)" [call, solid]
42. :FramesService -> :FramesRepository : "delete(frameId)" [call, solid]
43. :FramesRepository -> :FramesService : "Success" [reply, dashed]
44. :FramesService -> :FramesController : "Success" [reply, dashed]
45. :FramesController -> :frames.api : "Success" [reply, dashed]
46. :frames.api -> :FramesPage : "Success" [reply, dashed]
47. :FramesPage -> Quản trị viên : "xóa khung khỏi danh sách" [reply, dashed]
48. Quản trị viên -> :FramesPage : "nhấn Cancel" [call, solid]
49. :FramesPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply, dashed]
50. Quản trị viên -> :FramesPage : "nhấn icon Who owns this?" [call, solid]
51. :FramesPage -> :frames.api : "listFrameOwners(frameId)" [call, solid]
52. :frames.api -> :FramesController : "listOwners(frameId)" [call, solid]
53. :FramesController -> :FramesService : "listOwners(frameId)" [call, solid]
54. :FramesService -> :UsersRepository : "listByUnlockedFrame(frameId)" [call, solid]
55. :UsersRepository -> :FramesService : "User[]" [reply, dashed]
56. :FramesService -> :FramesController : "{frame, owners}" [reply, dashed]
57. :FramesController -> :frames.api : "{frame, owners}" [reply, dashed]
58. :frames.api -> :FramesPage : "{frame, owners}" [reply, dashed]
59. :FramesPage -> Quản trị viên : "hiển thị danh sách người sở hữu" [reply, dashed]
60. Quản trị viên -> :FramesPage : "nhấn icon quà tặng" [call, solid]
61. :FramesPage -> Quản trị viên : "hiển thị popup Grant frame" [reply, dashed]
62. Quản trị viên -> :FramesPage : "tìm và chọn người dùng, nhấn Grant frame" [call, solid]
63. :FramesPage -> :frames.api : "grantFrame(frameId, uid)" [call, solid]
64. :frames.api -> :FramesController : "grant(frameId, uid)" [call, solid]
65. :FramesController -> :FramesService : "unlockForUser(uid, frameId)" [call, solid]
66. :FramesService -> :UsersRepository : "unlockFrame(uid, frameId)" [call, solid]
67. :UsersRepository -> :FramesService : "Success" [reply, dashed]
68. :FramesService -> :FramesController : "Success" [reply, dashed]
69. :FramesController -> :frames.api : "Success" [reply, dashed]
70. :frames.api -> :FramesPage : "Success" [reply, dashed]
71. :FramesPage -> Quản trị viên : "thông báo đã tặng khung" [reply, dashed]

Combined fragments (all of type "alt", each covering all lifelines):
- Main alt with 5 operands: operand [Thêm khung ảnh] = messages 11-23; operand [Sửa khung ảnh] = messages 24-35; operand [Xóa khung ảnh] = messages 36-49; operand [Xem người sở hữu khung] = messages 50-59; operand [Tặng khung] = messages 60-71.
- Nested alt INSIDE operand [Thêm khung ảnh] of the main alt: operand [Dữ liệu hợp lệ] = messages 14-22; operand [Thiếu tên hoặc ảnh] = message 23.
- Nested alt INSIDE operand [Xóa khung ảnh] of the main alt: operand [Nhấn Delete] = messages 38-47; operand [Nhấn Cancel] = messages 48-49.

Show activation bars from each call a lifeline receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

## Quản lý cửa hàng (89 message)

```text
Create ONE UML sequence diagram titled "Quản lý cửa hàng".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":ShopItemsPage"
3. Boundary ":ShopHistoryPage"
4. Control ":shop.api"
5. Control ":ShopController"
6. Control ":ShopService"
7. Entity ":ShopRepository"
8. Entity ":UsersRepository"

Use EXACTLY these 89 messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not translate, add, remove, merge or reorder messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :ShopItemsPage : "mở menu Shop, chọn Items" [call, solid]
2. :ShopItemsPage -> :shop.api : "listShopItems()" [call, solid]
3. :shop.api -> :ShopController : "listItemsAsAdmin()" [call, solid]
4. :ShopController -> :ShopService : "listAllItems()" [call, solid]
5. :ShopService -> :ShopRepository : "listAllItems()" [call, solid]
6. :ShopRepository -> :ShopService : "ShopItem[]" [reply, dashed]
7. :ShopService -> :ShopController : "ShopItem[]" [reply, dashed]
8. :ShopController -> :shop.api : "ShopItem[]" [reply, dashed]
9. :shop.api -> :ShopItemsPage : "ShopItem[]" [reply, dashed]
10. :ShopItemsPage -> Quản trị viên : "hiển thị danh sách vật phẩm" [reply, dashed]
11. Quản trị viên -> :ShopItemsPage : "nhấn List item" [call, solid]
12. :ShopItemsPage -> Quản trị viên : "hiển thị popup List a new item" [reply, dashed]
13. Quản trị viên -> :ShopItemsPage : "chọn loại và id, nhập tên, giá, ảnh, nhấn List" [call, solid]
14. :ShopItemsPage -> :shop.api : "createShopItem(body)" [call, solid]
15. :shop.api -> :ShopController : "createItem(dto)" [call, solid]
16. :ShopController -> :ShopService : "createItem(dto)" [call, solid]
17. :ShopService -> :ShopRepository : "findItemByRef(itemType, refId)" [call, solid]
18. :ShopRepository -> :ShopService : "ShopItem | null" [reply, dashed]
19. :ShopService -> :ShopRepository : "createItem(item)" [call, solid]
20. :ShopRepository -> :ShopService : "ShopItem" [reply, dashed]
21. :ShopService -> :ShopController : "ShopItem" [reply, dashed]
22. :ShopController -> :shop.api : "ShopItem" [reply, dashed]
23. :shop.api -> :ShopItemsPage : "ShopItem" [reply, dashed]
24. :ShopItemsPage -> Quản trị viên : "hiển thị vật phẩm trong danh sách" [reply, dashed]
25. :ShopService -> :ShopController : "ConflictException" [reply, dashed]
26. :ShopController -> :shop.api : "409 Conflict" [reply, dashed]
27. :shop.api -> :ShopItemsPage : "lỗi" [reply, dashed]
28. :ShopItemsPage -> Quản trị viên : "báo vật phẩm đã được niêm yết" [reply, dashed]
29. Quản trị viên -> :ShopItemsPage : "nhấn Edit" [call, solid]
30. :ShopItemsPage -> Quản trị viên : "hiển thị popup Edit (khóa loại và id)" [reply, dashed]
31. Quản trị viên -> :ShopItemsPage : "đổi tên, giá, ảnh hoặc thứ tự, nhấn Save" [call, solid]
32. :ShopItemsPage -> :shop.api : "updateShopItem(itemId, body)" [call, solid]
33. :shop.api -> :ShopController : "updateItem(itemId, dto)" [call, solid]
34. :ShopController -> :ShopService : "updateItem(itemId, dto)" [call, solid]
35. :ShopService -> :ShopRepository : "updateItem(itemId, patch)" [call, solid]
36. :ShopRepository -> :ShopService : "Success" [reply, dashed]
37. :ShopService -> :ShopController : "ShopItem" [reply, dashed]
38. :ShopController -> :shop.api : "ShopItem" [reply, dashed]
39. :shop.api -> :ShopItemsPage : "ShopItem" [reply, dashed]
40. :ShopItemsPage -> Quản trị viên : "cập nhật vật phẩm trong danh sách" [reply, dashed]
41. Quản trị viên -> :ShopItemsPage : "gạt công tắc mở bán" [call, solid]
42. :ShopItemsPage -> :shop.api : "updateShopItem(itemId, {isActive})" [call, solid]
43. :shop.api -> :ShopController : "updateItem(itemId, dto)" [call, solid]
44. :ShopController -> :ShopService : "updateItem(itemId, dto)" [call, solid]
45. :ShopService -> :ShopRepository : "updateItem(itemId, patch)" [call, solid]
46. :ShopRepository -> :ShopService : "Success" [reply, dashed]
47. :ShopService -> :ShopController : "ShopItem" [reply, dashed]
48. :ShopController -> :shop.api : "ShopItem" [reply, dashed]
49. :shop.api -> :ShopItemsPage : "ShopItem" [reply, dashed]
50. :ShopItemsPage -> Quản trị viên : "cập nhật trạng thái hiển thị trong Shop" [reply, dashed]
51. Quản trị viên -> :ShopItemsPage : "nhấn icon thùng rác" [call, solid]
52. :ShopItemsPage -> Quản trị viên : "hiển thị hộp xác nhận Remove item from the Shop?" [reply, dashed]
53. Quản trị viên -> :ShopItemsPage : "nhấn Remove" [call, solid]
54. :ShopItemsPage -> :shop.api : "deleteShopItem(itemId)" [call, solid]
55. :shop.api -> :ShopController : "deleteItem(itemId)" [call, solid]
56. :ShopController -> :ShopService : "deleteItem(itemId)" [call, solid]
57. :ShopService -> :ShopRepository : "deleteItem(itemId)" [call, solid]
58. :ShopRepository -> :ShopService : "Success" [reply, dashed]
59. :ShopService -> :ShopController : "ShopItem" [reply, dashed]
60. :ShopController -> :shop.api : "ShopItem" [reply, dashed]
61. :shop.api -> :ShopItemsPage : "ShopItem" [reply, dashed]
62. :ShopItemsPage -> Quản trị viên : "xóa vật phẩm khỏi danh sách" [reply, dashed]
63. Quản trị viên -> :ShopItemsPage : "nhấn Cancel" [call, solid]
64. :ShopItemsPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply, dashed]
65. Quản trị viên -> :ShopItemsPage : "nhấn icon quà tặng" [call, solid]
66. :ShopItemsPage -> Quản trị viên : "hiển thị popup Gift" [reply, dashed]
67. Quản trị viên -> :ShopItemsPage : "tìm và chọn người dùng, nhấn Gift" [call, solid]
68. :ShopItemsPage -> :shop.api : "grantShopItem(itemId, uid)" [call, solid]
69. :shop.api -> :ShopController : "grantItem(itemId, uid)" [call, solid]
70. :ShopController -> :ShopService : "grantItem(itemId, uid)" [call, solid]
71. :ShopService -> :UsersRepository : "findByUid(uid)" [call, solid]
72. :UsersRepository -> :ShopService : "User" [reply, dashed]
73. :ShopService -> :UsersRepository : "unlockCollectible(uid, field, value)" [call, solid]
74. :UsersRepository -> :ShopService : "Success" [reply, dashed]
75. :ShopService -> :ShopController : "ShopItem" [reply, dashed]
76. :ShopController -> :shop.api : "ShopItem" [reply, dashed]
77. :shop.api -> :ShopItemsPage : "ShopItem" [reply, dashed]
78. :ShopItemsPage -> Quản trị viên : "thông báo đã tặng vật phẩm" [reply, dashed]
79. Quản trị viên -> :ShopHistoryPage : "chọn menu Purchase history, chọn bộ lọc" [call, solid]
80. :ShopHistoryPage -> :shop.api : "listShopPurchases(filter)" [call, solid]
81. :shop.api -> :ShopController : "listPurchasesAsAdmin(query)" [call, solid]
82. :ShopController -> :ShopService : "listAllPurchases(filter)" [call, solid]
83. :ShopService -> :ShopRepository : "listAllPurchases(1000)" [call, solid]
84. :ShopRepository -> :ShopService : "ShopPurchase[]" [reply, dashed]
85. :ShopService -> :ShopService : "lọc theo người mua, loại, ngày; tính thống kê" [call, solid]
86. :ShopService -> :ShopController : "{rows, summary}" [reply, dashed]
87. :ShopController -> :shop.api : "{rows, summary}" [reply, dashed]
88. :shop.api -> :ShopHistoryPage : "{rows, summary}" [reply, dashed]
89. :ShopHistoryPage -> Quản trị viên : "hiển thị lịch sử mua và thống kê" [reply, dashed]

Combined fragments (all of type "alt", each covering all lifelines):
- Main alt with 6 operands: operand [Thêm vật phẩm] = messages 11-28; operand [Sửa vật phẩm] = messages 29-40; operand [Ẩn/hiện vật phẩm] = messages 41-50; operand [Gỡ vật phẩm] = messages 51-64; operand [Tặng vật phẩm] = messages 65-78; operand [Xem lịch sử mua hàng] = messages 79-89.
- Nested alt INSIDE operand [Thêm vật phẩm] of the main alt: operand [Vật phẩm chưa niêm yết] = messages 19-24; operand [Vật phẩm đã niêm yết] = messages 25-28.
- Nested alt INSIDE operand [Gỡ vật phẩm] of the main alt: operand [Nhấn Remove] = messages 53-62; operand [Nhấn Cancel] = messages 63-64.

Show activation bars from each call a lifeline receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```

## Quản lý gói nạp (70 message)

```text
Create ONE UML sequence diagram titled "Quản lý gói nạp".

Use EXACTLY these lifelines from left to right. Do not add, remove or rename any lifeline:
1. Actor "Quản trị viên"
2. Boundary ":TopupPackagesPage"
3. Boundary ":TopupHistoryPage"
4. Control ":topup.api"
5. Control ":TopupController"
6. Control ":TopupService"
7. Entity ":TopupRepository"

Use EXACTLY these 70 messages in this order. Keep every label text exactly as written (Vietnamese text and method names). Do not translate, add, remove, merge or reorder messages. Number the messages 1, 2, 3...
1. Quản trị viên -> :TopupPackagesPage : "mở menu Shop, chọn Top-up packages" [call, solid]
2. :TopupPackagesPage -> :topup.api : "listTopupPackages()" [call, solid]
3. :topup.api -> :TopupController : "listPackagesAsAdmin()" [call, solid]
4. :TopupController -> :TopupService : "listAllPackages()" [call, solid]
5. :TopupService -> :TopupRepository : "listAllPackages()" [call, solid]
6. :TopupRepository -> :TopupService : "TopupPackage[]" [reply, dashed]
7. :TopupService -> :TopupController : "TopupPackage[]" [reply, dashed]
8. :TopupController -> :topup.api : "TopupPackage[]" [reply, dashed]
9. :topup.api -> :TopupPackagesPage : "TopupPackage[]" [reply, dashed]
10. :TopupPackagesPage -> Quản trị viên : "hiển thị danh sách gói nạp" [reply, dashed]
11. Quản trị viên -> :TopupPackagesPage : "nhấn Add package" [call, solid]
12. :TopupPackagesPage -> Quản trị viên : "hiển thị popup Add package" [reply, dashed]
13. Quản trị viên -> :TopupPackagesPage : "nhập tên gói, số Astrite, giá VND, nhấn Add" [call, solid]
14. :TopupPackagesPage -> :topup.api : "createTopupPackage(body)" [call, solid]
15. :topup.api -> :TopupController : "createPackage(dto)" [call, solid]
16. :TopupController -> :TopupService : "createPackage(dto)" [call, solid]
17. :TopupService -> :TopupRepository : "createPackage(pkg)" [call, solid]
18. :TopupRepository -> :TopupService : "TopupPackage" [reply, dashed]
19. :TopupService -> :TopupController : "TopupPackage" [reply, dashed]
20. :TopupController -> :topup.api : "TopupPackage" [reply, dashed]
21. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply, dashed]
22. :TopupPackagesPage -> Quản trị viên : "hiển thị gói mới trong danh sách" [reply, dashed]
23. :TopupPackagesPage -> Quản trị viên : "báo lỗi trên form" [reply, dashed]
24. Quản trị viên -> :TopupPackagesPage : "nhấn Edit" [call, solid]
25. :TopupPackagesPage -> Quản trị viên : "hiển thị popup Edit" [reply, dashed]
26. Quản trị viên -> :TopupPackagesPage : "đổi tên, số Astrite, giá hoặc thứ tự, nhấn Save" [call, solid]
27. :TopupPackagesPage -> :topup.api : "updateTopupPackage(packageId, body)" [call, solid]
28. :topup.api -> :TopupController : "updatePackage(packageId, dto)" [call, solid]
29. :TopupController -> :TopupService : "updatePackage(packageId, dto)" [call, solid]
30. :TopupService -> :TopupRepository : "updatePackage(packageId, patch)" [call, solid]
31. :TopupRepository -> :TopupService : "Success" [reply, dashed]
32. :TopupService -> :TopupController : "TopupPackage" [reply, dashed]
33. :TopupController -> :topup.api : "TopupPackage" [reply, dashed]
34. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply, dashed]
35. :TopupPackagesPage -> Quản trị viên : "cập nhật gói trong danh sách" [reply, dashed]
36. Quản trị viên -> :TopupPackagesPage : "gạt công tắc hiển thị" [call, solid]
37. :TopupPackagesPage -> :topup.api : "updateTopupPackage(packageId, {isActive})" [call, solid]
38. :topup.api -> :TopupController : "updatePackage(packageId, dto)" [call, solid]
39. :TopupController -> :TopupService : "updatePackage(packageId, dto)" [call, solid]
40. :TopupService -> :TopupRepository : "updatePackage(packageId, patch)" [call, solid]
41. :TopupRepository -> :TopupService : "Success" [reply, dashed]
42. :TopupService -> :TopupController : "TopupPackage" [reply, dashed]
43. :TopupController -> :topup.api : "TopupPackage" [reply, dashed]
44. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply, dashed]
45. :TopupPackagesPage -> Quản trị viên : "cập nhật trạng thái hiển thị gói trong app" [reply, dashed]
46. Quản trị viên -> :TopupPackagesPage : "nhấn icon thùng rác" [call, solid]
47. :TopupPackagesPage -> Quản trị viên : "hiển thị hộp xác nhận Delete package?" [reply, dashed]
48. Quản trị viên -> :TopupPackagesPage : "nhấn Delete" [call, solid]
49. :TopupPackagesPage -> :topup.api : "deleteTopupPackage(packageId)" [call, solid]
50. :topup.api -> :TopupController : "deletePackage(packageId)" [call, solid]
51. :TopupController -> :TopupService : "deletePackage(packageId)" [call, solid]
52. :TopupService -> :TopupRepository : "deletePackage(packageId)" [call, solid]
53. :TopupRepository -> :TopupService : "Success" [reply, dashed]
54. :TopupService -> :TopupController : "TopupPackage" [reply, dashed]
55. :TopupController -> :topup.api : "TopupPackage" [reply, dashed]
56. :topup.api -> :TopupPackagesPage : "TopupPackage" [reply, dashed]
57. :TopupPackagesPage -> Quản trị viên : "xóa gói khỏi danh sách" [reply, dashed]
58. Quản trị viên -> :TopupPackagesPage : "nhấn Cancel" [call, solid]
59. :TopupPackagesPage -> Quản trị viên : "đóng hộp xác nhận, giữ nguyên danh sách" [reply, dashed]
60. Quản trị viên -> :TopupHistoryPage : "chọn menu Top-up history, chọn bộ lọc" [call, solid]
61. :TopupHistoryPage -> :topup.api : "listTopupOrders(filter)" [call, solid]
62. :topup.api -> :TopupController : "listOrdersAsAdmin(query)" [call, solid]
63. :TopupController -> :TopupService : "listAllOrders(filter)" [call, solid]
64. :TopupService -> :TopupRepository : "listAllOrders()" [call, solid]
65. :TopupRepository -> :TopupService : "TopupOrder[]" [reply, dashed]
66. :TopupService -> :TopupService : "lọc theo người dùng, trạng thái, ngày; tính doanh thu đơn PAID" [call, solid]
67. :TopupService -> :TopupController : "{rows, summary}" [reply, dashed]
68. :TopupController -> :topup.api : "{rows, summary}" [reply, dashed]
69. :topup.api -> :TopupHistoryPage : "{rows, summary}" [reply, dashed]
70. :TopupHistoryPage -> Quản trị viên : "hiển thị lịch sử nạp và doanh thu" [reply, dashed]

Combined fragments (all of type "alt", each covering all lifelines):
- Main alt with 5 operands: operand [Thêm gói nạp] = messages 11-23; operand [Sửa gói nạp] = messages 24-35; operand [Ẩn/hiện gói nạp] = messages 36-45; operand [Xóa gói nạp] = messages 46-59; operand [Xem lịch sử nạp và doanh thu] = messages 60-70.
- Nested alt INSIDE operand [Thêm gói nạp] of the main alt: operand [Dữ liệu hợp lệ] = messages 14-22; operand [Dữ liệu không hợp lệ] = message 23.
- Nested alt INSIDE operand [Xóa gói nạp] of the main alt: operand [Nhấn Delete] = messages 48-57; operand [Nhấn Cancel] = messages 58-59.

Show activation bars from each call a lifeline receives until it sends its reply. Do not add any other fragments, notes or lifelines.
```
