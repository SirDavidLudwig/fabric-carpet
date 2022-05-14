package carpet;

import carpet.commands.*;
import carpet.helpers.HopperCounter;
import carpet.helpers.TickSpeed;
import carpet.logging.HUDController;
import carpet.logging.LoggerRegistry;
import carpet.network.ServerNetworkHandler;
import carpet.script.CarpetScriptServer;
import carpet.settings.SettingsManager;
import carpet.utils.ForgeAPIHooks;
import carpet.utils.MobAI;
import carpet.utils.SpawnReporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.PerfCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static carpet.script.CarpetEventServer.Event.PLAYER_BREAK_BLOCK;

@Mod("carpet")
public class CarpetServer {
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Random rand = new Random();
    public static MinecraftServer minecraft_server;
    private static CommandDispatcher<CommandSourceStack> currentCommandDispatcher;
    public static CarpetScriptServer scriptServer;
    public static SettingsManager settingsManager;
    public static final List<CarpetExtension> extensions = new ArrayList<>();

    public CarpetServer()
    {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(new ForgeAPIHooks());
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        onGameStarted();
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // Some example code to dispatch IMC to another mod
        InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // Some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.messageSupplier().get()).
                collect(Collectors.toList()));
    }

    public static void onGameStarted()
    {
        settingsManager = new SettingsManager(CarpetSettings.carpetVersion, "carpet", "Carpet Mod");
        settingsManager.parseSettingsClass(CarpetSettings.class);
        extensions.forEach(CarpetExtension::onGameStarted);
//        FabricAPIHooks.initialize();
        CarpetScriptServer.parseFunctionClasses();
    }

    public static void onServerLoaded(MinecraftServer server)
    {
        CarpetServer.minecraft_server = server;
        // shoudl not be needed - that bit needs refactoring, but not now.
        SpawnReporter.reset_spawn_stats(server, true);

        settingsManager.attachServer(server);
        extensions.forEach(e -> {
            SettingsManager sm = e.customSettingsManager();
            if (sm != null) sm.attachServer(server);
            e.onServerLoaded(server);
        });
        scriptServer = new CarpetScriptServer(server);
        MobAI.resetTrackers();
        LoggerRegistry.initLoggers();
        TickSpeed.reset();
    }

    public static void onServerLoadedWorlds(MinecraftServer minecraftServer)
    {
        HopperCounter.resetAll(minecraftServer, true);
        extensions.forEach(e -> e.onServerLoadedWorlds(minecraftServer));
        scriptServer.initializeForWorld();
    }

    public static void tick(MinecraftServer server)
    {
        TickSpeed.tick();
        HUDController.update_hud(server, null);
        if (scriptServer != null) scriptServer.tick();

        //in case something happens
        CarpetSettings.impendingFillSkipUpdates.set(false);

        extensions.forEach(e -> e.onTick(server));
    }

    public static void registerCarpetCommands(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection environment)
    {
        if (settingsManager == null) // bootstrap dev initialization check
        {
            return;
        }
        settingsManager.registerCommand(dispatcher);
        extensions.forEach(e -> {
            SettingsManager sm = e.customSettingsManager();
            if (sm != null) sm.registerCommand(dispatcher);
        });
        TickCommand.register(dispatcher);
        ProfileCommand.register(dispatcher);
        CounterCommand.register(dispatcher);
        LogCommand.register(dispatcher);
        SpawnCommand.register(dispatcher);
        PlayerCommand.register(dispatcher);
        //CameraModeCommand.register(dispatcher);
        InfoCommand.register(dispatcher);
        DistanceCommand.register(dispatcher);
        PerimeterInfoCommand.register(dispatcher);
        DrawCommand.register(dispatcher);
        ScriptCommand.register(dispatcher);
        MobAICommand.register(dispatcher);
        // registering command of extensions that has registered before either server is created
        // for all other, they will have them registered when they add themselves
        extensions.forEach(e -> e.registerCommands(dispatcher));
        currentCommandDispatcher = dispatcher;

        if (environment != Commands.CommandSelection.DEDICATED)
            PerfCommand.register(dispatcher);

        if (!FMLEnvironment.production)
            TestCommand.register(dispatcher);
        // todo 1.16 - re-registerer apps if that's a reload operation.
    }

    public static void onPlayerLoggedIn(ServerPlayer player)
    {
        ServerNetworkHandler.onPlayerJoin(player);
        LoggerRegistry.playerConnected(player);
        scriptServer.onPlayerJoin(player);
        extensions.forEach(e -> e.onPlayerLoggedIn(player));

    }

    public static void onPlayerLoggedOut(ServerPlayer player)
    {
        ServerNetworkHandler.onPlayerLoggedOut(player);
        LoggerRegistry.playerDisconnected(player);
        extensions.forEach(e -> e.onPlayerLoggedOut(player));
    }

    public static void clientPreClosing()
    {
        if (scriptServer != null) scriptServer.onClose();
        scriptServer = null;
    }

    public static void onServerClosed(MinecraftServer server)
    {
        // this for whatever reason gets called multiple times even when joining on SP
        // so we allow to pass multiple times gating it only on existing server ref
        if (minecraft_server != null)
        {
            if (scriptServer != null) scriptServer.onClose();
            scriptServer = null;
            ServerNetworkHandler.close();
            currentCommandDispatcher = null;

            LoggerRegistry.stopLoggers();
            HUDController.resetScarpetHUDs();
            extensions.forEach(e -> e.onServerClosed(server));
            minecraft_server = null;
        }

        // this for whatever reason gets called multiple times even when joining;
        TickSpeed.reset();
    }
    public static void onServerDoneClosing(MinecraftServer server)
    {
        settingsManager.detachServer();
    }

    public static void registerExtensionLoggers()
    {
        extensions.forEach(CarpetExtension::registerLoggers);
    }

    public static void onReload(MinecraftServer server)
    {
        scriptServer.reload(server);
        extensions.forEach(e -> e.onReload(server));
    }
}
