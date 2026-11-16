package dev.lowspec.rendercore.aether;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AetherPlayerGeometryArenaTest {
    private static ModelPart oneCuboid() {
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                0, 0, 0, 0, 0, 2, 3, 4,
                0, 0, 0, false, 64, 64, EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cuboid), Map.of());
    }

    @Test
    void packsMultiplePlayerMeshesIntoOneContiguousArena() {
        AetherPlayerGeometryArena arena = new AetherPlayerGeometryArena(3, 6, 600);
        assertTrue(arena.capture(0, oneCuboid(), new MatrixStack()));
        MatrixStack translated = new MatrixStack();
        translated.translate(4, 0, 0);
        assertTrue(arena.capture(2, oneCuboid(), translated));
        assertEquals(2, arena.capturedPlayers());
        assertTrue(arena.hasGeometry(0));
        assertFalse(arena.hasGeometry(1));
        assertTrue(arena.hasGeometry(2));
        assertEquals(36, arena.vertexCount(0));
        assertEquals(36, arena.vertexCount(2));
        assertEquals(arena.floatCount(0), arena.offset(2));
        assertEquals(arena.floatCount(0) + arena.floatCount(2), arena.usedFloats());
        int first = arena.offset(2);
        assertEquals(arena.data()[first] - arena.data()[arena.offset(0)], 4.0f, 1.0e-5f);
    }

    @Test
    void rejectsDuplicateAndOutOfRangePlayerSlots() {
        AetherPlayerGeometryArena arena = new AetherPlayerGeometryArena(1, 6, 500);
        assertTrue(arena.capture(0, oneCuboid(), new MatrixStack()));
        assertFalse(arena.capture(0, oneCuboid(), new MatrixStack()));
        assertFalse(arena.capture(1, oneCuboid(), new MatrixStack()));
        assertEquals(2, arena.rejectedCount());
    }

    @Test
    void boundsTotalArenaCapacityAndCanReuseNextFrame() {
        AetherPlayerGeometryArena arena = new AetherPlayerGeometryArena(2, 6, 400);
        assertTrue(arena.capture(0, oneCuboid(), new MatrixStack()));
        assertFalse(arena.capture(1, oneCuboid(), new MatrixStack()));
        assertEquals(1, arena.overflowCount());
        arena.beginFrame();
        assertTrue(arena.capture(1, oneCuboid(), new MatrixStack()));
        assertEquals(1, arena.capturedPlayers());
        assertEquals(0, arena.offset(1));
    }

    @Test
    void rejectsInvalidCapacities() {
        assertThrows(IllegalArgumentException.class, () -> new AetherPlayerGeometryArena(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AetherPlayerGeometryArena(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new AetherPlayerGeometryArena(1, 1, 0));
    }
}
