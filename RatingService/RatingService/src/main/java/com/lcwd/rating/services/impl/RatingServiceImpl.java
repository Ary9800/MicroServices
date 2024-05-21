package com.lcwd.rating.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcwd.rating.entities.Hotel;
import com.lcwd.rating.entities.Rating;
import com.lcwd.rating.repository.RatingRepository;
import com.lcwd.rating.services.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class RatingServiceImpl implements RatingService {

    private static final Logger logger = LoggerFactory.getLogger(RatingServiceImpl.class);
    List<Rating> ratings;
    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private KafkaTemplate<String,String>kafkaTemplate;

    @Override
    public Rating create(Rating rating) {
        return ratingRepository.save(rating);
    }

    @Override
    public List<Rating> getRatings() {
        return ratingRepository.findAll();
    }

    @Override
    @KafkaListener(topics = "user-topic", groupId = "rating-group")
    public void  getRatingByUserId(String userId) {

        System.out.println("in method");
        System.out.println(userId);
        ratings = ratingRepository.findByuserId(userId);
        System.out.print(ratings);


        for (Rating rating : ratings) {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("rating-topic", rating.getHotelId());
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("Sent message with hotelId: {} with offset: {}", rating.getHotelId(), result.getRecordMetadata().offset());
                } else {
                    logger.error("Failed to send message with hotelId: {}", rating.getHotelId(), ex);
                }
            });
        }
    }

    @KafkaListener(topics = "hotel-topic", groupId = "rating-group")
    public void handleHotelDetails(String hotelDetailsJson) {
        System.out.println("in method after listening hotel_topic");

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Hotel hotel = objectMapper.readValue(hotelDetailsJson, Hotel.class);

            for (Rating rating : ratings) {
                if (rating.getHotelId().equals(hotel.getId())) {
                    rating.setHotel(hotel);
                    break;
                }
            }
            boolean allRatingsHaveHotelDetails = ratings.stream().allMatch(r -> r.getHotel() != null);


            if (allRatingsHaveHotelDetails) {
                String ratingsJson = serializeRatingsToJson(ratings);
                System.out.println(ratingsJson);
                kafkaTemplate.send("rating-user-topic", ratingsJson);
            }
        } catch (JsonProcessingException e) {

            e.printStackTrace();
        }
    }
    private String serializeRatingsToJson(List<Rating> ratings) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(ratings);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public List<Rating> getRatingByHotelId(String hotelId) {
        return ratingRepository.findByHotelId(hotelId);
    }
}
