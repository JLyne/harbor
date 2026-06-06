package xyz.nkomarn.harbor.task;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.clock.WorldClock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;
import xyz.nkomarn.harbor.util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Time checker for when paper's time.affects-all-worlds is set to true.
 * World clocks are global and can be used on multiple worlds at once, so treat all worlds with the same clock together.
 * Only one AccelerateNighTask is created per clock, as setting the time in one world will affect the rest.
 */
public final class GlobalTimeChecker extends Checker implements Listener {
    private final HashMap<Holder<WorldClock>, Set<World>> clockWorlds; // Worlds with the same world clock set
    private final Set<Holder<WorldClock>> skippingClocks;

    public GlobalTimeChecker(@NotNull Harbor harbor) {
        super(harbor);
        harbor.getSLF4JLogger().info("Using global time checker");
        clockWorlds = new HashMap<>();
        skippingClocks = new HashSet<>();
        
        // Determine world clock for each world
        for (World world: Bukkit.getWorlds()) {
            handleWorld(world);
        }
        
        harbor.getSLF4JLogger().info(clockWorlds.toString());
        harbor.getServer().getPluginManager().registerEvents(this, harbor);
    }
    
    private void handleWorld(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);

        // World's may not have a clock
        if (clock == null) {
            harbor.getSLF4JLogger().info("World {} has no clock", world.key());
            return;
        }
        
        harbor.getSLF4JLogger().info("Handling world {} ({}) has no clock", world.key(), clock.unwrapKey().get().identifier());

