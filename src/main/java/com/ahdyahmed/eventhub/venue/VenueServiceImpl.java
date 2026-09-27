package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    @Override
    @Transactional
    public VenueResponse create(VenueRequest request) {
        Venue saved = venueRepository.save(venueMapper.toEntity(request));
        return venueMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getById(Long id) {
        return venueMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VenueResponse> getAll() {
        return venueRepository.findAll().stream()
                .map(venueMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        Venue venue = findOrThrow(id);
        venueMapper.updateEntity(venue, request);
        return venueMapper.toResponse(venue);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        venueRepository.delete(findOrThrow(id));
    }

    private Venue findOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id " + id));
    }

}
