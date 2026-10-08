package com.hmdp.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ConversationStoreTest {
    @Test
    void isolatesOwnersAndClearsDeletedAndExpiredConversations() {
        var store = new ConversationStore(MessageWindowChatMemory.builder().maxMessages(20).build());
        var c = store.get("user-a", null);
        assertThat(store.get("user-a", c.id)).isSameAs(c);
        assertThatThrownBy(() -> store.get("user-b", c.id)).isInstanceOf(ResponseStatusException.class);
        store.delete("user-a", c.id);
        assertThatThrownBy(() -> store.get("user-a", c.id)).isInstanceOf(ResponseStatusException.class);
        var expired = store.get("user-a", null);
        expired.touched = 0;
        assertThatThrownBy(() -> store.get("user-a", expired.id)).isInstanceOf(ResponseStatusException.class);
        assertThat(store.get("user-a", null).selectedShop).isNull();
    }
}
