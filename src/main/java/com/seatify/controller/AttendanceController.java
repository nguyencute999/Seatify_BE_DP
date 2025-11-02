package com.seatify.controller;

import com.seatify.dto.user.request.CheckInRequest;
import com.seatify.dto.user.response.CheckInResponse;
import com.seatify.model.constants.AttendanceAction;
import com.seatify.service.user.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý check-in và checkout bằng QR code
 * 
 * Author: Lê Văn Nguyễn - CE181235
 */
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
     * Khi mở URL này, hệ thống sẽ tự động xử lý check-in và hiển thị kết quả
     * 
     * @param data QR code data từ query parameter (format: SEATIFY:seatId:userId:eventId:UUID)
     * @return HTML page với kết quả check-in hoặc redirect
     */
    @Operation(
        summary = "Check-in tự động từ QR code URL",
        description = "API này cho phép check-in tự động khi scan QR code bằng các app như SanQR. " +
                     "QR code chứa URL, khi mở sẽ tự động xử lý check-in. " +
                     "QR code format: SEATIFY:seatId:userId:eventId:UUID"
    )
    @GetMapping("/auto-checkin")
    public ResponseEntity<?> autoCheckIn(@RequestParam("data") String data) {
        try {
            // Tạo request từ query parameter
            CheckInRequest request = new CheckInRequest();
            request.setQrCodeData(data);
            
            // Xử lý check-in
            CheckInResponse response = attendanceService.processCheckIn(request);
            
            // Trả về HTML page với kết quả
            String html = buildCheckInResultPage(response);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);
        } catch (Exception e) {
            // Trả về error page
            String html = buildErrorPage(e.getMessage());
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);
        }
    }

    private String buildCheckInResultPage(CheckInResponse response) {
        boolean isCheckIn = response.getAction() == AttendanceAction.CHECK_IN;
        String icon = isCheckIn ? "✅" : "🔚";
        String bgColor = isCheckIn ? "#4CAF50" : "#2196F3";
        
        return String.format("""
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Check-in %s - Seatify</title>
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
                        max-width: 500px;
                        width: 100%%;
                        padding: 40px;
                        text-align: center;
                    }
                    .icon {
                        font-size: 80px;
                        margin-bottom: 20px;
                    }
                    h1 {
                        color: #333;
                        margin-bottom: 10px;
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
                    .info {
                        background: #f5f5f5;
                        border-radius: 10px;
                        padding: 20px;
                        margin: 20px 0;
                        text-align: left;
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 10px 0;
                        border-bottom: 1px solid #e0e0e0;
                    }
                    .info-row:last-child {
                        border-bottom: none;
                    }
                    .label {
                        color: #666;
                        font-weight: 600;
                    }
                    .value {
                        color: #333;
                        font-weight: bold;
                    }
                    .message {
                        margin-top: 20px;
                        padding: 15px;
                        background: #e3f2fd;
                        border-left: 4px solid %s;
                        border-radius: 5px;
                        color: #1976d2;
                        font-size: 14px;
                    }
                    .timestamp {
                        margin-top: 15px;
                        color: #999;
                        font-size: 12px;
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
                    @keyframes pulse {
                        0%%, 100%% {
                            transform: scale(1);
                        }
                        50%% {
                            transform: scale(1.1);
                        }
                    }
                    .icon {
                        animation: pulse 0.6s ease-in-out;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">%s</div>
                    <h1>%s</h1>
                    <div class="status">%s</div>
                    <div class="info">
                        <div class="info-row">
                            <span class="label">Sự kiện:</span>
                            <span class="value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="label">Ghế:</span>
                            <span class="value">%s</span>
                        </div>
                    </div>
                    <div class="message">%s</div>
                    <div class="timestamp">Thời gian: %s</div>
                </div>
                <script>
                    // Phát âm thanh thành công
                    function playSuccessSound() {
                        try {
                            // Tạo audio context và phát âm thanh beep
                            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
                            const oscillator = audioContext.createOscillator();
                            const gainNode = audioContext.createGain();
                            
                            oscillator.connect(gainNode);
                            gainNode.connect(audioContext.destination);
                            
                            oscillator.frequency.value = %s; // Tần số (Hz)
                            oscillator.type = 'sine';
                            gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
                            gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.3);
                            
                            oscillator.start(audioContext.currentTime);
                            oscillator.stop(audioContext.currentTime + 0.3);
                        } catch (e) {
                            console.log('Cannot play sound:', e);
                        }
                    }
                    
                    // Rung điện thoại (nếu hỗ trợ)
                    function vibrate() {
                        if ('vibrate' in navigator) {
                            navigator.vibrate([200, 100, 200]); // Rung 200ms, nghỉ 100ms, rung 200ms
                        }
                    }
                    
                    // Browser notification
                    function showNotification() {
                        if ('Notification' in window && Notification.permission === 'granted') {
                            new Notification('%s', {
                                body: '%s - Ghế: %s',
                                icon: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="%s"/><text x="50" y="65" font-size="50" text-anchor="middle" fill="white">%s</text></svg>',
                                badge: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="%s"/></svg>',
                                tag: 'seatify-checkin',
                                requireInteraction: false
                            });
                        } else if ('Notification' in window && Notification.permission !== 'denied') {
                            Notification.requestPermission().then(function(permission) {
                                if (permission === 'granted') {
                                    showNotification();
                                }
                            });
                        }
                    }
                    
                    // Chạy tất cả tín hiệu khi trang load
                    window.addEventListener('load', function() {
                        playSuccessSound();
                        vibrate();
                        showNotification();
                    });
                </script>
            </body>
            </html>
            """,
            isCheckIn ? "thành công" : "thoát thành công",
            bgColor,
            bgColor,
            icon,
            isCheckIn ? "Check-in thành công!" : "Check-out thành công!",
            isCheckIn ? "Đã vào" : "Đã ra",
            response.getEventName(),
            response.getSeatLabel(),
            response.getMessage(),
            response.getTimestamp() != null ? response.getTimestamp().toString() : "N/A",
            // Sound frequency (Hz) - 800Hz cho success, 600Hz cho checkout
            isCheckIn ? 800 : 600,
            // Notification title
            isCheckIn ? "✅ Check-in thành công!" : "🔚 Check-out thành công!",
            // Notification body
            response.getEventName(),
            response.getSeatLabel(),
            // Notification icon color
            bgColor,
            // Notification icon emoji
            icon
        );
    }

    private String buildErrorPage(String errorMessage) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Lỗi Check-in - Seatify</title>
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
                        max-width: 500px;
                        width: 100%%;
                        padding: 40px;
                        text-align: center;
                    }
                    .icon {
                        font-size: 80px;
                        margin-bottom: 20px;
                    }
                    h1 {
                        color: #d32f2f;
                        margin-bottom: 10px;
                        font-size: 28px;
                    }
                    .error-message {
                        margin-top: 20px;
                        padding: 15px;
                        background: #ffebee;
                        border-left: 4px solid #d32f2f;
                        border-radius: 5px;
                        color: #c62828;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">❌</div>
                    <h1>Lỗi Check-in</h1>
                    <div class="error-message">%s</div>
                </div>
            </body>
            </html>
            """,
            errorMessage
        );
    }
}
