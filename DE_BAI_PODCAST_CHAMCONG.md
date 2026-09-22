# CHUYỂN ĐỔI SỐ Ở DGPACK: CÂU CHUYỆN BẮT ĐẦU TỪ CỔNG NHÀ MÁY

*Tài liệu nguồn để NotebookLM tạo podcast cho toàn thể cán bộ công nhân viên Công ty TNHH Dona Green
Pack (DGPack). Giọng kể thân mật, gần gũi, như hai người đồng nghiệp trò chuyện, không thiên về kỹ thuật.*

---

## Gợi ý prompt cho NotebookLM (dán vào phần "Customize" của Audio Overview)

> Hãy làm một tập podcast bằng tiếng Việt, dài khoảng 12 đến 15 phút, dành cho công nhân và nhân viên
> văn phòng của một nhà máy sản xuất bao bì carton. Hai người dẫn trò chuyện tự nhiên, ấm áp, có
> chút hài hước, như đang nói với người trong gia đình. Kể câu chuyện chuyển đổi số bắt đầu từ chiếc
> máy chấm công nhận diện khuôn mặt ở cổng nhà máy: vì sao công ty phải làm, những ngỡ ngàng và lo lắng
> ban đầu của mọi người, rồi lợi ích về sự minh bạch và công bằng khi mọi thứ dựa trên dữ liệu. Nhấn
> mạnh rằng mỗi người sẽ tự biết ngày công, giờ tăng ca của mình mà không phải hỏi ai. Kể về chương
> trình trúng thưởng lon nước ngọt như một niềm vui nhỏ mỗi sáng, và thông điệp công ty là một gia đình.
> Cuối tập dành khoảng 3 phút hướng dẫn thật cụ thể, từng bước, cách đăng ký khuôn mặt, cách đứng trước
> máy, chớp mắt, và làm gì khi máy báo chưa nhận dạng được. Không đi sâu vào thuật ngữ kỹ thuật, không
> nói về API hay cơ sở dữ liệu.

---

## 1. Chuyện bắt đầu từ một câu hỏi rất đời thường

Mỗi tháng đến kỳ lương, ở nhiều nhà máy vẫn có cảnh quen thuộc: công nhân lên phòng nhân sự hỏi "tháng
này em được bao nhiêu công?", "hôm đó em có tăng ca mà sao không thấy?", "em nhớ em đi đúng giờ mà".
Phòng nhân sự lật sổ, dò bảng, đối chiếu chữ ký. Người hỏi thì hồi hộp, người trả lời thì mệt. Không ai
sai, chỉ là không có gì để cùng nhìn vào.

DGPack là công ty sản xuất bao bì carton sóng. Công ty đã có một hệ thống quản trị nội bộ, gọi tắt là
ERP, để theo dõi đơn hàng, sản xuất, kho, và có cả phần chấm công nhà máy. Nhưng phần chấm công đó
giống một cái bảng đẹp mà chưa có ai viết lên. Nó chờ dữ liệu, mà dữ liệu thì vẫn nằm trên giấy, trên
trí nhớ, trên lời nói.

Chuyển đổi số ở DGPack không bắt đầu từ một dự án hoành tráng. Nó bắt đầu từ việc trả lời câu hỏi
"tháng này em được bao nhiêu công?" bằng một con số mà cả công ty và người lao động cùng tin.

---

## 2. Vì sao phải chuyển đổi số, và vì sao là bây giờ

Có ba lý do rất thực tế.

**Thứ nhất, doanh nghiệp cần số liệu thật để quyết định.** Ban lãnh đạo muốn biết mỗi ngày bao nhiêu
người đang lên ca, bao nhiêu người nghỉ, bao nhiêu giờ tăng ca, để xếp ca, nhận đơn hàng, tính chi phí.
Số liệu ghi tay đến cuối tháng mới tổng hợp thì đã muộn để điều chỉnh.

**Thứ hai, người lao động cần được đánh giá công bằng.** Khi mọi thứ dựa vào ghi chép thủ công, người
cẩn thận và người xuề xoà đôi khi được nhìn như nhau. Người đi đúng giờ suốt tháng không có gì để chứng
minh. Dữ liệu ghi tự động từng ngày sẽ nói thay cho họ.

