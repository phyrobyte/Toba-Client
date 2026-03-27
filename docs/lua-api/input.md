# Input API

The `input` table lets scripts check keyboard state.

## Functions

### `input.isKeyDown(keyCode)`
Returns `true` if the given key is currently pressed. Use the key constants below.

```lua
function onTick()
    if input.isKeyDown(input.KEY_R) then
        chat.info("R is pressed!")
    end
end
```

## Key Constants

### Letters
`input.KEY_A` through `input.KEY_Z`

### Numbers
`input.KEY_0` through `input.KEY_9`

### Special Keys
| Constant | Key |
|----------|-----|
| `KEY_SPACE` | Space |
| `KEY_SHIFT` | Left Shift |
| `KEY_CTRL` | Left Control |
| `KEY_ALT` | Left Alt |
| `KEY_TAB` | Tab |
| `KEY_ESCAPE` | Escape |
| `KEY_ENTER` | Enter |
| `KEY_BACKSPACE` | Backspace |

### Arrow Keys
`KEY_UP`, `KEY_DOWN`, `KEY_LEFT`, `KEY_RIGHT`

### Function Keys
`KEY_F1` through `KEY_F12`
