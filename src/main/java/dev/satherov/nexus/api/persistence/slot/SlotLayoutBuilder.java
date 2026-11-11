package dev.satherov.nexus.api.persistence.slot;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.internal.persistence.slot.SlotSources;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

///
/// A builder of a [SlotLayout] over the groups of slots of one class of holders.
///
/// The name of a group inside the layout is one of the following:
/// - The given name for a group added through [#group(String, Function)].
/// - `<source_id>` for the group of a source under the empty name.
/// - `<source_id>/<group>` for every other group of a source.
///
/// Can be started through [Persistence#slots(Class)].
///
/// @param <HOLDER> The type of the holders of the slots.
///
public final class SlotLayoutBuilder<HOLDER> {

    ///
    /// The class of the holders.
    ///
    private final Class<HOLDER> holder;

    ///
    /// The functions that get the slots of the layout's own groups, by the name of the group.
    ///
    private final Map<String, Function<HOLDER, SlotAccess>> groups = new LinkedHashMap<>();

    ///
    /// The identifiers of the sources added through [#source(Identifier)].
    ///
    private final Set<Identifier> sources = new LinkedHashSet<>();

    ///
    /// The predicates of the item stacks that a group refuses, by the name of the group.
    ///
    private final Map<String, Predicate<ItemStack>> refusals = new LinkedHashMap<>();

    ///
    /// The fallback groups, in the order they are tried.
    ///
    private final List<String> fallbacks = new ArrayList<>();

    ///
    /// The identifiers of the registered sources that are left out, or `null` if [#registered(Identifier...)] wasn't called.
    ///
    private @Nullable Set<Identifier> excluded;

    ///
    /// Should only ever be called from [Persistence#slots(Class)].
    ///
    /// @param holder The class of the holders.
    ///
    @ApiStatus.Internal
    public SlotLayoutBuilder(Class<HOLDER> holder) {
        this.holder = holder;
    }

    ///
    /// Casts the given holder to the holder class of the given source and then gets the groups of the source for it.
    ///
    private static <SOURCE> Map<String, SlotAccess> sourceGroups(SlotSource<SOURCE> source, Object holder) {
        return source.groups(source.holder().cast(holder));
    }

    ///
    /// Gets the identifier before the first `/` of the given group name, or `null` if that part isn't an identifier.
    ///
    private static @Nullable Identifier sourceOf(String name) {
        int slash = name.indexOf('/');
        String candidate = slash < 0 ? name : name.substring(0, slash);
        Identifier id = Identifier.tryParse(candidate);
        return id != null && id.toString().equals(candidate) ? id : null;
    }

    ///
    /// Adds a group of the layout's own under the given name.
    ///
    /// @param name   The name of the group.
    /// @param access The function that gets the slots of a holder.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalArgumentException If the layout already has a group of its own under the given name.
    ///
    @CanIgnoreReturnValue
    public SlotLayoutBuilder<HOLDER> group(String name, Function<HOLDER, SlotAccess> access) {
        if (this.groups.putIfAbsent(name, access) != null) {
            throw new IllegalArgumentException("The slot layout of '" + this.holder.getName() + "' already has the group '" + name + "'");
        }

        return this;
    }