**Thứ ba, đối tác và thị trường đòi hỏi.** Khách hàng lớn ngày càng hỏi nhà cung cấp về cách quản lý
lao động, về minh bạch. Một nhà máy có dữ liệu rõ ràng là một nhà máy đáng tin.

Và vì sao bắt đầu từ cổng? Vì cổng là nơi mọi người đi qua mỗi ngày, hai lần. Đó là điểm chạm tự nhiên
nhất, không tốn thêm phút nào của ai.

---

## 3. Chiếc máy ở cổng làm gì

Ở cổng nhà máy có một chiếc máy tính bảng gắn cố định, camera hướng ra lối đi. Khi bạn đi tới, máy nhìn
thấy khuôn mặt bạn, nhận ra bạn là ai, hiện tên bạn lên màn hình, đọc to lời chào, và ghi lại giờ bạn có
mặt. Bạn không cần thẻ, không cần chạm tay, không cần bấm gì.

Máy tự ghi nhận vào bộ nhớ của nó, kể cả khi mất mạng. Khi có mạng, nó gửi dữ liệu về hệ thống ERP của
công ty. Từ đó, số liệu ngày công, giờ tăng ca của từng người được tính tự động.

Một điều quan trọng để mọi người yên tâm: máy không lưu ảnh của bạn để gửi đi đâu cả. Máy chỉ ghi nhớ
một "dấu vân" của khuôn mặt dưới dạng dãy số, và chỉ gửi về công ty ba thứ: mã nhân viên, giờ có mặt,
và tên cổng. Không ảnh, không video.

---

## 4. Những ngày đầu ngỡ ngàng

Nói thật, không có chuyển đổi số nào suôn sẻ ngay từ ngày đầu. Ở DGPack cũng vậy, và kể ra để mọi người
thấy đó là chuyện bình thường.

Có anh công nhân đi qua cổng như mọi ngày, không dừng lại, rồi hôm sau thắc mắc sao máy không ghi. Có
chị đội nón bảo hộ sụp xuống, đeo khẩu trang kín, máy báo "chưa nhận dạng được", chị tưởng máy hỏng.
Có bác lớn tuổi thấy máy đọc tên mình thì giật mình, hỏi "nó biết tôi hả?". Có người lo "máy chụp hình
tôi rồi để đâu?".

Có cả những bối rối rất con người: đứng trước máy mà cả nhóm cùng cười, máy chờ chớp mắt mà mình cứ
trợn mắt nhìn nó. Hoặc đứng nói chuyện trước cổng, máy nhận đi nhận lại, mọi người sợ bị ghi trùng.

Tất cả những chuyện đó đều có lời giải, và phần lớn chỉ cần một tuần làm quen. Máy có chống ghi trùng,
đứng lâu cũng chỉ tính một lần. Máy có cách để nhận ra bạn tốt hơn nếu đăng ký đủ ảnh. Và lo lắng về
hình ảnh thì như đã nói ở trên, máy không giữ ảnh để gửi đi.

Điều đáng nhớ nhất từ giai đoạn này: chuyển đổi số không phải là lắp máy, mà là để mọi người quen dần
và tin dần.

---

## 5. Lợi ích lớn nhất: minh bạch và công bằng bằng dữ liệu

Khi dữ liệu chấm công tự ghi từng ngày, có ba thứ thay đổi.

**Minh bạch.** Ngày công của bạn không còn phụ thuộc vào trí nhớ hay chữ ký của ai. Nó là một dòng ghi
lúc bạn đi qua cổng, có giờ, có phút, có tên cổng. Bạn và công ty cùng nhìn vào một con số.

**Công bằng.** Người đi đúng giờ, ít nghỉ, tăng ca đều được dữ liệu ghi nhận. Khi xét khen thưởng,
nâng bậc, chọn người đi đào tạo, công ty có căn cứ rõ ràng thay vì cảm tính. Người làm tốt được nhìn thấy.

**Tự chủ.** Đây là điều công ty muốn hướng tới: mỗi người có một kênh để tự xem ngày công, giờ tăng ca
của mình bất cứ lúc nào, không cần chờ cuối tháng, không cần lên phòng nhân sự hỏi. Thấy sai thì báo
ngay trong tuần, không để dồn đến kỳ lương. Với phòng nhân sự, điều này cũng là giải phóng: thay vì dò
sổ, họ dành thời gian cho những việc có ý nghĩa hơn với con người.

