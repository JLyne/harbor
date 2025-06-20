package xyz.nkomarn.harbor;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.api.ExclusionProvider;
import xyz.nkomarn.harbor.command.ForceSkipCommand;
import xyz.nkomarn.harbor.command.HarborCommand;
import xyz.nkomarn.harbor.task.Checker;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

@SuppressWarnings("UnstableApiUsage")
public class Harbor extends JavaPlugin {
    private Config config;
    private Checker checker;
    private Messages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();

        config = new Config(this);
        checker = new Checker(this);
        messages = new Messages(this);

        pluginManager.registerEvents(messages, this);

        getCommand("harbor").setExecutor(new HarborCommand(this));
        getCommand("forceskip").setExecutor(new ForceSkipCommand(this));
    }

    @Override
    public void onDisable() {
        for (World world : getServer().getWorlds()) {
            messages.clearBar(world);
        }
    }

    @NotNull
    public String getVersion() {
        return getPluginMeta().getVersion();
    }

    @NotNull
    public Config getConfiguration() {
        return config;
    }

    @NotNull
    public Checker getChecker() {
        return checker;
    }

    @NotNull
    public Messages getMessages() {
        return messages;
    }

    @NotNull
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    /**
     * Add an {@link ExclusionProvider} to harbor, so an external plugin can set a player to be excluded from the sleep count
     *
     * @param provider An external implementation of an {@link ExclusionProvider}, provided by an implementing plugin
     *
     * @see ExclusionProvider
     * @see Checker#addExclusionProvider(ExclusionProvider)
     */
    @SuppressWarnings("unused")
    public void addExclusionProvider(ExclusionProvider provider) {
        checker.addExclusionProvider(provider);
    }

    /**
     * Remove an {@link ExclusionProvider}, for use by an external plugin
     *
     * @param provider The provider to remove
     *
     * @see #addExclusionProvider(ExclusionProvider)
     */
    @SuppressWarnings("unused")
    public void removeExclusionProvider(ExclusionProvider provider){
        checker.removeExclusionProvider(provider);
    }
}
