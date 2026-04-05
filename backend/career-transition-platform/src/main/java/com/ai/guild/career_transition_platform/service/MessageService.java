package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.MessageDto;
import com.ai.guild.career_transition_platform.entity.Message;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.repository.MessageRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public Message saveMessage(Long userId, String context, String sender, String content, String type) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
		int max = messageRepository.findMaxOrderIndexByUserIdAndContext(userId, context);
		int next = max + 1;
		String bodyJson = toBodyJson(sender, type, content);
		Message message = Message.builder()
				.user(user)
				.context(context)
				.orderIndex(next)
				.body(bodyJson)
				.build();
		Message saved = messageRepository.save(message);
		log.info("Message saved: userId={} context={} orderIndex={} sender={} type={}", userId, context, next, sender, type);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<Message> getMessages(Long userId, String context) {
		List<Message> list = messageRepository.findByUser_IdAndContextOrderByOrderIndexAsc(userId, context);
		log.info("Messages loaded: userId={} context={} count={}", userId, context, list.size());
		return list;
	}

	@Transactional(readOnly = true)
	public List<MessageDto> getMessagesAsDto(Long userId, String context) {
		return getMessages(userId, context).stream()
				.map(this::toDto)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public boolean hasMessagesForContext(Long userId, String context) {
		return !getMessages(userId, context).isEmpty();
	}

	private String toBodyJson(String sender, String type, String content) {
		try {
			return objectMapper.createObjectNode()
					.put("sender", sender)
					.put("type", type)
					.put("content", content)
					.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize message body", e);
		}
	}

	private MessageDto toDto(Message m) {
		String sender = "UNKNOWN";
		String type = "TEXT";
		String content = m.getBody() != null ? m.getBody() : "";
		if (m.getBody() != null && m.getBody().trim().startsWith("{")) {
			try {
				JsonNode n = objectMapper.readTree(m.getBody());
				if (n.hasNonNull("sender")) {
					sender = n.get("sender").asText(sender);
				}
				if (n.hasNonNull("type")) {
					type = n.get("type").asText(type);
				}
				if (n.hasNonNull("content")) {
					content = n.get("content").asText(content);
				}
			} catch (JsonProcessingException e) {
				log.debug("Message body not JSON, using raw body: id={}", m.getId());
			}
		}
		return MessageDto.builder()
				.id(m.getId())
				.orderIndex(m.getOrderIndex())
				.sender(sender)
				.type(type)
				.content(content)
				.createdAt(m.getCreatedAt())
				.build();
	}
}
