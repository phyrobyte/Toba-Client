package dev.toba.client.screens.gui.proxy;

import dev.toba.client.api.proxy.ProxyManager;
import dev.toba.client.api.proxy.ProxyProfile;
import dev.toba.client.api.proxy.ProxyTestResult;
import dev.toba.client.mixin.MultiplayerScreenAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.UUID;

public class ProxyManagerScreen extends Screen {
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 88;
    private static final int LIST_ITEM_HEIGHT = 42;
    private static final int STATUS_Y_OFFSET = 102;
    private static final int TITLE_Y = 10;

    private final Screen parent;
    private final String preferredTestTarget;
    private final ThreePartsLayoutWidget layout;

    private ProxyListWidget listWidget;
    private ButtonWidget useButton;
    private ButtonWidget disableButton;
    private ButtonWidget testButton;
    private ButtonWidget addButton;
    private ButtonWidget editButton;
    private ButtonWidget deleteButton;
    private ButtonWidget backButton;

    private UUID selectedProfileId;
    private boolean testing;
    private String statusMessage = "";
    private int statusColor = 0xFFAEC6CF;

    public ProxyManagerScreen(Screen parent, String preferredTestTarget) {
        super(Text.literal("Proxy Manager"));
        this.parent = parent;
        this.preferredTestTarget = preferredTestTarget;
        this.layout = new ThreePartsLayoutWidget(this, HEADER_HEIGHT, FOOTER_HEIGHT);
    }

    @Override
    protected void init() {
        listWidget = (ProxyListWidget) layout.addBody(new ProxyListWidget(
                this,
                client,
                width,
                layout.getContentHeight(),
                layout.getHeaderHeight(),
                LIST_ITEM_HEIGHT
        ));

        DirectionalLayoutWidget footer = (DirectionalLayoutWidget) layout.addFooter(DirectionalLayoutWidget.vertical().spacing(4));
        footer.getMainPositioner().alignHorizontalCenter();

        DirectionalLayoutWidget row1 = (DirectionalLayoutWidget) footer.add(DirectionalLayoutWidget.horizontal().spacing(4));
        DirectionalLayoutWidget row2 = (DirectionalLayoutWidget) footer.add(DirectionalLayoutWidget.horizontal().spacing(4));
        DirectionalLayoutWidget row3 = (DirectionalLayoutWidget) footer.add(DirectionalLayoutWidget.horizontal().spacing(4));

        useButton = (ButtonWidget) row1.add(ButtonWidget.builder(Text.literal("Use Proxy"), button -> activateSelectedProxy()).width(100).build());
        disableButton = (ButtonWidget) row1.add(ButtonWidget.builder(Text.literal("Disable"), button -> disableProxy()).width(100).build());
        testButton = (ButtonWidget) row1.add(ButtonWidget.builder(Text.literal("Test"), button -> testSelectedProxy()).width(100).build());

        addButton = (ButtonWidget) row2.add(ButtonWidget.builder(Text.literal("Add Proxy"), button -> openEditor(null)).width(100).build());
        editButton = (ButtonWidget) row2.add(ButtonWidget.builder(Text.literal("Edit"), button -> editSelectedProxy()).width(100).build());
        deleteButton = (ButtonWidget) row2.add(ButtonWidget.builder(Text.literal("Delete"), button -> confirmDeleteSelectedProxy()).width(100).build());

        backButton = (ButtonWidget) row3.add(ButtonWidget.builder(ScreenTexts.BACK, button -> close()).width(100).build());

        layout.forEachChild(widget -> addDrawableChild(widget));
        refreshWidgetPositions();
        refreshFromManager(selectedProfileId);
    }

    @Override
    protected void refreshWidgetPositions() {
        layout.refreshPositions();
        if (listWidget != null) {
            listWidget.position(width, layout);
        }
    }

    @Override
    public void close() {
        if (client == null) {
            return;
        }

        if (parent instanceof MultiplayerScreen multiplayerScreen) {
            Screen multiplayerParent = ((MultiplayerScreenAccessor) multiplayerScreen).toba$getParentScreen();
            client.setScreen(new MultiplayerScreen(multiplayerParent));
            return;
        }

        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        renderBackground(drawContext, mouseX, mouseY, delta);
        super.render(drawContext, mouseX, mouseY, delta);
        renderDisableButton(drawContext);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, TITLE_Y, 0xFFFFFFFF);

