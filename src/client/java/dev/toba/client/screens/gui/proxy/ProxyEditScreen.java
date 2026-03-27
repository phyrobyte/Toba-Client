package dev.toba.client.screens.gui.proxy;

import com.google.common.net.HostAndPort;
import dev.toba.client.api.proxy.ProxyManager;
import dev.toba.client.api.proxy.ProxyProfile;
import dev.toba.client.api.proxy.ProxyTestResult;
import dev.toba.client.api.proxy.ProxyType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.UUID;

public class ProxyEditScreen extends Screen {
    private static final int FORM_WIDTH = 240;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_SPACING = 36;
    private static final int BUTTON_Y_OFFSET = 52;

    private final ProxyManagerScreen parent;
    private final ProxyProfile existingProfile;
    private final String preferredTestTarget;

    private ProxyType type;
    private boolean passwordVisible;
    private boolean testing;

    private TextFieldWidget nameField;
    private TextFieldWidget hostField;
    private TextFieldWidget portField;
    private TextFieldWidget usernameField;
    private TextFieldWidget passwordField;

    private ButtonWidget typeButton;
    private ButtonWidget togglePasswordButton;
    private ButtonWidget saveButton;
    private ButtonWidget testButton;
    private ButtonWidget cancelButton;

    private String validationMessage = "";
    private String statusMessage = "";
    private int statusColor = 0xFFAEC6CF;

    public ProxyEditScreen(ProxyManagerScreen parent, ProxyProfile existingProfile, String preferredTestTarget) {
        super(Text.literal(existingProfile == null ? "Add Proxy" : "Edit Proxy"));
        this.parent = parent;
        this.existingProfile = existingProfile;
        this.preferredTestTarget = preferredTestTarget;
        this.type = existingProfile == null ? ProxyType.SOCKS5 : existingProfile.type();
    }