        clockWorlds.compute(clock, (_, value) -> {
            if (value == null) {
                value = new HashSet<>();
            }

            value.add(world);
            return value;
        });
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        handleWorld(event.getWorld());
    }
    
    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        clockWorlds.values().forEach(s -> s.remove(event.getWorld()));
    }

    @Override
    public void run() {
        clockWorlds.keySet().forEach(this::checkClock);
    }
    
    /**
     * Checks if enough people are sleeping, and in the case there are, starts the night skip task.
     *
     * @param clock The world clock to check.
     */
    private void checkClock(@NotNull Holder<WorldClock> clock) {
        if (isBlacklisted(clock)) {
            return;
        }
        
        Set<World> worlds = clockWorlds.get(clock);
        
        if (worlds == null) {
            return;
        }
        
        // All worlds will have the same time, only need to check one
        // TODO: If paper add a proper API for this which doesn't require a world, use that.
        if (!isNight(worlds.iterator().next())) {
            return;
        }
        
        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        //Send title to excluded sleeping players too
        messages.sendTitleMessage(getSleepingPlayers(clock, true), config.getString("messages.title.sleep-title"),
                                  config.getString("messages.title.sleep-subtitle"));

        if (getSleepingPlayers(clock).isEmpty()) {
            worlds.forEach(messages::clearBar);
            return;
        }

        worlds.forEach(w -> messages.sendBossBarMessage(w, config.getString("messages.bossbar.message"),
                                    config.getString("messages.bossbar.color"), 1));

        if (!skippingClocks.contains(clock) &&
                (config.getBoolean("night-speed.enabled") || config.getBoolean("night-skip.enabled"))) {
            harbor.getSLF4JLogger().info("Starting skipping {}", clock.unwrapKey().get().identifier());
            skippingClocks.add(clock);
            worlds.stream().map(World::getUID).forEach(skippingWorlds::add); // Mark all worlds as skipping
            new AccelerateNightTask(harbor, worlds.iterator().next());
        }
    }
    
    /**
     * Checks if a world clock has been blacklisted (or whitelisted) in the configuration.
     *
     * @param clock The world clock to check.
     *
     * @return Whether the world clock is excluded from Harbor checks.
     */
    private boolean isBlacklisted(@NotNull Holder<WorldClock> clock) {
        ResourceKey<WorldClock> clockKey = clock.unwrapKey().orElse(null);
        boolean blacklisted = clockKey == null || harbor.getConfiguration().getStringList("blacklist")
                .contains(clockKey.identifier().toString());

        if (harbor.getConfiguration().getBoolean("whitelist-mode")) {
            return !blacklisted;
        }

        return blacklisted;
    }

    /**
     * Returns the amount of players that should be counted for Harbor's checks, ignoring excluded players.
     *
     * @param world The world for which to check player count, using its world clock.
     *
     * @return The amount of players in worlds with the same world clock as the given world, minus excluded players.
     */
    public int getPlayers(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        return clock == null ? 0 : getPlayers(clock);
    }
    
    /**
     * Returns the amount of players that should be counted for Harbor's checks, ignoring excluded players.
     *
     * @param clock The world clock for which to check player count.
     *
     * @return The amount of players worlds with the given world clock, minus excluded players.
     */
    private int getPlayers(@NotNull Holder<WorldClock> clock) {
        return clockWorlds.getOrDefault(clock, Collections.emptySet())
				.stream()
                .mapToInt(w -> w.getPlayerCount() - super.getExcluded(w).size())
                .sum();
    }

    /**
     * Returns the amount sleeping players in worlds with the given world clock.
     *
     * @param clock The world clock in which to check for sleeping players.
     *
     * @return The amount currently sleeping players in worlds with the provided world clock.
     */
    private List<Player> getSleepingPlayers(@NotNull Holder<WorldClock> clock) {
        return getSleepingPlayers(clock, false);
    }
    
    /**
     * Returns a list of all sleeping players in worlds with the same world clock as the given world.
     *
     * @param world The world in which to check for sleeping players.
     * @param includeExcluded Whether to include players that are excluded by an ExclusionProvider
     *
     * @return A list of all currently sleeping players in worlds with the same world clock as the given world.
     */
    @NotNull
    public List<Player> getSleepingPlayers(@NotNull World world, boolean includeExcluded) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        return clock == null ? Collections.emptyList() : getSleepingPlayers(clock, includeExcluded);
    }

    /**
     * Returns a list of all sleeping players in worlds with the given world clock.
     *
     * @param clock The world clock in which to check for sleeping players.
     * @param includeExcluded Whether to include players that are excluded by an ExclusionProvider
     *
     * @return A list of all currently sleeping players in worlds with the given world clock.
     */
    @NotNull
    private List<Player> getSleepingPlayers(@NotNull Holder<WorldClock> clock, boolean includeExcluded) {
        List<Player> excluded = getExcluded(clock);
        List<Player> result = new ArrayList<>();

        clockWorlds.getOrDefault(clock, Collections.emptySet())
                .forEach(w -> w.getPlayers().stream()
                        .filter(e -> (includeExcluded || !excluded.contains(e)) && e.isSleeping())
                        .forEach(result::add));
        
        return result;
    }

    /**
     * Returns the amount of players that must be sleeping to skip the night in worlds with the same world clock as the given world.
     *
     * @param world The world for which to check skip amount, using its world clock.
     *
     * @return The amount of players that need to sleep to skip the night.
     */
    public int getSkipAmount(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        return clock == null ? Integer.MAX_VALUE : getSkipAmount(clock);
    }
    
    /**
     * Returns the amount of players that must be sleeping to skip the night in worlds with the given world clock.
     *
     * @param clock The world clock for which to check skip amount.
     *
     * @return The amount of players that need to sleep to skip the night.
     */
    private int getSkipAmount(@NotNull Holder<WorldClock> clock) {
        return (int) Math.ceil(getPlayers(clock) * (harbor.getConfiguration().getDouble("night-skip.skip-percentage") / 100));
    }
    
    /**
     * Returns the amount of players that are still needed to skip the night in worlds with the same world clock as the given world.
     *
     * @param world The world for which to check the amount of needed players, using its world clock.
     *
     * @return The amount of players that still need to get into bed to start the night skipping task.
     */
    public int getNeeded(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        return clock == null ? Integer.MAX_VALUE : getNeeded(clock);
    }

    /**
     * Returns the amount of players that are still needed to skip the night in worlds with the given world clock.
     *
     * @param clock The world clock for which to check the amount of needed players.
     *
     * @return The amount of players that still need to get into bed to start the night skipping task.
     */
    private int getNeeded(@NotNull Holder<WorldClock> clock) {
        double percentage = harbor.getConfiguration().getDouble("night-skip.skip-percentage");
        return Math.max(0, (int) Math.ceil((getPlayers(clock)) * (percentage / 100) - getSleepingPlayers(clock).size()));
    }
    
    /**
     * Returns the current timescale for the given world, based on the configured sleep speeds and skip thresholds
     *
     * @param world The world to check
     *
     * @return The timescale
     */
    public double getTimescale(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        return clock == null ? 1 : getTimescale(clock);
    }

    /**
     * Returns the current timescale for the given world clock, based on the configured sleep speeds and skip thresholds
     *
     * @param clock The world clock to check
     *
     * @return The timescale
     */
    private double getTimescale(@NotNull Holder<WorldClock> clock) {
        int sleeping = getSleepingPlayers(clock).size();
        int total = getPlayers(clock);

        if(sleeping == 0 || total == 0) {
            return 1;
        }
        
        int skipPlayerCount = getSkipAmount(clock);

        return calculateTimescale(sleeping, total, skipPlayerCount);
    }

    /**
     * Returns a list of players that are considered to be excluded from Harbor's player count checks.
     *
     * @param clock The world clock for which to check for excluded players.
     *
     * @return A list of excluded players in worlds with the given world clock.
     */
    @NotNull
    private List<Player> getExcluded(@NotNull Holder<WorldClock> clock) {
        List<Player> result = new ArrayList<>();

        clockWorlds.getOrDefault(clock, Collections.emptySet())
                .stream()
                .map(super::getExcluded)
                .forEach(result::addAll);
        
        return result;
    }

    /**
     * Resets worlds with the provided world's world clock to a non-skipping status.
     *
     * @param world The world for which to reset status, using its world clock.
     */
    void resetStatus(@NotNull World world) {
        Holder<WorldClock> clock = Time.getWorldClock(world);
        if (clock != null) {
            resetStatus(clock);
        }
    }

    /**
     * Resets the provided world clock to a non-skipping status.
     *
     * @param clock The world clock for which to reset status.
     */
    void resetStatus(@NotNull Holder<WorldClock> clock) {
        harbor.getSLF4JLogger().info("Stopping skipping {}", clock.unwrapKey().get().identifier());
        skippingClocks.remove(clock);
        clockWorlds.getOrDefault(clock, Collections.emptySet()).forEach(this::wakeUpPlayers);
        
        harbor.getServer().getScheduler().runTaskLater(harbor, () -> {
            clockWorlds.getOrDefault(clock, Collections.emptySet()).forEach(w -> {
                harbor.getMessages().clearBar(w);
                skippingWorlds.remove(w.getUID());
            });
        }, 20L);
    }
}
