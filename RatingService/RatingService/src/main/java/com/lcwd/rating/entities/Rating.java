package com.lcwd.rating.entities;

import jakarta.persistence.*;
import lombok.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@ToString
@Table(name = "user_ratings")
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ratingId;
    private String userId;
    private String hotelId;
    private  int rating;
    private  String feedback;
    @Transient
    private Hotel hotel;
}

