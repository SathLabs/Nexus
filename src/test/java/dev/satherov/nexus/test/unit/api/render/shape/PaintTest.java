package dev.satherov.nexus.test.unit.api.render.shape;

import dev.satherov.nexus.api.render.shape.Paint;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the line widths a paint accepts, its factories, and its copies.
///
public class PaintTest {
    
    private static final Paint PAINT = new Paint(0xFFFF0000, 4.0F, 0x8000FF00, Paint.Depth.HIDDEN);
    
    @Test
    public void refusesALineWidthOfZero() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Paint(0xFFFF0000, 0.0F, 0, Paint.Depth.VISIBLE));
    }
    
    @Test
    public void refusesANegativeLineWidth() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Paint(0xFFFF0000, -1.0F, 0, Paint.Depth.VISIBLE));
    }
    
    @Test
    public void refusesALineWidthThatIsNotANumber() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Paint(0xFFFF0000, Float.NaN, 0, Paint.Depth.VISIBLE));
    }
    
    @Test
    public void acceptsTheSmallestPositiveLineWidth() {
        Assertions.assertThat(new Paint(0xFFFF0000, Float.MIN_VALUE, 0, Paint.Depth.VISIBLE).lineWidth()).isEqualTo(Float.MIN_VALUE);
    }
    
    @Test
    public void strokesAtVanillasLineWidthWithoutAFill() {
        Assertions.assertThat(Paint.DEFAULT_LINE_WIDTH).isEqualTo(2.5F);
        Assertions.assertThat(Paint.stroke(0xFFFF0000)).isEqualTo(new Paint(0xFFFF0000, Paint.DEFAULT_LINE_WIDTH, 0, Paint.Depth.VISIBLE));
    }
    
    @Test
    public void fillsWithoutAStroke() {
        Paint paint = Paint.fill(0x8000FF00);
        Assertions.assertThat(paint.stroke()).isZero();
        Assertions.assertThat(paint.fill()).isEqualTo(0x8000FF00);
        Assertions.assertThat(paint.depth()).isEqualTo(Paint.Depth.VISIBLE);
    }
    
    @Test
    public void copiesWithTheStroke() {
        Assertions.assertThat(PaintTest.PAINT.withStroke(0xFF0000FF)).isEqualTo(new Paint(0xFF0000FF, 4.0F, 0x8000FF00, Paint.Depth.HIDDEN));
    }
    
    @Test
    public void copiesWithTheLineWidth() {
        Assertions.assertThat(PaintTest.PAINT.withLineWidth(1.0F)).isEqualTo(new Paint(0xFFFF0000, 1.0F, 0x8000FF00, Paint.Depth.HIDDEN));
    }
    
    @Test
    public void copiesWithTheFill() {
        Assertions.assertThat(PaintTest.PAINT.withFill(0)).isEqualTo(new Paint(0xFFFF0000, 4.0F, 0, Paint.Depth.HIDDEN));
    }
    
    @Test
    public void copiesWithTheDepth() {
        Assertions.assertThat(PaintTest.PAINT.withDepth(Paint.Depth.ALWAYS)).isEqualTo(new Paint(0xFFFF0000, 4.0F, 0x8000FF00, Paint.Depth.ALWAYS));
    }
    
    @Test
    public void refusesACopyWithALineWidthOfZero() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> PaintTest.PAINT.withLineWidth(0.0F));
    }
}
