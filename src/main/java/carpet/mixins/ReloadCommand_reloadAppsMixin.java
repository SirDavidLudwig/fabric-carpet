package carpet.mixins;

import carpet.CarpetServer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ReloadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(ReloadCommand.class)
public class ReloadCommand_reloadAppsMixin {
    //method_13530(Lcom/mojang/brigadier/context/CommandContext;)I
    // internal of register.
    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "reloadPacks", at = @At("TAIL"))
    private static void onReload(Collection<String> p_138236_, CommandSourceStack stack, CallbackInfo cir)
    {
        // can't fetch here the reference to the server
        CarpetServer.onReload(stack.getServer());
    }
}
