package dev.lowspec.rendercore.aether;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.SkinTextures;

import java.util.Arrays;

/**
 * Bounded, render-thread-owned snapshot queue for the base player model path.
 *
 * It deliberately stores only immutable render-state inputs, never live player
 * entities or feature-layer/item state. GPU geometry generation consumes these
 * snapshots in a later stage; until that draw path is active, vanilla rendering
 * remains authoritative.
 */
public final class AetherPlayerRenderQueue {
    public static final int DEFAULT_CAPACITY = 128;

    public static final int SPECTATOR = 1;
    public static final int INVISIBLE = 1 << 1;
    public static final int INVISIBLE_TO_PLAYER = 1 << 2;
    public static final int HURT = 1 << 3;
    public static final int SHAKING = 1 << 4;
    public static final int UPSIDE_DOWN = 1 << 5;
    public static final int BABY = 1 << 6;
    public static final int SNEAKING = 1 << 7;
    public static final int SWIMMING = 1 << 8;
    public static final int GLIDING = 1 << 9;
    public static final int SLEEPING = 1 << 10;
    public static final int OUTLINE = 1 << 11;
    public static final int ON_FIRE = 1 << 12;
    public static final int HAT_VISIBLE = 1 << 13;
    public static final int JACKET_VISIBLE = 1 << 14;
    public static final int LEFT_PANTS_VISIBLE = 1 << 15;
    public static final int RIGHT_PANTS_VISIBLE = 1 << 16;
    public static final int LEFT_SLEEVE_VISIBLE = 1 << 17;
    public static final int RIGHT_SLEEVE_VISIBLE = 1 << 18;
    public static final int CAPE_VISIBLE = 1 << 19;
    public static final int EXTRA_EARS = 1 << 20;
    public static final int APPLY_FLYING_ROTATION = 1 << 21;

    private final int capacity;
    private final SkinTextures[] skins;
    private final double[] x, y, z;
    private final float[] bodyYaw, pitch, headYaw, limbProgress, limbAmplitude, age, baseScale, ageScale;
    private final int[] light, outlineColor, flags, playerIds;
    private final float[] flyingRotation, glidingTicks;
    private int size;
    private long overflowCount;
    private long rejectedCount;

    public AetherPlayerRenderQueue() {
        this(DEFAULT_CAPACITY);
    }

    public AetherPlayerRenderQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        skins = new SkinTextures[capacity];
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        bodyYaw = new float[capacity];
        pitch = new float[capacity];
        headYaw = new float[capacity];
        limbProgress = new float[capacity];
        limbAmplitude = new float[capacity];
        age = new float[capacity];
        baseScale = new float[capacity];
        ageScale = new float[capacity];
        light = new int[capacity];
        outlineColor = new int[capacity];
        flags = new int[capacity];
        playerIds = new int[capacity];
        flyingRotation = new float[capacity];
        glidingTicks = new float[capacity];
    }

    public void beginFrame() {
        Arrays.fill(skins, 0, size, null);
        size = 0;
    }

    /** Snapshot the base-model inputs. Feature layers remain on vanilla. */
    public boolean capture(PlayerEntityRenderState state) {
        AetherRenderThread.assertCurrent();
        if (state == null || state.skinTextures == null) {
            rejectedCount++;
            return false;
        }
        if (size == capacity) {
            overflowCount++;
            return false;
        }

        int i = size++;
        skins[i] = state.skinTextures;
        x[i] = state.x;
        y[i] = state.y;
        z[i] = state.z;
        bodyYaw[i] = state.bodyYaw;
        pitch[i] = state.pitch;
        headYaw[i] = state.relativeHeadYaw;
        limbProgress[i] = state.limbSwingAnimationProgress;
        limbAmplitude[i] = state.limbSwingAmplitude;
        age[i] = state.age;
        baseScale[i] = state.baseScale;
        ageScale[i] = state.ageScale;
        light[i] = state.light;
        playerIds[i] = state.id;
        flyingRotation[i] = state.flyingRotation;
        glidingTicks[i] = state.glidingTicks;
        outlineColor[i] = state.outlineColor;
        flags[i] = (state.spectator ? SPECTATOR : 0)
                | (state.invisible ? INVISIBLE : 0)
                | (state.invisibleToPlayer ? INVISIBLE_TO_PLAYER : 0)
                | (state.hurt ? HURT : 0)
                | (state.shaking ? SHAKING : 0)
                | (state.flipUpsideDown ? UPSIDE_DOWN : 0)
                | (state.baby ? BABY : 0)
                | (state.sneaking ? SNEAKING : 0)
                | (state.isSwimming ? SWIMMING : 0)
                | (state.isGliding ? GLIDING : 0)
                | (state.sleepingDirection != null ? SLEEPING : 0)
                | (state.hasOutline() ? OUTLINE : 0)
                | (state.onFire ? ON_FIRE : 0)
                | (state.hatVisible ? HAT_VISIBLE : 0)
                | (state.jacketVisible ? JACKET_VISIBLE : 0)
                | (state.leftPantsLegVisible ? LEFT_PANTS_VISIBLE : 0)
                | (state.rightPantsLegVisible ? RIGHT_PANTS_VISIBLE : 0)
                | (state.leftSleeveVisible ? LEFT_SLEEVE_VISIBLE : 0)
                | (state.rightSleeveVisible ? RIGHT_SLEEVE_VISIBLE : 0)
                | (state.capeVisible ? CAPE_VISIBLE : 0)
                | (state.extraEars ? EXTRA_EARS : 0)
                | (state.applyFlyingRotation ? APPLY_FLYING_ROTATION : 0);
        return true;
    }

    public int size() { return size; }
    public int capacity() { return capacity; }
    public long overflowCount() { return overflowCount; }
    public long rejectedCount() { return rejectedCount; }
    public SkinTextures skin(int i) { checkIndex(i); return skins[i]; }
    public double x(int i) { checkIndex(i); return x[i]; }
    public double y(int i) { checkIndex(i); return y[i]; }
    public double z(int i) { checkIndex(i); return z[i]; }
    public float bodyYaw(int i) { checkIndex(i); return bodyYaw[i]; }
    public float pitch(int i) { checkIndex(i); return pitch[i]; }
    public float headYaw(int i) { checkIndex(i); return headYaw[i]; }
    public float limbProgress(int i) { checkIndex(i); return limbProgress[i]; }
    public float limbAmplitude(int i) { checkIndex(i); return limbAmplitude[i]; }
    public float age(int i) { checkIndex(i); return age[i]; }
    public float baseScale(int i) { checkIndex(i); return baseScale[i]; }
    public float ageScale(int i) { checkIndex(i); return ageScale[i]; }
    public int light(int i) { checkIndex(i); return light[i]; }
    public int outlineColor(int i) { checkIndex(i); return outlineColor[i]; }
    public int playerId(int i) { checkIndex(i); return playerIds[i]; }
    public float flyingRotation(int i) { checkIndex(i); return flyingRotation[i]; }
    public float glidingTicks(int i) { checkIndex(i); return glidingTicks[i]; }
    public int flags(int i) { checkIndex(i); return flags[i]; }

    private void checkIndex(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException("player snapshot " + i + " of " + size);
    }
}
