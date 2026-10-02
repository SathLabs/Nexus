package dev.satherov.nexus.test.game.api.registry.attribute;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attribute.AttributeEntry;
import dev.satherov.nexus.api.registry.entity.EntityEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

///
/// Checks the attributes that attribute builders add to entity types, and their values on the entities.
///
public class AttributeBuilderSamples {
    
    private static @Nullable EntityEntry<SampleMob> mob;
    private static @Nullable EntityEntry<SampleMob> other;
    private static @Nullable AttributeEntry added;
    private static @Nullable AttributeEntry missing;
    
    @ServerTest
    public static void addsTheAttributeWithItsDefaultToEveryType(GameTestHelper helper) {
        AttributeEntry added = AttributeBuilderSamples.added();
        for (EntityEntry<SampleMob> type : List.of(AttributeBuilderSamples.mob(), AttributeBuilderSamples.other())) {
            AttributeInstance instance = AttributeBuilderSamples.instance(helper, added, AttributeBuilderSamples.create(helper, type));
            helper.assertValueEqual(instance.getBaseValue(), 4.0D, "base value of the attribute on '" + type.getId() + "'");
        }
        
        helper.succeed();
    }
    
    @ServerTest
    public static void getIsTheCurrentValueOnTheEntity(GameTestHelper helper) {
        AttributeEntry added = AttributeBuilderSamples.added();
        SampleMob mob = AttributeBuilderSamples.create(helper, AttributeBuilderSamples.mob());
        AttributeInstance instance = AttributeBuilderSamples.instance(helper, added, mob);
        helper.assertValueEqual(added.get(mob), 4.0D, "default value of the attribute");
        instance.setBaseValue(6.0D);
        helper.assertValueEqual(added.get(mob), 6.0D, "changed value of the attribute");
        instance.setBaseValue(100.0D);
        helper.assertValueEqual(added.get(mob), 10.0D, "value of the attribute above its maximum");
        helper.succeed();
    }
    
    @ServerTest
    public static void getIsTheDefaultOnAnEntityWithoutTheAttribute(GameTestHelper helper) {
        AttributeEntry missing = AttributeBuilderSamples.missing();
        SampleMob mob = AttributeBuilderSamples.create(helper, AttributeBuilderSamples.mob());
        helper.assertTrue(missing.instance(mob) == null, "The mob has an attribute that was never added to its type");
        helper.assertValueEqual(missing.get(mob), 2.0D, "value of the missing attribute");
        helper.succeed();
    }
    
    private static SampleMob create(GameTestHelper helper, EntityEntry<SampleMob> type) {
        return Objects.requireNonNull(type.create(helper.getLevel(), EntitySpawnReason.COMMAND));
    }
    
    private static AttributeInstance instance(GameTestHelper helper, AttributeEntry attribute, SampleMob mob) {
        AttributeInstance instance = attribute.instance(mob);
        if (instance == null) {
            throw helper.assertionException("The mob of '" + mob.getType() + "' doesn't have the attribute '" + attribute.getId() + "'");
        }
        
        return instance;
    }
    
    private static EntityEntry<SampleMob> mob() {
        return Objects.requireNonNull(AttributeBuilderSamples.mob);
    }
    
    private static EntityEntry<SampleMob> other() {
        return Objects.requireNonNull(AttributeBuilderSamples.other);
    }
    
    private static AttributeEntry added() {
        return Objects.requireNonNull(AttributeBuilderSamples.added);
    }
    
    private static AttributeEntry missing() {
        return Objects.requireNonNull(AttributeBuilderSamples.missing);
    }
    
    private static final class SampleMob extends Mob {
        
        private SampleMob(EntityType<? extends Mob> type, Level level) {
            super(type, level);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            EntityEntry<SampleMob> mob = registrations.entity("attribute_builder_samples_mob", "Attribute Sample Mob", MobCategory.CREATURE, SampleMob::new)
                    .attributes(Mob::createMobAttributes)
                    .register();
            EntityEntry<SampleMob> other = registrations.entity("attribute_builder_samples_other", "Other Attribute Sample Mob", MobCategory.CREATURE, SampleMob::new)
                    .attributes(Mob::createMobAttributes)
                    .register();
            
            AttributeBuilderSamples.mob = mob;
            AttributeBuilderSamples.other = other;
            AttributeBuilderSamples.added = registrations.attribute("attribute_builder_samples_added", "Added Sample Attribute", 4.0D, 1.0D, 10.0D)
                    .on(mob)
                    .on(other)
                    .register();
            AttributeBuilderSamples.missing = registrations.attribute("attribute_builder_samples_missing", "Missing Sample Attribute", 2.0D, 0.0D, 5.0D).register();
        }
    }
}
