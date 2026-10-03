# Voxy — Minecraft 26.2 Vulkan and external LOD API

This `codex/lodfix6-10000` branch targets Minecraft 26.2/Fabric on the
experimental Vulkan renderer. It is based on [MCRcortex/voxy](https://github.com/MCRcortex/voxy)
and preserves upstream history. It supports standalone Vulkan rendering and
external LOD API 5 for the matching
[Caustica branch](https://github.com/skayla2007/Caustica/tree/codex/lodfix6-10000).

- Requires Minecraft 26.2, Fabric Loader and Fabric API.
- Does not require Sodium or Caustica.
- Targets Vulkan only; there is no OpenGL fallback in this build.
- Voxy owns LOD generation, shape, selection, distance and visual detail.
- With Caustica ray tracing active, Voxy exports LOD data and suppresses its raster
  allocations/draws; Caustica owns rendering of that data.
- Caustica's 10000-block ray limit is implemented by Caustica, not this provider.

See [external renderer API and integration](docs/EXTERNAL-RENDERER-API.md) for
exported data, synchronization, caching and configuration details.

Build with JDK 25 using `./gradlew build` (`gradlew.bat build` on Windows). The
mod jar is written to `build/libs/`. Install only one Voxy jar in a Fabric 26.2
Vulkan instance.

This is an unofficial experimental branch, not an upstream release. The
upstream [license](LICENSE.md) is unchanged; it is **all rights reserved** and
states **do not redistribute**. A GitHub fork does not grant a separate
distribution license for binaries or source outside the permissions of GitHub's
service.
