# Voxy — Minecraft 26.2 Vulkan standalone branch

This `caustica` branch is a development base for Minecraft 26.2/Fabric on the
experimental Vulkan renderer. It is based on [MCRcortex/voxy](https://github.com/MCRcortex/voxy)
and preserves upstream history. It contains the standalone Vulkan Voxy renderer
**before** the experimental Caustica ray-tracing bridge was introduced.

- Requires Minecraft 26.2, Fabric Loader and Fabric API.
- Does not require Sodium or Caustica.
- Targets Vulkan only; there is no OpenGL fallback in this build.
- Voxy owns LOD generation, selection, distance and visual detail. Caustica
  integration work should consume those results rather than replace them.

Build with JDK 25 using `./gradlew build` (`gradlew.bat build` on Windows). The
mod jar is written to `build/libs/`. Install only one Voxy jar in a Fabric 26.2
Vulkan instance.

This is an unofficial experimental branch, not an upstream release. The
upstream [license](LICENSE.md) is unchanged; it is **all rights reserved** and
states **do not redistribute**. A GitHub fork does not grant a separate
distribution license for binaries or source outside the permissions of GitHub's
service.