    ///
    /// Adds the registered source of the given identifier.
    ///
    /// If no source is registered under the identifier or the source is for another class of holders,
    /// the first use of the layout will throw.
    ///
    /// @param id The identifier of the source.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public SlotLayoutBuilder<HOLDER> source(Identifier id) {
        this.sources.add(id);
        return this;
    }

    ///
    /// Adds every source registered through [RegisterSlotSourcesEvent] whose holder class is the class of the holders or
    /// a supertype of it.
    ///
    /// If this is called more than once, a source is only left out if every call left it out.
    ///
    /// @param except The identifiers of the sources to leave out.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public SlotLayoutBuilder<HOLDER> registered(Identifier... except) {
        if (this.excluded == null) {
            this.excluded = new HashSet<>(List.of(except));
        } else {
            this.excluded.retainAll(List.of(except));
        }

        return this;
    }

    ///
    /// Keeps every item stack that matches the given predicate out of the given group when a snapshot is restored.
    ///
    /// Applies to the group's own item stacks as well as to those falling back to it.
    ///
    /// Stacks with previous calls for the same group.
    ///
    /// @param group  The name of the group in the layout.
    /// @param stacks The predicate of the item stacks to keep out.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public SlotLayoutBuilder<HOLDER> refuse(String group, Predicate<ItemStack> stacks) {
        this.refusals.merge(group, stacks, Predicate::or);
        return this;
    }

    ///
    /// Adds the given groups to the fallback groups,
    /// which take the item stacks of a restored snapshot that didn't fit into their own slot.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param groups The groups in the layout, in the order they are tried.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public SlotLayoutBuilder<HOLDER> fallback(String... groups) {
        this.fallbacks.addAll(List.of(groups));
        return this;
    }

    ///
    /// Builds the layout.
    ///
    /// @return The layout.
    ///
    /// @throws IllegalArgumentException If a group given to [#refuse(String, Predicate)] or [#fallback(String...)] can't be a group of the layout,
    /// as far as that can be told before the registered sources are looked up.
    ///
    public SlotLayout<HOLDER> build() {
        for (String name : this.referenced()) {
            Collection<Identifier> ids = this.excluded == null ? this.sources : Stream.ofNullable(SlotLayoutBuilder.sourceOf(name)).toList();
            if (!this.couldHave(name, ids)) {
                throw new IllegalArgumentException("The slot layout of '" + this.holder.getName() + "' can't have the group '" + name + "'");
            }
        }

        Supplier<Map<Identifier, SlotSource<?>>> resolved = Suppliers.memoize(this::resolve);
        return new SlotLayout<>(holder -> this.groupsOf(holder, resolved.get()), Map.copyOf(this.refusals), List.copyOf(this.fallbacks));
    }

    ///
    /// Gets the name of every group given to [#refuse(String, Predicate)] or [#fallback(String...)].
    ///
    private Set<String> referenced() {
        Set<String> names = new LinkedHashSet<>(this.refusals.keySet());
        names.addAll(this.fallbacks);
        return names;
    }

    ///
    /// If the given group name belongs to a group of the layout's own or could belong to a source of one of the given identifiers.
    ///
    private boolean couldHave(String name, Collection<Identifier> ids) {
        return this.groups.containsKey(name) || ids.stream().anyMatch(id -> name.equals(id.toString()) || name.startsWith(id + "/"));
    }

    ///
    /// Looks up the sources of the layout among the registered sources.
    ///
    private Map<Identifier, SlotSource<?>> resolve() {
        Map<Identifier, SlotSource<?>> registered = SlotSources.getAll();
        Map<Identifier, SlotSource<?>> chosen = new LinkedHashMap<>();
        for (Identifier id : this.sources) {
            SlotSource<?> source = registered.get(id);
            if (source == null) {
                throw new IllegalStateException("The slot layout of '" + this.holder.getName() + "' uses the slot source '" + id + "', which isn't registered");
            }

            if (!source.holder().isAssignableFrom(this.holder)) {
                throw new IllegalStateException("The slot source '" + id + "' holds the slots of '" + source.holder().getName() + "' and not of '" + this.holder.getName() + "'");
            }

            chosen.put(id, source);
        }

        Set<Identifier> except = this.excluded;
        if (except != null) {
            registered.forEach((id, source) -> {
                if (!except.contains(id) && source.holder().isAssignableFrom(this.holder)) {
                    chosen.putIfAbsent(id, source);
                }
            });
        }

        for (String name : this.referenced()) {
            if (!this.couldHave(name, chosen.keySet())) {
                throw new IllegalStateException("The slot layout of '" + this.holder.getName() + "' doesn't have a source for the group '" + name + "'");
            }
        }

        return Collections.unmodifiableMap(chosen);
    }

    ///
    /// Gets the groups of the given holder by their name in the layout.
    ///
    private Map<String, SlotAccess> groupsOf(HOLDER holder, Map<Identifier, SlotSource<?>> sources) {
        Map<String, SlotAccess> groups = new LinkedHashMap<>();
        this.groups.forEach((name, access) -> groups.put(name, access.apply(holder)));
        sources.forEach((id, source) -> SlotLayoutBuilder.sourceGroups(source, holder).forEach((group, access) -> {
            String name = group.isEmpty() ? id.toString() : id + "/" + group;
            if (groups.putIfAbsent(name, access) != null) {
                throw new IllegalStateException("The slot layout of '" + this.holder.getName() + "' has two groups named '" + name + "'");
            }
        }));

        return groups;
    }
}