        ProxyProfile activeProfile = ProxyManager.getInstance().getActiveProfile();
        String activeText = activeProfile == null
                ? "Active: Direct connection"
                : "Active: " + activeProfile.displayName() + " (" + activeProfile.shortSummary() + ")";
        int activeColor = activeProfile == null ? 0xFFCFD8DC : 0xFFA5D6A7;
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal(activeText), width / 2, 28, activeColor);

        if (!statusMessage.isBlank()) {
            drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage), width / 2, height - STATUS_Y_OFFSET, statusColor);
        }
    }

    private void renderDisableButton(DrawContext drawContext) {
        if (disableButton == null || !disableButton.visible || !ProxyManager.getInstance().hasActiveProxy()) {
            return;
        }

        int x = disableButton.getX();
        int y = disableButton.getY();
        int right = x + disableButton.getWidth();
        int bottom = y + disableButton.getHeight();

        int borderColor;
        int fillColor;
        int textColor;
        if (!disableButton.active) {
            borderColor = 0xFF4A2323;
            fillColor = 0xCC3A1B1B;
            textColor = 0xFFB9A4A4;
        } else if (disableButton.isHovered() || disableButton.isFocused()) {
            borderColor = 0xFFFFB3B3;
            fillColor = 0xFFE24A4A;
            textColor = 0xFFFFFFFF;
        } else {
            borderColor = 0xFFF08A8A;
            fillColor = 0xFFB93737;
            textColor = 0xFFFFFFFF;
        }

        drawContext.fill(x, y, right, bottom, borderColor);
        drawContext.fill(x + 1, y + 1, right - 1, bottom - 1, fillColor);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                disableButton.getMessage(),
                x + disableButton.getWidth() / 2,
                y + (disableButton.getHeight() - 8) / 2,
                textColor
        );
    }

    public void onSelectionChanged(ProxyProfile profile) {
        selectedProfileId = profile == null ? null : profile.id();
        updateButtonStates();
    }

    public void selectProfile(UUID profileId, boolean activateOnDoubleClick) {
        selectedProfileId = profileId;
        refreshFromManager(profileId);
        if (activateOnDoubleClick) {
            activateSelectedProxy();
        }
    }

    public void refreshFromManager(UUID preferredSelectionId) {
        if (listWidget == null) {
            return;
        }

        listWidget.reload(ProxyManager.getInstance().getProfiles(), preferredSelectionId);
        updateButtonStates();
    }

    public String getPreferredTestTarget() {
        return preferredTestTarget;
    }

    private void updateButtonStates() {
        ProxyProfile selected = selectedProfile();
        boolean hasSelection = selected != null;
        boolean isActive = hasSelection && ProxyManager.getInstance().isActive(selected.id());

        useButton.active = hasSelection && !testing && !isActive;
        disableButton.active = ProxyManager.getInstance().hasActiveProxy() && !testing;
        testButton.active = hasSelection && !testing;
        addButton.active = !testing;
        editButton.active = hasSelection && !testing;
        deleteButton.active = hasSelection && !testing;
        backButton.active = !testing;

        useButton.setMessage(Text.literal(isActive ? "In Use" : "Use Proxy"));
    }

    private ProxyProfile selectedProfile() {
        return selectedProfileId == null ? null : ProxyManager.getInstance().getProfile(selectedProfileId).orElse(null);
    }

    private void activateSelectedProxy() {
        ProxyProfile selected = selectedProfile();
        if (selected == null) {
            return;
        }

        ProxyManager.getInstance().setActiveProfile(selected.id());
        statusMessage = "";
        refreshFromManager(selected.id());
    }

    private void disableProxy() {
        ProxyManager.getInstance().clearActiveProfile();
        statusMessage = "Proxy disabled. Multiplayer connections will use direct networking.";
        statusColor = 0xFFCFD8DC;
        refreshFromManager(selectedProfileId);
    }

    private void openEditor(ProxyProfile profile) {
        if (client == null) {
            return;
        }
        client.setScreen(new ProxyEditScreen(this, profile, preferredTestTarget));
    }

    private void editSelectedProxy() {
        ProxyProfile selected = selectedProfile();
        if (selected != null) {
            openEditor(selected);
        }
    }

    private void confirmDeleteSelectedProxy() {
        ProxyProfile selected = selectedProfile();
        if (selected == null || client == null) {
            return;
        }

        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ProxyManager.getInstance().removeProfile(selected.id());
                selectedProfileId = null;
                statusMessage = "Deleted " + selected.displayName();
                statusColor = 0xFFFFCDD2;
                refreshFromManager(null);
            }
            if (client != null) {
                client.setScreen(this);
            }
        }, Text.literal("Delete Proxy"), Text.literal("Remove " + selected.displayName() + "?"), Text.literal("Delete"), ScreenTexts.CANCEL));
    }

    private void testSelectedProxy() {
        ProxyProfile selected = selectedProfile();
        if (selected == null || client == null) {
            return;
        }

        testing = true;
        statusMessage = "Testing " + selected.displayName() + "...";
        statusColor = 0xFFAEC6CF;
        updateButtonStates();

        ProxyManager.getInstance().testProxy(selected, preferredTestTarget).whenComplete((result, throwable) -> client.execute(() -> {
            if (client.currentScreen != this) {
                return;
            }

            testing = false;
            if (throwable != null) {
                statusMessage = throwable.getMessage() == null ? "Proxy test failed" : throwable.getMessage();
                statusColor = 0xFFFFCDD2;
            } else {
                applyTestResult(result);
            }
            updateButtonStates();
        }));
    }

    private void applyTestResult(ProxyTestResult result) {
        if (result == null) {
            statusMessage = "Proxy test did not return a result.";
            statusColor = 0xFFFFCDD2;
            return;
        }

        statusMessage = result.message() + " (" + result.durationMs() + " ms)";
        statusColor = result.success() ? 0xFFA5D6A7 : 0xFFFFCDD2;
    }
}
