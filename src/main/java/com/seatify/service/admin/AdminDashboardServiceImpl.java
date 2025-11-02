package com.seatify.service.admin;

import com.seatify.dto.admin.response.DashboardStatsDTO;
import com.seatify.dto.admin.response.RecentBookingDTO;
import com.seatify.dto.admin.response.RecentEventDTO;
import com.seatify.model.Booking;
import com.seatify.model.Event;
import com.seatify.model.constants.BookingStatus;
import com.seatify.model.constants.EventStatus;
import com.seatify.repository.BookingRepository;
import com.seatify.repository.EventRepository;
import com.seatify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation cho dashboard admin.
 * 
 * @author : Lê Văn Nguyễn - CE181235
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        Long totalUsers = userRepository.count();
        Long totalEvents = eventRepository.count();
        Long totalBookings = bookingRepository.count();
        
        // Tính số sự kiện theo từng trạng thái
        Long activeEvents = eventRepository.countByStatus(EventStatus.ONGOING);
        Long upcomingEvents = eventRepository.countByStatus(EventStatus.UPCOMING);
        Long completedEvents = eventRepository.countByStatus(EventStatus.FINISHED);
        Long cancelledEvents = eventRepository.countByStatus(EventStatus.CANCELLED);
        
        // Tính tổng doanh thu 
        // Có thể tính dựa trên số booking nhân với giá vé nếu có
        Long totalRevenue = 0L;
        
        return DashboardStatsDTO.builder()
                .totalUsers(totalUsers)
                .totalEvents(totalEvents)
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .activeEvents(activeEvents)
                .upcomingEvents(upcomingEvents)
                .completedEvents(completedEvents)
                .cancelledEvents(cancelledEvents)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentBookingDTO> getRecentBookings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "bookingTime"));
        
        // Lấy bookings với user và event đã được fetch
        List<Booking> bookings = bookingRepository.findAllWithDetails()
                .stream()
                .sorted((b1, b2) -> b2.getBookingTime().compareTo(b1.getBookingTime()))
                .limit(limit)
                .collect(Collectors.toList());
        
        return bookings.stream()
                .map(this::toRecentBookingDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentEventDTO> getRecentEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<Event> events = eventRepository.findAll(pageable).getContent();
        
        return events.stream()
                .map(this::toRecentEventDTO)
                .collect(Collectors.toList());
    }

    private RecentBookingDTO toRecentBookingDTO(Booking booking) {
        String userName = booking.getUser() != null ? booking.getUser().getFullName() : "N/A";
        String eventName = booking.getEvent() != null ? booking.getEvent().getEventName() : "N/A";
        
        return RecentBookingDTO.builder()
                .id(booking.getBookingId())
                .user(userName)
                .event(eventName)
                .bookingTime(booking.getBookingTime())
                .status(booking.getStatus())
                .build();
    }

    private RecentEventDTO toRecentEventDTO(Event event) {
        // Đếm số booking cho sự kiện này (BOOKED và CHECKED_IN)
        Long bookedCount = bookingRepository.countByEventEventIdAndStatus(
                event.getEventId(), 
                BookingStatus.BOOKED
        );
        
        // Đếm thêm CHECKED_IN bookings
        Long checkedInCount = bookingRepository.countByEventEventIdAndStatus(
                event.getEventId(),
                BookingStatus.CHECKED_IN
        );
        
        int totalBooked = bookedCount.intValue() + checkedInCount.intValue();
        
        return RecentEventDTO.builder()
                .id(event.getEventId())
                .name(event.getEventName())
                .startTime(event.getStartTime())
                .capacity(event.getCapacity())
                .booked(totalBooked)
                .status(event.getStatus())
                .build();
    }
}

