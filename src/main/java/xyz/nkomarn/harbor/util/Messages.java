package xyz.nkomarn.harbor.util;

import com.google.common.base.Enums;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.task.Checker;

import java.util.*;

public class Messages implements Listener {

    private final Harbor harbor;
    private final Config config;
    private final Random random;
    private final HashMap<UUID, BossBar> bossBars;
    private final boolean papiPresent;

    public Messages(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.config = harbor.getConfiguration();
        this.random = new Random();
        this.bossBars = new HashMap<>();
        this.papiPresent = harbor.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        for (World world : Bukkit.getWorlds()) {
            if (harbor.getChecker().isBlacklisted(world)) {
                return;
            }

            registerBar(world);
        }
    }

    /**
     * Sets the message for the given world's bossbar.
     *
     * @param world      The world in which the bossbar exists.
     * @param message    The message to set.
     * @param color      The bossbar color to set.
     * @param percentage The bossbar percentage to set.
     */
    public void sendBossBarMessage(@NotNull World world, @NotNull String message, @NotNull String color, double percentage) {
        if (!config.getBoolean("messages.bossbar.enabled") || message.length() < 1) {
            return;
        }

        BossBar bar = bossBars.get(world.getUID());

        if (bar == null) {
            return;
        }

        if (percentage == 0) {
            bar.removeAll();
            return;
        }

        bar.setTitle(harbor.getMessages().prepareMessage(world, message));
        bar.setColor(Enums.getIfPresent(BarColor.class, color).or(BarColor.BLUE));
        bar.setProgress(percentage);
        world.getPlayers().forEach(bar::addPlayer);
    }

    /**
     * Replaces all available placeholders in a given string.
     *
     * @param world   The world context.
     * @param message The raw message with placeholders.
     * @return The provided message with placeholders replaced with correct values for the world context.
     */
    @NotNull
    public String prepareMessage(@NotNull World world, @NotNull String message) {
        Checker checker = harbor.getChecker();
        return ChatColor.translateAlternateColorCodes('&', message
                .replace("[sleeping]", String.valueOf(checker.getSleepingPlayers(world).size()))
                .replace("[players]", String.valueOf(checker.getPlayers(world)))
                .replace("[needed]", String.valueOf(checker.getSkipAmount(world)))
                .replace("[more]", String.valueOf(checker.getNeeded(world))));
    }

    @NotNull
    public String prepareMessage(@NotNull Player player, @NotNull String message) {
        String output = ChatColor.translateAlternateColorCodes('&', message
                .replace("[player]", player.getName())
                .replace("[displayname]", player.getDisplayName()));

        if (papiPresent) {
            output = PlaceholderAPI.setPlaceholders(player, output);
        }

        return output;
    }

    /**
     * Creates a new bossbar for the given world if one isn't already present.
     *
     * @param world The world in which to create the bossbar.
     */
    private void registerBar(@NotNull World world) {
        bossBars.computeIfAbsent(world.getUID(), uuid -> Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID));
    }

    /**
     * Hides the bossbar for the given world if one is present.
     *
     * @param world The world in which to hide the bossbar.
     */
    public void clearBar(@NotNull World world) {
        Optional.ofNullable(bossBars.get(world.getUID())).ifPresent(BossBar::removeAll);
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        registerBar(event.getWorld());
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Optional.ofNullable(bossBars.get(event.getFrom().getUID())).ifPresent(bossBar -> bossBar.removePlayer(event.getPlayer()));
    }
}
