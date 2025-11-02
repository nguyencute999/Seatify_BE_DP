package com.seatify.controller;

import com.seatify.dto.user.request.CheckInRequest;
import com.seatify.dto.user.response.CheckInResponse;
import com.seatify.model.constants.AttendanceAction;
import com.seatify.service.user.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý check-in và checkout bằng QR code
 * 
 * Author: Lê Văn Nguyễn - CE181235
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "APIs for event check-in and checkout using QR code")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * API check-in từ QR code
     * 
     * Logic:
     * - Nếu chưa check-in: Thực hiện check-in
     * - Nếu đã check-in và chưa checkout: Tự động checkout (toggle)
     *   + Nếu thời gian check-in < 5s: Đánh dấu autoCheckedOut = true (coi như check-in nhầm)
     * - Nếu đã checkout: Cho phép check-in lại (toggle)
     * 
     * QR code format: SEATIFY:seatId:userId:eventId:UUID
     * 
     * @param request QR code data từ client
     * @return Kết quả check-in
     */
    @Operation(
        summary = "Check-in/Checkout bằng QR code (Toggle)",
        description = "Tự động toggle giữa check-in và checkout khi scan QR code. " +
                     "Lần scan đầu: check-in, lần scan thứ 2: checkout, lần scan thứ 3: check-in lại... " +
                     "Nếu thời gian check-in < 5 giây trước khi checkout, sẽ đánh dấu autoCheckedOut = true. " +
                     "QR code format: SEATIFY:seatId:userId:eventId:UUID"
    )
    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        CheckInResponse response = attendanceService.processCheckIn(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API checkout từ QR code
     * 
     * Logic:
     * - Phải đã check-in mới có thể checkout
     * - Nếu thời gian check-in < 5s: Tự động checkout (coi như check-in nhầm)
     * - Nếu thời gian check-in >= 5s: Checkout bình thường
     * 
     * QR code format: SEATIFY:seatId:userId:eventId:UUID
     * 
     * @param request QR code data từ client
     * @return Kết quả checkout
     */
    @Operation(
        summary = "Checkout bằng QR code",
        description = "Thực hiện checkout từ QR code. " +
                     "Phải đã check-in mới có thể checkout. " +
                     "Nếu thời gian check-in < 5 giây, sẽ tự động checkout (coi như check-in nhầm). " +
                     "QR code format: SEATIFY:seatId:userId:eventId:UUID"
    )
    @PostMapping("/checkout")
    public ResponseEntity<CheckInResponse> checkout(@Valid @RequestBody CheckInRequest request) {
        CheckInResponse response = attendanceService.processCheckout(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API check-in tự động từ QR code URL
     * 
     * Endpoint này cho phép check-in tự động khi scan QR code bằng các app như SanQR
     * QR code sẽ chứa URL dạng: https://seatify.com.vn/api/v1/attendance/auto-checkin?data=SEATIFY:...
     * 
     * Khi scan QR code, trình duyệt sẽ tự động mở URL này và hiển thị kết quả thành công/thất bại
     * 
     * @param data QR code data từ query parameter (format: SEATIFY:seatId:userId:eventId:UUID)
     * @return HTML page với kết quả thành công/thất bại
     */
    @Operation(
        summary = "Check-in tự động từ QR code URL",
        description = "API này cho phép check-in tự động khi scan QR code bằng các app như SanQR. " +
                     "QR code chứa URL, khi mở sẽ tự động xử lý check-in và hiển thị kết quả trên trang web. " +
                     "QR code format: SEATIFY:seatId:userId:eventId:UUID"
    )
    @GetMapping("/auto-checkin")
    public ResponseEntity<?> autoCheckIn(@RequestParam(value = "data", required = false) String data) {
        try {
            // Log để debug
            log.info("Auto check-in request received. Data: {}", data);
            
            // Kiểm tra parameter
            if (data == null || data.isEmpty()) {
                String html = buildSimpleResultPage(false, "Thiếu thông tin QR code. Vui lòng scan lại mã QR.");
                return ResponseEntity.ok()
                        .header("Content-Type", "text/html; charset=UTF-8")
                        .body(html);
            }
            
            // Decode URL nếu cần (đề phòng double encoding)
            String decodedData = data;
            try {
                decodedData = java.net.URLDecoder.decode(data, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                // Nếu không decode được, dùng data gốc
                log.warn("Could not decode data, using original: {}", data);
            }
            
            // Tạo request từ query parameter
            CheckInRequest request = new CheckInRequest();
            request.setQrCodeData(decodedData);
            
            // Xử lý check-in
            CheckInResponse response = attendanceService.processCheckIn(request);
            
            // Trả về HTML page với kết quả thành công
            String html = buildSimpleResultPage(true, response.getMessage());
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);
        } catch (Exception e) {
            // Log error để debug
            log.error("Error processing auto check-in", e);
            // Trả về HTML page với kết quả thất bại
            String html = buildSimpleResultPage(false, e.getMessage() != null ? e.getMessage() : "Lỗi xử lý check-in");
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);
        }
    }

    /**
     * Tạo trang HTML đơn giản chỉ hiển thị kết quả thành công/thất bại
     * 
     * @param success true nếu thành công, false nếu thất bại
     * @param message Thông báo kết quả
     * @return HTML string
     */
    private String buildSimpleResultPage(boolean success, String message) {
        String icon = success ? "✅" : "❌";
        String title = success ? "Thành công" : "Thất bại";
        String bgColor = success ? "#4CAF50" : "#f44336";
        
        return String.format("""
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Seatify</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 20px;
                        box-shadow: 0 10px 40px rgba(0,0,0,0.2);
                        max-width: 400px;
                        width: 100%%;
                        padding: 40px;
                        text-align: center;
                    }
                    .icon {
                        font-size: 80px;
                        margin-bottom: 20px;
                        animation: pulse 0.6s ease-in-out;
                    }
                    h1 {
                        color: #333;
                        margin-bottom: 20px;
                        font-size: 28px;
                    }
                    .status {
                        display: inline-block;
                        background: %s;
                        color: white;
                        padding: 8px 20px;
                        border-radius: 20px;
                        font-weight: bold;
                        margin: 20px 0;
                        font-size: 18px;
                    }
                    .message {
                        margin-top: 20px;
                        padding: 15px;
                        background: %s;
                        border-radius: 10px;
                        color: %s;
                        font-size: 16px;
                        line-height: 1.5;
                    }
                    @keyframes pulse {
                        0%%, 100%% { transform: scale(1); }
                        50%% { transform: scale(1.1); }
                    }
                    @keyframes slideIn {
                        from {
                            opacity: 0;
                            transform: translateY(-20px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }
                    .container {
                        animation: slideIn 0.5s ease-out;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">%s</div>
                    <h1>%s</h1>
                    <div class="status">%s</div>
                    <div class="message">%s</div>
                </div>
                <script>
                    // Phát âm thanh beep khi trang load
                    function playBeepSound() {
                        try {
                            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
                            const oscillator = audioContext.createOscillator();
                            const gainNode = audioContext.createGain();
                            
                            oscillator.connect(gainNode);
                            gainNode.connect(audioContext.destination);
                            
                            // Tần số và thời gian khác nhau cho thành công/thất bại
                            oscillator.frequency.value = %s; // Hz
                            oscillator.type = 'sine';
                            
                            // Gain envelope để tạo âm thanh beep mượt mà
                            gainNode.gain.setValueAtTime(0, audioContext.currentTime);
                            gainNode.gain.linearRampToValueAtTime(0.3, audioContext.currentTime + 0.01);
                            gainNode.gain.linearRampToValueAtTime(0, audioContext.currentTime + %s);
                            
                            oscillator.start(audioContext.currentTime);
                            oscillator.stop(audioContext.currentTime + %s);
                            
                            // Nếu thành công, phát thêm một beep ngắn
                            if (%s) {
                                setTimeout(() => {
                                    const osc2 = audioContext.createOscillator();
                                    const gain2 = audioContext.createGain();
                                    osc2.connect(gain2);
                                    gain2.connect(audioContext.destination);
                                    osc2.frequency.value = %s + 200;
                                    osc2.type = 'sine';
                                    gain2.gain.setValueAtTime(0, audioContext.currentTime + 0.15);
                                    gain2.gain.linearRampToValueAtTime(0.2, audioContext.currentTime + 0.16);
                                    gain2.gain.linearRampToValueAtTime(0, audioContext.currentTime + 0.25);
                                    osc2.start(audioContext.currentTime + 0.15);
                                    osc2.stop(audioContext.currentTime + 0.25);
                                }, 150);
                            }
                        } catch (e) {
                            console.log('Cannot play sound:', e);
                        }
                    }
                    
                    // Rung điện thoại (nếu hỗ trợ)
                    function vibrate() {
                        if ('vibrate' in navigator) {
                            const pattern = %s;
                            navigator.vibrate(pattern);
                        }
                    }
                    
                    // Chạy khi trang load
                    window.addEventListener('load', function() {
                        playBeepSound();
                        vibrate();
                    });
                </script>
            </body>
            </html>
            """,
            title,
            bgColor,
            success ? "#e8f5e9" : "#ffebee",
            success ? "#2e7d32" : "#c62828",
            icon,
            title,
            success ? "THÀNH CÔNG" : "THẤT BẠI",
            message,
            // Sound parameters
            success ? 800 : 400, // frequency (Hz) - cao hơn cho success, thấp hơn cho failure
            success ? 0.2 : 0.3, // duration (seconds)
            success ? 0.2 : 0.3, // stop time
            success, // isSuccess
            success ? 800 : 400, // second beep frequency
            success ? "[200, 50, 200]" : "[300, 100, 300]" // vibration pattern - array format for JavaScript
        );
    }
}
