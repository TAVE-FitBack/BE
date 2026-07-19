package com.fitback.domain.schedule.entity;

import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.schedule.enums.TaskType;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "task_checklist")
@Getter
public class TaskChecklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private TaskType taskType;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "is_done", nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    public void syncFromSchedule(String title, TaskType taskType, LocalDate dueDate) {
        this.title = title;
        this.taskType = taskType;
        this.dueDate = dueDate;
    }

    public void updateDone(boolean done, OffsetDateTime doneAt) {
        this.done = done;
        this.doneAt = done ? doneAt : null;
    }
}
