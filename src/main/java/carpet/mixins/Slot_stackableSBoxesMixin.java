package carpet.mixins;

import carpet.CarpetSettings;
import carpet.helpers.InventoryHelper;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Slot.class)
public class Slot_stackableSBoxesMixin {
    @Redirect(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I"
    ))
    private int getMaxCountForSboxesInSlot(Slot slot, ItemStack stack)
    {
        if (CarpetSettings.shulkerBoxStackSize > 1 &&
                stack.getItem() instanceof BlockItem &&
                ((BlockItem)stack.getItem()).getBlock() instanceof ShulkerBoxBlock &&
                !InventoryHelper.shulkerBoxHasItems(stack)
        )
        {
            return CarpetSettings.shulkerBoxStackSize;
        }
        return slot.getMaxStackSize(stack);
    }
}
