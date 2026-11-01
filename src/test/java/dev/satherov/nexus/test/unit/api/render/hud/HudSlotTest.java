package dev.satherov.nexus.test.unit.api.render.hud;

import dev.satherov.nexus.api.render.hud.HudSlot;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the relation and the anchor of the slots the factories create.
///
public class HudSlotTest {
    
    private static final Identifier ANCHOR = Identifier.fromNamespaceAndPath("nexus", "hud_slot_test");
    
    @Test
    public void sitsAboveTheAnchor() {
        Assertions.assertThat(HudSlot.above(HudSlotTest.ANCHOR)).isEqualTo(new HudSlot(HudSlot.Relation.ABOVE, HudSlotTest.ANCHOR));
    }
    
    @Test
    public void sitsBelowTheAnchor() {
        Assertions.assertThat(HudSlot.below(HudSlotTest.ANCHOR)).isEqualTo(new HudSlot(HudSlot.Relation.BELOW, HudSlotTest.ANCHOR));
    }
    
    @Test
    public void sitsAboveEveryLayer() {
        Assertions.assertThat(HudSlot.top()).isEqualTo(new HudSlot(HudSlot.Relation.ABOVE, null));
    }
    
    @Test
    public void sitsBelowEveryLayer() {
        Assertions.assertThat(HudSlot.bottom()).isEqualTo(new HudSlot(HudSlot.Relation.BELOW, null));
    }
}
