package dev.satherov.nexus.test.game.api.registry.menu;

import lombok.Getter;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class NumberMenu extends AbstractContainerMenu {

    @Getter
    private final int number;

    public NumberMenu(MenuType<?> type, int containerId, int number) {
        super(type, containerId);
        this.number = number;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
