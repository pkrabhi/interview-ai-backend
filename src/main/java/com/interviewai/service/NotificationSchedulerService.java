package com.interviewai.service;

import com.interviewai.entity.InterviewSession;
import com.interviewai.entity.User;
import com.interviewai.repository.InterviewSessionRepository;
import com.interviewai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    /**
     * Every hour: nudge candidates who left a session ACTIVE (not completed) for more than
     * 3 hours. reminderSent guards against re-notifying the same session on every run.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional // scheduled jobs have no open Hibernate session by default (unlike web
                    // requests), and session.getUser() below is a LAZY relation — without this,
                    // accessing it here would throw LazyInitializationException.
    public void remindIncompleteInterviews() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(3);
        List<InterviewSession> stale = sessionRepository.findByStatusAndCreatedAtBeforeAndReminderSentFalse("ACTIVE", cutoff);

        for (InterviewSession session : stale) {
            User user = session.getUser();
            if (user == null || !Boolean.TRUE.equals(user.getNotificationsEnabled()) || user.getPushToken() == null) continue;

            Map<String, Object> data = new HashMap<>();
            data.put("type", "resume_interview");
            data.put("sessionId", session.getId());
            pushNotificationService.send(
                    user.getPushToken(),
                    "Unfinished interview waiting",
                    "You left a " + session.getRole() + " interview mid-way — pick up where you left off?",
                    data
            );

            session.setReminderSent(true);
            sessionRepository.save(session);
        }
        if (!stale.isEmpty()) log.info("Sent {} resume-interview reminders", stale.size());
    }

    /**
     * Once a day: nudge users who haven't started any session yet today.
     */
    @Scheduled(cron = "0 0 19 * * *") // 7pm server time
    public void remindDailyPractice() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<User> candidates = userRepository.findByNotificationsEnabledTrueAndPushTokenIsNotNull();

        int sent = 0;
        for (User user : candidates) {
            boolean practicedToday = sessionRepository.existsByUserIdAndCreatedAtAfter(user.getId(), todayStart);
            if (practicedToday) continue;

            Map<String, Object> data = new HashMap<>();
            data.put("type", "daily_reminder");
            pushNotificationService.send(
                    user.getPushToken(),
                    "Ready to practice today?",
                    "Aryan is ready whenever you are — even 10 minutes keeps you sharp.",
                    data
            );
            sent++;
        }
        if (sent > 0) log.info("Sent {} daily practice reminders", sent);
    }
}
