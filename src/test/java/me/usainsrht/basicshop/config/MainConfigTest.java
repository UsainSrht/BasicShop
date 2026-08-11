package me.usainsrht.basicshop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainConfigTest {

    @Test
    public void testCommandsConfigParsing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("commands.root", "shop");
        config.set("commands.aliases", List.of("bs", "basicshop"));
        config.set("commands.subcommands.help", "help");
        config.set("commands.subcommands.reload", "reload");
        config.set("commands.subcommands.quicksell", "quicksell");
        config.set("commands.subcommands.quicksell-aliases", List.of("qs", "sell"));
        config.set("commands.subcommands.quicksell-hand", "hand");
        config.set("commands.subcommands.quicksell-inventory", "inv");

        MainConfig mainConfig = new MainConfig(config);
        MainConfig.CommandsConfig cmdCfg = mainConfig.getCommandsConfig();

        assertEquals("shop", cmdCfg.root());
        assertEquals(List.of("bs", "basicshop"), cmdCfg.aliases());
        assertEquals("quicksell", cmdCfg.sub("quicksell"));
        assertEquals("hand", cmdCfg.sub("quicksell-hand"));
        assertEquals("inv", cmdCfg.sub("quicksell-inventory"));
        assertEquals(List.of("qs", "sell"), cmdCfg.quicksellAliases());
    }

    @Test
    public void testCommandsConfigDefaults() {
        YamlConfiguration config = new YamlConfiguration();

        MainConfig mainConfig = new MainConfig(config);
        MainConfig.CommandsConfig cmdCfg = mainConfig.getCommandsConfig();

        assertEquals("shop", cmdCfg.root());
        assertEquals("quicksell", cmdCfg.sub("quicksell"));
        assertTrue(cmdCfg.quicksellAliases().isEmpty());
    }
}
