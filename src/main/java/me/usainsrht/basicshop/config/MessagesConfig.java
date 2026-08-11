package me.usainsrht.basicshop.config;

import me.usainsrht.yamlmessage.YamlMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed wrapper around messages.yml — prefix, player messages, and sound messages.
 */
public final class MessagesConfig {

    private final String prefix;
    private final Map<String, YamlMessage> messages;

    public MessagesConfig(FileConfiguration cfg) {
        this.prefix = cfg.getString("prefix", "<dark_gray>[<gold>BasicShop</gold>]</dark_gray> ");

        Map<String, YamlMessage> msgs = new HashMap<>();
        for (String key : cfg.getKeys(false)) {
            if ("prefix".equalsIgnoreCase(key)) continue;
            Object raw = cfg.get(key);
            if (raw != null) {
                msgs.put(key, YamlMessage.parse(raw));
            }
        }
        this.messages = Collections.unmodifiableMap(msgs);
    }

    public String getPrefix() {
        return prefix;
    }

    public YamlMessage getMessage(String key) {
        return messages.getOrDefault(key, YamlMessage.chat("<red>Missing message: " + key));
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        if (sender == null) return;
        getMessage(key).send(sender, prefix, resolvers);
    }

    public void sendRaw(CommandSender sender, String key, TagResolver... resolvers) {
        if (sender == null) return;
        getMessage(key).send(sender, resolvers);
    }

    public void sendOffline(OfflinePlayer player, String key, TagResolver... resolvers) {
        if (player == null) return;
        getMessage(key).send(player, prefix, resolvers);
    }

    public void sendOfflineRaw(OfflinePlayer player, String key, TagResolver... resolvers) {
        if (player == null) return;
        getMessage(key).send(player, resolvers);
    }
}
