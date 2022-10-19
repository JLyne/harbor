package xyz.nkomarn.harbor.task;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.api.ExclusionProvider;
import xyz.nkomarn.harbor.provider.GameModeExclusionProvider;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class Checker extends BukkitRunnable {
    private final Set<ExclusionProvider> providers;
    private final Harbor harbor;
    private final Set<UUID> skippingWorlds;

    public Checker(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.skippingWorlds = new HashSet<>();
        this.providers = new HashSet<>();

        // GameModeExclusionProvider checks each case on its own
        providers.add(new GameModeExclusionProvider(harbor));

        // The others are simple enough that we can use lambdas
        providers.add(player -> harbor.getConfig().getBoolean("exclusions.ignored-permission", true) && player.hasPermission("harbor.ignored"));
        providers.add(player -> harbor.getConfig().getBoolean("exclusions.exclude-vanished", false) && isVanished(player));
        providers.add(Player::isSleepingIgnored);

        int interval = harbor.getConfiguration().getInteger("interval");
        // Default to 1 if its invalid
        if (interval <= 0)
            interval = 1;
        runTaskTimerAsynchronously(harbor, 0L, interval);
    }

    @Override
    public void run() {
        Bukkit.getWorlds().stream()
                .filter(this::validateWorld)
                .forEach(this::checkWorld);
    }

    /**
     * Checks if a given world is applicable for night skipping.
     *
     * @param world The world to check.
     *
     * @return Whether Harbor should run the night skipping check below.
     */
    private boolean validateWorld(@NotNull World world) {
        return !isBlacklisted(world) && isNight(world);
    }

    /**
     * Checks if enough people are sleeping, and in the case there are, starts the night skip task.
     *
     * @param world The world to check.
     */
    private void checkWorld(@NotNull World world) {
        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        //Send title to excluded sleeping players too
        messages.sendTitleMessage(getSleepingPlayers(world, true), config.getString("messages.title.sleep-title"),
                config.getString("messages.title.sleep-subtitle"));

        if (getSleepingPlayers(world).isEmpty()) {
            messages.clearBar(world);
            return;
        }

        messages.sendBossBarMessage(world, config.getString("messages.bossbar.message"),
                config.getString("messages.bossbar.color"), 1);

        if (!skippingWorlds.contains(world.getUID()) &&
                (config.getBoolean("night-speed.enabled") || config.getBoolean("night-skip.enabled"))) {
            skippingWorlds.add(world.getUID());
            new AccelerateNightTask(harbor, this, world);
        }
    }

    /**
     * Checks if the time in a given world is considered to be night.
     *
     * @param world The world to check.
     *
     * @return Whether it is currently night in the provided world.
     */
    private boolean isNight(@NotNull World world) {
        return world.getTime() > 12541L && world.getTime() < 23460L;
    }

    /**
     * Checks if a current world has been blacklisted (or whitelisted) in the configuration.
     *
     * @param world The world to check.
     *
     * @return Whether a world is excluded from Harbor checks.
     */
    public boolean isBlacklisted(@NotNull World world) {
        boolean blacklisted = harbor.getConfiguration().getStringList("blacklisted-worlds").contains(world.getName());

        if (harbor.getConfiguration().getBoolean("whitelist-mode")) {
            return !blacklisted;
        }

        return blacklisted;
    }

    /**
     * Checks if a given player is in a vanished state.
     *
     * @param player The player to check.
     *
     * @return Whether the provided player is vanished.
     */
    public static boolean isVanished(@NotNull Player player) {
        return player.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean);
    }

    /**
     * Returns the amount of players that should be counted for Harbor's checks, ignoring excluded players.
     *
     * @param world The world for which to check player count.
     *
     * @return The amount of players in a given world, minus excluded players.
     */
    public int getPlayers(@NotNull World world) {
        return Math.max(0, world.getPlayers().size() - getExcluded(world).size());
    }

    /**
     * Returns a list of all sleeping players in a given world.
     *
     * @param world The world in which to check for sleeping players.
     *
     * @return A list of all currently sleeping players in the provided world.
     */
    @NotNull
    public List<Player> getSleepingPlayers(@NotNull World world) {
        return getSleepingPlayers(world, true);
    }

    /**
     * Returns a list of all sleeping players in a given world.
     *
     * @param world The world in which to check for sleeping players.
     * @param includeExcluded Whether to include players that are excluded by an ExclusionProvider
     *
     * @return A list of all currently sleeping players in the provided world.
     */
    @NotNull
    public List<Player> getSleepingPlayers(@NotNull World world, boolean includeExcluded) {
        List<Player> excluded = getExcluded(world);

        return world.getPlayers().stream()
                .filter(e -> (includeExcluded || !excluded.contains(e)) && e.isSleeping())
                .collect(toList());
    }

    /**
     * Returns the amount of players that must be sleeping to skip the night in the given world.
     *
     * @param world The world for which to check skip amount.
     *
     * @return The amount of players that need to sleep to skip the night.
     */
    public int getSkipAmount(@NotNull World world) {
        return (int) Math.ceil(getPlayers(world) * (harbor.getConfiguration().getDouble("night-skip.skip-percentage") / 100));
    }

    /**
     * Returns the amount of players that are still needed to skip the night in a given world.
     *
     * @param world The world for which to check the amount of needed players.
     *
     * @return The amount of players that still need to get into bed to start the night skipping task.
     */
    public int getNeeded(@NotNull World world) {
        double percentage = harbor.getConfiguration().getDouble("night-skip.skip-percentage");
        return Math.max(0, (int) Math.ceil((getPlayers(world)) * (percentage / 100) - getSleepingPlayers(world).size()));
    }

    /**
     * Returns the current timescale for the given world, based on the configured sleep speeds and skip thresholds
     *
     * @param world The world to check
     *
     * @return The timescale
     */
    public double getTimescale(@NotNull World world) {
        Config config = harbor.getConfiguration();
        int sleeping = getSleepingPlayers(world).size();
        int total = getPlayers(world);

        if(sleeping == 0 || total == 0) {
            return 1;
        }

        boolean speedEnabled = config.getBoolean("night-speed.enabled");
        boolean skipEnabled = config.getBoolean("night-skip.enabled");

        boolean instantSkip = config.getBoolean("night-skip.instant-skip");
        int skipPlayerCount = getSkipAmount(world);

        int minMultiplier = config.getInteger("night-speed.min-speed-multiplier");
        int maxMultiplier = config.getInteger("night-speed.max-speed-multiplier");
        int skipMultiplier = config.getInteger("night-skip.skip-speed-multiplier");

        if(skipEnabled && sleeping >= skipPlayerCount) { // Enough asleep players to skip
            if (instantSkip) { // Instantly skip night if enabled
                return Double.POSITIVE_INFINITY;
            }

            return skipMultiplier; // Otherwise use skip multiplier
        } else if(speedEnabled) { // Speed up night
            if (sleeping == 1 && total > 1) { // Single player sleeping, use min multiplier
                return minMultiplier;
            } else if(skipEnabled && sleeping == skipPlayerCount - 1) {
                return maxMultiplier;
            } else if(skipEnabled) { // Scale speed between 1 player and [skip threshold] - 1 players
                return minMultiplier + Math.round(
                        ((double) (maxMultiplier - minMultiplier) *
                                (sleeping - 1)  //Ignore first sleeping player as handled by minSpeed
                                / (skipPlayerCount - 2))); //Ignore first sleeping player and skip causing player
            } else { // Otherwise scale between 1 and all players
                //Ignore first sleeping player as handled by minSpeed
                return minMultiplier + Math.round(
                        (double) (maxMultiplier - minMultiplier) * (double) ((sleeping - 1) / (total - 1)));
            }
        }

        return 1;
    }

    /**
     * Returns a list of players that are considered to be excluded from Harbor's player count checks.
     *
     * @param world The world for which to check for excluded players.
     *
     * @return A list of excluded players in the given world.
     */
    @NotNull
    private List<Player> getExcluded(@NotNull World world) {
        return world.getPlayers().stream()
                .filter(this::isExcluded)
                .collect(toList());
    }

    /**
     * Checks if a given player is considered excluded from Harbor's checks.
     *
     * @param player The player to check.
     *
     * @return Whether the given player is excluded.
     */
    private boolean isExcluded(@NotNull Player player) {
        return providers.stream().anyMatch(provider -> provider.isExcluded(player));
    }

    /**
     * Checks whether the night is currently being skipped in the given world.
     *
     * @param world The world to check.
     *
     * @return Whether the night is currently skipping in the provided world.
     */
    public boolean isSkipping(@NotNull World world) {
        return skippingWorlds.contains(world.getUID());
    }

    /**
     * Forces a world to begin skipping the night, skipping over the checks.
     *
     * @param world The world in which to force night skipping.
     */
    public void forceSkip(@NotNull World world) {
        skippingWorlds.add(world.getUID());
        new AccelerateNightTask(harbor, this, world);
    }

    /**
     * Resets the provided world to a non-skipping status.
     *
     * @param world The world for which to reset status.
     */
    public void resetStatus(@NotNull World world) {
        wakeUpPlayers(world);
        harbor.getServer().getScheduler().runTaskLater(harbor, () -> {
            harbor.getMessages().clearBar(world);
            skippingWorlds.remove(world.getUID());
            harbor.getPlayerManager().clearCooldowns();
        }, 20L);
    }

    /**
     * Kicks all sleeping players out of bed in the provided world.
     *
     * @param world The world for which to kick players out of bed.
     */
    public void wakeUpPlayers(@NotNull World world) {
        ensureMain(() -> {
            Config config = harbor.getConfiguration();
            Optional<Sound> sound = Enums.getIfPresent(Sound.class, config.getString("morning.play-sound"));

            world.getPlayers().stream()
                    .filter(LivingEntity::isSleeping)
                    .forEach(player -> {
                        if (sound.isPresent()) {
                            player.playSound(player.getLocation(), sound.get(), 1.0f, 1.0f);
                        }

                        //Send title to excluded sleeping players too
                        harbor.getMessages().sendTitleMessage(player,
                                                              config.getString("messages.title.morning-title"),
                                                              config.getString("messages.title.morning-subtitle"));

                        player.wakeup(true);
                    });
        });
    }

    /**
     * Resets the weather states in the provided world.
     *
     * @param world The world for which to clear weather.
     */
    public void clearWeather(@NotNull World world) {
        ensureMain(() -> {
            Config config = harbor.getConfiguration();

            if (world.hasStorm() && config.getBoolean("night-skip.clear-rain")) {
                world.setStorm(false);
            }

            if (world.isThundering() && config.getBoolean("night-skip.clear-thunder")) {
                world.setThundering(false);
            }
        });
    }

    /**
     * Ensures the provided task is ran on the server thread.
     *
     * @param runnable The task to run on the server thread.
     */
    public void ensureMain(@NotNull Runnable runnable) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(harbor, runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Adds an {@link ExclusionProvider}, which will be checked as a condition. All Exclusions will be ORed together
     * on which to exclude a given player
     */
    public void addExclusionProvider(ExclusionProvider provider) {
        providers.add(provider);
    }

    /**
     * Removes an {@link ExclusionProvider}
     */
    public void removeExclusionProvider(ExclusionProvider provider) {
        providers.remove(provider);
    }
}