Chuyển đổi số, nói cho cùng, là để mọi người bớt phải đoán và bớt phải xin.

---

## 6. Một niềm vui nhỏ mỗi sáng: trúng thưởng lon nước ngọt

Công ty muốn việc đi qua cổng không chỉ là "bị ghi nhận", mà là một khoảnh khắc vui. Vì vậy trong hai
tháng, tháng 8 và tháng 9 năm 2026, có chương trình trúng thưởng lon nước ngọt.

Mỗi ngày, trong số những người quét mặt ở cổng, có tám người may mắn trúng một lon nước ngọt. Trúng
thì màn hình bắn pháo hoa, hiện tên bạn thật to, giọng nói chúc mừng, và nhắc bạn ghé phòng nhân sự
nhận thưởng. Người đứng sau cũng thấy, cũng vui lây, và sáng hôm đó ở cổng có thêm tiếng cười.

Có vài điều nhỏ khiến chương trình ấm áp hơn:

- **Sinh nhật là chắc chắn trúng.** Ai đi làm đúng ngày sinh nhật của mình sẽ được tặng ba lon, không
  cần may mắn. Công ty muốn bạn biết rằng hôm nay có người nhớ đến bạn.
- **Người giữ nếp tốt có cơ hội cao hơn.** Bạn không đi trễ về sớm trong tháng, hoặc được ghi nhận vì
  phối hợp tốt nội quy, thì cơ hội trúng của bạn cao hơn người khác. Không phải để phạt ai, mà để việc
  tốt nhỏ mỗi ngày được thấy.
- **Lon nước lan đều.** Người đã trúng rồi thì cơ hội giảm bớt để người khác có dịp. Mục tiêu là trong
  hai tháng, càng nhiều người từng được một lần nghe máy gọi tên mình chúc mừng càng tốt.

Một lon nước ngọt không lớn. Nhưng cái cảm giác "mình đến công ty và có điều gì đó vui đang chờ" thì
lớn hơn nhiều.

---

## 7. Công ty như một gia đình

Máy móc không làm nên gia đình. Con người làm nên gia đình. Chuyển đổi số chỉ là cách để mỗi người trong
nhà bớt phải lo những chuyện vụn vặt, bớt hiểu lầm nhau vì thiếu thông tin, và có thêm thời gian cho việc
cùng nhau làm ra sản phẩm tốt.

Khi ngày công minh bạch, không ai phải nghi ngờ ai. Khi giờ tăng ca được ghi đúng, không ai thấy mình bị
thiệt. Khi buổi sáng ở cổng có tiếng máy chào tên từng người và thỉnh thoảng có pháo hoa, nhà máy bớt
lạnh hơn một chút.

Công ty mong mọi người xem chiếc máy ở cổng không phải là người giám sát, mà là người gác cổng của gia
đình: nhớ mặt từng người, chào từng người, và ghi lại công sức của từng người một cách công bằng.

---

## 8. Hướng dẫn thực hiện, từng bước

### Bước 1. Đăng ký khuôn mặt (làm một lần, có nhân sự hỗ trợ)

1. Bạn đến gặp nhân sự hoặc người phụ trách máy. Họ mở phần đăng ký trên máy, chọn tên bạn từ danh sách
   nhân viên của công ty. Mã nhân viên của bạn phải có sẵn trong hệ thống, nếu chưa có thì nhân sự tạo
   trước.
2. Bạn đứng trước camera, bỏ khẩu trang, bỏ nón, tóc không che mắt, mặt nhìn thẳng.
3. Máy chụp từ ba đến năm tấm. Người phụ trách sẽ nhắc bạn hơi nghiêng trái, nghiêng phải, ngẩng nhẹ,
   để máy nhớ bạn ở nhiều góc. Càng nhiều góc, sau này máy nhận bạn càng nhanh.
4. Bấm lưu. Xong. Đăng ký ở một cổng thì các cổng khác của công ty cũng nhận ra bạn.

