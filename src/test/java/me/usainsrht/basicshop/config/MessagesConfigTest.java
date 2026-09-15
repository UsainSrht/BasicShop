package me.usainsrht.basicshop.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class MessagesConfigTest {

    @Test
    public void testEmptyMessageSuppression() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "[Shop] ");
        config.set("top-sellers-footer", "");
        config.set("empty-list", List.of(""));
        config.set("blank-spacer", " ");
        config.set("normal-msg", "<green>Success!</green>");

        MessagesConfig messagesConfig = new MessagesConfig(config);

        // Empty message check
        assertTrue(messagesConfig.getMessage("top-sellers-footer").isEmpty());
        assertTrue(messagesConfig.getMessage("empty-list").isEmpty());
        assertFalse(messagesConfig.getMessage("blank-spacer").isEmpty());
        assertFalse(messagesConfig.getMessage("normal-msg").isEmpty());

        CommandSender sender = mock(CommandSender.class);

        // Sending empty message should do nothing
        messagesConfig.send(sender, "top-sellers-footer");
        messagesConfig.sendRaw(sender, "top-sellers-footer");
        messagesConfig.send(sender, "empty-list");
        verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.any(Component.class));
    }

    @Test
    public void testBlankSpacerSendsWithoutPrefix() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "[Shop] ");
        config.set("spacer", " ");
        config.set("normal-msg", "Hello");

        MessagesConfig messagesConfig = new MessagesConfig(config);
        CommandSender sender = mock(CommandSender.class);

        // Sending single space blank spacer should not prepend prefix
        messagesConfig.send(sender, "spacer");

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(captor.capture());

        String plainText = PlainTextComponentSerializer.plainText().serialize(captor.getValue());
        assertEquals(" ", plainText);
        assertFalse(plainText.contains("[Shop]"));
    }
}
