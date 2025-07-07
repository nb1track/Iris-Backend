package com.chaptime.backend.service;

import com.chaptime.backend.dto.HistoricalPointDTO;
import com.chaptime.backend.dto.PhotoResponseDTO;
import com.chaptime.backend.dto.PlaceDTO;
import com.chaptime.backend.model.Photo;
import com.chaptime.backend.repository.FeedRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final ObjectMapper objectMapper;

    public FeedService(FeedRepository feedRepository, ObjectMapper objectMapper) {
        this.feedRepository = feedRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PlaceDTO> generateHistoricalFeed(List<HistoricalPointDTO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            String historyJson = objectMapper.writeValueAsString(history);
            List<Photo> photos = feedRepository.findPhotosMatchingHistoricalBatch(historyJson);

            // Gruppiere die gefundenen Fotos nach ihrem Ort (Place)
            Map<PlaceDTO, List<PhotoResponseDTO>> groupedByPlace = photos.stream()
                    .collect(Collectors.groupingBy(
                            // Schlüssel für die Gruppierung: das PlaceDTO des Fotos
                            photo -> new PlaceDTO(
                                    photo.getPlace().getId(),
                                    photo.getPlace().getGooglePlaceId(),
                                    photo.getPlace().getName(),
                                    photo.getPlace().getAddress(),
                                    null // Foto-Liste ist hier noch nicht relevant
                            ),
                            // Werte: eine Liste der PhotoResponseDTOs für jeden Ort
                            Collectors.mapping(
                                    photo -> new PhotoResponseDTO(
                                            photo.getId(),
                                            photo.getStorageUrl(),
                                            photo.getUploader().getUsername()
                                    ),
                                    Collectors.toList()
                            )
                    ));

            // Wandle die Map in die finale Listenstruktur um
            return groupedByPlace.entrySet().stream()
                    .map(entry -> new PlaceDTO(
                            entry.getKey().id(),
                            entry.getKey().googlePlaceId(),
                            entry.getKey().name(),
                            entry.getKey().address(),
                            entry.getValue() // Füge die Liste der Fotos hinzu
                    ))
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing historical data", e);
        }
    }
}