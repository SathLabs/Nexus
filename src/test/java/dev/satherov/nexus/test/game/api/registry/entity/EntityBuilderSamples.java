package dev.satherov.nexus.test.game.api.registry.entity;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.entity.EntityBuilder;
import dev.satherov.nexus.api.registry.entity.EntityEntry;
import dev.satherov.nexus.api.registry.entity.MobEntityEntry;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks the entity types and spawn eggs that entity builders register, with their lookups, translations, attributes, and spawn placements.
///
public class EntityBuilderSamples {
    
    private static @Nullable EntityEntry<SampleEntity> plain;
    private static @Nullable EntityEntry<SampleEntity> slashed;
    private static @Nullable EntityEntry<SampleEntity> refused;
    private static @Nullable MobEntityEntry<SampleMob, SpawnEggItem> mob;
    private static @Nullable MobEntityEntry<SampleMob, SampleEgg> custom;
    private static @Nullable EntityBuilder<SampleMob, Registrations> takenOver;
    private static boolean clientRequested;
    
    @ServerTest
    public static void registersTheTypeUnderItsName(GameTestHelper helper) {
        EntityEntry<SampleEntity> plain = EntityBuilderSamples.plain();
        helper.assertValueEqual(plain.getId(), Nexus.getMod().id("entity_builder_samples_plain"), "identifier of the type");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getValue(plain.getId()), plain.value(), "registered type");
        helper.assertValueEqual(plain.value().getCategory(), MobCategory.MISC, "category of the type");
        helper.assertFalse(BuiltInRegistries.ITEM.containsKey(Nexus.getMod().id("entity_builder_samples_plain_spawn_egg")), "The plain type registered a spawn egg");
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesTheSizeBeforeTheEdits(GameTestHelper helper) {
        EntityType<SampleEntity> type = EntityBuilderSamples.plain().value();
        helper.assertValueEqual(type.getWidth(), 2.0F, "width of the type");
        helper.assertValueEqual(type.getHeight(), 3.0F, "height of the type");
        helper.assertValueEqual(type.getDimensions().eyeHeight(), 0.5F, "eye height of the type");
        helper.assertTrue(type.fireImmune(), "The type is not immune to fire");
        helper.succeed();
    }
    
    @ServerTest
    public static void translatesTheTypeUnderItsKey(GameTestHelper helper) {
        EntityEntry<SampleEntity> plain = EntityBuilderSamples.plain();
        MobEntityEntry<SampleMob, SpawnEggItem> mob = EntityBuilderSamples.mob();
        helper.assertValueEqual(plain.getTranslation().getKey(), "entity.nexus.entity_builder_samples_plain", "translation key");
        helper.assertValueEqual(plain.getTranslation().getEnglish(), "Plain Sample Entity", "English name");
        helper.assertValueEqual(plain.value().getDescriptionId(), plain.getTranslation().getKey(), "description id of the type");
        helper.assertValueEqual(mob.getTranslation().getKey(), "entity.nexus.entity_builder_samples_mob", "translation key of the mob");
        helper.assertValueEqual(mob.value().getDescriptionId(), mob.getTranslation().getKey(), "description id of the mob");
        helper.succeed();
    }
    
    @ServerTest
    public static void translatesASlashedNameUnderTheDescriptionId(GameTestHelper helper) {
        EntityEntry<SampleEntity> slashed = EntityBuilderSamples.slashed();
        helper.assertValueEqual(slashed.getTranslation().getKey(), "entity.nexus.entity_builder_samples.slashed", "translation key");
        helper.assertValueEqual(slashed.value().getDescriptionId(), slashed.getTranslation().getKey(), "description id of the type");
        helper.succeed();
    }
    
