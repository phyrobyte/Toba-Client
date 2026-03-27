package dev.toba.client.screens.gui.proxy;

import dev.toba.client.api.proxy.ProxyManager;
import dev.toba.client.api.proxy.ProxyProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public class ProxyListWidget extends AlwaysSelectedEntryListWidget<ProxyListWidget.ProxyEntry> {
    private final ProxyManagerScreen owner;

    public ProxyListWidget(ProxyManagerScreen owner, MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, itemHeight);
        this.owner = owner;
        this.centerListVertically = false;
    }

    public void reload(List<ProxyProfile> profiles, UUID preferredSelectionId) {
        clearEntries();

        for (ProxyProfile profile : profiles) {
            addEntry(new ProxyEntry(owner, profile));
        }

        ProxyEntry selectedEntry = null;
        if (preferredSelectionId != null) {
            selectedEntry = getEntry(preferredSelectionId);
        }

        if (selectedEntry == null) {
            ProxyProfile activeProfile = ProxyManager.getInstance().getActiveProfile();
            if (activeProfile != null) {
                selectedEntry = getEntry(activeProfile.id());
            }
        }

        if (selectedEntry == null && !children().isEmpty()) {
            selectedEntry = children().getFirst();
        }

        setSelected(selectedEntry);
    }

    @Override
    public void setSelected(ProxyEntry entry) {
        super.setSelected(entry);
        owner.onSelectionChanged(entry == null ? null : entry.profile());
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 24, 360);
    }

    private ProxyEntry getEntry(UUID id) {
        for (ProxyEntry entry : children()) {
            if (entry.profile().id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public static final class ProxyEntry extends AlwaysSelectedEntryListWidget.Entry<ProxyEntry> {
        private static final int ACTIVE_BADGE_COLOR = 0xAA2E7D32;
        private static final int ACTIVE_BADGE_TEXT_COLOR = 0xFFE8F5E9;
        private static final int TITLE_COLOR = 0xFFFFFFFF;
        private static final int SUBTITLE_COLOR = 0xFFB0BEC5;
        private static final int META_COLOR = 0xFF90A4AE;

        private final ProxyManagerScreen owner;
        private final ProxyProfile profile;

        private ProxyEntry(ProxyManagerScreen owner, ProxyProfile profile) {
            this.owner = owner;
            this.profile = profile;
        }

        public ProxyProfile profile() {
            return profile;
        }

        @Override
        public Text getNarration() {
            return Text.literal(profile.displayName() + ", " + profile.shortSummary());
        }

        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, boolean hovered, float delta) {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int x = getContentX() + 6;
            int y = getContentY() + 5;

            drawContext.drawTextWithShadow(textRenderer, Text.literal(profile.displayName()), x, y, TITLE_COLOR);
            drawContext.drawTextWithShadow(textRenderer, Text.literal(profile.shortSummary()), x, y + 12, SUBTITLE_COLOR);
            drawContext.drawTextWithShadow(textRenderer, Text.literal(profile.authSummary()), x, y + 24, META_COLOR);

            if (ProxyManager.getInstance().isActive(profile.id())) {
                String badgeText = "ACTIVE";
                int badgeWidth = textRenderer.getWidth(badgeText) + 10;
                int badgeX = getContentRightEnd() - badgeWidth - 6;
                int badgeY = y;
                drawContext.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 12, ACTIVE_BADGE_COLOR);
                drawContext.drawTextWithShadow(textRenderer, badgeText, badgeX + 5, badgeY + 2, ACTIVE_BADGE_TEXT_COLOR);
            }
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubleClick) {
            owner.selectProfile(profile.id(), doubleClick);
            return true;
        }
    }
}
