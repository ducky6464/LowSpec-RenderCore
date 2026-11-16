# Aether / LowSpec RenderCore test checklist

This build is a diagnostics and GPU-upload groundwork build. It does **not** render player models through a custom Aether draw pass yet. Vanilla remains the renderer.

## 1. Prepare the client

1. Install the latest JAR from the successful GitHub Actions **Build Fabric mod** run for commit `1de3e751e63494e4c9fa9c9700b2da003c313c89` or a newer passing commit.
2. Launch Minecraft 1.21.11 with Fabric.
3. Open LowSpec RenderCore settings, go to **Diagnostics**, and turn **General diagnostics ON**. This enables GPU timer-query sampling. Entity CPU extraction/enqueue timing also runs while Adaptive Entity Visibility is enabled.
4. Enter a single-player world first and wait about 30 seconds.

## 2. Check the profiler

Run:

```text
/rendercore stats
```

Relevant lines:
- **Extracted**: entity render states prepared in the last frame, with player count.
- **Render commands queued**: entity render calls queued in the last frame, with player count. This is CPU enqueue work, not GPU time.
- **Extraction avg/p95** and **Enqueue avg/p95**: rolling per-frame CPU timings.
- **RenderDispatcher CPU dispatch**: CPU time spent processing queued render commands.
- **GPU timer scope**: asynchronous GPU timer query around the RenderDispatcher call only. It may not include later deferred draw work, so do not treat it as total entity GPU cost.
- **Samples**: wait for values to populate. Busy skips are normal if Minecraft or another profiler owns the shared timer.

## 3. Test the GPU upload ring

Run:

```text
/rendercore aether upload-test
```

Wait for the next frame, then run:

```text
/rendercore stats
```

Expected:
- `Aether upload self-test: passed` (the buffer map/write succeeded and an upload fence was created; it does not wait for GPU completion).
- Upload count increases by one.
- Failed uploads and fence errors stay at zero.
- A small amount of retained GPU memory is expected (one 18 KiB ring slot). The slot is checked for completion when the ring tries to reuse it.

This test uploads one 72-byte identity record. It does **not** issue a draw call or change the picture on screen.

## 4. Compare normal gameplay

Test these scenes separately:
1. Empty single-player area.
2. Normal survival area with mobs and items.
3. Multiplayer area with several visible players.
4. Crowded lobby, if available.

For each scene, keep render distance, resolution, camera direction, and graphics settings the same. Record:
- Average FPS and lowest sustained FPS you observe.
- `/rendercore stats` output.
- Whether nearby players or entities disappear unexpectedly.
- Any visual glitches, freezes, or crashes.

Compare Adaptive Entity Visibility ON and OFF separately. The setting changes visibility distance under load, so note any entities that disappear at longer range.

## 5. Report problems

If it crashes, send the latest `latest.log` and crash report. For a visual or performance issue, include the scene, whether AEV was on, and the stats output.

Do not expect a custom player-rendering FPS gain from this build yet: the custom player GPU draw path is still unimplemented.
