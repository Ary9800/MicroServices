
package com.microservices.user.service.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.user.service.entities.Rating;
import com.microservices.user.service.entities.User;
import com.microservices.user.service.exceptions.ResourceNotFoundException;
import com.microservices.user.service.repositories.UserRepository;
import com.microservices.user.service.services.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;


    private final BlockingQueue<String> userIdQueue = new LinkedBlockingQueue<>();

    private final ConcurrentMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, User> userMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Thread consumerThread = new Thread(this::processQueue);
        consumerThread.start();
    }

    private void processQueue() {
        while (true) {
            try {
                String userId = userIdQueue.take();
                processUserId(userId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processUserId(String userId) {
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(userId, latch);
        kafkaTemplate.send("user-topic", userId);

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public List<User> getAllUser() {
        List<User> users = userRepository.findAll();
        List<String> userIds = new ArrayList<>();

        for (User user : users) {
            String userId = String.valueOf(user.getUserId());
            userIds.add(userId);
            userIdQueue.add(userId);
        }

        try {
            for (String userId : userIds) {
                CountDownLatch latch = latches.get(userId);
                if (latch != null) {
                    latch.await(10, TimeUnit.SECONDS);
                }
            }

            List<User> result = new ArrayList<>();
            for (String userId : userIds) {
                User user = userMap.get(userId);
                if (user == null) {
                    user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                }
                result.add(user);
            }

            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to get users with ratings", e);
        }
    }

    @Override
    public User getUser(String userId) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            latches.put(userId, latch);

            kafkaTemplate.send("user-topic", userId);
            latch.await(10, TimeUnit.SECONDS);

            return userMap.get(userId);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to get user with rating", e);
        }
    }
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 5000, multiplier = 2))
    @KafkaListener(topics = "rating-user-topic", groupId = "user-group")
    public void handleRatingMessage(String ratingsJson) {
        try {
            List<Rating> newRatings = objectMapper.readValue(ratingsJson, objectMapper.getTypeFactory().constructCollectionType(List.class, Rating.class));
            for (Rating rating : newRatings) {
                String userId = rating.getUserId();
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User with given id is not found on server !! : " + userId));

                user.setRatings(newRatings);
                userMap.put(userId, user);

                CountDownLatch latch = latches.get(userId);
                if (latch != null) {
                    latch.countDown();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
     @DltHandler
    public void handleDltMessage(String ratingsJson) {
        // Handle the message that could not be processed after retries
        System.err.println("Message sent to DLT: " + ratingsJson);
    }
}
