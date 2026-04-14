package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.out.persistence.JpaConversationAdapter;
import com.aiarchitect.terraquery.config.PersistenceScopeConfig;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.model.Conversation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
@Import(JpaConversationAdapter.class)
@EnableConfigurationProperties(PersistenceScopeConfig.class)
@TestPropertySource(properties = {
        "terra-query.conversation.persistence-scope=MESSAGES_ONLY"
})
class JpaConversationAdapterTest {

    @Autowired JpaConversationAdapter adapter;

    @Test
    void save_newConversation_persistsToDatabase() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "What are the deadliest floods?"));

        adapter.save(conv);

        assertThat(adapter.findById(conv.id())).isPresent();
    }

    @Test
    void findById_existingConversation_returnsDomainObject() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "Tell me about earthquakes."));
        conv.addMessage(ChatMessage.assistantMessage(conv.id(), "Earthquakes are seismic events."));

        adapter.save(conv);

        var found = adapter.findById(conv.id());
        assertThat(found).isPresent();
        assertThat(found.get().messages()).hasSize(2);
        assertThat(found.get().messages().get(0).role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(found.get().messages().get(1).role()).isEqualTo(ChatMessage.MessageRole.ASSISTANT);
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        assertThat(adapter.findById("does-not-exist")).isEmpty();
    }

    @Test
    void deleteById_removesConversation() {
        Conversation conv = Conversation.newConversation();
        adapter.save(conv);

        adapter.deleteById(conv.id());

        assertThat(adapter.existsById(conv.id())).isFalse();
    }

    @Test
    void existsById_afterSave_returnsTrue() {
        Conversation conv = Conversation.newConversation();
        adapter.save(conv);

        assertThat(adapter.existsById(conv.id())).isTrue();
    }

    @Test
    void save_updatesExistingConversation_appendedMessages() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "First message"));
        adapter.save(conv);

        conv.addMessage(ChatMessage.assistantMessage(conv.id(), "First reply"));
        conv.addMessage(ChatMessage.userMessage(conv.id(), "Second message"));
        adapter.save(conv);

        var updated = adapter.findById(conv.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().messages()).hasSize(3);
    }

    @Test
    void findAll_returnsAllConversations() {
        Conversation c1 = Conversation.newConversation();
        Conversation c2 = Conversation.newConversation();
        adapter.save(c1);
        adapter.save(c2);

        assertThat(adapter.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}