    @ServerTest
    public static void createsAnEntityOfTheTypeOutsideTheLevel(GameTestHelper helper) {
        EntityEntry<SampleEntity> plain = EntityBuilderSamples.plain();
        SampleEntity created = Objects.requireNonNull(plain.create(helper.getLevel(), EntitySpawnReason.COMMAND));
        helper.assertValueEqual(created.getType(), plain.value(), "type of the created entity");
        helper.assertTrue(helper.getLevel().getEntity(created.getUUID()) == null, "The created entity was added to the level");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnsAnEntityOfTheTypeAtThePosition(GameTestHelper helper) {
        EntityEntry<SampleEntity> plain = EntityBuilderSamples.plain();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        SampleEntity spawned = Objects.requireNonNull(plain.spawn(helper.getLevel(), pos, EntitySpawnReason.COMMAND));
        helper.assertValueEqual(spawned.getType(), plain.value(), "type of the spawned entity");
        helper.assertValueEqual(helper.getLevel().getEntity(spawned.getUUID()), spawned, "entity in the level");
        helper.assertValueEqual(spawned.blockPosition(), pos, "position of the spawned entity");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnIsNullIfTheEntityCouldNotJoinTheLevel(GameTestHelper helper) {
        EntityEntry<SampleEntity> refused = EntityBuilderSamples.refused();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.assertTrue(refused.spawn(helper.getLevel(), helper.absolutePos(pos), EntitySpawnReason.COMMAND) == null, "The spawn returned an entity that could not join the level");
        helper.assertEntityNotPresent(refused.value(), pos);
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesEntitiesOfTheTypeOnly(GameTestHelper helper) {
        EntityEntry<SampleEntity> plain = EntityBuilderSamples.plain();
        SampleEntity entity = Objects.requireNonNull(plain.create(helper.getLevel(), EntitySpawnReason.COMMAND));
        SampleMob mob = Objects.requireNonNull(EntityBuilderSamples.mob().create(helper.getLevel(), EntitySpawnReason.COMMAND));
        helper.assertTrue(plain.is(entity), "The type did not match its own entity");
        helper.assertFalse(plain.is(mob), "The type matched the mob");
        helper.assertTrue(EntityBuilderSamples.mob().is(mob), "The mob type did not match its own mob");
        helper.assertFalse(EntityBuilderSamples.custom().is(mob), "The type matched a mob of another type with the same class");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersTheAttributesOfTheMob(GameTestHelper helper) {
        MobEntityEntry<SampleMob, SpawnEggItem> mob = EntityBuilderSamples.mob();
        helper.assertTrue(DefaultAttributes.hasSupplier(mob.value()), "The mob has no attributes");
        SampleMob created = Objects.requireNonNull(mob.create(helper.getLevel(), EntitySpawnReason.COMMAND));
        helper.assertValueEqual(created.getMaxHealth(), 42.0F, "maximum health of the mob");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersTheSpawnPlacementOfTheMob(GameTestHelper helper) {
        EntityType<SampleMob> type = EntityBuilderSamples.mob().value();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(SpawnPlacements.hasPlacement(type), "The mob has no spawn placement");
        helper.assertValueEqual(SpawnPlacements.getPlacementType(type), SpawnPlacementTypes.ON_GROUND, "placement type of the mob");
        helper.assertValueEqual(SpawnPlacements.getHeightmapType(type), Heightmap.Types.OCEAN_FLOOR, "heightmap of the mob");
        helper.assertFalse(SpawnPlacements.checkSpawnRules(type, helper.getLevel(), EntitySpawnReason.NATURAL, pos, helper.getLevel().getRandom()), "The mob passed spawn rules that refuse every position");
        helper.succeed();
    }
    
    @ServerTest
    public static void namesTheEggAfterTheEntity(GameTestHelper helper) {
        ItemEntry<SpawnEggItem> egg = EntityBuilderSamples.mob().getEgg();
        helper.assertValueEqual(egg.getId(), Nexus.getMod().id("entity_builder_samples_mob_spawn_egg"), "identifier of the spawn egg");
        helper.assertValueEqual(BuiltInRegistries.ITEM.getValue(egg.getId()), egg.value(), "registered spawn egg");
        helper.assertValueEqual(egg.getTranslation().getKey(), "item.nexus.entity_builder_samples_mob_spawn_egg", "translation key of the spawn egg");
        helper.assertValueEqual(egg.getTranslation().getEnglish(), "Sample Mob Spawn Egg", "English name of the spawn egg");
        helper.assertValueEqual(egg.value().getDescriptionId(), egg.getTranslation().getKey(), "description id of the spawn egg");
        helper.succeed();
    }
    
    @ServerTest
    public static void theEggSpawnsAnEntityOfTheType(GameTestHelper helper) {
        MobEntityEntry<SampleMob, SpawnEggItem> mob = EntityBuilderSamples.mob();
        BlockPos clicked = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.setBlock(clicked, Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, mob.getEgg().stack());
        helper.useBlock(clicked, player);
        helper.assertEntityPresent(mob.value(), clicked, 2.0D);
        helper.succeed();
    }
    
    @ServerTest
    public static void keepsTheTypeOfAnEggWhosePropertiesWereReplaced(GameTestHelper helper) {
        MobEntityEntry<SampleMob, SpawnEggItem> mob = EntityBuilderSamples.mob();
        helper.assertTrue(SpawnEggItem.spawnsEntity(mob.getEgg().stack(), mob.value()), "The spawn egg lost its type with its properties");
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesTheEggEditsAfterTheFeaturesOfTheType(GameTestHelper helper) {
        helper.assertTrue(EntityBuilderSamples.custom().getEgg().value().requiredFeatures().contains(FeatureFlags.TRADE_REBALANCE), "The features of the type replaced the features of the edit");
        helper.succeed();
    }
    
    @ServerTest
    public static void givesTheEggFactoryThePropertiesWithTheType(GameTestHelper helper) {
        MobEntityEntry<SampleMob, SampleEgg> custom = EntityBuilderSamples.custom();
        helper.assertValueEqual(custom.getEgg().value().getClass(), SampleEgg.class, "class of the spawn egg");
        helper.assertTrue(SpawnEggItem.spawnsEntity(custom.getEgg().stack(), custom.value()), "The custom spawn egg is not of the type");
        helper.assertFalse(SpawnEggItem.spawnsEntity(custom.getEgg().stack(), EntityBuilderSamples.mob().value()), "The custom spawn egg is of another type");
        helper.assertValueEqual(custom.getEgg().getTranslation().getEnglish(), "Custom Sample Mob Spawn Egg", "English name of the custom spawn egg");
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesChangesToTheEntityBuilderAfterTheEgg(GameTestHelper helper) {
        EntityBuilder<SampleMob, Registrations> builder = EntityBuilderSamples.takenOver();
        helper.assertTrue(Assertions.catchThrowable(() -> builder.size(1.0F, 1.0F)) instanceof IllegalStateException, "The size was replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.type(type -> type)) instanceof IllegalStateException, "The vanilla type builder was edited");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.attributes(Mob::createMobAttributes)) instanceof IllegalStateException, "The attributes were replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.WORLD_SURFACE, (_, _, _, _, _) -> true)) instanceof IllegalStateException, "The spawn placement was replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.renderer(() -> SampleClient::renderer)) instanceof IllegalStateException, "The renderer was replaced");
        helper.assertTrue(Assertions.catchThrowable(builder::egg) instanceof IllegalStateException, "A second spawn egg was opened");
        helper.assertTrue(Assertions.catchThrowable(builder::register) instanceof IllegalStateException, "The type was registered a second time");
        helper.succeed();
    }
    
    @ServerTest
    public static void neverRequestsTheRendererOnADedicatedServer(GameTestHelper helper) {
        helper.assertFalse(EntityBuilderSamples.clientRequested, "A renderer was requested on a dedicated server");
        helper.succeed();
    }
    
    private static EntityEntry<SampleEntity> plain() {
        return Objects.requireNonNull(EntityBuilderSamples.plain);
    }
    
    private static EntityEntry<SampleEntity> slashed() {
        return Objects.requireNonNull(EntityBuilderSamples.slashed);
    }
    
    private static EntityEntry<SampleEntity> refused() {
        return Objects.requireNonNull(EntityBuilderSamples.refused);
    }
    
    private static MobEntityEntry<SampleMob, SpawnEggItem> mob() {
        return Objects.requireNonNull(EntityBuilderSamples.mob);
    }
    
    private static MobEntityEntry<SampleMob, SampleEgg> custom() {
        return Objects.requireNonNull(EntityBuilderSamples.custom);
    }
    
    private static EntityBuilder<SampleMob, Registrations> takenOver() {
        return Objects.requireNonNull(EntityBuilderSamples.takenOver);
    }
    
    private static final class SampleEntity extends Entity {
        
        private SampleEntity(EntityType<?> type, Level level) {
            super(type, level);
        }
        
        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) { }
        
        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
            return false;
        }
        
        @Override
        protected void readAdditionalSaveData(ValueInput input) { }
        
        @Override
        protected void addAdditionalSaveData(ValueOutput output) { }
    }
    
    private static final class SampleMob extends Mob {
        
        private SampleMob(EntityType<? extends Mob> type, Level level) {
            super(type, level);
        }
    }
    
    private static final class SampleEgg extends SpawnEggItem {
        
        private SampleEgg(Item.Properties properties) {
            super(properties);
        }
    }
    
    private static final class SampleClient {
        
        private static <ENTITY extends Entity> NoopRenderer<ENTITY> renderer(EntityRendererProvider.Context context) {
            return new NoopRenderer<>(context);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            EntityBuilderSamples.plain = registrations.entity("entity_builder_samples_plain", "Plain Sample Entity", MobCategory.MISC, SampleEntity::new)
                    .type(builder -> builder.eyeHeight(0.5F))
                    .size(2.0F, 3.0F)
                    .type(EntityType.Builder::fireImmune)
                    .renderer(() -> {
                        EntityBuilderSamples.clientRequested = true;
                        return SampleClient::renderer;
                    })
                    .register();
            
            EntityBuilderSamples.slashed = registrations.entity("entity_builder_samples/slashed", "Slashed Sample Entity", MobCategory.MISC, SampleEntity::new).register();
            EntityBuilderSamples.refused = registrations.entity("entity_builder_samples_refused", "Refused Sample Entity", MobCategory.MISC, SampleEntity::new).register();
            
            EntityBuilder<SampleMob, Registrations> builder = registrations.entity("entity_builder_samples_mob", "Sample Mob", MobCategory.CREATURE, SampleMob::new)
                    .attributes(() -> Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 42.0D))
                    .spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.OCEAN_FLOOR, (_, _, _, _, _) -> false)
                    .renderer(() -> {
                        EntityBuilderSamples.clientRequested = true;
                        return SampleClient::renderer;
                    });
            EntityBuilderSamples.mob = builder.egg()
                    .properties(new Item.Properties())
                    .end()
                    .register();
            EntityBuilderSamples.takenOver = builder;
            
            EntityBuilderSamples.custom = registrations.entity("entity_builder_samples_custom", "Custom Sample Mob", MobCategory.CREATURE, SampleMob::new)
                    .attributes(Mob::createMobAttributes)
                    .renderer(() -> {
                        EntityBuilderSamples.clientRequested = true;
                        return SampleClient::renderer;
                    })
                    .egg(SampleEgg::new)
                    .properties(properties -> properties.requiredFeatures(FeatureFlags.TRADE_REBALANCE))
                    .noTab()
                    .end()
                    .register();
        }
        
        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (EntityBuilderSamples.refused().is(event.getEntity())) {
                event.setCanceled(true);
            }
        }
    }
}
