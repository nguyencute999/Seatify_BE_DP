package com.seatify.service.user;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.seatify.dto.user.request.UserBookingRequest;
import com.seatify.dto.user.response.UserBookingResponse;
import com.seatify.dto.user.response.UserAttendanceStatsResponse;
import com.seatify.exception.ResourceNotFoundException;
import com.seatify.exception.ValidationException;
import com.seatify.model.Booking;
import com.seatify.model.Event;
import com.seatify.model.Seat;
import com.seatify.model.User;
import com.seatify.model.constants.BookingStatus;
import com.seatify.model.constants.EventStatus;
import com.seatify.repository.BookingRepository;
import com.seatify.repository.EventRepository;
import com.seatify.repository.SeatRepository;
import com.seatify.repository.UserRepository;
import com.seatify.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * @author : Lê Văn Nguyễn - CE181235
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FileUploadUtil fileUploadUtil;

    @Value("${seatify.app.base-url:https://www.seatify.com.vn}")
    private String baseUrl;

    @Transactional
    public UserBookingResponse createBooking(UserBookingRequest request, Long userId) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Validate event exists and is available for booking
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (event.getStatus() != EventStatus.UPCOMING) {
            throw new ValidationException("Event is not available for booking");
        }

        if (event.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Event has already started");
        }

        // Check if user already has a booking for this event
        if (bookingRepository.existsByUserAndEvent(user, event)) {
            throw new ValidationException("You already have a booking for this event");
        }

        // Validate seat exists and is available
        Seat seat = seatRepository.findAvailableSeatByIdAndEventId(request.getSeatId(), request.getEventId())
                .orElseThrow(() -> new ValidationException("Seat is not available"));

        // Generate QR code data string
        String qrCodeData = generateQRCodeData(seat.getSeatId(), user.getUserId(), event.getEventId());
        
        // Generate QR code URL for automatic check-in (compatible with SanQR and similar apps)
        String qrCodeUrlForScan = generateQRCodeUrl(qrCodeData);
        
        // Generate QR code image
        String qrCodeImageUrl = generateQRCode(qrCodeUrlForScan);

        // Create booking
        Booking booking = Booking.builder()
                .user(user)
                .event(event)
                .seat(seat)
                .qrCode(qrCodeImageUrl)
                .qrCodeData(qrCodeData)
                .bookingTime(LocalDateTime.now())
                .status(BookingStatus.BOOKED)
                .build();

        // Mark seat as unavailable
        seat.setIsAvailable(false);

        // Save booking and update seat
        booking = bookingRepository.save(booking);
        seatRepository.save(seat);

        // Send confirmation email
        sendBookingConfirmationEmail(user, event, seat, qrCodeImageUrl);

        return convertToResponse(booking);
    }

    public List<UserBookingResponse> getUserBookings(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        return bookings.stream()
                .map(this::convertToResponse)
                .toList();
    }

    public UserAttendanceStatsResponse getUserAttendanceStats(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);

        long total = bookings.size();
        long present = bookings.stream()
                .filter(b -> b.getCheckInTime() != null)
                .count();
        long absent = total - present;

        return UserAttendanceStatsResponse.builder()
                .totalParticipated(total)
                .presentCount(present)
                .absentCount(absent)
                .build();
    }

    public UserBookingResponse getBookingById(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getUser().getUserId().equals(userId)) {
            throw new ValidationException("You can only view your own bookings");
        }

        return convertToResponse(booking);
    }

    @Transactional
    public UserBookingResponse cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getUser().getUserId().equals(userId)) {
            throw new ValidationException("You can only cancel your own bookings");
        }

        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new ValidationException("Only booked reservations can be cancelled");
        }

        // Mark seat as available again
        Seat seat = booking.getSeat();
        seat.setIsAvailable(true);
        seatRepository.save(seat);

        // Update booking status
        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);

        return convertToResponse(booking);
    }

    private String generateQRCodeData(Long seatId, Long userId, Long eventId) {
        return String.format("SEATIFY:%d:%d:%d:%s", 
                seatId, userId, eventId, UUID.randomUUID().toString());
    }

    /**
     * Tạo URL cho QR code để check-in tự động
     * URL này sẽ được encode vào QR code, khi scan sẽ tự động mở và check-in
     */
    private String generateQRCodeUrl(String qrCodeData) {
        try {
            // Encode QR code data để dùng trong URL
            String encodedData = URLEncoder.encode(qrCodeData, StandardCharsets.UTF_8);
            // Tạo URL check-in tự động
            return String.format("%s/api/v1/attendance/auto-checkin?data=%s", baseUrl, encodedData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code URL", e);
        }
    }

    private String generateQRCode(String data) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 200, 200);
            
            // Convert BitMatrix to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] qrCodeBytes = outputStream.toByteArray();
            
            // Create a temporary MultipartFile-like object for Cloudinary upload
            org.springframework.web.multipart.MultipartFile tempFile = new org.springframework.web.multipart.MultipartFile() {
                @Override
                public String getName() {
                    return "qrCode";
                }
                
                @Override
                public String getOriginalFilename() {
                    return "qr_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
                }
                
                @Override
                public String getContentType() {
                    return "image/png";
                }
                
                @Override
                public boolean isEmpty() {
                    return qrCodeBytes.length == 0;
                }
                
                @Override
                public long getSize() {
                    return qrCodeBytes.length;
                }
                
                @Override
                public byte[] getBytes() throws IOException {
                    return qrCodeBytes;
                }
                
                @Override
                public java.io.InputStream getInputStream() throws IOException {
                    return new java.io.ByteArrayInputStream(qrCodeBytes);
                }
                
                @Override
                public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                    // Not needed for Cloudinary upload
                }
            };
            
            // Upload to Cloudinary
            String qrCodeUrl = fileUploadUtil.uploadFile(tempFile, "qr-codes");
            return qrCodeUrl;
            
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private void sendBookingConfirmationEmail(User user, Event event, Seat seat, String qrCodeUrl) {
        String subject = "Xác nhận đặt chỗ thành công - " + event.getEventName();
        String htmlBody = buildBookingConfirmationEmailBody(user, event, seat, qrCodeUrl);
        emailService.sendBookingConfirmationEmail(user.getEmail(), subject, htmlBody, qrCodeUrl);
    }

    private String buildBookingConfirmationEmailBody(User user, Event event, Seat seat, String qrCodeUrl) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 20px; text-align: center; border-radius: 10px 10px 0 0; }
                        .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                        .booking-info { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .info-row { display: flex; margin: 10px 0; }
                        .info-label { font-weight: bold; width: 120px; color: #666; }
                        .info-value { flex: 1; }
                        .qr-section { text-align: center; margin: 20px 0; }
                        .qr-section img { max-width: 200px; border: 2px solid #ddd; border-radius: 8px; }
                        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 14px; }
                        .highlight { color: #667eea; font-weight: bold; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Đặt chỗ thành công!</h1>
                            <p>Chúc mừng bạn đã đặt chỗ thành công</p>
                        </div>
                        <div class="content">
                            <p>Xin chào <span class="highlight">%s</span>!</p>
                            
                            <div class="booking-info">
                                <h3>Thông tin đặt chỗ</h3>
                                <div class="info-row">
                                    <div class="info-label">Sự kiện:</div>
                                    <div class="info-value"><strong>%s</strong></div>
                                </div>
                                <div class="info-row">
                                    <div class="info-label">Địa điểm:</div>
                                    <div class="info-value">%s</div>
                                </div>
                                <div class="info-row">
                                    <div class="info-label">Thời gian:</div>
                                    <div class="info-value">%s - %s</div>
                                </div>
                                <div class="info-row">
                                    <div class="info-label">Ghế:</div>
                                    <div class="info-value"><strong>%s%d</strong></div>
                                </div>
                            </div>
                            
                            <div class="qr-section">
                                <h3>Mã QR Check-in</h3>
                                <p>Mã QR để check-in đã được đính kèm trong email này.</p>
                                <p><strong>Vui lòng mang theo mã QR này khi đến sự kiện để check-in.</strong></p>
                                <img src="%s" alt="QR Code" style="max-width: 200px; border: 2px solid #ddd; border-radius: 8px; margin: 10px 0;">
                            </div>
                            
                            <div style="background: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; border-radius: 5px; margin: 20px 0;">
                                <h4>⚠️Lưu ý quan trọng:</h4>
                                <ul>
                                    <li>Mã QR chỉ có hiệu lực cho sự kiện này</li>
                                    <li>Vui lòng đến đúng giờ để không bỏ lỡ sự kiện</li>
                                    <li>Nếu có thay đổi, vui lòng liên hệ với ban tổ chức</li>
                                </ul>
                            </div>
                        </div>
                        <div class="footer">
                            <p>Cảm ơn bạn đã sử dụng <strong>Seatify</strong>!</p>
                            <p>Team Seatify</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                user.getFullName(),
                event.getEventName(),
                event.getLocation() != null ? event.getLocation() : "Chưa cập nhật",
                event.getStartTime().toString(),
                event.getEndTime().toString(),
                seat.getSeatRow(),
                seat.getSeatNumber(),
                qrCodeUrl
        );
    }

    private UserBookingResponse convertToResponse(Booking booking) {
        return UserBookingResponse.builder()
                .bookingId(booking.getBookingId())
                .eventId(booking.getEvent().getEventId())
                .eventName(booking.getEvent().getEventName())
                .seatId(booking.getSeat().getSeatId())
                .seatRow(booking.getSeat().getSeatRow())
                .seatNumber(booking.getSeat().getSeatNumber())
                .seatLabel(booking.getSeat().getSeatRow() + String.valueOf(booking.getSeat().getSeatNumber()))
                .qrCode(booking.getQrCode()) // Now returns Cloudinary URL directly
                .status(booking.getStatus())
                .bookingTime(booking.getBookingTime())
                .checkInTime(booking.getCheckInTime())
                .checkOutTime(booking.getCheckOutTime())
                .build();
    }
}