Mẹo: nếu bạn thường đeo kính, hãy đăng ký với kính. Nếu sau này thay đổi nhiều như để râu, cắt tóc rất
khác, đổi kính, mà máy nhận chậm, chỉ cần đăng ký lại.

### Bước 2. Đứng trước máy mỗi ngày

1. Đi tới trước máy, cách khoảng một cánh tay đến một mét, mặt hướng vào camera.
2. Dừng lại một nhịp, khoảng một đến hai giây. Đừng đi lướt qua.
3. Kéo khẩu trang xuống, vén nón lên nếu che mặt. Ánh sáng chiếu vào mặt thì tốt hơn là ngược sáng.
4. Nhìn thẳng vào màn hình, không cần cười hay nghiêm, tự nhiên là được.

### Bước 3. Chớp mắt để xác nhận

1. Khi máy nhận ra bạn, màn hình hiện: "Xin chào [tên bạn]! Vui lòng chớp mắt để xác nhận."
2. Bạn chớp mắt bình thường một hai cái, như chớp mắt hằng ngày. Không cần nhắm lâu, không cần trợn
   mắt.
3. Máy hiện tên bạn cùng con số phần trăm, đọc to "Chấm công thành công, chào [tên bạn]", và tên bạn
   xuất hiện trong danh sách năm người vừa chấm công ở góc màn hình. Vậy là xong, bạn đi vào làm.

Vì sao phải chớp mắt? Để máy chắc chắn đó là bạn bằng xương bằng thịt, không phải tấm ảnh ai đó giơ lên.
Đây là cách bảo vệ chính bạn: không ai chấm công thay bạn được, và bạn cũng không bị ai đó "mượn mặt".

Nếu quá sáu giây bạn chưa chớp mắt, máy sẽ bỏ lượt đó. Bạn chỉ cần lùi ra rồi bước lại là máy bắt đầu lại.

### Bước 4. Hiểu con số độ nhận dạng

Khi máy nhận ra bạn, bên dưới tên có dòng "Độ tin cậy 92%" chẳng hạn. Đó là mức máy chắc chắn bạn là
bạn. Từ 80% trở lên máy mới ghi nhận. Số càng cao càng tốt, nhưng bạn không cần quan tâm nhiều, chỉ cần
thấy tên mình là được.

Nếu màn hình báo "Hệ thống chưa nhận dạng được (65%)", nghĩa là máy thấy có người nhưng chưa đủ chắc.
Máy sẽ không ghi gì cả, nên bạn không lo bị ghi nhầm thành người khác. Lúc đó hãy:

1. Bỏ khẩu trang, vén nón, gạt tóc khỏi mắt.
2. Đứng gần hơn một chút và nhìn thẳng.
3. Nếu ngược sáng, xoay nhẹ để ánh sáng chiếu vào mặt.
4. Vẫn không được sau vài lần, báo nhân sự để đăng ký lại. Chuyện này bình thường, nhất là sau khi đổi
   kiểu tóc hay để râu.

### Bước 5. Vài điều nên biết

- Đứng nói chuyện lâu trước máy không bị ghi nhiều lần. Trong năm phút, máy chỉ tính một lần.
- Về cũng đi qua máy như lúc đến, để hệ thống biết giờ ra và tính tăng ca.
- Mất mạng máy vẫn ghi bình thường, dữ liệu gửi về sau. Bạn không cần làm gì.
- Nếu bạn trúng thưởng, ghé phòng nhân sự trong ngày để nhận. Nhân sự có sổ ghi rõ ngày, tên, số lon.
- Thắc mắc về ngày công, giờ tăng ca: hỏi nhân sự để được hướng dẫn kênh tra cứu của riêng bạn.

---

## 9. Lời kết

Chuyển đổi số ở DGPack không phải là chuyện của máy tính. Nó là chuyện của việc mỗi người được nhìn thấy
đúng, được ghi nhận đúng, và được đối xử công bằng bằng những con số không thiên vị ai. Chiếc máy ở cổng
là bước đầu tiên. Những bước sau sẽ dễ hơn, vì mọi người đã quen, đã tin, và đã thấy lợi ích ngay trên
bảng lương của mình.

Sáng mai đi làm, dừng lại một nhịp trước máy, chớp mắt, nghe máy chào tên mình. Biết đâu có pháo hoa.
