package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.sessionId IN :sessionIds AND cm.senderType = 'USER'")
    long countUserMessagesBySessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
