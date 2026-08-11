package me.usainsrht.basicshop.util;

import me.usainsrht.basicshop.config.MessagesConfig;
import me.usainsrht.yamlmessage.YamlMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Plays configured shop sound-only messages for a player using YamlMessageAPI.
 */
public final class ShopSounds {

    private ShopSounds() {}

    public static void play(Player player, YamlMessage message) {
        if (player == null || message == null) return;
        message.send((CommandSender) player);
    }

    public static void play(Player player, MessagesConfig messagesConfig, String soundKey) {
        if (player == null || messagesConfig == null) return;
        messagesConfig.sendRaw(player, soundKey);
    }
}
