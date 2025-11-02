package com.seatify.service;

import com.seatify.dto.response.PublicNewsResponseDTO;
import com.seatify.exception.ResourceNotFoundException;
import com.seatify.model.News;
import com.seatify.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;

    @Override
    public List<PublicNewsResponseDTO> getAllPublishedNews() {
        List<News> publishedNews = newsRepository.findByIsPublishedTrueOrderByPublishedAtDesc();
        return publishedNews.stream()
                .map(this::convertToPublicDTO)
                .toList();
    }

    @Override
    public PublicNewsResponseDTO getPublishedNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found with id: " + id));
        
        if (!news.getIsPublished()) {
            throw new ResourceNotFoundException("News is not published");
        }
        
        return convertToPublicDTO(news);
    }

    private PublicNewsResponseDTO convertToPublicDTO(News news) {
        return PublicNewsResponseDTO.builder()
                .newsId(news.getNewsId())
                .eventId(news.getEvent() != null ? news.getEvent().getEventId() : null)
                .eventName(news.getEvent() != null ? news.getEvent().getEventName() : null)
                .title(news.getTitle())
                .content(news.getContent())
                .thumbnail(news.getThumbnail())
                .publishedAt(news.getPublishedAt())
                .createdAt(news.getPublishedAt()) // Dùng publishedAt 
                .build();
    }
}
