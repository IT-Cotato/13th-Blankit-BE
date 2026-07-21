package com.cotato.blankit.domain.timetable.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "timetable")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timetable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long timetableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private byte dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 100)
    private String place;

    @Column(nullable = false, length = 20)
    private String color;

    public static Timetable create(User user, byte dayOfWeek, LocalTime startTime, LocalTime endTime,
                                   String title, String place, String color) {
        Timetable timetable = new Timetable();
        timetable.user = user;
        timetable.dayOfWeek = dayOfWeek;
        timetable.startTime = startTime;
        timetable.endTime = endTime;
        timetable.title = title;
        timetable.place = place;
        timetable.color = color;
        return timetable;
    }

    public void update(Byte dayOfWeek, LocalTime startTime, LocalTime endTime,
                       String title, String place, String color) {
        if (dayOfWeek != null) this.dayOfWeek = dayOfWeek;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (title != null) this.title = title;
        if (place != null) this.place = place;
        if (color != null) this.color = color;
    }
}
