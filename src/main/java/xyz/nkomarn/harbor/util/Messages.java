package xyz.nkomarn.harbor.util;

import com.google.common.base.Enums;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
    private final MiniMessage miniMessage;
    private final Harbor harbor;
    private final Config config;
    private final HashMap<UUID, BossBar> bossBars;
    private final boolean papiPresent;

    public Messages(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.config = harbor.getConfiguration();
        this.bossBars = new HashMap<>();
        this.papiPresent = harbor.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.miniMessage = harbor.getMiniMessage();

        for (World world : Bukkit.getWorlds()) {
            if (harbor.getChecker().isBlacklisted(world)) {
                return;
            }

            registerBar(world);
        }
    }

    /**
     * Sends a title/subtitle message to the given players
     *
     * @param title The title message to send.
     * @param subTitle The subtitle message to send.
     * @param players Players to send the title to
     */
    public void sendTitleMessage(@NotNull List<Player> players, @NotNull String title, @NotNull String subTitle) {
        if (!config.getBoolean("messages.title.enabled") || (title.isEmpty() & subTitle.isEmpty())) {
            return;
        }

        for (Player player : players) {
            sendTitleMessage(player, title, subTitle);
        }
    }

    /**
     * Sends a title/subtitle message to the given player
     *
     * @param title The title message to send.
     * @param subTitle The subtitle message to send.
     * @param player Player to send the title to
     */
    public void sendTitleMessage(@NotNull Player player, @NotNull String title, @NotNull String subTitle) {
        if (!config.getBoolean("messages.title.enabled") || (title.isEmpty() & subTitle.isEmpty())) {
            return;
        }

        int fadeTicks = config.getInteger("messages.title.fade-ticks");
        int stayTicks = config.getInteger("messages.title.stay-ticks");
        player.showTitle(Title.title(
                prepareMessage(player, title),
                prepareMessage(player, subTitle),
                Title.Times.times(Ticks.duration(0), Ticks.duration(stayTicks), Ticks.duration(fadeTicks))));
    }

    /**
     * Sets the message for the given world's bossbar.
     *
     * @param world      The world in which the bossbar exists.
     * @param message    The message to set.
     * @param color      The bossbar color to set.
     * @param percentage The bossbar percentage to set.
     */
    public void sendBossBarMessage(@NotNull World world, @NotNull String message, @NotNull String color, float percentage) {
        if (!config.getBoolean("messages.bossbar.enabled") || message.isEmpty()) {
            return;
        }

        BossBar bar = bossBars.get(world.getUID());

        if (bar == null) {
            return;
        }

        if (percentage == 0) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
            return;
        }

        bar.name(harbor.getMessages().prepareMessage(world, message));
        bar.color(Enums.getIfPresent(BossBar.Color.class, color).or(BossBar.Color.BLUE));
        bar.progress(percentage);
        world.getPlayers().forEach(p -> p.showBossBar(bar));
    }

    /**
     * Replaces all available placeholders in a given string.
     *
     * @param world   The world context.
     * @param message The raw message with placeholders.
     * @return The provided message with placeholders replaced with correct values for the world context.
     */
    public @NotNull Component prepareMessage(@NotNull World world, @NotNull String message) {
        TagResolver.@NotNull Builder placeholders = TagResolver.builder();

        worldPlaceholders(world, placeholders);

        return miniMessage.deserialize(message, placeholders.build());
    }

    public @NotNull Component prepareMessage(@NotNull Player player, @NotNull String message) {
        World world = player.getLocation().getWorld();

        if (papiPresent) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        TagResolver.@NotNull Builder placeholders = TagResolver.builder();

        placeholders.resolver(Placeholder.parsed("player", player.getName()));
        placeholders.resolver(Placeholder.component("displayname", player.displayName()));

        if(world != null) {
            worldPlaceholders(world, placeholders);
        }

        return miniMessage.deserialize(message, placeholders.build());
    }

    private void worldPlaceholders(World world, TagResolver.Builder builder) {
        Checker checker = harbor.getChecker();
        long time = world.getTime();

        builder.resolver(Placeholder.parsed("sleeping", String.valueOf(checker.getSleepingPlayers(world).size())));
        builder.resolver(Placeholder.parsed("players", String.valueOf(checker.getPlayers(world))));
        builder.resolver(Placeholder.parsed("needed", String.valueOf(checker.getSkipAmount(world))));
        builder.resolver(Placeholder.parsed("timescale", String.format("%.2f", checker.getTimescale(world))));
        builder.resolver(Placeholder.parsed("12h", String.valueOf(Time.ticksTo12Hours(time))));
        builder.resolver(Placeholder.parsed("24h", String.format("%02d", Time.ticksTo24Hours(time))));
        builder.resolver(Placeholder.parsed("min", String.format("%02d", Time.ticksToMinutes(time))));
        builder.resolver(Placeholder.parsed("mer_upper", Time.ticksIsAM(time) ? "AM" : "PM"));
        builder.resolver(Placeholder.parsed("mer_lower", Time.ticksIsAM(time) ? "am" : "pm"));
        builder.resolver(Placeholder.parsed("more", String.valueOf(checker.getNeeded(world))));
    }

    /**
     * Creates a new bossbar for the given world if one isn't already present.
     *
     * @param world The world in which to create the bossbar.
     */
    private void registerBar(@NotNull World world) {
        bossBars.computeIfAbsent(world.getUID(), uuid ->
                BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
    }

    /**
     * Hides the bossbar for the given world if one is present.
     *
     * @param world The world in which to hide the bossbar.
     */
    public void clearBar(@NotNull World world) {
        Optional.ofNullable(bossBars.get(world.getUID()))
                .ifPresent(bar -> Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar)));
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        registerBar(event.getWorld());
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Optional.ofNullable(bossBars.get(event.getFrom().getUID()))
                .ifPresent(bossBar -> event.getPlayer().hideBossBar(bossBar));
    }
}
