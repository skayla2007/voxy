package me.cortex.voxy.client.config;

import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.NormalRenderPipeline;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sodium-free Voxy settings screen used by Mod Menu. */
public final class StandaloneVoxyConfigScreen extends Screen {
    private static final float[] SUBDIVISION_VALUES = {28, 40, 64, 96, 128, 192, 256};

    private final Screen parent;
    private final VoxyConfig config = VoxyConfig.CONFIG;
    private boolean applied;

    public StandaloneVoxyConfigScreen(Screen parent) {
        super(Component.translatable("voxy.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(310, this.width - 40);
        int x = (this.width - buttonWidth) / 2;
        int y = 42;

        addOption(x, y, buttonWidth, () -> boolLabel("voxy.config.general.enabled", config.enabled),
                () -> config.enabled = !config.enabled);
        addOption(x, y += 24, buttonWidth, () -> boolLabel("voxy.config.general.rendering", config.enableRendering),
                () -> config.enableRendering = !config.enableRendering);
        addOption(x, y += 24, buttonWidth, () -> boolLabel("voxy.config.general.ingest", config.ingestEnabled),
                () -> config.ingestEnabled = !config.ingestEnabled);
        addStepper(x, y += 24, buttonWidth,
                () -> valueLabel("voxy.config.general.renderDistance", Component.translatable(
                        "voxy.config.units.chunks", Math.round(config.sectionRenderDistance * 32))),
                () -> config.sectionRenderDistance = Math.max(0.625f, config.sectionRenderDistance - 0.0625f),
                () -> config.sectionRenderDistance = Math.min(64.0f, config.sectionRenderDistance + 0.0625f));
        addStepper(x, y += 24, buttonWidth,
                () -> valueLabel("voxy.config.general.serviceThreads", Integer.toString(config.serviceThreads)),
                () -> config.serviceThreads = Math.max(1, config.serviceThreads - 1),
                () -> config.serviceThreads = Math.min(CpuLayout.getCoreCount(), config.serviceThreads + 1));
        addOption(x, y += 24, buttonWidth,
                () -> valueLabel("voxy.config.general.subDivisionSize", Integer.toString(Math.round(config.subDivisionSize))),
                () -> config.subDivisionSize = nextSubdivision(config.subDivisionSize));
        addOption(x, y += 24, buttonWidth,
                () -> valueLabel("voxy.config.general.environmental_fog", Component.translatable(
                        "voxy.config.general.environmental_fog." + config.getFogMode().name().toLowerCase(java.util.Locale.ROOT))),
                () -> config.setFogMode(next(config.getFogMode())));
        addOption(x, y += 24, buttonWidth,
                () -> valueLabel("voxy.config.general.ssao_mode", Component.translatable(
                        "voxy.config.general.ssao_mode." + config.getSSAOMode().name().toLowerCase(java.util.Locale.ROOT))),
                () -> config.setSSAOMode(next(config.getSSAOMode())));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> closeAndApply())
                .bounds(x, y + 34, buttonWidth, 20).build());
    }

    private void addOption(int x, int y, int width, java.util.function.Supplier<Component> label, Runnable action) {
        Button button = Button.builder(label.get(), ignored -> {
            action.run();
            ignored.setMessage(label.get());
        }).bounds(x, y, width, 20).build();
        this.addRenderableWidget(button);
    }

    private void addStepper(int x, int y, int width, java.util.function.Supplier<Component> label,
                            Runnable decrease, Runnable increase) {
        int side = 24;
        Button value = Button.builder(label.get(), ignored -> {}).bounds(x + side + 2, y, width - side * 2 - 4, 20).build();
        this.addRenderableWidget(Button.builder(Component.literal("−"), ignored -> {
            decrease.run();
            value.setMessage(label.get());
        }).bounds(x, y, side, 20).build());
        this.addRenderableWidget(value);
        this.addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            increase.run();
            value.setMessage(label.get());
        }).bounds(x + width - side, y, side, 20).build());
    }

    private static Component boolLabel(String key, boolean value) {
        return valueLabel(key, Component.translatable(value ? "options.on" : "options.off").getString());
    }

    private static Component valueLabel(String key, String value) {
        return Component.translatable(key).append(": ").append(value);
    }

    private static Component valueLabel(String key, Component value) {
        return Component.translatable(key).append(": ").append(value);
    }

    private static float nextSubdivision(float current) {
        for (float value : SUBDIVISION_VALUES) {
            if (value > current + 0.5f) return value;
        }
        return SUBDIVISION_VALUES[0];
    }

    private static NormalRenderPipeline.FogMode next(NormalRenderPipeline.FogMode current) {
        var values = NormalRenderPipeline.FogMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static SSAO.SSAOMode next(SSAO.SSAOMode current) {
        var values = SSAO.SSAOMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void closeAndApply() {
        if (!this.applied) {
            this.applied = true;
            this.config.save();
            applyRuntimeChanges();
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    private void applyRuntimeChanges() {
        var holder = IVoxyRenderSystemHolder.getNullableHolder();
        if (!config.enabled) {
            if (holder != null) holder.voxy$shutdownRenderer();
            if (VoxyCommon.getInstance() != null) VoxyCommon.shutdownInstance();
            return;
        }

        if (ClientSessionEvents.inSession && VoxyCommon.getInstance() == null) {
            VoxyCommon.createInstance();
        }
        if (holder != null) {
            holder.voxy$shutdownRenderer();
            if (config.enableRendering) holder.voxy$createRenderer();
        }
        var instance = VoxyCommon.getInstance();
        if (instance != null) instance.updateDedicatedThreads();
    }

    @Override
    public void onClose() {
        closeAndApply();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
