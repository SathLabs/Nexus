package dev.satherov.nexus.test.game.api.tick;

import lombok.Getter;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.block.entity.BlockEntityEntry;
import dev.satherov.nexus.api.tick.ticker.Next;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// A block with a block entity that records the elapsed passes of its client ticker.
///
public final class ClientTickingBlock extends Block implements EntityBlock {
    
    private static @Nullable PlainBlockEntry<ClientTickingBlock> entry;
    private static @Nullable BlockEntityEntry<ClientTickingBlock.Recorder> recorder;
    
    public ClientTickingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
    
    public static PlainBlockEntry<ClientTickingBlock> entry() {
        return Objects.requireNonNull(ClientTickingBlock.entry);
    }
    
    public static BlockEntityEntry<ClientTickingBlock.Recorder> recorder() {
        return Objects.requireNonNull(ClientTickingBlock.recorder);
    }
    
    @Override
    public ClientTickingBlock.Recorder newBlockEntity(BlockPos pos, BlockState state) {
        return new ClientTickingBlock.Recorder(pos, state);
    }
    
    public static final class Recorder extends BlockEntity {
        
        @Getter
        private final List<Integer> elapsed = new ArrayList<>();
        
        public Recorder(BlockPos pos, BlockState state) {
            super(ClientTickingBlock.recorder().value(), pos, state);
        }
        
        private Next tick(int elapsed) {
            this.elapsed.add(elapsed);
            return Next.SLEEP;
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            PlainBlockEntry<ClientTickingBlock> entry = registrations.block("client_ticking_block", "Client Ticking Block", ClientTickingBlock::new).register();
            ClientTickingBlock.entry = entry;
            ClientTickingBlock.recorder = registrations.blockEntity("client_ticking_block", ClientTickingBlock.Recorder::new)
                    .blocks(entry)
                    .clientTicker(() -> ClientTickingBlock.Recorder::tick)
                    .register();
        }
    }
}
