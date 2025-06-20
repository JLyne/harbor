package xyz.nkomarn.harbor;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.api.ExclusionProvider;
import xyz.nkomarn.harbor.task.Checker;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

import static io.papermc.paper.command.brigadier.Commands.literal;

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

        LifecycleEventManager<@NotNull Plugin> manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS,
                                     event -> registerCommands(event.registrar()));
    }

    @Override
    public void onDisable() {
        for (World world : getServer().getWorlds()) {
            messages.clearBar(world);
        }
    }

    private void registerCommands(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> reloadCommand = literal("reload")
                .requires(source -> source.getSender().hasPermission("harbor.admin"))
                .executes(ctx -> {
                    config.reload();
                    ctx.getSource().getSender().sendRichMessage(config.getPrefix() + "Reloaded configuration.");
                    return Command.SINGLE_SUCCESS;
                });

        LiteralArgumentBuilder<CommandSourceStack> forceSkipCommand = literal("forceskip")
                .requires(source -> source.getSender().hasPermission("harbor.forceskip"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        ctx.getSource().getSender()
                                .sendRichMessage(config.getPrefix() + "This command can only be used by a player.");
                        return Command.SINGLE_SUCCESS;
                    }

                    World world = player.getWorld();

                    if (getChecker().isSkipping(world)) {
                        player.sendRichMessage(config.getPrefix() + "This world's time is already being accelerated.");
                    } else {
                        player.sendRichMessage(config.getPrefix() + "Forcing night skip in your world.");
                        getChecker().forceSkip(world);
                    }

                    return Command.SINGLE_SUCCESS;
                });

        commands.register(literal("harbor")
                                  .requires(source ->
                                                    source.getSender().hasPermission("harbor.admin")
                                                            || source.getSender().hasPermission("harbor.forceskip"))
                                  .then(reloadCommand)
                                  .then(forceSkipCommand)
                                  .build(), "Main command for Harbor");
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