    @Override
    protected void init() {
        int left = (width - FORM_WIDTH) / 2;
        int hostWidth = 170;
        int portWidth = FORM_WIDTH - hostWidth - 8;

        nameField = createField(left, 56, FORM_WIDTH, existingProfile == null ? "" : existingProfile.name(), 64);
        hostField = createField(left, 56 + FIELD_SPACING * 2, hostWidth, existingProfile == null ? "" : existingProfile.host(), 253);
        portField = createField(left + hostWidth + 8, 56 + FIELD_SPACING * 2, portWidth, existingProfile == null ? "1080" : Integer.toString(existingProfile.port()), 5);
        usernameField = createField(left, 56 + FIELD_SPACING * 3, FORM_WIDTH, existingProfile == null ? "" : existingProfile.username(), 128);
        passwordField = createField(left, 56 + FIELD_SPACING * 4, FORM_WIDTH - 64, existingProfile == null ? "" : existingProfile.password(), 128);
        passwordField.addFormatter((text, firstCharacterIndex) -> formatPassword(text));

        hostField.setTextPredicate(value -> !value.contains(" "));
        portField.setTextPredicate(value -> value.isEmpty() || value.matches("\\d{0,5}"));

        typeButton = addDrawableChild(ButtonWidget.builder(typeButtonText(), button -> {
                    type = type.next();
                    updateTypeUi();
                    updateValidation();
                })
                .dimensions(left, 56 + FIELD_SPACING, FORM_WIDTH, FIELD_HEIGHT)
                .build());

        togglePasswordButton = addDrawableChild(ButtonWidget.builder(togglePasswordText(), button -> {
                    passwordVisible = !passwordVisible;
                    togglePasswordButton.setMessage(togglePasswordText());
                })
                .dimensions(left + FORM_WIDTH - 56, 56 + FIELD_SPACING * 4, 56, FIELD_HEIGHT)
                .build());

        int buttonsY = height - BUTTON_Y_OFFSET;
        saveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save()).dimensions(width / 2 - 154, buttonsY, 100, FIELD_HEIGHT).build());
        testButton = addDrawableChild(ButtonWidget.builder(Text.literal("Test"), button -> testCurrentProxy()).dimensions(width / 2 - 50, buttonsY, 100, FIELD_HEIGHT).build());
        cancelButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).dimensions(width / 2 + 54, buttonsY, 100, FIELD_HEIGHT).build());

        setInitialFocus(nameField);
        updateTypeUi();
        updateValidation();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(nameField);
    }

    @Override
    public void resize(int width, int height) {
        String name = nameField == null ? "" : nameField.getText();
        String host = hostField == null ? "" : hostField.getText();
        String port = portField == null ? "" : portField.getText();
        String username = usernameField == null ? "" : usernameField.getText();
        String password = passwordField == null ? "" : passwordField.getText();
        boolean revealPassword = passwordVisible;
        super.resize(width, height);
        nameField.setText(name);
        hostField.setText(host);
        portField.setText(port);
        usernameField.setText(username);
        passwordField.setText(password);
        passwordVisible = revealPassword;
        togglePasswordButton.setMessage(togglePasswordText());
        updateTypeUi();
        updateValidation();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        renderBackground(drawContext, mouseX, mouseY, delta);
        super.render(drawContext, mouseX, mouseY, delta);

        int left = (width - FORM_WIDTH) / 2;
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);

        drawLabel(drawContext, Text.literal("Name"), left, 46);
        drawLabel(drawContext, Text.literal("Type"), left, 46 + FIELD_SPACING);
        drawLabel(drawContext, Text.literal("Host"), left, 46 + FIELD_SPACING * 2);
        drawLabel(drawContext, Text.literal("Port"), left + 178, 46 + FIELD_SPACING * 2);
        if (type == ProxyType.SOCKS4) {
            drawSocks4UserIdLabel(drawContext, left, 46 + FIELD_SPACING * 3);
        } else {
            drawLabel(drawContext, Text.literal("Username"), left, 46 + FIELD_SPACING * 3);
            drawLabel(drawContext, Text.literal("Password"), left, 46 + FIELD_SPACING * 4);
        }

        String message = !validationMessage.isBlank() ? validationMessage : statusMessage;
        int color = !validationMessage.isBlank() ? 0xFFFFCDD2 : statusColor;
        if (!message.isBlank()) {
            drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal(message), width / 2, height - 76, color);
        }
    }

    private void drawLabel(DrawContext drawContext, Text label, int x, int y) {
        drawContext.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFFFF);
    }

    private void drawSocks4UserIdLabel(DrawContext drawContext, int x, int y) {
        Text requiredLabel = Text.literal("User ID");
        drawLabel(drawContext, requiredLabel, x, y);
        drawContext.drawTextWithShadow(textRenderer, Text.literal("(Optional)"), x + textRenderer.getWidth(requiredLabel) + 4, y, 0xFF90A4AE);
    }

    private OrderedText formatPassword(String text) {
        if (passwordVisible) {
            return Text.literal(text).asOrderedText();
        }
        return Text.literal("*".repeat(text.length())).asOrderedText();
    }

    private Text typeButtonText() {
        return Text.literal("Type: " + type.getDisplayName());
    }

    private Text togglePasswordText() {
        return Text.literal(passwordVisible ? "Hide" : "Show");
    }

    private TextFieldWidget createField(int x, int y, int width, String initialText, int maxLength) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, width, FIELD_HEIGHT, Text.empty()));
        field.setMaxLength(maxLength);
        field.setText(initialText == null ? "" : initialText);
        field.setChangedListener(value -> updateValidation());
        return field;
    }

    private void updateTypeUi() {
        typeButton.setMessage(typeButtonText());

        boolean passwordSupported = type.supportsPassword();
        passwordField.visible = passwordSupported;
        passwordField.active = passwordSupported && !testing;
        togglePasswordButton.visible = passwordSupported;
        togglePasswordButton.active = passwordSupported && !testing;
    }

    private void updateValidation() {
        validationMessage = validateForm();

        boolean formValid = validationMessage.isBlank();
        boolean busy = testing;
        saveButton.active = formValid && !busy;
        testButton.active = formValid && !busy;
        cancelButton.active = !busy;
    }

    private String validateForm() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (host.isBlank()) {
            return "Proxy host is required.";
        }
        if (portText.isBlank()) {
            return "Proxy port is required.";
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            return "Proxy port must be numeric.";
        }

        if (port < 1 || port > 65535) {
            return "Proxy port must be between 1 and 65535.";
        }

        try {
            HostAndPort.fromParts(host, port);
        } catch (IllegalArgumentException ignored) {
            if (host.contains(" ") || host.startsWith(":")) {
                return "Proxy host is not valid.";
            }
        }

        if (type.supportsPassword() && !password.isBlank() && username.isBlank()) {
            return "A username is required when a password is set.";
        }

        return "";
    }

    private void save() {
        ProxyProfile profile = buildProfile();
        if (profile == null) {
            updateValidation();
            return;
        }

        ProxyManager.getInstance().upsertProfile(profile);
        parent.refreshFromManager(profile.id());
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void testCurrentProxy() {
        ProxyProfile profile = buildProfile();
        if (profile == null || client == null) {
            updateValidation();
            return;
        }

        testing = true;
        statusMessage = "Testing " + profile.displayName() + "...";
        statusColor = 0xFFAEC6CF;
        updateTypeUi();
        updateValidation();

        ProxyManager.getInstance().testProxy(profile, preferredTestTarget).whenComplete((result, throwable) -> client.execute(() -> {
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
            updateTypeUi();
            updateValidation();
        }));
    }

    private ProxyProfile buildProfile() {
        if (!validationMessage.isBlank()) {
            return null;
        }

        int port = Integer.parseInt(portField.getText().trim());
        UUID id = existingProfile == null ? UUID.randomUUID() : existingProfile.id();
        return new ProxyProfile(
                id,
                nameField.getText(),
                type,
                hostField.getText(),
                port,
                usernameField.getText(),
                passwordField.getText()
        );
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
