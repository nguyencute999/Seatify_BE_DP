package com.seatify.service;

import com.seatify.model.Event;
import com.seatify.model.constants.EventStatus;
import com.seatify.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service tự động cập nhật status của sự kiện dựa trên thời gian
 * 
 * Logic:
 * - UPCOMING -> ONGOING: khi startTime <= now và endTime > now
 * - ONGOING -> FINISHED: khi endTime <= now
 * - UPCOMING -> FINISHED: khi endTime <= now (bỏ qua ONGOING nếu thời gian ngắn)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatusUpdateService {

    private final EventRepository eventRepository;

    /**
     * Tự động cập nhật status của sự kiện mỗi 1 phút
     * Có thể điều chỉnh thời gian chạy theo nhu cầu (ví dụ: 5 phút, 15 phút)
     */
    @Scheduled(fixedRate = 60000) // Chạy mỗi 1 phút (60000ms)
    @Transactional
    public void updateEventStatuses() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Checking and updating event statuses at {}", now);

        //Update UPCOMING -> ONGOING: sự kiện đã bắt đầu nhưng chưa kết thúc
        List<Event> upcomingEvents = eventRepository.findByStatus(EventStatus.UPCOMING);
        int ongoingCount = 0;
        for (Event event : upcomingEvents) {
            if (event.getStartTime().isBefore(now) || event.getStartTime().isEqual(now)) {
                if (event.getEndTime().isAfter(now)) {
                    // Sự kiện đã bắt đầu nhưng chưa kết thúc -> ONGOING
                    event.setStatus(EventStatus.ONGOING);
                    eventRepository.save(event);
                    ongoingCount++;
                    log.info("Event {} changed from UPCOMING to ONGOING", event.getEventId());
                } else {
                    // Sự kiện đã kết thúc -> FINISHED (bỏ qua ONGOING)
                    event.setStatus(EventStatus.FINISHED);
                    eventRepository.save(event);
                    log.info("Event {} changed from UPCOMING to FINISHED", event.getEventId());
                }
            }
        }

        //Update ONGOING -> FINISHED: sự kiện đã kết thúc
        List<Event> ongoingEvents = eventRepository.findByStatus(EventStatus.ONGOING);
        int finishedCount = 0;
        for (Event event : ongoingEvents) {
            if (event.getEndTime().isBefore(now) || event.getEndTime().isEqual(now)) {
                event.setStatus(EventStatus.FINISHED);
                eventRepository.save(event);
                finishedCount++;
                log.info("Event {} changed from ONGOING to FINISHED", event.getEventId());
            }
        }

        if (ongoingCount > 0 || finishedCount > 0) {
            log.info("Updated {} events to ONGOING, {} events to FINISHED", ongoingCount, finishedCount);
        }
    }
}
