package carpet.utils;

import carpet.CarpetServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static carpet.script.CarpetEventServer.Event.PLAYER_BREAK_BLOCK;

public class ForgeAPIHooks {
    @SubscribeEvent
    public void onBlockBroken(final BlockEvent.BreakEvent event) {
        PLAYER_BREAK_BLOCK.onBlockBroken((ServerPlayer)event.getPlayer(), event.getPos(), event.getState());
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CarpetServer.registerCarpetCommands(event.getDispatcher(), event.getEnvironment());
    }
}
