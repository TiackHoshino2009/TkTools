**TkTools – Plugin Spigot cho Minecraft**

TkTools là một plugin, cung cấp một bộ công cụ đa năng giúp người chơi và quản trị viên thực hiện các tác vụ nhanh hơn và hiệu quả hơn.

| Tính năng | Mô tả |
|-----------|-------|
| **3×3 Mining** | Khi dùng cục khóa (pickaxe) được cấu hình, một khối trung tâm sẽ khai thác đồng thời 9 khối (3×3) xung quanh, tăng tốc độ thu thập tài nguyên. |
| **Tree Chopping** | Dùng rìu (axe) bật tính năng, chặt một khối gỗ sẽ làm toàn bộ cây rụng ngay lập tức, giảm thời gian thu thập gỗ. |
| **Water Draining** | Công cụ có thể hút và loại bỏ nước trong khu vực được chỉ định, hữu ích cho việc dọn sạch mưa, bể nước hoặc tạo khu vực khô ráo. |
| **MultiTool abilities** | Một công cụ duy nhất kết hợp các chức năng trên (khoe, chặt, khai thác, hút nước) và có thể chuyển đổi chế độ qua lệnh hoặc cấu hình. |
| **Countdown‑ticks: 20** | Thời gian hoạt động của các khả năng giảm 1 tick mỗi 20 tick (≈ 1 giây) khi item chỉ ở trong inventory người chơi, giúp kiểm soát tốc độ sử dụng. |
| **Command** | `/tool give` – cấp công cụ cho người chơi; <br>`/tool reload` – tải lại cấu hình plugin mà không cần khởi động lại máy chủ. |
| **Permissions** | <ul><li>`tktool.admin` – quyền quản trị (mặc định OP), bao gồm các quyền con `tktool.command.give` và `tktool.command.reload`.</li><li>`tktool.command.give` – cho phép dùng lệnh `/tool give` (mặc định OP).</li><li>`tktool.command.reload` – cho phép dùng lệnh `/tool reload` (mặc định OP).</li></ul> |
| **Depend** | Yêu cầu thư viện **NBTAPI** để thao tác với dữ liệu NBT của các item. |

TkTools được thiết kế để giảm thiểu thời gian và công sức trong các công việc thường ngày (khai thác, chặt cây, hút nước) và cung cấp các lệnh quản trị đơn giản, giúp quản trị viên dễ dàng cài đặt, cấp phát và cập nhật plugin trên máy chủ.
