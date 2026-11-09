package dev.satherov.nexus.test.game.api.component.scenario;

import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

///
/// A timed rule with its component filter, a handwritten direct read of the same test, and if it is a common use case.
///
public record Rule(String name, ComponentFilter filter, Predicate<ItemStack> direct, boolean common) { }
