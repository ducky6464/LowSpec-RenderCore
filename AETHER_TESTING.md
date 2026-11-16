# Aether / LowSpec RenderCore test checklist

## Current build status

The current branch contains a live CPU capture path and an opt-in custom GPU **mirror** pass for ordinary player base models. Vanilla rendering remains enabled at all times. The mirror is a visual integration test, not a replacement renderer and not an FPS mode.

The GPU vertex ring is now uploaded only when mirror mode is explicitly enabled. With mirror mode OFF, the mod still gathers bounded CPU-side player geometry and draw-plan diagnostics, but it does not upload those meshes to the GPU.

## 1. Install and launch

1. Build the `aether-engine-prototype` branch with the successful GitHub Actions build artifact.
2. Install it in Minecraft 1.21.11 with Fabric and Java 21.
3. Launch the client and enter a single-player world first.
4. Wait several seconds, then run:

```text
/rendercore stats
```

Check that player snapshots are being captured and that ordinary visible standing players appear as custom-base candidates. Players in unsupported poses or with unsupported effects should remain vanilla fallbacks.

## 2. Validate the custom mirror pass

Run:

```text
/rendercore aether mirror on
```

Walk around players with different skins and camera angles. Then run:

```text
/rendercore stats
```

Check the Aether player mirror status:
- A successful frame should report submitted player base-model ranges.
- Skipped frames can occur when there are no eligible players or no safe GPU ring slot.
- Failed frames should remain at zero. If they increase, turn mirror mode OFF and send the latest log.
- GPU uploads should increase only while mirror mode is ON.

Turn the test off afterward:

```text
/rendercore aether mirror off
```

Vanilla base models and all player feature layers remain active in both modes. Do not use mirror mode for performance comparisons because it intentionally adds extra draw work.

## 3. Check fallback and stability

Test:
1. An ordinary standing player.
2. A player sneaking, swimming, gliding, sleeping, or using an unsupported pose.
3. Players with different skins.
4. A world with several visible players.
5. A crowded multiplayer lobby, if available.
6. Leave and rejoin a world, then repeat the mirror test.

Unsupported player states must continue to render through vanilla. Watch for missing geometry, incorrect skin mapping, flicker, transparency problems, crashes, or a rising failed-frame count.

## 4. Performance comparison

For FPS comparisons, keep mirror mode OFF. Compare the same scene, view direction, render distance, and graphics settings with the relevant LowSpec features enabled and disabled. Record average FPS, lowest sustained FPS, and the full `/rendercore stats` output.

The mirror pass is not an FPS optimization. No performance improvement is claimed until measured on the target Chromebook.

## 5. If something fails

Send:
- The latest `latest.log`.
- A screenshot of the visual issue, if applicable.
- The complete `/rendercore stats` output.
- Whether mirror mode was ON or OFF and what player pose/scene was visible.

Do not leave mirror mode enabled if it causes visual corruption or repeated render-pass failures.
