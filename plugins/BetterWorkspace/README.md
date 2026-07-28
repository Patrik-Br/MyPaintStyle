# BetterWorkspace – JOSM Plugin

Workspace tweaks for JOSM. All menu-driven features live under **More tools → BetterWorkspace**
(each item is also a separate, shortcut-bindable, toolbar-registerable action — assign a key or add
it to your toolbar via JOSM's own Preferences → Shortcuts / toolbar customization), in this order:
- Arrange the docked side panels, remembered across restarts.
- Load a HOT Tasking Manager project's task grid as a data layer — including **private and draft**
  projects you have access to, not just public ones — via your personal TM API token.
- Set/update that HOT TM API token.
- Toggle the visibility of the currently active layer.
- Multi-validation prep — select all ways in the layer below the active one and add them to the
  todo plugin's list, for paging through task borders during a second/third mapping pass.
- Open/close a secondary, view-only map window that always tracks the main view's position and
  zoom, with its own independent set of which layers are shown.
- Rotate the whole map view (data + imagery) clockwise/counter-clockwise, or reset it.

**Note on "More tools":** that top-level menu isn't part of JOSM core's own default UI — core
creates it empty and hidden (`MainMenu.initialize()` calls `moreToolsMenu.setVisible(false)`), and
it only becomes visible in a stock JOSM because plugins like `utilsplugin2` or `buildings_tools`
populate it. BetterWorkspace makes it visible itself, so the submenu appears whether or not those
other plugins are installed. It attaches its own submenu via `SwingUtilities.invokeLater(...)`
rather than directly in the plugin constructor, so it runs after every other plugin's own
(synchronous) constructor-time menu setup — landing BetterWorkspace at the bottom of the list
regardless of plugin load order.

Separately, it also adds a **"Select objects"** entry to the right-click menu of JOSM's built-in
**Authors** panel (which otherwise only offers "Copy").

## License

GPLv3 — see [`LICENSE`](LICENSE). JOSM core is "GPLv2 or later," which makes GPLv3 a compatible
choice for plugins built against it.

Source was recovered by decompiling the previously jar-only plugin with
[CFR](https://www.benf.org/other/cfr/) on 2026-07-20, since no `.java` source existed in this repo
before then. `BetterWorkspacePlugin.java`, `RotatingProjection.java`, `ArrangePanelsDialog.java`
and `PanelReorderer.java` are that recovered original code (compiles and packages identically to
the original jar, `AuthorSelectHook.class` aside). `AuthorSelectHook.java` is new.

`ToggleActiveLayerAction.java`, `MultiValidationPrepAction.java` and `TodoBridge.java` are ported
from [3rdPassJOSMPlugin](https://github.com/MissingMaps/3rdPassJOSMPlugin) ("ThirdPassMM"), which
this plugin's two features replace — decompiled with
[Vineflower](https://github.com/Vineflower/vineflower) (no `.java` source existed there either).

## Build

From within this `BetterWorkspace/` folder (needs JDK 17+):

1. Download `josm-tested.jar` from https://josm.openstreetmap.de/josm-tested.jar into `lib/` (gitignored, not committed).
2. **Windows:** `build.bat`
   **Linux/Mac:** `./build.sh`

This produces `BetterWorkspace.jar` in this folder — copy to JOSM's plugins folder and restart JOSM.

## HOT Tasking Manager task grid loading (private/draft projects)

JOSM's built-in **File → Open Location** can load a public TM project's task grid via
`https://tasking-manager-production-api.hotosm.org/api/v2/projects/<id>/tasks/?as_file=true&format=geojson`,
but that only works for public projects — the TM API returns HTTP 403 for private or draft
projects even if you have access, since Open Location has no way to send an `Authorization` header.

**Set HOT TM API Token...** and **Load HOT TM Task Grid...** (both under More tools →
BetterWorkspace) work around that:

- Get your personal token from the TM website: **tasks.hotosm.org → Settings → enable "Expert
  mode" → API Key** card. It's stored via JOSM's own preferences (`Config.getPref()`), the same
  mechanism JOSM uses for its own OSM OAuth token.
- The token expires roughly 7 days after your last TM login (confirmed against the TM backend's
  `login_required` check, `hotosm/tasking-manager` on GitHub) — re-copy it periodically.
- **Load HOT TM Task Grid...** fetches `GET /projects/<id>/tasks/?as_file=true&format=geojson`
  itself via `HttpURLConnection`, attaching `Authorization: Token <token>` when a token is set, then
  parses the response with JOSM's own `org.openstreetmap.josm.io.GeoJSONReader.parseDataSet(...)`
  and adds it as a new `OsmDataLayer` — this works for public projects too (no token needed), so
  it's a drop-in replacement for the Ctrl+L workflow either way.

## The Authors-panel "Select objects" hook

JOSM's built-in Authors panel is `org.openstreetmap.josm.gui.dialogs.UserListDialog` — a core
class, not a plugin, and it exposes **no public extension point** for its right-click menu.
`AuthorSelectHook.java` reaches it via reflection:

- Gets the live instance via `MapFrame.getToggleDialog(UserListDialog.class)` (public API).
- Reflectively reads three **private** fields: `userTable` (`JTable`), `popupMenu` (`JPopupMenu`),
  `model` (package-private `UserTableModel`) — confirmed present with these exact names/types via
  `javap -p` against `josm-tested.jar`.
- Adds one `JMenuItem` directly to the live `popupMenu`.
- On click, delegates to the model's own `selectPrimitivesOwnedBy(int...)` — the same method the
  dialog's built-in "Select" button already calls — rather than reimplementing selection logic.
- Hooked from `BetterWorkspacePlugin.mapFrameInitialized`, with the same retry-timer pattern
  already used there for `PanelReorderer` (`UserListDialog` may not be ready the instant the map
  frame is created).

**This is inherently version-fragile** since it depends on private JOSM internals rather than a
documented API. If a future JOSM version renames/removes `userTable`, `popupMenu`, `model`, or
`selectPrimitivesOwnedBy`, the hook fails silently (caught, logged via `Logging.warn`, no crash) —
the Authors panel just goes back to only offering "Copy". If that happens, the fix is to re-run
`javap -p` against the new `josm-tested.jar` on `UserListDialog` and `UserListDialog$UserTableModel`
to find the new names.

## Secondary Map View

`SecondaryMapViewFrame.java` is a second, view-only map window with its own checkbox list of
layers, opened/closed via **Secondary Map View** (`SecondaryMapViewAction.java`). Two design points
worth knowing:

- **No manual position/zoom sync code exists, by design.** It paints layers via the main
  `MapView`'s own public `paintLayer(Layer, Graphics2D)` method, which always uses that MapView's
  *current* position/scale/projection — so as long as the secondary window's canvas is the same
  pixel size as the main map view (kept matched automatically via a resize listener + `pack()`),
  it's pixel-perfect in sync with the main view for free, on every repaint. This only works for a
  same-size window; a genuinely independently-sized/positioned second view would additionally need
  a private-field reflection read of `MapView`'s `registeredLayers` map plus a hand-built
  `MapViewState` for the target size — not implemented here.
- **Layer visibility is independent per window.** `paintLayer()` doesn't check `Layer.isVisible()`
  itself — the caller decides which layers to paint. So the checklist here tracks its own
  `Set<Layer>`, seeded from each layer's visibility at the time it's added to the list, but never
  written back to `Layer.isVisible()` afterwards — checking a box only affects this window.

Repaints are triggered by `NavigatableComponent.addZoomChangeListener` (pan/zoom) and
`MapView.addRepaintListener` (everything else — edits, tile loads, etc.), both public JOSM hooks.
The layer checklist stays in sync with `LayerManager.addAndFireLayerChangeListener`. Paint order
matches JOSM's own (`LayerManager.getLayers()` is topmost-first, so the loop here runs it in
reverse) — confirmed by decompiling `MainLayerManager.getVisibleLayersInZOrder()` rather than
guessing.

**Align to Main View** button: since this window has its own sidebar (of a different width than the
main window's toolbar/panels), lining up the two *windows'* edges doesn't line up the two *canvases*
— the actual map content ends up offset even though both show the identical position/zoom. This
button fixes that in one click: it re-syncs the canvas size (in case this window itself got resized,
which the automatic main-view-resize sync doesn't catch), then computes screen coordinates via
`getLocationOnScreen()` so this window's canvas ends up pixel-adjacent to the main view's canvas —
snapping left or right based on whichever side this window is currently closer to, so it matches
whatever rough arrangement you already dragged it into.

## Multi-validation prep and the todo-plugin bridge

**Multi-validation prep** looks at the layer directly below the currently active one in the Layers
panel, switches to it, selects all its ways, hands them to the todo plugin, then switches back —
one click instead of four to set up "page through every task border with the todo dialog" for a
second/third mapping pass.

`TodoBridge.java` is a reflection bridge (no compile-time dependency on any todo plugin jar) that
looks for a `ToggleDialog` whose class name is exactly `org.openstreetmap.josm.plugins.todo.TodoDialog`
— matched by this user's own `Todo_patrik` fork as well as the standard "todo" plugin, since the
fork kept the same package/class name. It tries the modern public `addItemsFromPrimitives(Collection)`
method first; if that doesn't exist (older todo plugin versions), it falls back to reflectively
invoking the dialog's private `actAdd`/`actSelect` action listeners directly, simulating clicks on
the "Add" and "Select" buttons. Either way, if no matching dialog is found (todo plugin not
installed) or all reflection paths fail, `addWaysToTodo` returns `false` and the action shows a
warning dialog rather than throwing — same fail-soft approach as the Authors-panel hook above.
