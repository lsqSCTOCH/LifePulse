package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Component
public class ConversationStore {
    private final Map<String, Conversation> conversations = new HashMap<>();
    private final ChatMemory memory;
    public ConversationStore(ChatMemory memory) { this.memory = memory; }

    public static class Conversation {
        final String id = UUID.randomUUID().toString();
        final String owner;
        volatile long touched = System.currentTimeMillis();
        JsonNode shops;
        JsonNode selectedShop;
        Conversation(String owner) { this.owner = owner; }
    }

    public synchronized Conversation get(String owner, String id) {
        long expiry = System.currentTimeMillis() - 30 * 60_000L;
        conversations.values().removeIf(c -> {
            if (c.touched < expiry) { memory.clear(c.id); return true; }
            return false;
        });
        if (id == null || id.isBlank()) {
            if (conversations.size() >= 1000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "会话数量已达上限");
            Conversation created = new Conversation(owner);
            conversations.put(created.id, created);
            return created;
        }
        Conversation found = conversations.get(id);
        if (found == null || !found.owner.equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或已过期");
        found.touched = System.currentTimeMillis();
        return found;
    }

    public synchronized void delete(String owner, String id) {
        Conversation found = get(owner, id);
        conversations.remove(found.id);
        memory.clear(found.id);
    }
}
