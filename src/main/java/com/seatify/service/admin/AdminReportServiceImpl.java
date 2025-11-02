package com.seatify.service.admin;

import com.seatify.dto.admin.response.*;
import com.seatify.model.Booking;
import com.seatify.model.Event;
import com.seatify.model.constants.AttendanceAction;
import com.seatify.model.constants.BookingStatus;
import com.seatify.repository.AttendanceLogRepository;
import com.seatify.repository.BookingRepository;
import com.seatify.repository.EventRepository;
import com.seatify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation cho báo cáo admin.
 * 
 * @author : Lê Văn Nguyễn - CE181235
 */
@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {
    
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    
    // Giá vé mặc định
    private static final long DEFAULT_TICKET_PRICE = 50000L; // 50,000 VND
    
    @Override
    @Transactional(readOnly = true)
    public OverviewStatsDTO getOverviewStats(LocalDate startDate, LocalDate endDate, Long eventId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        // Lấy danh sách sự kiện
        List<Event> events;
        if (eventId != null) {
            // Nếu có eventId, lấy event đó (không cần filter theo startTime)
            Event event = eventRepository.findById(eventId).orElse(null);
            events = event != null ? List.of(event) : List.of();
        } else {
            events = eventRepository.findEventsByStartTimeBetween(startDateTime, endDateTime);
        }
        
        Long totalEvents = (long) events.size();
        
        // Tính tổng số bookings
        Long totalBookings;
        if (eventId != null) {
            totalBookings = bookingRepository.countByEventIdAndBookingTimeBetween(eventId, startDateTime, endDateTime);
        } else {
            totalBookings = bookingRepository.countByBookingTimeBetween(startDateTime, endDateTime);
        }
        
        // Tính tổng số users (distinct)
        Long totalUsers = bookingRepository.countDistinctUsersByBookingTimeBetween(startDateTime, endDateTime);
        
        // Tính doanh thu (giả định mỗi booking = DEFAULT_TICKET_PRICE)
        Long totalRevenue = totalBookings * DEFAULT_TICKET_PRICE;
        
        // Tính tỷ lệ tham gia trung bình
        Double averageAttendance = 0.0;
        String topEvent = null;
        
        if (!events.isEmpty() && totalBookings > 0) {
            List<Double> attendanceRates = new ArrayList<>();
            Event topEventObj = null;
            Long maxBookings = 0L;
            
            for (Event event : events) {
                // Tính bookings trong khoảng thời gian
                Long eventBookings = bookingRepository.countByEventIdAndBookingTimeBetween(
                    event.getEventId(), startDateTime, endDateTime
                );
                
                // Tính số lượng đã check-in trong khoảng thời gian
                Long checkedInCount = attendanceLogRepository.countDistinctBookingsByEventIdAndActionAndTimeBetween(
                    event.getEventId(), AttendanceAction.CHECK_IN, startDateTime, endDateTime
                );
                
                if (eventBookings > 0) {
                    double rate = (checkedInCount.doubleValue() / eventBookings.doubleValue()) * 100;
                    attendanceRates.add(rate);
                }
                
                // Tìm sự kiện có nhiều booking nhất
                if (eventBookings > maxBookings) {
                    maxBookings = eventBookings;
                    topEventObj = event;
                }
            }
            
            if (!attendanceRates.isEmpty()) {
                averageAttendance = attendanceRates.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            }
            
            if (topEventObj != null) {
                topEvent = topEventObj.getEventName();
            }
        }
        
        return OverviewStatsDTO.builder()
            .totalEvents(totalEvents)
            .totalBookings(totalBookings)
            .totalUsers(totalUsers)
            .totalRevenue(totalRevenue)
            .averageAttendance(Math.round(averageAttendance * 10.0) / 10.0)
            .topEvent(topEvent != null ? topEvent : "Không có dữ liệu")
            .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<EventStatsDTO> getEventStats(LocalDate startDate, LocalDate endDate, Long eventId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        List<Event> events;
        if (eventId != null) {
            // Nếu có eventId, lấy event đó (không cần filter theo startTime)
            Event event = eventRepository.findById(eventId).orElse(null);
            events = event != null ? List.of(event) : List.of();
        } else {
            events = eventRepository.findEventsByStartTimeBetween(startDateTime, endDateTime);
        }
        
        return events.stream()
            .map(event -> {
                // Tính bookings trong khoảng thời gian
                Long totalBookings = bookingRepository.countByEventIdAndBookingTimeBetween(
                    event.getEventId(), startDateTime, endDateTime
                );
                
                // Tính số lượng đã check-in trong khoảng thời gian
                Long attendance = attendanceLogRepository.countDistinctBookingsByEventIdAndActionAndTimeBetween(
                    event.getEventId(), AttendanceAction.CHECK_IN, startDateTime, endDateTime
                );
                
                Double attendanceRate = totalBookings > 0 
                    ? (attendance.doubleValue() / totalBookings.doubleValue()) * 100.0 
                    : 0.0;
                
                Long revenue = totalBookings * DEFAULT_TICKET_PRICE;
                
                return EventStatsDTO.builder()
                    .id(event.getEventId())
                    .eventName(event.getEventName())
                    .totalBookings(totalBookings)
                    .attendance(attendance)
                    .revenue(revenue)
                    .status(event.getStatus())
                    .attendanceRate(Math.round(attendanceRate * 10.0) / 10.0)
                    .build();
            })
            .sorted((a, b) -> Long.compare(b.getTotalBookings(), a.getTotalBookings()))
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserStatsDTO> getUserStats(LocalDate startDate, LocalDate endDate) {
        List<UserStatsDTO> stats = new ArrayList<>();
        
        YearMonth currentMonth = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);
        
        YearMonth previousMonth = null;
        
        while (!currentMonth.isAfter(endMonth)) {
            LocalDate monthStart = currentMonth.atDay(1);
            LocalDate monthEnd = currentMonth.atEndOfMonth();
            
            if (monthStart.isBefore(startDate)) {
                monthStart = startDate;
            }
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }
            
            LocalDateTime monthStartDateTime = monthStart.atStartOfDay();
            LocalDateTime monthEndDateTime = monthEnd.plusDays(1).atStartOfDay();
            
            // Số user mới trong tháng
            Long newUsers = userRepository.countByCreatedAtBetween(monthStartDateTime, monthEndDateTime);
            
            // Số user hoạt động (có booking trong tháng)
            Long activeUsers = bookingRepository.countDistinctUsersByBookingTimeBetween(monthStartDateTime, monthEndDateTime);
            
            // Tổng số bookings trong tháng
            Long totalBookings = bookingRepository.countByBookingTimeBetween(monthStartDateTime, monthEndDateTime);
            
            // Tính tỷ lệ tăng trưởng
            Double growthRate = null;
            if (previousMonth != null) {
                LocalDate prevMonthStart = previousMonth.atDay(1);
                LocalDate prevMonthEnd = previousMonth.atEndOfMonth();
                LocalDateTime prevMonthStartDateTime = prevMonthStart.atStartOfDay();
                LocalDateTime prevMonthEndDateTime = prevMonthEnd.plusDays(1).atStartOfDay();
                
                Long prevNewUsers = userRepository.countByCreatedAtBetween(prevMonthStartDateTime, prevMonthEndDateTime);
                
                if (prevNewUsers > 0) {
                    growthRate = ((newUsers - prevNewUsers) / prevNewUsers.doubleValue()) * 100.0;
                } else if (newUsers > 0) {
                    growthRate = 100.0;
                } else {
                    growthRate = 0.0;
                }
            }
            
            stats.add(UserStatsDTO.builder()
                .month(currentMonth.toString())
                .newUsers(newUsers)
                .activeUsers(activeUsers)
                .totalBookings(totalBookings)
                .growthRate(growthRate != null ? Math.round(growthRate * 10.0) / 10.0 : null)
                .build());
            
            previousMonth = currentMonth;
            currentMonth = currentMonth.plusMonths(1);
        }
        
        // Sắp xếp theo tháng giảm dần
        Collections.reverse(stats);
        
        return stats;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BookingTrendDTO> getBookingTrends(LocalDate startDate, LocalDate endDate) {
        List<BookingTrendDTO> trends = new ArrayList<>();
        
        LocalDate currentDate = startDate;
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        while (!currentDate.isAfter(endDate)) {
            LocalDateTime dateStart = currentDate.atStartOfDay();
            LocalDateTime dateEnd = currentDate.plusDays(1).atStartOfDay();
            
            Long count = bookingRepository.countByDate(dateStart, dateEnd);
            
            trends.add(BookingTrendDTO.builder()
                .date(currentDate)
                .bookings(count)
                .build());
            
            currentDate = currentDate.plusDays(1);
        }
        
        return trends;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDataDTO> getAttendanceData(LocalDate startDate, LocalDate endDate, Long eventId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        List<Event> events;
        if (eventId != null) {
            // Nếu có eventId, lấy event đó (không cần filter theo startTime)
            Event event = eventRepository.findById(eventId).orElse(null);
            events = event != null ? List.of(event) : List.of();
        } else {
            events = eventRepository.findEventsByStartTimeBetween(startDateTime, endDateTime);
        }
        
        return events.stream()
            .map(event -> {
                // Tính bookings trong khoảng thời gian
                Long totalBookings = bookingRepository.countByEventIdAndBookingTimeBetween(
                    event.getEventId(), startDateTime, endDateTime
                );
                
                // Tính số lượng đã check-in trong khoảng thời gian
                Long checkedInCount = attendanceLogRepository.countDistinctBookingsByEventIdAndActionAndTimeBetween(
                    event.getEventId(), AttendanceAction.CHECK_IN, startDateTime, endDateTime
                );
                
                Double attendance = totalBookings > 0 
                    ? (checkedInCount.doubleValue() / totalBookings.doubleValue()) * 100.0 
                    : 0.0;
                
                return AttendanceDataDTO.builder()
                    .eventName(event.getEventName())
                    .attendance(Math.round(attendance * 10.0) / 10.0)
                    .build();
            })
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RevenueStatsDTO> getRevenueStats(LocalDate startDate, LocalDate endDate, Long eventId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        List<Event> events;
        if (eventId != null) {
            events = eventRepository.findEventsByEventIdAndStartTimeBetween(eventId, startDateTime, endDateTime);
        } else {
            events = eventRepository.findEventsByStartTimeBetween(startDateTime, endDateTime);
        }
        
        List<RevenueStatsDTO> revenueStats = events.stream()
            .map(event -> {
                // Tính bookings trong khoảng thời gian
                Long totalBookings = bookingRepository.countByEventIdAndBookingTimeBetween(
                    event.getEventId(), startDateTime, endDateTime
                );
                
                Long revenue = totalBookings * DEFAULT_TICKET_PRICE;
                
                return RevenueStatsDTO.builder()
                    .eventName(event.getEventName())
                    .revenue(revenue)
                    .build();
            })
            .collect(Collectors.toList());
        
        // Tìm doanh thu cao nhất để tính phần trăm
        Long maxRevenue = revenueStats.stream()
            .map(RevenueStatsDTO::getRevenue)
            .max(Long::compare)
            .orElse(1L);
        
        // Tính phần trăm
        revenueStats.forEach(stat -> {
            double percentage = maxRevenue > 0 
                ? (stat.getRevenue() / maxRevenue.doubleValue()) * 100.0 
                : 0.0;
            stat.setPercentage(Math.round(percentage * 10.0) / 10.0);
        });
        
        // Sắp xếp theo doanh thu giảm dần
        revenueStats.sort((a, b) -> Long.compare(b.getRevenue(), a.getRevenue()));
        
        return revenueStats;
    }
}

