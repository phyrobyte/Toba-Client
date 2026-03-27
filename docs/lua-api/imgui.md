# ImGui API

The `imgui` table provides bindings to Dear ImGui for rendering custom HUD overlays. These functions should **only** be called inside the `onRender()` callback.

## Window Management

### `imgui.begin(title, flags?)`

Begins a new ImGui window. Returns `true` if the window is visible (not collapsed).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `title` | string | required | Window title (also used as unique ID) |
| `flags` | integer | `0` | Combination of window flags |

**Must** be paired with `imgui.finish()`.

```lua
if imgui.begin("My Window", imgui.Flags.NoResize) then
    imgui.text("Window content here")
end
imgui.finish()  -- always call, even if begin() returned false
```

### `imgui.finish()`

Ends the current ImGui window. Must be called after every `imgui.begin()`, regardless of the return value.

### `imgui.setNextWindowPos(x, y)`

Sets the position of the next window.

| Parameter | Type | Description |
|-----------|------|-------------|
| `x` | number | X position in pixels from left edge |
| `y` | number | Y position in pixels from top edge |

### `imgui.setNextWindowSize(w, h)`

Sets the size of the next window.

| Parameter | Type | Description |
|-----------|------|-------------|
| `w` | number | Width in pixels |
| `h` | number | Height in pixels |

## Text

### `imgui.text(str)`

Displays a line of text.

```lua
imgui.text("Hello, world!")
imgui.text("Health: " .. player.health())
```

### `imgui.textColored(r, g, b, a, str)`

Displays colored text.

| Parameter | Type | Range | Description |
|-----------|------|-------|-------------|
| `r` | number | 0.0 - 1.0 | Red component |
| `g` | number | 0.0 - 1.0 | Green component |
| `b` | number | 0.0 - 1.0 | Blue component |
| `a` | number | 0.0 - 1.0 | Alpha (opacity) |
| `str` | string | -- | Text to display |

```lua
imgui.textColored(1, 0, 0, 1, "This is red!")
imgui.textColored(0, 1, 0, 0.5, "Semi-transparent green")
```

## Widgets

### `imgui.button(label)`

Displays a clickable button. Returns `true` when clicked.

| Parameter | Type | Description |
|-----------|------|-------------|
| `label` | string | Button text |

```lua
if imgui.button("Click Me") then
    chat.info("Button was clicked!")
end
```

## Layout

### `imgui.separator()`

Draws a horizontal line separator.

### `imgui.sameLine()`

Places the next element on the same line as the previous one.

```lua
imgui.text("Left")
imgui.sameLine()
imgui.text("Right")  -- appears on the same line
```

### `imgui.dummy(w, h)`

Inserts invisible spacing.

| Parameter | Type | Description |
|-----------|------|-------------|
| `w` | number | Width in pixels |
| `h` | number | Height in pixels |

## Query

### `imgui.getWindowPosX()`
Returns the X position of the current window.

### `imgui.getWindowPosY()`
Returns the Y position of the current window.

### `imgui.getFontSize()`
Returns the current font size in pixels.

### `imgui.isWindowFocused()`
Returns `true` if the current window has keyboard/mouse focus.

### `imgui.wantCaptureMouse()`
Returns `true` if ImGui wants to capture mouse input (e.g., cursor is over a window).

## Window Flags

Access flags via `imgui.Flags`:

| Flag | Description |
|------|-------------|
| `imgui.Flags.NoTitleBar` | Hide the title bar |
| `imgui.Flags.NoResize` | Prevent resizing |
| `imgui.Flags.AlwaysAutoResize` | Auto-size to fit content |
| `imgui.Flags.NoBackground` | Transparent background |
| `imgui.Flags.NoInputs` | Ignore mouse and keyboard |
| `imgui.Flags.NoMove` | Prevent dragging |
| `imgui.Flags.NoScrollbar` | Hide scrollbar |

Combine flags with `+` or bitwise OR:

```lua
local flags = imgui.Flags.NoTitleBar + imgui.Flags.NoResize + imgui.Flags.AlwaysAutoResize
imgui.begin("Clean HUD", flags)
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `imgui.begin(title, flags?)` | boolean | Start a window |
| `imgui.finish()` | nil | End a window |
| `imgui.setNextWindowPos(x, y)` | nil | Set next window position |
| `imgui.setNextWindowSize(w, h)` | nil | Set next window size |
| `imgui.text(str)` | nil | Display text |
| `imgui.textColored(r,g,b,a,str)` | nil | Display colored text |
| `imgui.button(label)` | boolean | Clickable button |
| `imgui.separator()` | nil | Horizontal line |
| `imgui.sameLine()` | nil | Same-line layout |
| `imgui.dummy(w, h)` | nil | Invisible spacer |
| `imgui.getWindowPosX()` | number | Window X position |
| `imgui.getWindowPosY()` | number | Window Y position |
| `imgui.getFontSize()` | number | Font size in px |
| `imgui.isWindowFocused()` | boolean | Window has focus |
| `imgui.wantCaptureMouse()` | boolean | ImGui wants mouse |
